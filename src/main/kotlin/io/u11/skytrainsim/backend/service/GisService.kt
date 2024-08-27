package io.u11.skytrainsim.backend.service

import com.github.benmanes.caffeine.cache.Caffeine
import io.u11.skytrainsim.backend.db.dao.GisDao
import io.u11.skytrainsim.backend.entities.TrackData
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class GisService(private val gisDao: GisDao) {
    val trackDataCache =
        Caffeine.newBuilder()
            .expireAfterWrite(12, TimeUnit.HOURS)
            .build<Unit, TrackData> { gisDao.trackData() }
    val trackData get() = trackDataCache.get(Unit)
}
