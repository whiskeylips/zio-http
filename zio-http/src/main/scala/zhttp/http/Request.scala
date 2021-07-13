package zhttp.http

import java.nio.charset.Charset

// REQUEST
final case class Request(
  endpoint: Endpoint,
  headers: List[Header] = List.empty,
  content: HttpData[Any, Nothing] = HttpData.empty,
) extends HasHeaders
    with HeadersHelpers { self =>
  val method: Method = endpoint._1
  val url: URL       = endpoint._2
  val route: Route   = method -> url.path

  def getBodyAsString(charSet: Charset = HTTP_CHARSET): Option[String] = content match {
    case HttpData.CompleteData(data) => Option(new String(data.toArray, charSet))
    case _                           => Option.empty
  }

}

object Request {}
