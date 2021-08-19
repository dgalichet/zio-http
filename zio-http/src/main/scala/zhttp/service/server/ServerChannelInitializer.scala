package zhttp.service.server

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{Channel, ChannelHandler, ChannelInitializer}
import zhttp.service.Server.Settings
import zhttp.service._
import zhttp.service.server.ServerChannelInitializerUtil.configureClearText

/**
 * Initializes the netty channel with default handlers
 */
@Sharable
final case class ServerChannelInitializer[R](
  httpH: ChannelHandler,
  http2H: ChannelHandler,
  settings: Settings[R, Throwable],
) extends ChannelInitializer[Channel] {
  override def initChannel(channel: Channel): Unit = {

    val sslctx = ServerSSLHandler.build(settings.sslOption, settings.enableHttp2)
    if (sslctx != null) {
      channel
        .pipeline()
        .addLast(HTTP2_OR_HTTP_HANDLER, Http2OrHttpHandler(httpH, http2H, settings))
        .addFirst(SSL_HANDLER, new OptionalSSLHandler(httpH, http2H, sslctx, settings))
      ()
    } else configureClearText(httpH, http2H, channel, settings)
  }

}
