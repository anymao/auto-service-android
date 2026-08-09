package com.anymore.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
class ServiceLazyTest {

    @Test
    fun `并发访问时只创建一次服务实例`() {
        val createCount = AtomicInteger()
        val expected = Any()
        val lazyService = ServiceLazy(ServiceFactory {
            createCount.incrementAndGet()
            expected
        })
        val start = CountDownLatch(1)
        val completed = CountDownLatch(8)
        val instances = ConcurrentLinkedQueue<Any>()

        repeat(8) {
            Thread {
                start.await()
                instances.add(lazyService.get())
                completed.countDown()
            }.start()
        }

        start.countDown()

        assertEquals("并发任务未在预期时间内结束", true, completed.await(5, TimeUnit.SECONDS))
        assertEquals("服务创建次数应被缓存为一次", 1, createCount.get())
        instances.forEach { assertSame("所有调用应获得同一实例", expected, it) }
    }
}
