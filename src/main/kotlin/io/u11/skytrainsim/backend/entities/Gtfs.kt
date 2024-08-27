package io.u11.skytrainsim.backend.entities

import java.awt.Color
import java.time.Duration
import java.time.ZonedDateTime

data class Stop(
    val stop_id: String,
    val location_type: Type,
    val stop_name: String?,
    val stop_code: String?,
    val stop_desc: String?,
    val parent_station: String?,
) {
    enum class Type {
        STOP,
        STATION,
        ENTRANCE_EXIT,
        NODE,
        BOARDING_AREA,
    }
}

data class Route(
    val route_id: String,
    val route_short_name: String?,
    val route_long_name: String?,
    val route_color: Color,
    val route_text_color: Color,
)

data class Trip(
    val trip_id: String,
    val route_id: String,
    val trip_headsign: String?,
    val trip_short_name: String?,
    val block_id: String?,
)

data class StopTime(
    val trip_id: String,
    val arrival_time: ZonedDateTime,
    val departure_time: ZonedDateTime,
    val stop_id: String,
    val stop_sequence: Int,
) {
    val holdTime = Duration.between(arrival_time, departure_time)
}
