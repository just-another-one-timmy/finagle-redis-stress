package com.timmy.redis.http

import com.twitter.app.GlobalFlag

object Concurrency extends GlobalFlag[Int](400, "concurrency")
object Hosts extends GlobalFlag[String]("127.0.0.1:6379", "hosts")
object KeySize extends GlobalFlag[Int](55, "keysize")
object ValueSize extends GlobalFlag[Int](1, "objectuesize")
object Nworkers extends GlobalFlag[Int](-1, "nworkers")
