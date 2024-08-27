package io.u11.skytrainsim.backend.service

import com.github.benmanes.caffeine.cache.Caffeine
import io.u11.skytrainsim.backend.entities.GlobalTimelineEvent
import io.u11.skytrainsim.backend.entities.NodeTrackPosition
import io.u11.skytrainsim.backend.entities.PathingWay
import io.u11.skytrainsim.backend.entities.SimulatedTrain
import io.u11.skytrainsim.backend.entities.StopTime
import io.u11.skytrainsim.backend.entities.Timeline
import io.u11.skytrainsim.backend.entities.TrackData
import io.u11.skytrainsim.backend.entities.TrackPosition
import io.u11.skytrainsim.backend.entities.TrainDoGTFSBlockTimelineEvent
import io.u11.skytrainsim.backend.entities.TrainDoGTFSStopTimelineEvent
import io.u11.skytrainsim.backend.entities.TrainDoGTFSTripTimelineEvent
import io.u11.skytrainsim.backend.entities.TrainDwellTimelineEvent
import io.u11.skytrainsim.backend.entities.TrainMoveConstantTimelineEvent
import io.u11.skytrainsim.backend.entities.TrainPositionTimelineEvent
import io.u11.skytrainsim.backend.entities.TrainTimelineEvent
import io.u11.skytrainsim.backend.entities.Trip
import io.u11.skytrainsim.backend.entities.WayTrackPosition
import io.u11.skytrainsim.backend.util.splitAt
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import org.jgrapht.graph.DirectedWeightedMultigraph
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.measureTimedValue
import kotlin.time.toJavaDuration

@Component
class RealtimeSimulatorService(private val gisService: GisService, private val gtfsService: GtfsService) {
    val simulatedTimelineCache =
        Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .build<LocalDate, Timeline> { date ->
                SimulatedTimelineBuilder(
                    date,
                    gisService.trackData,
                    gtfsService.stopTimes.get(date),
                    gtfsService.trips,
                ).build()
            }
}

