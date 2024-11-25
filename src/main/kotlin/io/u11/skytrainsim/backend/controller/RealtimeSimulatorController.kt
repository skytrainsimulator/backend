package io.u11.skytrainsim.backend.controller

import io.u11.skytrainsim.backend.entities.timeline.Timeline
import io.u11.skytrainsim.backend.service.RealtimeSimulatorService
import io.u11.skytrainsim.backend.util.responseEntity
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDate
import java.time.LocalTime

@Controller
class RealtimeSimulatorController(val realtimeSimulatorService: RealtimeSimulatorService) {
    @GetMapping("/realtime")
    fun realtime(
        @RequestParam date: LocalDate = LocalDate.now(),
        @RequestParam start: LocalTime?,
        @RequestParam end: LocalTime?,
    ): ResponseEntity<Timeline> {
        val timeline = realtimeSimulatorService.simulatedTimelineCache.get(date)
        return (
            if (start != null && end != null) {
                timeline.filterTimes(start, end)
            } else if (start != null) {
                timeline.filterTimes(start = start)
            } else if (end != null) {
                timeline.filterTimes(end = end)
            } else {
                timeline
            }
        ).responseEntity
    }
}
