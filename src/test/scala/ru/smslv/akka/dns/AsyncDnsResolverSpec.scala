package ru.smslv.akka.dns

import java.net.{Inet4Address, Inet6Address, InetAddress}
import java.util.concurrent.TimeUnit

import akka.io.{Dns, IO}
import akka.pattern.ask
import akka.testkit.AkkaSpec
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class AsyncDnsResolverSpec extends AkkaSpec( """
    akka.io.dns.resolver = async-dns
    akka.io.dns.async-dns.nameservers = ["8.8.8.8"]
                                             """) {
  val duration = Duration(10, TimeUnit.SECONDS)
  implicit val timeout = Timeout(duration)

  "Resolver" should {
    "resolve some well-known addresses" in {
      val quadEight = Await.result(IO(Dns) ? Dns.Resolve("google-public-dns-a.google.com"), duration).asInstanceOf[Dns.Resolved]
      quadEight.name should be("google-public-dns-a.google.com")
      quadEight.ipv4 should contain(InetAddress.getByName("8.8.8.8").asInstanceOf[Inet4Address])
      quadEight.ipv6 should contain(InetAddress.getByName("2001:4860:4860::8888").asInstanceOf[Inet6Address])

      val exampleCom = Await.result(IO(Dns) ? Dns.Resolve("example.com"), duration).asInstanceOf[Dns.Resolved]
      exampleCom.name should be("example.com")
      exampleCom.ipv4 should contain(InetAddress.getByName("93.184.216.119").asInstanceOf[Inet4Address])
      exampleCom.ipv6 should contain(InetAddress.getByName("2606:2800:220:6d:26bf:1447:1097:aa7").asInstanceOf[Inet6Address])
    }
    "resolve same address twice" in {
      def resolve() = {
        Await.result(IO(Dns) ? Dns.Resolve("google-public-dns-a.google.com"), duration).asInstanceOf[Dns.Resolved]
      }
      resolve().ipv4 should contain(InetAddress.getByName("8.8.8.8").asInstanceOf[Inet4Address])
      resolve().ipv4 should contain(InetAddress.getByName("8.8.8.8").asInstanceOf[Inet4Address])
    }
    "handle nonexistent domains" in {
      val answer = Await.result(IO(Dns) ? Dns.Resolve("nonexistent.invalid"), duration).asInstanceOf[Dns.Resolved]
      answer.ipv4 should be(empty)
      answer.ipv6 should be(empty)
    }
  }
}
