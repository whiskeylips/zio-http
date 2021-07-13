import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.util.CharsetUtil
import zhttp.http.{Header, HttpData}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio._

object ClientWithCharset extends App {
  val env     = ChannelFactory.auto ++ EventLoopGroup.auto()
  val url     = "http://sports.api.decathlon.com/groups/water-aerobics"
  val headers = List(
    Header.host("sports.api.decathlon.com"),
    Header.custom(HttpHeaderNames.CONTENT_TYPE.toString, "text/html; charset=US-ASCII"),
  )

  val program = for {
    res <- Client.request(
      url,
      headers,
      HttpData.CompleteData(Chunk.fromArray("a".getBytes(CharsetUtil.US_ASCII))),
    )
    _   <- console.putStrLn {
      res.content match {
        case HttpData.CompleteData(data) => data.map(_.toChar).mkString
        case HttpData.StreamData(_)      => "<Chunked>"
        case HttpData.Empty              => ""
      }
    }
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.exitCode.provideCustomLayer(env)

}
