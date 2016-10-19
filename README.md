![Build status](https://travis-ci.org/ilya-epifanov/akka-dns.svg?branch=master)
[![Download](https://api.bintray.com/packages/hajile/maven/akka-dns/images/download.svg) ](https://bintray.com/hajile/maven/akka-dns/_latestVersion)

akka-dns
========

A fully asynchronous DNS resolver for Akka.

Usage
-----

Add a dependency to your `build.sbt`:

```scala
libraryDependencies += "ru.smslv.akka" %% "akka-dns" % "2.4.1-M1"
```

Configure akka-dns in `application.conf`. If you can rely on `/etc/resolv.conf` being available (which should be the case for most flavors of Unix):

```
akka.io.dns {
  resolver = async-dns
  async-dns.resolv-conf = on
}
```

Alternatively you can also configure the nameservers explicitly:

```
akka.io.dns {
  resolver = async-dns
  async-dns {
    nameservers = ["8.8.8.8", "8.8.4.4"]
  }
}
```

Note that you can declare both `resolv-conf` and `nameservers` in which case the latter will be used in the case where `/etc/resolv.conf` cannot be found/parsed.

Advanced configuration
----------------------

There are some other tunables, too, here are their defaults:

```
akka.io.dns {
  async-dns {
    min-positive-ttl = 0s
    max-positive-ttl = 1d
    negative-ttl = 10s

    resolve-ipv4 = true
    resolve-ipv6 = true
    resolve-srv  = false

    # How often to sweep out expired cache entries.
    # Note that this interval has nothing to do with TTLs
    cache-cleanup-interval = 120s
  }
}
```

To actually resolve addresses using akka-dns:

```scala
// send dns request
IO(Dns) ! Dns.Resolve("a-single.test.smslv.ru")

// wait for Dns.Resolved
def receive = {
  case Dns.Resolved(name, ipv4, ipv6) =>
    ...
}

// just to try it out synchronously
val answer = Await.result(IO(Dns) ? Dns.Resolve("a-single.test.smslv.ru"), duration).asInstanceOf[Dns.Resolved]
```

You can also resolve SRV records if you enable a config option:
```
akka.io.dns.async-dns.resolve-srv = true
```

The only difference with ordinary usage is that you send a regular Dns.Resovle request with a domain name 
that starts with `_` and wait for a `SrvResolved` message instead of a `Dns.Resolved` one, like this:
```scala
// send dns request
IO(Dns) ! Dns.Resolve("_http._tcp.smslv.ru")

// wait for SrvResolved
def receive = {
  case SrvResolved(name, records: immutable.Seq[SRVRecord]) =>
    ...
}

// just to try it out synchronously
val answer = Await.result(IO(Dns) ? Dns.Resolve("_http._tcp.smslv.ru"), duration).asInstanceOf[SrvResolved]
```
