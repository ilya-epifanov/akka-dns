package ru.smslv.akka.dns.raw

import akka.util.{ByteIterator, ByteString, ByteStringBuilder}

case class Question(name: String, qType: RecordType.Value, qClass: RecordClass.Value) {
  def write(out: ByteStringBuilder) {
    DomainName.write(out, name)
    RecordType.write(out, qType)
    RecordClass.write(out, qClass)
  }
}

object Question {
  def parse(it: ByteIterator, msg: ByteString): Question = {
    val name = DomainName.parse(it, msg)
    val qType = RecordType.parse(it)
    val qClass = RecordClass.parse(it)
    Question(name, qType, qClass)
  }
}
