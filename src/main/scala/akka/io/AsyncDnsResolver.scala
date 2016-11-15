package akka.io

import java.io.File
import java.net.{Inet4Address, Inet6Address, InetAddress, InetSocketAddress}
import java.nio.file.Paths
import java.util.concurrent.{ThreadLocalRandom, TimeUnit}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.typesafe.config.Config
import ru.smslv.akka.dns.raw.{AAAARecord, ARecord, Answer, CNAMERecord, DnsClient, Question4, Question6, SRVRecord, SrvQuestion}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.{breakOut, immutable}
import scala.concurrent.duration.{Deadline, FiniteDuration}
import scala.io.Source
import scala.util.control.NonFatal

class AsyncDnsResolver(cache: SimpleDnsCache, config: Config) extends Actor with ActorLogging {

  import AsyncDnsResolver._

  private val systemNameServers =
    if (config.getBoolean("resolv-conf"))
      parseSystemNameServers(Paths.get("/etc/resolv.conf").toFile)
    else
      Option.empty[immutable.Seq[InetSocketAddress]]
  private val nameServers: immutable.Seq[InetSocketAddress] =
    systemNameServers.getOrElse(config.getStringList("nameservers").asScala.map(parseNameserverAddress)(breakOut))
  private val resolveIpv4 = config.getBoolean("resolve-ipv4")
  private val resolveIpv6 = config.getBoolean("resolve-ipv6")
  private val resolveSrv = config.getBoolean("resolve-srv")
  private val negativeTtl = config.getDuration("negative-ttl", TimeUnit.MILLISECONDS)
  private val minPositiveTtl = config.getDuration("max-positive-ttl", TimeUnit.MILLISECONDS)
  private val maxPositiveTtl = config.getDuration("max-positive-ttl", TimeUnit.MILLISECONDS)
  private val requestTtl = FiniteDuration(config.getDuration("request-ttl", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)

  private var requestId: Short = 0

  // A List is used over Vector here given the scans required in order to filter out expired elements.
  private var requests = List.empty[(Short, CurrentRequest)]

  private val resolvers: IndexedSeq[ActorRef] = nameServers.map({ ns =>
    context.actorOf(Props(classOf[DnsClient], ns, self))
  })(breakOut)

  if (resolvers.nonEmpty)
    log.info("Using the following DNS nameservers: {}", nameServers.mkString(", "))
  else
    log.warning("No DNS resolvers can be determined, either because of malformed configuration or perhaps an invalid /etc/resolv.conf if that's being used. Do not expect DNS names to be resolved.")

  private def nextId(): Short = {
    requestId = (requestId + 2).toShort
    requestId
  }

  override def receive = {
    case Dns.Resolve(name) if resolveSrv && isSrv(name) =>
      val caseFoldedName = name.toLowerCase

      cache.cached(caseFoldedName) match {
        case Some(answer) =>
          sender() ! answer
        case None =>
          val id = nextId()

          requests =
            requests.filter(_._2.expires.hasTimeLeft) :+
              id -> CurrentRequest(sender(),
                                   caseFoldedName,
                                   None,
                                   None,
                                   Some(Nil),
                                   maxPositiveTtl,
                                   requestTtl.fromNow)

          resolvers(ThreadLocalRandom.current().nextInt(resolvers.length)) ! SrvQuestion(id, caseFoldedName)
      }

    case Dns.Resolve(name) =>
      val caseFoldedName = name.toLowerCase

      cache.cached(caseFoldedName) match {
        case Some(answer) =>
          sender() ! answer
        case None if isInetAddress(name) =>
          sender() ! Dns.Resolved(name, immutable.Seq(InetAddress.getByName(name)))
        case None =>
          val id = nextId()

          requests =
            requests.filter(_._2.expires.hasTimeLeft) :+
              id -> CurrentRequest(sender(),
                                   caseFoldedName,
                                   if (resolveIpv4) None else Some(Nil),
                                   if (resolveIpv6) None else Some(Nil),
                                   None,
                                   maxPositiveTtl,
                                   requestTtl.fromNow)

          if (resolveIpv4) {
            resolvers(ThreadLocalRandom.current().nextInt(resolvers.length)) ! Question4(id, caseFoldedName)
          }
          if (resolveIpv6) {
            resolvers(ThreadLocalRandom.current().nextInt(resolvers.length)) ! Question6((id | 1).toShort, caseFoldedName)
          }
      }

    case Answer(id, rrs) =>
      val baseId = (id & ~1).toShort

      val canonicalNames: Map[String, String] = rrs.collect({
        case CNAMERecord(incomingName, _, canonicalName) =>
          incomingName.toLowerCase -> canonicalName.toLowerCase
      })(breakOut)

      @tailrec
      def resolveCanonicalName(name: String): String = {
        canonicalNames.get(name) match {
          case Some(canonicalName) => resolveCanonicalName(canonicalName)
          case None => name
        }
      }

      val rrsMinTtl = if (rrs.isEmpty) {
        None
      } else {
        Some(rrs.map(_.ttl).min * 1000L)
      }

      requests = requests.find(_._1 == baseId) match {
        case Some((_, req@CurrentRequest(client, name, None, _, None, currentTtl, _))) if (id & 1) == 0 =>
          val canonicalName = resolveCanonicalName(name)
          val ttl = rrsMinTtl.fold(currentTtl)(math.min(currentTtl, _))
          (baseId, req.copy(ipv4 = Some(rrs.collect({
            case ARecord(incomingName, _, addr) if incomingName.toLowerCase == canonicalName =>
              addr
          })(breakOut)), ttl = ttl)) +: requests.filterNot(_._1 == baseId)
        case Some((_, req@CurrentRequest(client, name, _, None, None, currentTtl, _))) if (id & 1) == 1 =>
          val canonicalName = resolveCanonicalName(name)
          val ttl = rrsMinTtl.fold(currentTtl)(math.min(currentTtl, _))
          (baseId, req.copy(ipv6 = Some(rrs.collect({
            case AAAARecord(incomingName, _, addr) if incomingName.toLowerCase == canonicalName =>
              addr
          })(breakOut)), ttl = ttl)) +: requests.filterNot(_._1 == baseId)
        case Some((_, req@CurrentRequest(client, name, None, None, _, currentTtl, _))) =>
          val canonicalName = resolveCanonicalName(name)
          val ttl = rrsMinTtl.fold(currentTtl)(math.min(currentTtl, _))
          val srv = rrs.collect({
            case rec: SRVRecord if rec.name.toLowerCase == canonicalName =>
              rec
          })(breakOut)
          (baseId, req.copy(srv = Some(srv), ttl = ttl)) +: requests.filterNot(_._1 == baseId)
        case None =>
          requests
      }

      requests = requests.find(_._1 == baseId) match {
        case Some((_, CurrentRequest(client, name, Some(ipv4), Some(ipv6), None, recordsTtl, _))) =>
          val ttl = if (ipv4.isEmpty && ipv6.isEmpty) {
            negativeTtl
          } else {
            math.min(maxPositiveTtl, math.max(minPositiveTtl, recordsTtl))
          }

          cache.put(Dns.Resolved(name, ipv4, ipv6), ttl)
          client ! Dns.Resolved(name, ipv4, ipv6)
          requests.filterNot(_._1 == baseId)
        case Some((_, CurrentRequest(client, name, None, None, Some(srv), recordsTtl, _))) =>
          client ! SrvResolved(name, srv)
          requests.filterNot(_._1 == baseId)
        case _ =>
          requests
      }
  }
}

object AsyncDnsResolver {
  private val inetSocketAddress = """(.*?)(?::(\d+))?""".r

