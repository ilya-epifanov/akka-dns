package ru.smslv.akka.dns.raw


import java.net.{InetAddress, InetSocketAddress}

import akka.actor.{Actor, ActorLogging, ActorRef, Stash}
import akka.io.{IO, Udp}

class DnsClient(ns: InetSocketAddress, upstream: ActorRef) extends Actor with ActorLogging with Stash {

  import context.system

  IO(Udp) ! Udp.Bind(self, new InetSocketAddress(InetAddress.getByAddress(Array.ofDim(4)), 0))

  def receive = {
    case Udp.Bound(local) =>
      log.debug(s"Bound to UDP address $local")
      context.become(ready(sender()))
      unstashAll()
    case _: Question4 =>
      stash()
    case _: Question6 =>
      stash()
    case _: SrvQuestion =>
      stash()
  }

  def ready(socket: ActorRef): Receive = {
    {
      case Question4(id, name) =>
        log.debug(s"Resolving $name (A)")
        val msg4 = Message(id,
          MessageFlags(recursionDesired = true),
          Seq(
            Question(name, RecordType.A, RecordClass.IN)
          ))
        log.debug(s"Message to $ns: $msg4")
        socket ! Udp.Send(msg4.write(), ns)

      case Question6(id, name) =>
        log.debug(s"Resolving $name (AAAA)")
        val msg6 = Message(id,
          MessageFlags(recursionDesired = true),
          Seq(
            Question(name, RecordType.AAAA, RecordClass.IN)
          ))
        log.debug(s"Message to $ns: $msg6")
        socket ! Udp.Send(msg6.write(), ns)

      case SrvQuestion(id, name) =>
        log.debug(s"Resolving $name (SRV)")
        val msg = Message(id,
          MessageFlags(recursionDesired = true),
          Seq(
            Question(name, RecordType.SRV, RecordClass.IN)
          ))
        log.debug(s"Message to $ns: $msg")
        socket ! Udp.Send(msg.write(), ns)

      case Udp.Received(data, remote) =>
        log.debug(s"Received message from $remote: $data")
        val msg = Message.parse(data)
        log.debug(s"Decoded: $msg")
        if (msg.flags.responseCode == ResponseCode.SUCCESS) {
          upstream ! Answer(msg.id, msg.answerRecs)
        } else {
          upstream ! Answer(msg.id, Seq())
        }

      case Udp.Unbind => socket ! Udp.Unbind
      case Udp.Unbound => context.stop(self)
    }
  }
}

case class SrvQuestion(id: Short, name: String)

case class Question4(id: Short, name: String)

case class Question6(id: Short, name: String)

case class Answer(id: Short, rrs: Iterable[ResourceRecord])
