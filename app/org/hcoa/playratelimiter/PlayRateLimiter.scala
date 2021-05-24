package org.hcoa.playratelimiter

import org.hcoa.playratelimiter.limiter.{InMemoryRateLimiter, RateLimiter}
import org.hcoa.playratelimiter.playfun.RateLimitWithUsage.{
  RateLimitUsage,
  RateLimiterHeaders,
  RequestWithRateLimUsage,
  createDefaultHeaders
}
import org.hcoa.playratelimiter.playfun.{RateLimitFilter, RateLimitWithUsage}
import play.api.mvc.Results.TooManyRequests
import play.api.mvc.{ActionFunction, Request, Result}

import java.time.Clock
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

object PlayRateLimiter {

  def createFilter[A](
      capacity: Long,
      period: FiniteDuration,
      keyFrom: Request[_] => A,
      reject: Request[_] => Future[Result] = _ =>
        Future.successful(TooManyRequests),
      skip: Request[_] => Boolean = (_: Request[_]) => false
  )(implicit ec: ExecutionContext, ev: A <:< Object): RateLimitFilter[A] = {
    new RateLimitFilter[A](
      rateLimiter = InMemoryRateLimiter[A](capacity, period),
      keyFrom,
      reject,
      skip
    )
  }

  def createFilterWith[A](
      rateLimiter: RateLimiter[A],
      keyFrom: Request[_] => A,
      reject: Request[_] => Future[Result] = _ =>
        Future.successful(TooManyRequests),
      skip: Request[_] => Boolean = (_: Request[_]) => false
  )(implicit
      ec: ExecutionContext
  ) = new RateLimitFilter[A](rateLimiter, keyFrom, reject, skip)

  // TODO better than reject?
  def createWithRateLimUsage[A](
      rateLimiter: RateLimiter[A],
      keyFrom: Request[_] => A,
      reject: Request[_] => Future[Result] = _ =>
        Future.successful(TooManyRequests),
      skip: Request[_] => Boolean = (_: Request[_]) => false,
      addHeaders: RateLimitUsage => RateLimiterHeaders = createDefaultHeaders
  )(implicit
      ec: ExecutionContext,
      clock: Clock
  ): ActionFunction[Request, RequestWithRateLimUsage] =
    new RateLimitWithUsage[A](
      rateLimiter,
      keyFrom,
      reject,
      skip,
      addHeaders
    )

}
