package org.hcoa.playratelimiter.limiter

class DummyRateLimiter[A] extends RateLimiter[A] {
  override def getAvailableTokens(key: A): Option[Long] = Some(1L)

  override def tryConsumeWithProbe(key: A, tokens: Int): RateLimiterProbe =
    RateLimiterProbe(consumed = true, 1L, 0L)

  override def tryConsume(key: A, tokens: Int): Boolean = true

  override val capacity: Long = 1L
}
