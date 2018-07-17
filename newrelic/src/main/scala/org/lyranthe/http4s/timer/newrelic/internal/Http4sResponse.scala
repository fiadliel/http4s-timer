package org.lyranthe.http4s.timer
package newrelic
package internal

import cats.Functor
import com.newrelic.api.agent.{ExtendedResponse, HeaderType}
import org.http4s.Header

private[newrelic] class Http4sResponse[F[_]: Functor](
    @volatile var response: org.http4s.Response[F])
    extends ExtendedResponse {

  override def getContentLength: Long =
    response.contentLength.getOrElse(-1L)

  override def getStatusMessage: String =
    response.status.reason

  override def getStatus: Int =
    response.status.code

  override def getContentType: String =
    response.contentType.map(_.value).orNull

  override def getHeaderType: HeaderType =
    HeaderType.HTTP

  override def setHeader(name: String, value: String): Unit =
    response = response.putHeaders(Header(name, value))
}
