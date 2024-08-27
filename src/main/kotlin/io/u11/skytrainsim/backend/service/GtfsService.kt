package io.u11.skytrainsim.backend.service

import com.github.benmanes.caffeine.cache.Caffeine
import io.u11.skytrainsim.backend.db.dao.GtfsDao
import io.u11.skytrainsim.backend.entities.Stop
import io.u11.skytrainsim.backend.entities.StopTime
import io.u11.skytrainsim.backend.entities.Trip
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Component
class GtfsService(private val dao: GtfsDao) {
    val stopsCache =
        Caffeine.newBuilder()
            .expireAfterWrite(12, TimeUnit.HOURS)
            .build<Unit, List<Stop>> { dao.stops }
    val stops get() = stopsCache.get(Unit)

    val tripsCache =
        Caffeine.newBuilder()
            .expireAfterWrite(12, TimeUnit.HOURS)
            .build<Unit, List<Trip>> { dao.trips }
    val trips get() = tripsCache.get(Unit)

    val stopTimes =
        Caffeine.newBuilder()
            .expireAfterWrite(12, TimeUnit.HOURS)
            .build<LocalDate, List<StopTime>> { dao.allStopTimesForDate(it) }
}