@Suppress("UnstableApiUsage")
class SimulatedTimelineBuilder(
    private val date: LocalDate,
    private val trackData: TrackData,
    private val stopTimes: List<StopTime>,
    private val trips: List<Trip>,
) {
    private val trackGraph = DirectedWeightedMultigraph<TrackPosition, PathingWay>(PathingWay::class.java)

    init {
        for (way in trackData.ways.values) {
            val from = NodeTrackPosition(way.fromNode)
            val to = NodeTrackPosition(way.toNode)
            trackGraph.addVertex(from)
            trackGraph.addVertex(to)
            val fromWay = PathingWay(way, 0.0, 1.0)
            val toWay = PathingWay(way, 1.0, 0.0)
            trackGraph.addEdge(from, to, fromWay)
            trackGraph.addEdge(to, from, toWay)
            trackGraph.setEdgeWeight(fromWay, fromWay.staticWeight)
            trackGraph.setEdgeWeight(toWay, toWay.staticWeight)
        }
    }

//    private val trackGrapht = trackGraph.jgraphtWithStaticWeights
    private val blocks = trips.map { it.block_id }.distinct().filterNotNull()
    private val stopPositions =
        trackData.stopPositions.values.filter {
            it.gtfsId != null
        }.associate { it.gtfsId!! to NodeTrackPosition(it.id) }

    private val trains: MutableMap<UUID, TrainBuilder> = HashMap()
    private val globalTimeline: MutableList<GlobalTimelineEvent> = ArrayList()

    init {
        trains.putAll(
            trips.associate {
                val id = UUID.randomUUID()
                val train = TrainBuilder(id)
                if (train.addGtfsTrip(it)) id to train else id to null
            }.filterValues { it != null }.mapValues { it.value ?: throw NullPointerException("Null value after a not null check!") },
        )
    }

    private fun trainForGtfsBlock(block: String) =
        trains.values.firstOrNull { t ->
            t.events.any { it is TrainDoGTFSBlockTimelineEvent && it.gtfsBlockId == block }
        }

    private fun trainForGtfsTrip(trip: String) =
        trains.values.firstOrNull { t ->
            t.events.any { it is TrainDoGTFSTripTimelineEvent && it.gtfsTripId == trip }
        }

    fun build(): Timeline {
        logger.debug("Creating SimulatedTimeline for $date")

        logger.debug("Done!")

        return Timeline(
            trains.values.filter { it.events.isNotEmpty() }.map { it.asSimulatedTrain() },
            globalTimeline,
        )
    }

    inner class TrainBuilder(val id: UUID) {
        val events: MutableList<TrainTimelineEvent> = ArrayList()

        fun addGtfsTrip(trip: Trip): Boolean {
            val sts = stopTimes.filter { it.trip_id == trip.trip_id }.sortedBy { it.stop_sequence }
            if (sts.isEmpty()) return false

            val tmp = ArrayList<TrainTimelineEvent>()
            var lastPositionEvent: TrainPositionTimelineEvent? = null
            val from = sts.minOf { it.arrival_time }.toOffsetDateTime()
            val to = sts.maxOf { it.departure_time }.toOffsetDateTime()
            tmp += TrainDoGTFSTripTimelineEvent(from, to, trip.trip_id)
            sts.mapTo(tmp) {
                val stopPosition = stopPositions[it.stop_id]
                val lastPos = lastPositionEvent
                if (lastPos != null && stopPosition != null) {
                    val routeTimed = measureTimedValue { route(lastPos.toPosition, stopPosition, lastPos.timeTo).toMutableList() }
                    if (routeTimed.duration.toJavaDuration() > java.time.Duration.ofMillis(10)) {
                        logger.warn(
                            "Pathfinding took ${routeTimed.duration}",
                        )
                    }
                    val route = routeTimed.value
                    if (route.isNotEmpty()) {
                        val endRouteTime = route.maxOf { e -> e.timeTo }
                        val dwell = TrainDwellTimelineEvent(endRouteTime, it.departure_time.toOffsetDateTime(), stopPosition)
                        route += dwell
                        tmp += route
                        lastPositionEvent = dwell
                    } else {
                        logger.warn("Could not pathfind from $lastPos to $stopPosition (GTFS Stop ${it.stop_id})")
                        val dwell =
                            TrainDwellTimelineEvent(
                                it.arrival_time.toOffsetDateTime(),
                                it.departure_time.toOffsetDateTime(),
                                stopPosition,
                            )
                        lastPositionEvent = dwell
                        tmp += dwell
                    }
                } else if (stopPosition != null) {
                    val dwell =
                        TrainDwellTimelineEvent(it.arrival_time.toOffsetDateTime(), it.departure_time.toOffsetDateTime(), stopPosition)
                    lastPositionEvent = dwell
                    tmp += dwell
                } else {
                    logger.warn("Could not find stop position for stop id ${it.stop_id}!")
                }
                TrainDoGTFSStopTimelineEvent(
                    it.arrival_time.toOffsetDateTime(),
                    it.departure_time.toOffsetDateTime(),
                    it.stop_id,
                    it.stop_sequence,
                )
            }

            if (tmp.isNotEmpty()) {
                events += tmp
                return true
            }
            return false
        }

        fun positionAt(time: OffsetDateTime): TrackPosition? =
            events
                .filterIsInstance<TrainPositionTimelineEvent>()
                .filter { time in it.timeRange }
                .mapNotNull { it.positionAt(time) }
                .firstOrNull()

        fun route(
            from: TrackPosition,
            to: TrackPosition,
            fromTime: OffsetDateTime,
        ): List<TrainTimelineEvent> {
            val routeEvents = ArrayList<TrainTimelineEvent>()
            val routingGraph =
                if (trackGraph.containsVertex(from) && trackGraph.containsVertex(to)) {
                    trackGraph
                } else {
                    // Why do they not override the return type of clone?
                    @Suppress("UNCHECKED_CAST")
                    val tmp = trackGraph.clone() as DirectedWeightedMultigraph<TrackPosition, PathingWay>
                    if (!tmp.containsVertex(from)) {
                        if (from is WayTrackPosition) {
                            tmp.splitAt(from)
                        } else {
                            throw IllegalArgumentException("Attempt to pathfind from an unknown from $from!")
                        }
                    }
                    if (!tmp.containsVertex(to)) {
                        if (to is WayTrackPosition) {
                            tmp.splitAt(to)
                        } else {
                            throw IllegalArgumentException("Attempt to pathfind from an unknown to $to!")
                        }
                    }
                    tmp
                }

            val path =
                DijkstraShortestPath.findPathBetween(routingGraph, from, to)
                    ?: throw IllegalStateException("No path from $from to $to!")
            var lastTime = fromTime
            for (way in path.edgeList) {
//                val way = routingGraph.edgeValue(edge).orElseThrow()
                // TODO acceleration
                val traverseTimeMillis = (way.length / (way.backingWay.maxSpeed / 3.6) * 1000).roundToLong()
//                val endTime = lastTime.plusSeconds(traverseTimeSeconds.roundToLong())
                val endTime = lastTime.plus(traverseTimeMillis, ChronoUnit.MILLIS)
                routeEvents += TrainMoveConstantTimelineEvent(lastTime, endTime, listOf(way))
                lastTime = endTime
            }
            return routeEvents.toList()
        }

        fun asSimulatedTrain() = SimulatedTrain(id, events.sortedBy { it.timeFrom })
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SimulatedTimelineBuilder::class.java)
        const val GLOBAL_MAX_TRAIN_SPEED = 90 / 3.6 // m/s
        const val GLOBAL_TARGET_ACCELERATION = 1.0 // m/s^2
        const val GLOBAL_TARGET_DECELERATION = -1.0 // m/s^2
        const val GLOBAL_EMERGENCY_BRAKE_DECELERATION = -2.4 // m/s^2

        fun speedChangeDistance(
            vi: Double,
            vf: Double,
            a: Double,
        ): Double = (vf.pow(2) - vi.pow(2)) / (2 * a)

        fun speedChangeDistance(
            vi: Double,
            vf: Double,
        ): Double =
            if (vi < vf) {
                speedChangeDistance(vi, vf, GLOBAL_TARGET_ACCELERATION)
            } else {
                speedChangeDistance(vi, vf, GLOBAL_TARGET_DECELERATION)
            }

        fun normalStoppingDistance(vi: Double): Double = speedChangeDistance(vi, 0.0)

        fun emergencyStoppingDistance(vi: Double): Double = speedChangeDistance(vi, 0.0, GLOBAL_EMERGENCY_BRAKE_DECELERATION)
    }
}
