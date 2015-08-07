package com.timmy.redis.http

import com.twitter.finagle.httpx.Response
import com.twitter.finagle.util.DefaultTimer
import java.util.concurrent.{ TimeUnit, TimeoutException }
import com.twitter.util.{ Duration, Future, Promise }
import com.twitter.finagle.{Httpx, Service}
import com.twitter.finagle.httpx
import com.twitter.util.{Await, Future}
import com.twitter.app.App
import com.twitter.app.Flag
import com.twitter.finagle.{Service, ServiceFactory}
import com.twitter.util.{Future, Stopwatch}
import java.util.concurrent.atomic.AtomicLong
import com.twitter.io.Buf
import com.twitter.finagle.redis.{Client => RedisClient}
import org.jboss.netty.buffer.ChannelBuffer
import com.twitter.finagle.redis.util.{StringToChannelBuffer, CBToString}
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.redis.{Redis => RedisCodec}
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import com.twitter.concurrent.NamedPoolThreadFactory
import java.util.concurrent.Executors

class PersistentService[Req, Rep](factory: ServiceFactory[Req, Rep]) extends Service[Req, Rep] {
  @volatile private[this] var currentService: Future[Service[Req, Rep]] = factory()

  def apply(req: Req) =
    currentService flatMap { service =>
      service(req) onFailure { _ =>
        currentService = factory()
      }
    }
}

object Server extends App {
  val key1 = StringToChannelBuffer("A20")
  val key2 = StringToChannelBuffer("APPH:1:ebf4cb5d-b99d-45ed-9c6c-18587dfac9e9")
  val key3 = StringToChannelBuffer("H:1:ebf4cb5d-b99d-45ed-9c6c-18587dfac9e9")

  def main() {
    val timer = DefaultTimer.twitter
    val hosts = Hosts()
    val nworkers = Nworkers()
    val redisTimeoutMs = RedisTimeoutMs()

    var builder = ClientBuilder()
      .name("rc")
      .codec(RedisCodec())
      .hostConnectionLimit(1)
      .hosts(hosts)

    println(hosts)

    val factory = builder.buildFactory()
    val svc = new PersistentService(factory)
    val redisClient: RedisClient = RedisClient(svc)

    val service = new Service[httpx.Request, httpx.Response] {
      def apply(req: httpx.Request): Future[httpx.Response] = {
        val returnResponse = new Promise[httpx.Response]

        val f1 = redisClient.get(key1)
        val f2 = redisClient.hGetAll(key2)
        val f3 = redisClient.hGetAll(key3)

        val f = Future.collect(Seq(f1, f2, f3)).within(timer, Duration(redisTimeoutMs, TimeUnit.MILLISECONDS)) rescue {
          case _: Exception => {
            returnResponse.setValue(httpx.Response(req.version, httpx.Status(500)))
            Future.value(Unit)
          }
        }

        f.map(_ => {
          returnResponse.setValue(httpx.Response(req.version, httpx.Status(200)))
        })

        returnResponse
    }}
    val server = Httpx.serve(":8200", service)
    Await.ready(server)
  }

}
