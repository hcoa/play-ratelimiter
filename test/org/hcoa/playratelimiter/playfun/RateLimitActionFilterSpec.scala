package org.hcoa.playratelimiter.playfun

import akka.stream.Materializer
import org.hcoa.playratelimiter.PlayRateLimiter
import org.hcoa.playratelimiter.limiter.InMemoryRateLimiter
import org.hcoa.playratelimiter.playfun.RateLimitWithUsage.{RateLimitUsage, RateLimiterHeaders}
import org.hcoa.playratelimiter.util.{TestClock, TestTimeMeter}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.Results.Ok
import play.api.mvc.{Action, AnyContent, AnyContentAsEmpty, DefaultActionBuilder, PlayBodyParsers, Request, Result}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import play.api.test.Helpers._

import java.time.ZoneId
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class RateLimitActionFilterSpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with FutureAwaits
    with DefaultAwaitTimeout {
  implicit val materializer: Materializer = app.materializer
  implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  val playBodyParsers: PlayBodyParsers =
    app.injector.instanceOf[PlayBodyParsers]
  val actionBuilder: DefaultActionBuilder = DefaultActionBuilder(
    playBodyParsers.anyContent
  )

  "RateLimit filter" should {
    "process request when under rate-limit capacity" in {
      val capacity = 2
      val period: FiniteDuration = 100.milliseconds

      val rateLimitActionFilter =
        PlayRateLimiter
          .createFilter[String](capacity, period, r => r.path)

      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/")
      val action: Action[AnyContent] =
        (actionBuilder andThen rateLimitActionFilter) { Ok("1") }

      val res: Future[Result] = call(action, request)

      status(res) mustBe OK
    }

    "rate-limit too many requests" in {
      val capacity = 2
      val period: FiniteDuration = 100.milliseconds

      val rateLimitActionFilter =
        PlayRateLimiter
          .createFilter[String](capacity, period, r => r.path)

      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/")
      val action: Action[AnyContent] =
        (actionBuilder andThen rateLimitActionFilter) { Ok("1") }

      val responses: Seq[Int] = Seq.fill(5)(call(action, request)).map(status)

      responses mustBe Seq.fill(2)(OK) ++ Seq.fill(3)(TOO_MANY_REQUESTS)
    }

    "process requests after refill" in {
      val capacity = 10
      val period: FiniteDuration = 500.milliseconds

      val testClock = new TestTimeMeter

      val rateLimitActionFilter =
        PlayRateLimiter.createFilterWith[String](
          InMemoryRateLimiter(capacity, period, testClock),
          r => r.path
        )

      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/")
      val action: Action[AnyContent] =
        (actionBuilder andThen rateLimitActionFilter) { Ok("1") }

      val okResponses =
        Seq.fill(10)(call(action, request))

      val rateLimitedResponses: Seq[Int] =
        Seq.fill(2)(call(action, request)).map(status)

      testClock.moveForward(
        period.toNanos / 5
      ) // 1/5 of capacity must be available now (2 request)

      val afterRefillResponses: Seq[Int] =
        Seq.fill(4)(call(action, request)).map(status)

      okResponses.map(status) mustBe Seq.fill(10)(OK)
      rateLimitedResponses mustBe Seq.fill(2)(TOO_MANY_REQUESTS)
      afterRefillResponses mustBe Seq.fill(2)(OK) ++ Seq.fill(2)(
        TOO_MANY_REQUESTS
      )
    }

    "not rate-limit requests to skip" in {
      val skipPath = "/no-ratelimit-path"
      val noLimitRequest = FakeRequest(GET, skipPath)
      val testTimeMeter = new TestTimeMeter
      val cap = 2
      val period = 100.milliseconds

      val rateLimitActionFilter = PlayRateLimiter.createFilterWith[String](
        InMemoryRateLimiter(cap, period, testTimeMeter),
        r => r.path,
        skip = r => r.path == skipPath
      )

      val request = FakeRequest(GET, "/")

      val action: Action[AnyContent] =
        (actionBuilder andThen rateLimitActionFilter) { Ok("1") }

      val okResponses = Seq.fill(2)(call(action, request))
      val startedLimiting = Seq.fill(1)(call(action, request))
      val skippedRateLimiting = Seq.fill(2)(call(action, noLimitRequest))
      testTimeMeter.moveForward(period.toNanos / 10)
      val rateLimitedRequests = Seq.fill(2)(call(action, request))

      (okResponses ++ startedLimiting ++ skippedRateLimiting ++ rateLimitedRequests)
        .map(status) mustBe Seq(
        OK,
        OK,
        TOO_MANY_REQUESTS,
        OK,
        OK,
        TOO_MANY_REQUESTS,
        TOO_MANY_REQUESTS
      )
    }
  }

  "RateLimitWithUsage" should {

    // TODO refactor to more robust and clean test (this one covers too many things)
    "return correct default headers" in {
      val capacity = 10
      val period: FiniteDuration = 1.second

      val testTimeMeter = new TestTimeMeter
      implicit val clock =
        new TestClock(testTimeMeter, ZoneId.of("Europe/Berlin"))

      val startMillis = clock.instant().toEpochMilli

      val SimpleIpRateLimiter =
        InMemoryRateLimiter[String](
          capacity,
          period,
          timeMeter = testTimeMeter
        )

      val rateLimiterWithUsage =
        PlayRateLimiter.createWithRateLimUsage(
          SimpleIpRateLimiter,
          r => r.path,
          addHeaders = RateLimitWithUsage.createDefaultHeaders
        )

      val request = FakeRequest(GET, "/")
      val action: Action[AnyContent] =
        (actionBuilder andThen rateLimiterWithUsage)(_ => Ok("1"))

      val beforeTickResponses =
        Seq
          .fill(capacity)(call(action, request))
          .map(fr => await(fr))

      val additionalRequests = 3
      // time tick after which restored 1 token

      val timeShift = period.toNanos / 10
      testTimeMeter.moveForward(timeShift)
      val afterTickResponse =
        Seq
          .fill(additionalRequests)(call(action, request))
          .map(fr => await(fr))

      val responses = beforeTickResponses ++ afterTickResponse

      // --- EXPECTED ---
      val nextResetWhenRateLimiter =
        (testTimeMeter.instant().toEpochMilli + timeShift / 1_000_000).toString

      val expectedResets: Seq[(String, String)] = Seq
        .fill(capacity)(
          startMillis.toString
        )
        .zip(Seq("9", "8", "7", "6", "5", "4", "3", "2", "1", "0")) ++
        Seq(
          testTimeMeter.instant().toEpochMilli.toString,
          nextResetWhenRateLimiter,
          nextResetWhenRateLimiter
        ).zip(
          Seq("0", "0", "0")
        )

      val responsesHeaders =
        responses
          .map(_.header.headers.toMap)

      val actualResetsAndUsage: Seq[(String, String)] = responsesHeaders
        .map(m =>
          m.get(RateLimitWithUsage.XRateLimitResetHeader) ->
            m.get(RateLimitWithUsage.XRateLimitRemainingHeader)
        )
        .collect { case (Some(reset), Some(remaining)) =>
          reset -> remaining
        }
        .sortWith((fst, snd) => fst._2 > snd._2 || fst._1 < snd._1)

      // correct total capacity for all requests
      responsesHeaders.count(m =>
        m(RateLimitWithUsage.XRateLimitLimitHeader).toInt == capacity
      ) mustBe capacity + additionalRequests

      actualResetsAndUsage mustBe expectedResets

      responses.map(_.header.status) mustBe (Seq.fill(capacity + 1)(OK) ++ Seq(
        TOO_MANY_REQUESTS,
        TOO_MANY_REQUESTS
      ))
    }

    "custom headers appender for Result" in {
      val testHeaderName = "TEST-CAPACITY-HEADER"
      val capacity = 10
      val testTimeMeter = new TestTimeMeter
      implicit val clock =
        new TestClock(testTimeMeter, ZoneId.of("Europe/Berlin"))

      val SimpleIpRateLimiter =
        InMemoryRateLimiter[String](
          capacity,
          1.second,
          timeMeter = testTimeMeter
        )

      def customHeadersAppender(
          rateLimitUsage: RateLimitUsage
      ): RateLimiterHeaders = {
        Seq(testHeaderName -> rateLimitUsage.xRateLimitLimit.toString)
      }

      val rateLimiterWithUsage =
        PlayRateLimiter.createWithRateLimUsage(
          SimpleIpRateLimiter,
          r => r.path,
          addHeaders = customHeadersAppender
        )

      val action: Action[AnyContent] =
        (actionBuilder andThen rateLimiterWithUsage)(_ => Ok("1"))

      val resp = call(action, FakeRequest(GET, "/"))
      status(resp) mustBe OK
      await(resp).header.headers(testHeaderName) mustBe capacity.toString
    }
  }
}