  case class SrvResolved(name: String, srv: immutable.Seq[SRVRecord])

  def parseNameserverAddress(str: String): InetSocketAddress = {
    val inetSocketAddress(host, port) = str
    new InetSocketAddress(host, Option(port).fold(53)(_.toInt))
  }

  private[io] def isSrv(name: String) =
    name.nonEmpty && name(0) == '_'

  private val ipv4Address = """^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$""".r
  private val ipv6Address = """^\s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:)))(%.+)?\s*$""".r

  private[io] def isInetAddress(name: String): Boolean =
    ipv4Address.findAllMatchIn(name).nonEmpty || ipv6Address.findAllMatchIn(name).nonEmpty

  // Note that the corresponding man page doesn't actually dictate the format of this field,
  // just the keywords and their meanings. See http://man7.org/linux/man-pages/man5/resolv.conf.5.html
  //
  private[io] val NameserverLine = """^\s*nameserver\s+(.*)$""".r

  // OS specific. No encoding or charset is specified by the man page as I recall.
  // See http://man7.org/linux/man-pages/man5/resolv.conf.5.html.
  //
  def parseSystemNameServers(resolvConf: File): Option[immutable.Seq[InetSocketAddress]] =
    try {
      val addresses =
        for {
          line <- Source.fromFile(resolvConf).getLines()
          addr <- NameserverLine.findFirstMatchIn(line).map(_.group(1))
        } yield parseNameserverAddress(addr)
      Some(addresses.toList)
    } catch {
      case NonFatal(_) => Option.empty
    }

  private[io] final case class CurrentRequest(client: ActorRef,
                                              name: String,
                                              ipv4: Option[immutable.Seq[Inet4Address]],
                                              ipv6: Option[immutable.Seq[Inet6Address]],
                                              srv: Option[immutable.Seq[SRVRecord]],
                                              ttl: Long,
                                              expires: Deadline)
}
