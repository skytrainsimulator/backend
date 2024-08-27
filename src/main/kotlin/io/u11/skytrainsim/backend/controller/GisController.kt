package io.u11.skytrainsim.backend.controller

import io.u11.skytrainsim.backend.entities.TrackData
import io.u11.skytrainsim.backend.service.GisService
import io.u11.skytrainsim.backend.util.responseEntity
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class GisController(private val gisService: GisService) {
    @GetMapping("/gis/trackData")
    fun trackData(): ResponseEntity<TrackData> = gisService.trackData.responseEntity
}
