package ru.smslv.akka.dns.raw

import java.net.{Inet4Address, Inet6Address, InetAddress}

import akka.util.{ByteIterator, ByteString, ByteStringBuilder}

sealed abstract class ResourceRecord(val name: String, val ttl: Int, val recType: Short, val recClass: Short) {
  def write(it: ByteStringBuilder): Unit = {
    DomainName.write(it, name)
    it.putShort(recType)
    it.putShort(recClass)
  }
}

case class ARecord(override val name: String, override val ttl: Int,
                   ip: Inet4Address) extends ResourceRecord(name, ttl, RecordType.A.id.toShort, RecordClass.IN.id.toShort) {
  override def write(it: ByteStringBuilder): Unit = {
    super.write(it)
    val addr = ip.getAddress
    it.putShort(addr.length)
    it.putBytes(addr)
  }
}

object ARecord {
  def parseBody(name: String, ttl: Int, length: Short, it: ByteIterator): ARecord = {
    val addr = Array.ofDim[Byte](4)
    it.getBytes(addr)
    ARecord(name, ttl, InetAddress.getByAddress(addr).asInstanceOf[Inet4Address])
  }
}

case class AAAARecord(override val name: String, override val ttl: Int,
                      ip: Inet6Address) extends ResourceRecord(name, ttl, RecordType.AAAA.id.toShort, RecordClass.IN.id.toShort) {
  override def write(it: ByteStringBuilder): Unit = {
    super.write(it)
    val addr = ip.getAddress
    it.putShort(addr.length)
    it.putBytes(addr)
  }
}

object AAAARecord {
  def parseBody(name: String, ttl: Int, length: Short, it: ByteIterator): AAAARecord = {
    val addr = Array.ofDim[Byte](16)
    it.getBytes(addr)
    AAAARecord(name, ttl, InetAddress.getByAddress(addr).asInstanceOf[Inet6Address])
  }
}

case class CNAMERecord(override val name: String, override val ttl: Int,
                       canonicalName: String) extends ResourceRecord(name, ttl, RecordType.CNAME.id.toShort, RecordClass.IN.id.toShort) {
  override def write(it: ByteStringBuilder): Unit = {
    super.write(it)
    it.putShort(DomainName.length(name))
    DomainName.write(it, name)
  }
}

object CNAMERecord {
  def parseBody(name: String, ttl: Int, length: Short, it: ByteIterator, msg: ByteString): CNAMERecord = {
    CNAMERecord(name, ttl, DomainName.parse(it, msg))
  }
}

case class UnknownRecord(override val name: String, override val ttl: Int,
                         override val recType: Short, override val recClass: Short,
                         data: ByteString) extends ResourceRecord(name, ttl, recType, recClass) {
  override def write(it: ByteStringBuilder): Unit = {
    super.write(it)
    it.putShort(data.length)
    it.append(data)
  }
}

object UnknownRecord {
  def parseBody(name: String, ttl: Int, recType: Short, recClass: Short, length: Short, it: ByteIterator): UnknownRecord = {
    UnknownRecord(name, ttl, recType, recClass, it.toByteString)
  }
}

object ResourceRecord {
  def parse(it: ByteIterator, msg: ByteString): ResourceRecord = {
    val name = DomainName.parse(it, msg)
    val recType = it.getShort
    val recClass = it.getShort
    val ttl = it.getInt
    val rdLength = it.getShort
    val data = it.clone().take(rdLength)
    it.drop(rdLength)
    recType match {
      case 1 =>
        ARecord.parseBody(name, ttl, rdLength, data)
      case 5 =>
        CNAMERecord.parseBody(name, ttl, rdLength, data, msg)
      case 28 =>
        AAAARecord.parseBody(name, ttl, rdLength, data)
      case _ =>
        UnknownRecord.parseBody(name, ttl, recType, recClass, rdLength, data)
    }
  }
}

