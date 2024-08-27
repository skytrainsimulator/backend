package io.u11.skytrainsim.backend.simulator

import io.u11.skytrainsim.backend.entities.TrackData
import io.u11.skytrainsim.backend.util.Tickable
import io.u11.skytrainsim.backend.util.TickerTask
import java.util.*

interface Simulator : Tickable {
    val tickerTask: TickerTask
    val trackData: TrackData
    val simulatedTrains: Set<SimulatedTrain>

    fun stop()
}

interface SimulatedTrain {
    val id: String
    val position: TrackPosition
    /**
     * Node ID that the train is facing
     */
    val facing: UUID
}

data class TrackPosition(val edge: UUID, val positionAlong: Double)
