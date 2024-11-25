package io.u11.skytrainsim.backend.entities.timeline

import com.fasterxml.jackson.annotation.JsonIgnore
import io.u11.skytrainsim.backend.util.fromPosition
import io.u11.skytrainsim.backend.util.positionAlong
import io.u11.skytrainsim.backend.util.toPosition
import io.u11.skytrainsim.backend.util.totalLength
import java.time.OffsetDateTime

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

