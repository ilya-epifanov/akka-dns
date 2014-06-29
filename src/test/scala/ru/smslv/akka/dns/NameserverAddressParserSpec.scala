package ru.smslv.akka.dns

import java.net.InetSocketAddress

import org.scalatest.{ShouldMatchers, WordSpec}

class NameserverAddressParserSpec extends WordSpec with ShouldMatchers {
  "Parser" should {
    "handle explicit port in IPv4 address" in {
      AsyncDnsResolver.parseNameserverAddress("8.8.8.8:153") should equal(new InetSocketAddress("8.8.8.8", 153))
    }
    "handle explicit port in IPv6 address" in {
      AsyncDnsResolver.parseNameserverAddress("[2001:4860:4860::8888]:153") should equal(new InetSocketAddress("2001:4860:4860::8888", 153))
    }
    "handle default port in IPv4 address" in {
      AsyncDnsResolver.parseNameserverAddress("8.8.8.8") should equal(new InetSocketAddress("8.8.8.8", 53))
    }
    "handle default port in IPv6 address" in {
      AsyncDnsResolver.parseNameserverAddress("[2001:4860:4860::8888]") should equal(new InetSocketAddress("2001:4860:4860::8888", 53))
    }
  }
}
