package io.u11.skytrainsim.backend.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import io.u11.skytrainsim.backend.util.fromPosition
import io.u11.skytrainsim.backend.util.positionAlong
import io.u11.skytrainsim.backend.util.toPosition
import io.u11.skytrainsim.backend.util.totalLength
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.UUID

data class Timeline(
    val trains: List<SimulatedTrain>,
    override val events: List<GlobalTimelineEvent>,
) : TimelineContainer<GlobalTimelineEvent> {
    fun filterTimes(
        start: LocalTime = earliestTime.toLocalTime(),
        end: LocalTime = latestTime.toLocalTime(),
    ): Timeline {
        return copy(
            trains = trains.map { it.filterTimes(start, end) }.filter { it.events.isNotEmpty() },
            events = eventsBetween(start, end),
        )
    }
}

// TODO - this can't handle splitting/coupling trains. Is that something to expect in revenue service?
data class SimulatedTrain(
    val id: UUID,
    override val events: List<TrainTimelineEvent>,
) : TimelineContainer<TrainTimelineEvent> {
    fun filterTimes(
        start: LocalTime = earliestTime.toLocalTime(),
        end: LocalTime = latestTime.toLocalTime(),
    ): SimulatedTrain {
        return copy(events = eventsBetween(start, end))
    }
}

interface TimelineContainer<E : SimulatedTimelineEvent> {
    val events: List<E>
    val earliestTime: OffsetDateTime get() = events.minOfOrNull { it.timeFrom } ?: OffsetDateTime.MIN
    val latestTime: OffsetDateTime get() = events.maxOfOrNull { it.timeTo } ?: OffsetDateTime.MIN

    fun eventsBetween(
        start: LocalTime = earliestTime.toLocalTime(),
        end: LocalTime = latestTime.toLocalTime(),
    ): List<E> =
        events.filter {
            val tf = it.timeFrom.toLocalTime()
            val tt = it.timeTo.toLocalTime()

            tf in start..end || tt in start..end || start in tf..tt || end in tf..tt
        }.sortedBy { it.timeFrom }
}

interface SimulatedTimelineEvent {
    val timeFrom: OffsetDateTime
    val timeTo: OffsetDateTime
    val eventType: String get() =
        this::class.java.simpleName
            .replace("TimelineEvent", "", true)

    @get:JsonIgnore
    val timeRange get() = timeFrom..timeTo

    @get:JsonIgnore
    val totalSeconds get() = timeTo.toEpochSecond() - timeFrom.toEpochSecond()
}

interface TrainTimelineEvent : SimulatedTimelineEvent

interface TrainPositionTimelineEvent : TrainTimelineEvent {
    val fromPosition: TrackPosition
    val toPosition: TrackPosition

    fun positionAt(time: OffsetDateTime): TrackPosition?
}

interface GlobalTimelineEvent : SimulatedTimelineEvent

interface TrackPosition

data class NodeTrackPosition(val nodeId: UUID) : TrackPosition

data class WayTrackPosition(val wayId: UUID, val position: Double) : TrackPosition {
    companion object {
        fun atNode(
            nodeId: UUID,
            trackData: TrackData,
        ): TrackPosition {
            return WayTrackPosition(trackData.ways.values.first { it.fromNode == nodeId || it.toNode == nodeId }.id, 0.0)
        }
    }
}

data class PathingWay(
    @get:JsonIgnore
    val backingWay: Way,
    val from: Double,
    val to: Double,
) {
    init {
        if (from !in 0.0..1.0 || to !in 0.0..1.0) throw IllegalArgumentException("Expected range with bounds 0..1, got $from..$to")
    }

    val wayId: UUID = backingWay.id

    /**
     * This range **ignores** direction i.e. a contraflow's range would equal the range of the non-contraflow version of the way
     */
    val encompassedRange = if (from < to) from..to else to..from

    /**
     * If the path is going counter to the way's defined direction and the way is not bidirectional
     */
    val contraflow: Boolean = from > to && !backingWay.isBidirectional

    val percentLength = if (from < to) to - from else from - to
    val length by lazy { backingWay.length * percentLength }
    val staticWeight by lazy { length / backingWay.maxSpeed * (if (contraflow && !backingWay.isBidirectional) 1.5 else 1.0) }
}

data class TrainDoGTFSBlockTimelineEvent(
    override val timeFrom: OffsetDateTime,
    override val timeTo: OffsetDateTime,
    val gtfsBlockId: String,
) : TrainTimelineEvent

data class TrainDoGTFSTripTimelineEvent(
    override val timeFrom: OffsetDateTime,
    override val timeTo: OffsetDateTime,
    val gtfsTripId: String,
) : TrainTimelineEvent

data class TrainDoGTFSStopTimelineEvent(
    override val timeFrom: OffsetDateTime,
    override val timeTo: OffsetDateTime,
    val gtfsStopId: String,
    val gtfsStopSequence: Int,
) : TrainTimelineEvent

data class TrainRouteTimelineEvent(
    override val timeFrom: OffsetDateTime,
    override val timeTo: OffsetDateTime,
    val from: TrackPosition,
    val to: TrackPosition,
    val path: List<PathingWay>,
) : TrainTimelineEvent

data class TrainDwellTimelineEvent(
    override val timeFrom: OffsetDateTime,
    override val timeTo: OffsetDateTime,
    val dwellPosition: TrackPosition,
) : TrainPositionTimelineEvent {
    override fun positionAt(time: OffsetDateTime): TrackPosition? {
        return if (time in timeRange) dwellPosition else null
    }

    override val fromPosition: TrackPosition = dwellPosition
    override val toPosition: TrackPosition = dwellPosition
}

data class TrainMoveConstantTimelineEvent(
    override val timeFrom: OffsetDateTime,
    override val timeTo: OffsetDateTime,
    val path: List<PathingWay>,
) : TrainPositionTimelineEvent {
    init {
        if (path.isEmpty()) throw IllegalArgumentException("Empty Path!")
    }

    /**
     * meters / second
     */
    val speed = path.totalLength / totalSeconds

    override val fromPosition: TrackPosition get() = path.fromPosition
    override val toPosition: TrackPosition get() = path.toPosition

    override fun positionAt(time: OffsetDateTime): TrackPosition? {
        if (time !in timeRange) return null
        val totalLength = path.totalLength
        val percentAlong = ((time.toEpochSecond() - timeFrom.toEpochSecond()).toDouble() / totalSeconds).coerceIn(0.0..1.0)
        return path.positionAlong(totalLength * percentAlong)
    }
}

data class TrainMoveChangingSpeedTimelineEvent(
    override val timeFrom: OffsetDateTime,
    override val timeTo: OffsetDateTime,
    val speedFrom: Double,
    val speedTo: Double,
    val path: List<PathingWay>,
) : TrainPositionTimelineEvent {
    val acceleration = (speedTo - speedFrom) / totalSeconds

    override val fromPosition: TrackPosition get() = path.fromPosition
    override val toPosition: TrackPosition get() = path.toPosition

    override fun positionAt(time: OffsetDateTime): TrackPosition? {
        if (time !in timeRange) return null
        return path.positionAlong(0.5 * (time.toEpochSecond() - timeFrom.toEpochSecond()) * (speedTo - speedFrom))
    }
}
