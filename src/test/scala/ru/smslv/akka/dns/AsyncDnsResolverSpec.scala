package ru.smslv.akka.dns

import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import java.util.concurrent.TimeUnit

import akka.io.AsyncDnsResolver.SrvResolved
import akka.io.{AsyncDnsResolver, Dns, IO}
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
    "resolve an IPV4 to an IP" in {
      val answer = Await.result(IO(Dns) ? Dns.Resolve("1.4.32.128"), duration).asInstanceOf[Dns.Resolved]
      answer.name should equal("1.4.32.128")
      answer.ipv4 should equal(Seq(InetAddress.getByName("1.4.32.128")))
      answer.ipv6 should be(empty)
    }
    "resolve an IPV6 to an IP" in {
      val answer = Await.result(IO(Dns) ? Dns.Resolve("1080:0:0:0:8:800:200C:4171"), duration).asInstanceOf[Dns.Resolved]
      answer.name should equal("1080:0:0:0:8:800:200C:4171")
      answer.ipv4 should be(empty)
      answer.ipv6 should equal(Seq(InetAddress.getByName("1080:0:0:0:8:800:200C:4171")))
    }
  }

  "Resolver functions" should {
    "parse a resolv.conf file successfully" in {
      val resolvConf = Files.createTempFile("resolve", "conf").toFile
      resolvConf.deleteOnExit()
      Files.write(
        resolvConf.toPath,
        """# This file is automatically generated.
          |#
          |search lan
          |nameserver 10.0.1.1
          |nameserver 2001:44b8:3127:3500::1
          |
        """.stripMargin.getBytes
      )
      AsyncDnsResolver.parseSystemNameServers(resolvConf) should be(Some(List(
        AsyncDnsResolver.parseNameserverAddress("10.0.1.1"),
        AsyncDnsResolver.parseNameserverAddress("2001:44b8:3127:3500::1")
      )))
    }

    "return an empty list when a resolv.conf file has no nameservers" in {
      val resolvConf = Files.createTempFile("resolve", "conf").toFile
      resolvConf.deleteOnExit()
      Files.write(
        resolvConf.toPath,
        """# This file is automatically generated.
          |#
          |search lan
        """.stripMargin.getBytes
      )
      AsyncDnsResolver.parseSystemNameServers(resolvConf) should be(Some(List.empty))
    }

    "return an empty list when a resolv.conf file has a single nameserver with no address" in {
      val resolvConf = Files.createTempFile("resolve", "conf").toFile
      resolvConf.deleteOnExit()
      Files.write(
        resolvConf.toPath,
        """# This file is automatically generated.
          |#
          |nameserver
        """.stripMargin.getBytes
      )
      AsyncDnsResolver.parseSystemNameServers(resolvConf) should be(Some(List.empty))
    }

    "return nothing when there is no resolv.conf" in {
      val resolvConf = new File("something-somewhere")
      AsyncDnsResolver.parseSystemNameServers(resolvConf) should be(None)
    }
  }
}
