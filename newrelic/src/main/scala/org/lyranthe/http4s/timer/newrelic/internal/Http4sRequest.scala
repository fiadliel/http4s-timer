package org.lyranthe.http4s.timer
package newrelic
package internal

import com.newrelic.api.agent.{ExtendedRequest, HeaderType}
import org.http4s.Request
import org.http4s.syntax.string._

import scala.collection.JavaConverters._

private[newrelic] class Http4sRequest[F[_]](request: Request[F],
                                            user: Option[String])
    extends ExtendedRequest {
  override def getMethod: String =
    request.method.name

  override def getParameterValues(name: String): Array[String] =
    request.multiParams.get(name).map(_.toArray).orNull

  override def getRequestURI: String = request.uri.path.toString

  override def getCookieValue(name: String): String = null

  override def getAttribute(name: String): AnyRef = null

  override def getRemoteUser: String = user.orNull

  override def getParameterNames: java.util.Enumeration[_] =
    request.params.keys.iterator.asJavaEnumeration

  override def getHeaderType: HeaderType =
    HeaderType.HTTP

  override def getHeader(name: String): String =
    request.headers.get(name.ci).map(_.value).orNull
}
