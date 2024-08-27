package io.u11.skytrainsim.backend.util

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

interface Tickable {
    fun tick()
}

class TickerTask(val tickable: Tickable, val tps: Int) : Runnable {
    val nsPerTick = 1000000000 / tps
    init {
        logger.debug("tps: $tps nsPerTick $nsPerTick")
    }
    val ticks = AtomicLong(0)
    val doRun = AtomicBoolean(true)
    override fun run() {
        while (doRun.get()) {
            val end = System.nanoTime() + nsPerTick
            try {
                tickable.tick()
            } catch (t: Throwable) {
                logger.error("Uncaught exception during tick ${ticks.get()}", t)
            }
            ticks.incrementAndGet()
            while (System.nanoTime() < end && doRun.get()) {}
        }
    }

    fun stop() { doRun.set(false) }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }
}
