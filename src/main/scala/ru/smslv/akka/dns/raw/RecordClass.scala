package ru.smslv.akka.dns.raw

import akka.util.{ByteIterator, ByteStringBuilder}

object RecordClass extends Enumeration {
  val IN = Value(1)
  val CS = Value(2)
  val CH = Value(3)
  val HS = Value(4)

  val WILDCARD = Value(255)

  def parse(it: ByteIterator): Value = {
    RecordClass(it.getShort)
  }

  def write(out: ByteStringBuilder, c: Value): Unit = {
    out.putShort(c.id)
  }
}
