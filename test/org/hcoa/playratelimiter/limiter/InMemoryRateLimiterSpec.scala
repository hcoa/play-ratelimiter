package org.hcoa.playratelimiter.limiter

import com.github.benmanes.caffeine.cache.Caffeine
import org.hcoa.playratelimiter.util.TestTimeMeter
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class InMemoryRateLimiterSpec extends AnyFreeSpec with Matchers {

  trait TestRateLimEnv {
    val cap = 10
    val period: FiniteDuration = 500.milliseconds
    val someKey = "key"
    val testTimer = new TestTimeMeter
    val rateLimiter: InMemoryRateLimiter[String] =
      InMemoryRateLimiter[String](cap, period, testTimer)

  }

  "InMemoryRateLimiter" - {

    "consumes when there are available tokens" in new TestRateLimEnv {
      rateLimiter.tryConsume(someKey, 1) shouldBe true
    }
    "consumes after refill" in new TestRateLimEnv {
      rateLimiter.tryConsume(someKey, cap) shouldBe true
      rateLimiter.tryConsume(someKey, 1) shouldBe false
      testTimer.moveForward(period.toNanos / 10)
      rateLimiter.tryConsume(someKey, 1) shouldBe true
      rateLimiter.tryConsume(someKey, 1) shouldBe false
    }

    "consumes returning usage" in new TestRateLimEnv {
      val toConsume = 8
      val expectedCapacity: Int = cap - toConsume
      rateLimiter.tryConsumeWithProbe(
        someKey,
        toConsume
      ) shouldBe RateLimiterProbe(
        consumed = true,
        expectedCapacity,
        0
      )

      rateLimiter.tryConsumeWithProbe(
        someKey,
        toConsume
      ) shouldBe RateLimiterProbe(
        consumed = false,
        expectedCapacity,
        (period.toNanos / cap) * (toConsume - expectedCapacity)
        // amount of nanos that should pass in order to have full capacity refilled
        // (capacity after first consume = 2; and we want to consume 8 tokens,
        // so we need to wait until (8 - 2) tokens refills, 6 by time to one token refills (period_in_nanos / total_capacity))
      )
    }

    "using custom type as a key" in {
      case class IpAddress(ip: String)
      val testTimeMeter = new TestTimeMeter
      val cap = 10
      val rateLimiter =
        new InMemoryRateLimiter[IpAddress](
          cap,
          500.milliseconds,
          cache = Caffeine.newBuilder().maximumSize(2).build(),
          timeMeter = testTimeMeter
        )

      val ip1 = IpAddress("127.0.0.1")
      val ip2 = IpAddress("127.0.0.2")
      rateLimiter.tryConsume(ip1, cap) shouldBe true
      rateLimiter.getAvailableTokens(ip1) shouldBe Some(0)
      rateLimiter.tryConsume(ip2, cap) shouldBe true
      rateLimiter.getAvailableTokens(ip2) shouldBe Some(0)
    }
  }
}
