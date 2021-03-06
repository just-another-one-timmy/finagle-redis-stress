package com.timmy.redis.simple

import com.twitter.app.App
import com.twitter.app.Flag
import com.twitter.finagle.{Service, ServiceFactory}
import com.twitter.util.{Future, Stopwatch}
import java.util.concurrent.atomic.AtomicLong
import com.twitter.io.Buf
import com.twitter.finagle.redis.{Client => RedisClient}
import org.jboss.netty.buffer.ChannelBuffer
import com.twitter.finagle.redis.util.StringToChannelBuffer
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

object RedisStress extends App {
  val count = new AtomicLong

  def proc(client: RedisClient, key: ChannelBuffer) {
    client.get(key) onSuccess { _ =>
      count.incrementAndGet()
      proc(client, key)
    }
  }

  def main() {
    val concurrency = Concurrency()
    val hosts = Hosts()
    val keySize = KeySize()
    val valueSize = ValueSize()
    val nworkers = Nworkers()

    println(hosts)
    var builder = ClientBuilder()
      .name("rc")
      .codec(RedisCodec())
      .hostConnectionLimit(concurrency)
      .hosts(hosts)

    if (nworkers > -1) {
      builder = builder.channelFactory(
        new NioClientSocketChannelFactory(
          Executors.newCachedThreadPool(new NamedPoolThreadFactory("redisboss")),
          Executors.newCachedThreadPool(new NamedPoolThreadFactory("redisIO")),
          nworkers
        )
      )
    }

    // val key = StringToChannelBuffer("x" * keySize)
    // val value = StringToChannelBuffer(Buf.Utf8("y" * valueSize).toString)
    val key = StringToChannelBuffer("A20")

    val factory = builder.buildFactory()
    val elapsed = Stopwatch.start()

    for (_ <- 0 until concurrency) {
      val svc = new PersistentService(factory)
      val client = RedisClient(svc)
      proc(client, key)
    }

    while (true) {
      Thread.sleep(5000)

      val howlong = elapsed()
      val howmuch = count.get()
      assert(howmuch > 0)
      printf("%d QPS\n", howmuch / howlong.inSeconds)
    }
  }

}
