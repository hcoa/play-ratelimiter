package org.hcoa.playratelimiter.playfun

import org.hcoa.playratelimiter.limiter.RateLimiter
import play.api.mvc.{ActionFilter, Request, Result}

import scala.concurrent.{ExecutionContext, Future}

class RateLimitFilter[A](
    rateLimiter: RateLimiter[A],
    limitByKey: Request[_] => A,
    reject: Request[_] => Future[Result],
    skip: Request[_] => Boolean
)(implicit val executionContext: ExecutionContext)
    extends ActionFilter[Request] {

  def filter[T](request: Request[T]): Future[Option[Result]] = {
    val key = limitByKey(request)
    // TODO make configurable amount of tokens to consume
    if (skip(request) || rateLimiter.tryConsume(key, 1))
      Future.successful(None)
    else reject(request).map(Option(_))
  }
}
