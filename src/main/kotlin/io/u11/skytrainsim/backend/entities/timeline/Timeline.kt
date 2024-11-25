package io.u11.skytrainsim.backend.entities.timeline

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalTime
import java.time.OffsetDateTime

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

    @get:JsonIgnore
    val flattenedTimeline by lazy {
        mutableListOf<SimulatedTimelineEvent>().also { l ->
            l.addAll(trains.flatMap { it.events })
            l.addAll(events)
        }.asTimelineContainer
    }

    override val earliestTime: OffsetDateTime get() = flattenedTimeline.earliestTime
    override val latestTime: OffsetDateTime get() = flattenedTimeline.latestTime
}

interface TimelineContainer<E : SimulatedTimelineEvent> {
    val events: List<E>
    val earliestTime: OffsetDateTime get() = events.minOfOrNull { it.timeFrom } ?: OffsetDateTime.MIN
    val latestTime: OffsetDateTime get() = events.maxOfOrNull { it.timeTo } ?: OffsetDateTime.MAX

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

data class SimpleTimelineContainer<E : SimulatedTimelineEvent>(override val events: List<E>) : TimelineContainer<E>

val <E : SimulatedTimelineEvent> Collection<E>.asTimelineContainer get() = SimpleTimelineContainer(this.sortedBy { it.timeFrom })
