package org.hcoa.playratelimiter.playfun

import org.hcoa.playratelimiter.limiter.RateLimiter
import org.hcoa.playratelimiter.playfun.RateLimitWithUsage.{
  RateLimitUsage,
  RateLimiterHeaders,
  RequestWithRateLimUsage,
  createDefaultHeaders
}
import play.api.mvc.{ActionFunction, Request, Result, WrappedRequest}

import java.time.{Clock, Instant}
import scala.concurrent.{ExecutionContext, Future}

class RateLimitWithUsage[T](
    rateLimiter: RateLimiter[T],
    keyFrom: Request[_] => T,
    reject: Request[_] => Future[Result],
    skip: Request[_] => Boolean = (_: Request[_]) => false,
    addHeaders: RateLimitUsage => RateLimiterHeaders = createDefaultHeaders
)(implicit
    val executionContext: ExecutionContext,
    clock: Clock
) extends ActionFunction[Request, RequestWithRateLimUsage] {

  override def invokeBlock[A](
      request: Request[A],
      block: RequestWithRateLimUsage[A] => Future[Result]
  ): Future[Result] = {
    rateLimit(request).flatMap(
      _.fold(
        Future.successful,
        fb => block(fb).map(addRateLimHeaders(_, fb.rateLimitUsage))
      )
    )(executionContext)
  }

  private def rateLimit[A](
      request: Request[A]
  ): Future[Either[Result, RequestWithRateLimUsage[A]]] = {
    val key = keyFrom(request)
    val probe = rateLimiter.tryConsumeWithProbe(key, 1)

    val reset =
      Instant.now(clock).plusNanos(probe.nanosToWaitForRefill).toEpochMilli

    val rateLimitUsage =
      RateLimitUsage(rateLimiter.capacity, probe.availableTokens, reset)

    val wrappedRequest = new RequestWithRateLimUsage[A](request, rateLimitUsage)

    if (skip(request) || probe.consumed)
      Future.successful(Right(wrappedRequest))
    else
      reject(request).map(r => Left(addRateLimHeaders(r, rateLimitUsage)))
  }

  private def addRateLimHeaders[A](
      result: Result,
      rateLimitUsage: RateLimitUsage
  ): Result = result.withHeaders(addHeaders(rateLimitUsage): _*)

}

object RateLimitWithUsage {
  type RateLimiterHeaders = Seq[(String, String)]

  case class RateLimitUsage(
      xRateLimitLimit: Long,
      xRateLimitRemaining: Long,
      xRateLimitReset: Long
  )

  class RequestWithRateLimUsage[A](
      request: Request[A],
      val rateLimitUsage: RateLimitUsage
  ) extends WrappedRequest[A](request)

  // the rate limit ceiling that of the current request.
  val XRateLimitLimitHeader = "X-Rate-Limit-Limit"

  // the number of requests left for the current rate-limit period.
  val XRateLimitRemainingHeader = "X-Rate-Limit-Remaining"

  // the time at which the rate limit resets (UTC epoch time.)
  val XRateLimitResetHeader = "X-Rate-Limit-Reset"

  def createDefaultHeaders(rateLimitUsage: RateLimitUsage): RateLimiterHeaders =
    Seq(
      XRateLimitLimitHeader -> rateLimitUsage.xRateLimitLimit.toString,
      XRateLimitRemainingHeader -> rateLimitUsage.xRateLimitRemaining.toString,
      XRateLimitResetHeader -> rateLimitUsage.xRateLimitReset.toString
    )
}
