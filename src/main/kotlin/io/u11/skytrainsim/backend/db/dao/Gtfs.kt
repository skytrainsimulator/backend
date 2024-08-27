package io.u11.skytrainsim.backend.db.dao

import io.u11.skytrainsim.backend.entities.Stop
import io.u11.skytrainsim.backend.entities.StopTime
import io.u11.skytrainsim.backend.entities.Trip
import io.u11.skytrainsim.backend.util.withSpringHande
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.measureTimedValue

@Component
class GtfsDao(private val jdbi: Jdbi) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    val stops: List<Stop> get() =
        jdbi.withSpringHande { h ->
            // Current dataset contains non-skytrain stops. This is a messy but functional way of filtering
            // to only Skytrain stops/stations
            h.createQuery(
                """
                WITH from_stop_times AS (
                    SELECT DISTINCT st.stop_id, parent_station
                    FROM gtfs.stop_times st
                    LEFT JOIN gtfs.trips t ON t.trip_id = st.trip_id
                    LEFT JOIN gtfs.stops s ON s.stop_id = st.stop_id
                    WHERE gtfs.is_skytrain_route(t.route_id)
                )
                SELECT DISTINCT s.stop_id, location_type, stop_name, stop_code, stop_desc, s.parent_station
                FROM gtfs.stops s, from_stop_times
                WHERE s.stop_id = from_stop_times.stop_id OR s.stop_id = from_stop_times.parent_station
                ORDER BY stop_name, location_type;
                """.trimIndent(),
            )
                .map { rs, _ ->
                    Stop(
                        rs.getString("stop_id"),
                        Stop.Type.valueOf(rs.getString("location_type").uppercase()),
                        rs.getString("stop_name"),
                        rs.getString("stop_code"),
                        rs.getString("stop_desc"),
                        rs.getString("parent_station"),
                    )
                }
                .collectIntoList()
        }

    val trips: List<Trip> get() =
        jdbi.withSpringHande { h ->
            h.createQuery(
                """
                SELECT trip_id, route_id, trip_headsign, trip_short_name, block_id 
                FROM gtfs.trips
                WHERE gtfs.is_skytrain_route(route_id)
                """.trimIndent(),
            )
                .map { rs, _ ->
                    Trip(
                        rs.getString("trip_id"),
                        rs.getString("route_id"),
                        rs.getString("trip_headsign"),
                        rs.getString("trip_short_name"),
                        rs.getString("block_id"),
                    )
                }.collectIntoList()
        }

    fun allStopTimesForDate(date: LocalDate) =
        jdbi.withSpringHande { h ->
            logger.debug("Starting allStopTimesForDate query for $date...")
            val timed =
                measureTimedValue {
                    h.createQuery(
                        """
                        WITH date AS (
                            VALUES (:date)
                        ), dates AS (
                            SELECT unnest(ARRAY [date.column1::date - 1, date.column1::date]) AS date FROM date
                        ), filtered_dates AS (
                            SELECT date FROM dates, gtfs.feed_info WHERE feed_start_date <= date AND feed_end_date >= date
                        )
                        SELECT
                            ad.trip_id,
                            ad."date",
                            ad.t_arrival,
                            ad.t_departure,
                            ad.stop_id,
                            ad.stop_sequence,
                            ad.tz
                        FROM filtered_dates fd
                        JOIN gtfs.arrivals_departures ad ON fd.date = ad.date AND gtfs.is_skytrain_route(ad.route_id)
                        ORDER BY ad.t_arrival;
                        """.trimIndent(),
                    )
                        .bind("date", date.toString())
                        .map(StopTimeMapper)
                        .collectIntoList()
                }
            logger.debug("Done! Took ${timed.duration}.")
            timed.value
        }
}

object StopTimeMapper : RowMapper<StopTime> {
    override fun map(
        rs: ResultSet,
        ctx: StatementContext,
    ): StopTime {
        val zone = ZoneId.of(rs.getString("tz"))
        return StopTime(
            rs.getString("trip_id"),
            rs.getTimestamp("t_arrival").toInstant().atZone(zone),
            rs.getTimestamp("t_departure").toInstant().atZone(zone),
            rs.getString("stop_id"),
            rs.getInt("stop_sequence"),
        )
    }
}
