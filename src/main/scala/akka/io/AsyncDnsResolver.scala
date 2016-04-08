package akka.io

import java.net.{Inet4Address, Inet6Address, InetSocketAddress}
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.typesafe.config.Config
import ru.smslv.akka.dns.raw.{AAAARecord, ARecord, Answer, CNAMERecord, DnsClient, Question4, Question6}

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.collection.{breakOut, immutable, mutable}
import scala.util.Random

private case class CurrentRequest(client: ActorRef,
                                  name: String,
                                  ipv4: Option[immutable.Seq[Inet4Address]],
                                  ipv6: Option[immutable.Seq[Inet6Address]],
                                  ttl: Long)

class AsyncDnsResolver(cache: SimpleDnsCache, config: Config) extends Actor with ActorLogging {

  import AsyncDnsResolver._

  private val nameServers: immutable.Seq[InetSocketAddress] = config.getStringList("nameservers").map(parseNameserverAddress)(breakOut)
  private val resolveIpv4 = config.getBoolean("resolve-ipv4")
  private val resolveIpv6 = config.getBoolean("resolve-ipv6")
  private val negativeTtl = config.getDuration("negative-ttl", TimeUnit.MILLISECONDS)
  private val minPositiveTtl = config.getDuration("max-positive-ttl", TimeUnit.MILLISECONDS)
  private val maxPositiveTtl = config.getDuration("max-positive-ttl", TimeUnit.MILLISECONDS)

  private val random = new Random()

  private var requestId: Short = 0
  private val requests = mutable.OpenHashMap[Short, CurrentRequest]()

  private val resolvers: IndexedSeq[ActorRef] = nameServers.map({ ns =>
    context.actorOf(Props(classOf[DnsClient], ns, self))
  })(breakOut)

  private def nextId(): Short = {
    do {
      requestId = (requestId + 2).toShort
    } while (requests.contains(requestId))
    requestId
  }

  override def receive = {
    case Dns.Resolve(name) =>
      val id = nextId()

      val caseFoldedName = name.toLowerCase

      requests += id -> CurrentRequest(sender(), caseFoldedName,
        if (resolveIpv4) None else Some(immutable.Seq.empty),
        if (resolveIpv6) None else Some(immutable.Seq.empty),
        maxPositiveTtl
      )

      if (resolveIpv4) {
        resolvers(random.nextInt(resolvers.length)) ! Question4(id, caseFoldedName)
      }
      if (resolveIpv6) {
        resolvers(random.nextInt(resolvers.length)) ! Question6((id | 1).toShort, caseFoldedName)
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

      requests.get(baseId) match {
        case Some(req@CurrentRequest(client, name, None, _, currentTtl)) if (id & 1) == 0 =>
          val canonicalName = resolveCanonicalName(name)
          val ttl = rrsMinTtl.map(math.min(currentTtl, _)).getOrElse(currentTtl)
          requests.put(baseId, req.copy(ipv4 = Some(rrs.collect({
            case ARecord(incomingName, _, addr) if incomingName.toLowerCase == canonicalName =>
              addr
          })(breakOut)), ttl = ttl))
        case Some(req@CurrentRequest(client, name, _, None, currentTtl)) if (id & 1) == 1 =>
          val canonicalName = resolveCanonicalName(name)
          val ttl = rrsMinTtl.map(math.min(currentTtl, _)).getOrElse(currentTtl)
          requests.put(baseId, req.copy(ipv6 = Some(rrs.collect({
            case AAAARecord(incomingName, _, addr) if incomingName.toLowerCase == canonicalName =>
              addr
          })(breakOut)), ttl = ttl))
        case None =>
      }

      requests.get(baseId) match {
        case Some(CurrentRequest(client, name, Some(ipv4), Some(ipv6), recordsTtl)) =>
          val ttl = if (ipv4.isEmpty && ipv6.isEmpty) {
            negativeTtl
          } else {
            math.min(maxPositiveTtl, math.max(minPositiveTtl, recordsTtl))
          }

          cache.put(Dns.Resolved(name, ipv4, ipv6), ttl)
          client ! Dns.Resolved(name, ipv4, ipv6)
          requests -= baseId
        case _ =>
      }
  }
}

object AsyncDnsResolver {
  private val inetSocketAddress = """(.*?)(?::(\d+))?""".r

  def parseNameserverAddress(str: String): InetSocketAddress = {
    val inetSocketAddress(host, port) = str
    new InetSocketAddress(host, Option(port).map(_.toInt).getOrElse(53))
  }
}
