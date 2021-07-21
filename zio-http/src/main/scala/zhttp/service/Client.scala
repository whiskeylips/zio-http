package zhttp.service

import io.netty.handler.codec.http.{HttpVersion => JHttpVersion}
import zhttp.core._
import zhttp.http.URL.Location
import zhttp.http._
import zhttp.service
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.{ClientChannelInitializer, ClientHttpChannelReader, ClientInboundHandler}
import zio.{Promise, Task, ZIO}

import java.net.InetSocketAddress

final case class Client(zx: UnsafeChannelExecutor[Any], cf: JChannelFactory[JChannel], el: JEventLoopGroup)
    extends HttpMessageCodec {
  private def asyncRequest(
    req: Request,
    jReq: JFullHttpRequest,
    promise: Promise[Throwable, JFullHttpResponse],
    sslOption: ClientSSLOptions,
  ): Task[Unit] =
    ChannelFuture.unit {
      val read   = ClientHttpChannelReader(jReq, promise)
      val hand   = ClientInboundHandler(zx, read)
      val host   = req.url.host
      val port   = req.url.port.getOrElse(80) match {
        case -1   => 80
        case port => port
      }
      val scheme = req.url.kind match {
        case Location.Relative               => ""
        case Location.Absolute(scheme, _, _) => scheme.asString
      }
      val init   = ClientChannelInitializer(hand, scheme, sslOption)

      val jboo = new JBootstrap().channelFactory(cf).group(el).handler(init)
      if (host.isDefined) jboo.remoteAddress(new InetSocketAddress(host.get, port))

      jboo.connect()
    }

  def request(request: Request, sslOption: ClientSSLOptions = ClientSSLOptions.DefaultSSL): Task[UHttpResponse] =
    for {
      promise <- Promise.make[Throwable, JFullHttpResponse]
      jReq = encodeRequest(JHttpVersion.HTTP_1_1, request)
      _    <- asyncRequest(request, jReq, promise, sslOption).catchAll(cause => promise.fail(cause)).fork
      jRes <- promise.await
      res  <- ZIO.fromEither(decodeJResponse(jRes))
    } yield res

}

object Client {
  def make: ZIO[EventLoopGroup with ChannelFactory, Nothing, Client] = for {
    cf <- ZIO.access[ChannelFactory](_.get)
    el <- ZIO.access[EventLoopGroup](_.get)
    zx <- UnsafeChannelExecutor.make[Any](el)
  } yield service.Client(zx, cf, el)

  def request(
    url: String,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] = for {
    url <- ZIO.fromEither(URL.fromString(url))
    res <- request(Method.GET -> url)
  } yield res

  def request(
    url: String,
    sslOptions: ClientSSLOptions,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] = for {
    url <- ZIO.fromEither(URL.fromString(url))
    res <- request(Method.GET -> url, sslOptions)
  } yield res

  def request(
    url: String,
    headers: List[Header],
    sslOptions: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    for {
      url <- ZIO.fromEither(URL.fromString(url))
      res <- request(Method.GET -> url, headers, sslOptions)
    } yield res

  def request(
    endpoint: Endpoint,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    request(Request(endpoint))

  def request(
    endpoint: Endpoint,
    sslOptions: ClientSSLOptions,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    request(Request(endpoint), sslOptions)

  def request(
    endpoint: Endpoint,
    headers: List[Header],
    sslOptions: ClientSSLOptions,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    request(Request(endpoint, headers), sslOptions)

  def request(
    req: Request,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    make.flatMap(_.request(req))

  def request(
    req: Request,
    sslOptions: ClientSSLOptions,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    make.flatMap(_.request(req, sslOptions))

}
