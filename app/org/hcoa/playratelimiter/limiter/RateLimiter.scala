package org.hcoa.playratelimiter.limiter

trait RateLimiter[A] {
  def tryConsume(key: A, tokens: Int): Boolean
  def tryConsumeWithProbe(key: A, tokens: Int): RateLimiterProbe
  def getAvailableTokens(key: A): Option[Long]
  val capacity: Long
}
