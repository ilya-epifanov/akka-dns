![Build status](https://travis-ci.org/ilya-epifanov/akka-dns.svg?branch=master)

akka-dns
========

A fully asynchronous DNS resolver for Akka,

Usage
-----

Add a dependency to your `build.sbt`:

```scala
libraryDependencies += "ru.smslv.akka" %% "akka-dns" % "2.4.0"
```

Configure akka-dns in `application.conf`:

```
akka.io.dns {
  resolver = async-dns
  async-dns {
    nameservers = ["8.8.8.8", "8.8.4.4"]
  }
}
```

There are some other tunables, too, here're their defaults:

```
akka.io.dns {
  async-dns {
    min-positive-ttl = 0s
    max-positive-ttl = 1d
    negative-ttl = 10s

    resolve-ipv4 = true
    resolve-ipv6 = true

    # How often to sweep out expired cache entries.
    # Note that this interval has nothing to do with TTLs
    cache-cleanup-interval = 120s
  }
}
```

To actually resolve addresses using akka-dns:

```scala
// send dns request
IO(Dns) ! Dns.Resolve("example.com")

// wait for Dns.Resolved
def receive = {
  case Dns.Resolved(name, ipv4, ipv6) =>
    ...
}

// just to try it out synchronously
val answer = Await.result(IO(Dns) ? Dns.Resolve("a-single.test.smslv.ru"), duration).asInstanceOf[Dns.Resolved]
```
