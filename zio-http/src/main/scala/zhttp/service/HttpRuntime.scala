package zhttp.service

import io.netty.channel.{ChannelHandlerContext, EventLoopGroup => JEventLoopGroup}
import io.netty.util.concurrent.EventExecutor
import zio.internal.Executor
import zio.{Exit, Runtime, URIO, ZIO}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext => JExecutionContext}
import scala.jdk.CollectionConverters._

/**
 * Provides basic ZIO based utilities for any ZIO based program to execute in a channel's context. It will automatically
 * cancel the execution when the channel closes.
 */
final class HttpRuntime[+R](strategy: HttpRuntime.Strategy[R]) {

  def unsafeRun(ctx: ChannelHandlerContext)(program: ZIO[R, Throwable, Any]): Unit = {
    val rtm = strategy.getRuntime(ctx)
    rtm
      .unsafeRunAsync(for {
        fiber <- program.fork
        _     <- ZIO.effectTotal {
          ctx.channel().closeFuture.addListener((_: AnyRef) => rtm.unsafeRunAsync_(fiber.interrupt): Unit)
        }
        _     <- fiber.join
      } yield ()) {
        case Exit.Success(_)     => ()
        case Exit.Failure(cause) =>
          cause.failureOption match {
            case None    => ()
            case Some(_) => System.err.println(cause.prettyPrint)
          }
          ctx.close()
      }
  }
}

object HttpRuntime {
  sealed trait Strategy[R] {
    def getRuntime(ctx: ChannelHandlerContext): Runtime[R]
  }

  object Strategy {

    case class Default[R](runtime: Runtime[R]) extends Strategy[R] {
      override def getRuntime(ctx: ChannelHandlerContext): Runtime[R] = runtime
    }

    case class Dedicated[R](runtime: Runtime[R], group: JEventLoopGroup) extends Strategy[R] {
      private val localRuntime: Runtime[R] = runtime.withYieldOnStart(false).withExecutor {
        Executor.fromExecutionContext(runtime.platform.executor.yieldOpCount) {
          JExecutionContext.fromExecutor(group)
        }
      }

      override def getRuntime(ctx: ChannelHandlerContext): Runtime[R] = localRuntime
    }

    case class Group[R](runtime: Runtime[R], group: JEventLoopGroup) extends Strategy[R] {
      private val localRuntime: mutable.Map[EventExecutor, Runtime[R]] = {
        val map = mutable.Map.empty[EventExecutor, Runtime[R]]
        for (exe <- group.asScala)
          map += exe -> runtime.withYieldOnStart(false).withExecutor {
            Executor.fromExecutionContext(runtime.platform.executor.yieldOpCount) {
              JExecutionContext.fromExecutor(exe)
            }
          }

        map
      }

      override def getRuntime(ctx: ChannelHandlerContext): Runtime[R] =
        localRuntime.getOrElse(ctx.executor(), runtime)
    }

    def sticky[R](group: JEventLoopGroup): ZIO[R, Nothing, Strategy[R]] =
      ZIO.runtime[R].map(runtime => Group(runtime, group))

    def default[R](): ZIO[R, Nothing, Strategy[R]] =
      ZIO.runtime[R].map(runtime => Default(runtime))

    def dedicated[R](group: JEventLoopGroup): ZIO[R, Nothing, Strategy[R]] =
      ZIO.runtime[R].map(runtime => Dedicated(runtime, group))
  }

  def sticky[R](group: JEventLoopGroup): URIO[R, HttpRuntime[R]] =
    Strategy.sticky(group).map(runtime => new HttpRuntime[R](runtime))

  def dedicated[R](group: JEventLoopGroup): URIO[R, HttpRuntime[R]] =
    Strategy.dedicated(group).map(runtime => new HttpRuntime[R](runtime))

  def default[R](): URIO[R, HttpRuntime[R]] =
    Strategy.default().map(runtime => new HttpRuntime[R](runtime))
}
