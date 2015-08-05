package com.timmy.redis

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
  private[this] val config = new {
    val concurrency = Concurrency()
    val hosts = Hosts()
    val keySize = KeySize()
    val valueSize = ValueSize()
    val nworkers = Nworkers()
  }
  val count = new AtomicLong

  def proc(client: RedisClient, key: ChannelBuffer, value: ChannelBuffer) {
    client.set(key, value) onSuccess { _ => 
      count.incrementAndGet()
      proc(client, key, value)
    }
  }

  def main() {
    var builder = ClientBuilder()
      .name("rc")
      .codec(RedisCodec())
      .hostConnectionLimit(config.concurrency)
      .hosts(config.hosts)

    if (config.nworkers > -1) {
      builder = builder.channelFactory(
        new NioClientSocketChannelFactory(
          Executors.newCachedThreadPool(new NamedPoolThreadFactory("redisboss")),
          Executors.newCachedThreadPool(new NamedPoolThreadFactory("redisIO")),
          config.nworkers
        )
      )
    }

    val key = StringToChannelBuffer("x" * config.keySize)
    val value = StringToChannelBuffer(Buf.Utf8("y" * config.valueSize).toString)

    val factory = builder.buildFactory()
    val elapsed = Stopwatch.start()

    for (_ <- 0 until config.concurrency) {
      val svc = new PersistentService(factory)
      val client = RedisClient(svc)
      proc(client, key, value)
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
