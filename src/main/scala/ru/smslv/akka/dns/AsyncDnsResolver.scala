package ru.smslv.akka.dns

import java.net.{InetSocketAddress, Inet6Address, Inet4Address}
import java.util.concurrent.TimeUnit

import akka.actor.{ActorLogging, ActorRef, Props, Actor}
import akka.io.{Dns, SimpleDnsCache}
import com.typesafe.config.Config
import ru.smslv.akka.dns.raw.{AAAARecord, ARecord, Answer, Question6, Question4, DnsClient}

import scala.collection.{breakOut, mutable, immutable}
import scala.util.Random
import scala.collection.JavaConversions._

private case class CurrentRequest(client: ActorRef,
                                  name: String,
                                  ipv4: Option[immutable.Seq[Inet4Address]],
                                  ipv6: Option[immutable.Seq[Inet6Address]])

class AsyncDnsResolver(cache: SimpleDnsCache, config: Config) extends Actor with ActorLogging {
  import AsyncDnsResolver._

  private val nameServers: immutable.Seq[InetSocketAddress] = config.getStringList("nameservers").map(parseNameserverAddress)(breakOut)
  private val minPositiveTtl = config.getDuration("min-positive-ttl", TimeUnit.MILLISECONDS)
  private val maxPositiveTtl = config.getDuration("max-positive-ttl", TimeUnit.MILLISECONDS)
  private val negativeTtl = config.getDuration("negative-ttl", TimeUnit.MILLISECONDS)

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

      requests += id -> CurrentRequest(sender(), name, None, None)
      resolvers(random.nextInt(resolvers.length)) ! Question4(id, name)
      resolvers(random.nextInt(resolvers.length)) ! Question6((id | 1).toShort, name)

    case Answer(id, rrs) =>
      val baseId = (id & ~1).toShort

      requests.get(baseId) match {
        case Some(req@CurrentRequest(client, name, None, _)) if (id & 1) == 0 =>
          requests.put(baseId, req.copy(ipv4 = Some(rrs.collect({
            case ARecord(incomingName, _, addr) =>
              addr
          })(breakOut))))
        case Some(req@CurrentRequest(client, name, _, None)) if (id & 1) == 1 =>
          requests.put(baseId, req.copy(ipv6 = Some(rrs.collect({
            case AAAARecord(incomingName, _, addr) =>
              addr
          })(breakOut))))
        case None =>
      }
      requests.get(baseId) match {
        case Some(CurrentRequest(client, name, Some(ipv4), Some(ipv6))) =>
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