package io.u11.skytrainsim.backend.service

import io.u11.skytrainsim.backend.db.dao.GisDao
import io.u11.skytrainsim.backend.db.dao.GtfsDao
import org.springframework.stereotype.Component

@Component
class SimulatorService(
    private val gtfsDao: GtfsDao,
    private val gisDao: GisDao
) {
//    val realtimeSimulator: RealtimeSimulator = RealtimeSimulator(gisDao, gtfsDao)
//    val simulators: Map<String, Simulator> = mapOf("realtime" to realtimeSimulator)
}
