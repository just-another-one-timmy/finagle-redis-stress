package com.timmy.redis.http

import com.twitter.app.GlobalFlag

object Hosts extends GlobalFlag[String]("127.0.0.1:6379", "hosts")
object Nworkers extends GlobalFlag[Int](-1, "nworkers")
