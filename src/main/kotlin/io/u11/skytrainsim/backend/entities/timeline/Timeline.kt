package io.u11.skytrainsim.backend.entities.timeline

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.OffsetDateTime

interface ITimeline : TimelineContainer<GlobalTimelineEvent> {
    val trains: List<SimulatedTrain>

    @get:JsonIgnore
    val flattenedTimeline get() =
        mutableListOf<SimulatedTimelineEvent>().also { l ->
            l.addAll(trains.flatMap { it.events })
            l.addAll(events)
        }.asTimelineContainer

    fun filterTimes(
        start: OffsetDateTime = earliestTime,
        end: OffsetDateTime = latestTime,
    ): FilteredTimeline {
        return FilteredTimeline(
            start,
            end,
            trains.map { it.filterTimes(start, end) }.filter { it.events.isNotEmpty() },
            eventsBetween(start, end),
        )
    }

    override val earliestTime: OffsetDateTime get() = flattenedTimeline.earliestTime
    override val latestTime: OffsetDateTime get() = flattenedTimeline.latestTime
}

data class Timeline(
    override val trains: List<SimulatedTrain>,
    override val events: List<GlobalTimelineEvent>,
) : ITimeline

data class FilteredTimeline(
    val filteredFrom: OffsetDateTime,
    val filteredTo: OffsetDateTime,
    override val trains: List<SimulatedTrain>,
    override val events: List<GlobalTimelineEvent>,
) : ITimeline

interface TimelineContainer<E : SimulatedTimelineEvent> {
    val events: List<E>
    val earliestTime: OffsetDateTime get() = events.minOfOrNull { it.timeFrom } ?: OffsetDateTime.MIN
    val latestTime: OffsetDateTime get() = events.maxOfOrNull { it.timeTo } ?: OffsetDateTime.MAX

    fun eventsBetween(
        start: OffsetDateTime = earliestTime,
        end: OffsetDateTime = latestTime,
    ): List<E> =
        events.filter {
            val tf = it.timeFrom
            val tt = it.timeTo

            tf in start..end || tt in start..end || start in tf..tt || end in tf..tt
        }.sortedBy { it.timeFrom }
}

data class SimpleTimelineContainer<E : SimulatedTimelineEvent>(override val events: List<E>) : TimelineContainer<E>

val <E : SimulatedTimelineEvent> Collection<E>.asTimelineContainer get() = SimpleTimelineContainer(this.sortedBy { it.timeFrom })
