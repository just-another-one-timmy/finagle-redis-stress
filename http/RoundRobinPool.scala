package com.timmy.redis.http

class RoundRobinPool[T](items: Array[T]) {
  private[this] var cur: Int = 0
  def get(): T = this.synchronized {
      cur = (cur + 1) % items.size
      return items(cur)
  }
}
