package io.u11.skytrainsim.backend.controller

import io.u11.skytrainsim.backend.entities.Stop
import io.u11.skytrainsim.backend.entities.StopTime
import io.u11.skytrainsim.backend.entities.Trip
import io.u11.skytrainsim.backend.service.GtfsService
import io.u11.skytrainsim.backend.util.responseEntity
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class GtfsController(private val gtfsService: GtfsService) {
    @GetMapping("/gtfs/stops")
    fun stops(): ResponseEntity<List<Stop>> {
        return gtfsService.stops.responseEntity
    }

    @GetMapping("/gtfs/trips")
    fun trips(): ResponseEntity<List<Trip>> {
        return gtfsService.trips.responseEntity
    }

    @GetMapping("/gtfs/stoptimes")
    fun stopTimes(): ResponseEntity<List<StopTime>> {
        return gtfsService.stopTimes.get(LocalDate.now()).responseEntity
    }
}
