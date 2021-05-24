package org.hcoa.playratelimiter.limiter

case class RateLimiterProbe(
    consumed: Boolean,
    availableTokens: Long,
    nanosToWaitForRefill: Long
)
