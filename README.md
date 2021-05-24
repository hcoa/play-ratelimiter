# Play rate limiter
In-memory lock-free rate limiter for playframework. Backed by Caffeine for cache and Bucket4j-core for TokenBucket implementation.

To start using, just import

```sbt
libraryDependencies ++= "org.hcoa" % "play-ratelimiter" % <version>
```
 
available two options:

1. RateLimiter as play action filter - `RateLimitFilter`,
   e.g. to create an Ip rate-limiter action filter for 10 requests per second would like:
```scala
import org.hcoa.playratelimiter.PlayRateLimiter

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

val capacity: Long = 10
val period: FiniteDuration = 1.second

val rateLimiterFilter = PlayRateLimiter.createFilter[String](capacity, period, req => req.remoteAddress)

```
Note: ensure that you have a client IP in a Request.remoteAddress, docs link here TODO


## Usage

For example, simple IP rate limiter with no more than 10 requests per 100 milliseconds, and maximum elements to store in-memory:
```scala
val rateLimiter = new InMemoryRateLimiter[String](
  10, 
  100.milliseconds,
  10_000) // max amount of elements to store in the cache 
```
(of course the number might heavily differ for your use-cases, estimate size of elements by: `max_amount * (keySize + valueSize)`)

