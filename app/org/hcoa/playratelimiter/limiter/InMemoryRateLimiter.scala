package org.hcoa.playratelimiter.limiter

import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import io.github.bucket4j.{Bandwidth, Bucket, Bucket4j, Refill, TimeMeter}
import io.github.bucket4j.local.SynchronizationStrategy

import java.time.Duration
import scala.concurrent.duration._

@SerialVersionUID(1337L)
class InMemoryRateLimiter[A](
    val capacity: Long,
    period: FiniteDuration,
    cache: Cache[A, Bucket],
    timeMeter: TimeMeter = TimeMeter.SYSTEM_NANOTIME,
    refill: (Long, Duration) => Refill = Refill.greedy
) extends RateLimiter[A]
    with Serializable {

  private val bandwidth = Bandwidth.classic(
    capacity,
    refill(capacity, Duration.ofNanos(period.toNanos))
  )

  override def getAvailableTokens(key: A): Option[Long] =
    Option(cache.getIfPresent(key)).map(_.getAvailableTokens)

  override def tryConsume(key: A, tokens: Int = 1): Boolean =
    getOrCreate(key).tryConsume(tokens)

  override def tryConsumeWithProbe(key: A, tokens: Int): RateLimiterProbe = {
    val probe = getOrCreate(key)
      .tryConsumeAndReturnRemaining(tokens)
    RateLimiterProbe(
      probe.isConsumed,
      probe.getRemainingTokens,
      probe.getNanosToWaitForRefill
    )
  }

  private def getOrCreate(key: A): Bucket =
    cache.get(key, (_: A) => newBucket)

  private def newBucket: Bucket = {
    Bucket4j
      .builder()
      .addLimit(bandwidth)
      .withSynchronizationStrategy(SynchronizationStrategy.LOCK_FREE)
      .withCustomTimePrecision(timeMeter)
      .build()
  }
}

object InMemoryRateLimiter {

  /** @param capacity available tokens per specified period
    * @param period the window in which capacity of tokens are available
    * @tparam A Type that can be a key to Caffeine cache
    * @return InMemoryRateLimiter instance
    */
  def apply[A](
      capacity: Long,
      period: FiniteDuration,
      timeMeter: TimeMeter = TimeMeter.SYSTEM_NANOTIME,
      cache: Cache[A, Bucket] =
        Caffeine.newBuilder().weakKeys().build[A, Bucket],
      refill: (Long, Duration) => Refill = Refill.greedy
  ): InMemoryRateLimiter[A] = new InMemoryRateLimiter[A](
    capacity,
    period,
    cache,
    timeMeter,
    refill
  )

  def apply[A](
      capacity: Long,
      period: FiniteDuration,
      maxElements: Long
  ): InMemoryRateLimiter[A] =
    apply(
      capacity,
      period,
      cache = Caffeine
        .newBuilder()
        .softValues()
        .maximumSize(maxElements)
        .build[A, Bucket]
    )
}
