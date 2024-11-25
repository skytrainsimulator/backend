package io.u11.skytrainsim.backend.entities.timeline

import com.fasterxml.jackson.annotation.JsonIgnore
import io.u11.skytrainsim.backend.entities.TrackData
import io.u11.skytrainsim.backend.entities.Way
import java.time.OffsetDateTime
import java.util.UUID

// TODO - this can't handle splitting/coupling trains. Is that something to expect in revenue service?
data class SimulatedTrain(
    val id: UUID,
    override val events: List<TrainTimelineEvent>,
) : TimelineContainer<TrainTimelineEvent> {
    fun filterTimes(
        start: OffsetDateTime = earliestTime,
        end: OffsetDateTime = latestTime,
    ): SimulatedTrain {
        return copy(events = eventsBetween(start, end))
    }
}

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
