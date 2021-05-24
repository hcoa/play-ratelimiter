package org.hcoa.playratelimiter.util

import io.github.bucket4j.TimeMeter

import java.time.{Clock, Instant, ZoneId}

// TODO add test for the test method :)
class TestTimeMeter extends TimeMeter {
  private var currentNanos = 100_000_000_000_000L
//  private var inst = instFromNanosEpoch(currentNanos)
//  private val clock = Clock.fixed(inst, ZoneId.of("Europe/Berlin"))
  override def isWallClockBased: Boolean = false

  override def currentTimeNanos(): Long = currentNanos
  override def toString: String = "TEST_TIME_METER"

  def moveForward(byNanos: Long): Unit = currentNanos += byNanos

  def instant(): Instant = instFromNanosEpoch(currentNanos)

  private def instFromNanosEpoch(nanos: Long): Instant = {
    Instant.ofEpochSecond(0L, nanos)
  }
}

class TestClock(timeMeter: TestTimeMeter, zoneId: ZoneId) extends Clock {
  override def getZone: ZoneId = zoneId

  override def withZone(zone: ZoneId): Clock = new TestClock(timeMeter, zone)

  override def instant(): Instant = timeMeter.instant()
}
