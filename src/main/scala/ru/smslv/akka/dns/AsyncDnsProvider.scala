package ru.smslv.akka.dns

import akka.io.{Dns, DnsProvider, SimpleDnsCache, SimpleDnsManager}

object AsyncDnsProvider extends DnsProvider {
  override def cache: Dns = new SimpleDnsCache()
  override def actorClass = classOf[AsyncDnsResolver]
  override def managerClass = classOf[SimpleDnsManager]
}
