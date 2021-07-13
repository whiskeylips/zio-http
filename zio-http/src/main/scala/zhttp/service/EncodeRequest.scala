package zhttp.service

import io.netty.buffer.{Unpooled => JUnpooled}
import io.netty.handler.codec.http.{
  HttpHeaderNames => JHttpHeaderNames,
  HttpUtil => JHttpUtil,
  HttpVersion => JHttpVersion,
}
import zhttp.core.{JDefaultFullHttpRequest, JFullHttpRequest}
import zhttp.http.{HTTP_CHARSET, Header, Request, Root}
trait EncodeRequest {

  /**
   * Converts Request to JFullHttpRequest
   */
  def encodeRequest(jVersion: JHttpVersion, req: Request): JFullHttpRequest = {
    val method  = req.method.asJHttpMethod
    val uri     = req.url.path match {
      case Root => "/"
      case _    => req.url.relative.asString
    }
    val headers = Header.disassemble(req.headers)
    val jReq    = new JDefaultFullHttpRequest(jVersion, method, uri)
    jReq.headers().set(headers)

    val charSet =
      JHttpUtil.getCharset(jReq, HTTP_CHARSET)

    val content         = req.getBodyAsString(charSet) match {
      case Some(text) => JUnpooled.copiedBuffer(text, HTTP_CHARSET)
      case None       => JUnpooled.EMPTY_BUFFER
    }
    val writerIndex     = content.writerIndex()
    if (writerIndex != 0) {
      headers.set(JHttpHeaderNames.CONTENT_LENGTH, writerIndex.toString())
    }
    val JReqWithContent = jReq.replace(content)
    JReqWithContent
  }
}
