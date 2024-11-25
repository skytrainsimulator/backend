package io.u11.skytrainsim.backend.controller

import io.u11.skytrainsim.backend.entities.timeline.ITimeline
import io.u11.skytrainsim.backend.service.RealtimeSimulatorService
import io.u11.skytrainsim.backend.util.responseEntity
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

@Controller
class RealtimeSimulatorController(val realtimeSimulatorService: RealtimeSimulatorService) {
    @GetMapping("/realtime")
    fun realtime(
        @RequestParam date: LocalDate = LocalDate.now(),
        @RequestParam start: LocalTime?,
        @RequestParam startInstant: Long?,
        @RequestParam end: LocalTime?,
        @RequestParam endInstant: Long?,
    ): ResponseEntity<ITimeline> {
        val timeline = realtimeSimulatorService.simulatedTimelineCache.get(date)
        val filterFrom = startInstant?.let {
            OffsetDateTime.ofInstant(Instant.ofEpochSecond(it), timeline.earliestTime.offset)
        } ?: start?.let { OffsetDateTime.of(date, it, timeline.earliestTime.offset) }
        val filterTo = endInstant?.let {
            OffsetDateTime.ofInstant(Instant.ofEpochSecond(it), timeline.earliestTime.offset)
        } ?: end?.let { OffsetDateTime.of(date, it, timeline.latestTime.offset) }

        return (
            if (filterFrom != null && filterTo != null) {
                timeline.filterTimes(filterFrom, filterTo)
            } else if (filterFrom != null) {
                timeline.filterTimes(start = filterFrom)
            } else if (filterTo != null) {
                timeline.filterTimes(end = filterTo)
            } else {
                timeline
            }
        ).responseEntity
    }
}
