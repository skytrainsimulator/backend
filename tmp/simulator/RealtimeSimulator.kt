package io.u11.skytrainsim.backend.simulator

import io.u11.skytrainsim.backend.db.dao.GisDao
import io.u11.skytrainsim.backend.db.dao.GtfsDao
import io.u11.skytrainsim.backend.entities.StaticTrackGraph
import io.u11.skytrainsim.backend.entities.StopTime
import io.u11.skytrainsim.backend.util.Tickable
import io.u11.skytrainsim.backend.util.TickerTask
import org.jgrapht.GraphPath
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class RealtimeSimulator(
    gisDao: GisDao,
    private val gtfsDao: GtfsDao
) : Simulator {
    override val tickerTask: TickerTask = TickerTask(this, 60)
    override val trackData = gisDao.trackData()

    private val staticTrackGraph = StaticTrackGraph(trackData)
    private val stopTimes : ConcurrentMap<String, List<StopTime>> = ConcurrentHashMap()
    private val trips = gtfsDao.trips.associateBy { it.trip_id }
    private val thread = Thread(tickerTask, "rt-simulator")
    private val executorService = Executors.newSingleThreadScheduledExecutor { Thread(it, "rt-simulator-SES") }
    private val updateGtfsTask : ScheduledFuture<*>
    private val activeSimulatedTrains: ConcurrentHashMap<String, RTSimulatedTrain> = ConcurrentHashMap()

    override val simulatedTrains: Set<SimulatedTrain> get() = activeSimulatedTrains.values.toSet()

    val now: ZonedDateTime get() = ZonedDateTime.now()

    init {
        logger.info("Starting...")
        thread.start()
        updateGtfsTask = executorService.scheduleAtFixedRate(this::updateGtfsStopTimes, 0L, 30L, TimeUnit.SECONDS)
    }

    override fun tick() {
        val tick = tickerTask.ticks.get()

        // Active Train Housekeeping
        for (activeBlock in activeSimulatedTrains.keys) {
            if (!stopTimes.containsKey(activeBlock)) {
                logger.debug("Removing expired block $activeBlock")
                activeSimulatedTrains.remove(activeBlock)
            }
        }
        for (potentialEntry in stopTimes.entries) {
            if (activeSimulatedTrains.containsKey(potentialEntry.key)) continue
            if (
                potentialEntry.value.first().arrival_time <= now &&
                potentialEntry.value.last().departure_time >= now
                ) {
                logger.debug("Adding new block ${potentialEntry.key}")
                activeSimulatedTrains[potentialEntry.key] = RTSimulatedTrain(potentialEntry.key)
            }
        }

        for (train in activeSimulatedTrains.values) {
            train.tick()
        }
    }

    override fun stop() {
        tickerTask.stop()
        updateGtfsTask.cancel(false)
        executorService.shutdown()
    }

    private fun updateGtfsStopTimes() {
        logger.info("Beginning GTFS update...")
        val start = System.currentTimeMillis()
        val rawTimes = gtfsDao
            .stopTimesRelative(Duration.ofMinutes(15), Duration.ofMinutes(45))
        logger.trace("Fetched ${rawTimes.size} stopTimes from DB")
        val newTimes = rawTimes
            .groupBy { trips[it.trip_id]?.block_id ?: throw IllegalStateException("Could not find block id for trip ${it.trip_id}") }
            .mapValues { it.value.sortedBy { st -> st.arrival_time } }
        logger.trace("Grouped stopTimes into ${newTimes.size} blocks")
        for (key in stopTimes.keys) {
            if (!newTimes.containsKey(key)) {
                logger.trace("Popping block $key from stopTimes")
                stopTimes.remove(key)
            }
        }
        for (entry in newTimes.entries) {
            logger.trace("Adding block ${entry.key} to stopTimes")
            stopTimes[entry.key] = entry.value
        }
        logger.debug("Updated GTFS trips in ${System.currentTimeMillis() - start}ms")
    }

    inner class RTSimulatedTrain(val blockId: String) : SimulatedTrain, Tickable {
        override val id: String = blockId

        private val stopTimes: List<StopTime> get() = this@RealtimeSimulator.stopTimes[blockId] ?: emptyList()
        private var currentStopTime: StopTime = stopTimes.first { it.arrival_time <= now }
        private var currentStopNode = trackData.nodeForStopId(currentStopTime.stop_id) ?: throw IllegalStateException("Could not find node for GTFS stop id ${currentStopTime.stop_id}")
        private var nextStopTime: StopTime
        init {
            val index = stopTimes.indexOf(currentStopTime) + 1
            nextStopTime = if (index == stopTimes.size) currentStopTime else stopTimes[index]
        }
        private var nextStopNode = trackData.nodeForStopId(nextStopTime.stop_id) ?: throw IllegalStateException("Could not find node for GTFS stop id ${currentStopTime.stop_id}")

        override var position: TrackPosition
            get() = TODO()
            set(new) = TODO()
        override var facing: UUID
            get() = TODO()
            set(new) = TODO()
        init {
            if (currentStopNode == nextStopNode) {
                val way = trackData.ways.values.first { it.fromNode == currentStopNode.id || it.toNode == currentStopNode.id }
                position = TrackPosition(way.id, if (way.fromNode == currentStopNode.id) 0.0 else 1.0)
                facing = currentStopNode.id
            } else {

            }
        }

        override fun tick() {

        }

        private fun pathfind(): GraphPath<UUID, UUID> =
            DijkstraShortestPath.findPathBetween(staticTrackGraph.fullNetwork, currentStopNode.id, nextStopNode.id)
                ?: throw IllegalStateException("Could not find a path between ${currentStopNode.id} and ${nextStopNode.id}")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }
}
