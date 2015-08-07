package com.timmy.redis.http

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
  val key = StringToChannelBuffer("A20")

  def main() {
    val hosts = Hosts()
    val nworkers = Nworkers()

    var builder = ClientBuilder()
      .name("rc")
      .codec(RedisCodec())
      .hostConnectionLimit(1)
      .hosts(hosts)

    println(hosts)

    if (nworkers > -1) {
      builder = builder.channelFactory(
        new NioClientSocketChannelFactory(
          Executors.newCachedThreadPool(new NamedPoolThreadFactory("redisboss")),
          Executors.newCachedThreadPool(new NamedPoolThreadFactory("redisIO")),
          nworkers
        )
      )
    }

    val factory = builder.buildFactory()
    val svc = new PersistentService(factory)
    val redisClient: RedisClient = RedisClient(svc)

    val service = new Service[httpx.Request, httpx.Response] {
      def apply(req: httpx.Request): Future[httpx.Response] =
        val uuid = java.util.UUID.randomUUID.toString
        redisClient.get(key).map(response => {
          val res = httpx.Response()
          res.contentString = response match {
            case Some(c) => CBToString(response.get)
            case None => "[None]"
          }
          res
        })
    }
    val server = Httpx.serve(":8200", service)
    Await.ready(server)
  }

}
