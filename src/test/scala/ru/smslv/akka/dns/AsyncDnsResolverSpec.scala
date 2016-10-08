package ru.smslv.akka.dns

import java.net.InetAddress
import java.util.concurrent.TimeUnit

import akka.io.AsyncDnsResolver.SrvResolved
import akka.io.{Dns, IO}
import akka.pattern.ask
import akka.testkit.AkkaSpec
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class AsyncDnsResolverSpec extends AkkaSpec(
  """
    akka.io.dns.resolver = async-dns
    akka.io.dns.async-dns.nameservers = ["8.8.8.8", "8.8.4.4"]
    akka.io.dns.async-dns.resolve-srv = true
  """) {
  val duration = Duration(10, TimeUnit.SECONDS)
  implicit val timeout = Timeout(duration)

  "Resolver" should {
    "resolve single A record" in {
      val answer = Await.result(IO(Dns) ? Dns.Resolve("a-single.test.smslv.ru"), duration).asInstanceOf[Dns.Resolved]
      answer.name should equal("a-single.test.smslv.ru")
      answer.ipv4 should equal(Seq(InetAddress.getByName("1.4.32.128")))
      answer.ipv6 should be(empty)
    }
    "resolve double A records" in {
      val answer = Await.result(IO(Dns) ? Dns.Resolve("a-double.test.smslv.ru"), duration).asInstanceOf[Dns.Resolved]
      answer.name should equal("a-double.test.smslv.ru")
      answer.ipv4.toSet should equal(Set(
        InetAddress.getByName("1.4.32.128"), InetAddress.getByName("2.8.16.64")
      ))
      answer.ipv6 should be(empty)
    }
    "resolve single AAAA record" in {
      val answer = Await.result(IO(Dns) ? Dns.Resolve("aaaa-single.test.smslv.ru"), duration).asInstanceOf[Dns.Resolved]
      answer.name should equal("aaaa-single.test.smslv.ru")
      answer.ipv6 should equal(Seq(InetAddress.getByName("2001:0db8:85a3:0:0:8a2e:0370:7334")))
      answer.ipv4 should be(empty)
    }
    "resolve double AAAA records" in {
      val answer = Await.result(IO(Dns) ? Dns.Resolve("aaaa-double.test.smslv.ru"), duration).asInstanceOf[Dns.Resolved]
      answer.name should equal("aaaa-double.test.smslv.ru")
      answer.ipv6.toSet should equal(Set(
        InetAddress.getByName("2001:0db8:85a3:0:0:8a2e:0370:7334"),
        InetAddress.getByName("fe80:0:0:0:202:b3ff:fe1e:8329")
      ))
      answer.ipv4 should be(empty)
    }
    "resolve mixed A/AAAA records" in {
      val answer = Await.result(IO(Dns) ? Dns.Resolve("a+aaaa.test.smslv.ru"), duration).asInstanceOf[Dns.Resolved]
      answer.name should equal("a+aaaa.test.smslv.ru")
      answer.ipv4.toSet should equal(Set(
        InetAddress.getByName("1.4.32.128"), InetAddress.getByName("2.8.16.64")
      ))
      answer.ipv6.toSet should equal(Set(
        InetAddress.getByName("2001:0db8:85a3:0:0:8a2e:0370:7334"),
        InetAddress.getByName("fe80:0:0:0:202:b3ff:fe1e:8329")
      ))
    }
    "resolve external CNAME record" in {
      val answer = Await.result(IO(Dns) ? Dns.Resolve("cname-ext.test.smslv.ru"), duration).asInstanceOf[Dns.Resolved]
      answer.name should equal("cname-ext.test.smslv.ru")
      answer.ipv4.toSet should equal(Set(
        InetAddress.getByName("1.4.32.128")
      ))
      answer.ipv6.toSet should be(empty)
    }
    "resolve internal CNAME record" in {
      val answer = Await.result(IO(Dns) ? Dns.Resolve("cname-int.test.smslv.ru"), duration).asInstanceOf[Dns.Resolved]
      answer.name should equal("cname-int.test.smslv.ru")
      answer.ipv4.toSet should equal(Set(
        InetAddress.getByName("1.4.32.128"), InetAddress.getByName("2.8.16.64")
      ))
      answer.ipv6.toSet should be(empty)
    }
    "resolve SRV record" in {
      val answer = Await.result(IO(Dns) ? Dns.Resolve("_http._tcp.smslv.ru"), duration).asInstanceOf[SrvResolved]
      answer.name should equal("_http._tcp.smslv.ru")
      answer.srv.head.name should equal("_http._tcp.smslv.ru")
      answer.srv.head.priority should equal(10)
      answer.srv.head.port should equal(80)
      answer.srv.head.target should equal("a-single.test.smslv.ru")
    }
    "resolve same address twice" in {
      def resolve() = {
        Await.result(IO(Dns) ? Dns.Resolve("a-single.test.smslv.ru"), duration).asInstanceOf[Dns.Resolved]
      }
      resolve().ipv4 should equal(Seq(InetAddress.getByName("1.4.32.128")))
      resolve().ipv4 should equal(Seq(InetAddress.getByName("1.4.32.128")))
    }
    "handle nonexistent domains" in {
      val answer = Await.result(IO(Dns) ? Dns.Resolve("nonexistent.test.smslv.ru"), duration).asInstanceOf[Dns.Resolved]
      answer.ipv4 should be(empty)
      answer.ipv6 should be(empty)
    }
  }
}
