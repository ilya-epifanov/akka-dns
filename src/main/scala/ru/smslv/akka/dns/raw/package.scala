package ru.smslv.akka.dns

import java.nio.ByteOrder

package object raw {
  implicit val networkByteOrder = ByteOrder.BIG_ENDIAN
}
