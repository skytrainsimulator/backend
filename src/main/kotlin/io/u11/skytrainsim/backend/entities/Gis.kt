package io.u11.skytrainsim.backend.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.locationtech.jts.geom.Point
import java.util.UUID

data class TrackData(
    @get:JsonIgnore
    val systems: Map<UUID, TrackSystem>,
    @get:JsonIgnore
    val nodes: Map<UUID, Node>,
    @get:JsonIgnore
    val ways: Map<UUID, Way>,
    val bufferStops: Set<UUID>,
    val crossings: Set<UUID>,
    @get:JsonIgnore
    val milestones: Map<UUID, NodeMilestone>,
    @get:JsonIgnore
    val railwayCrossings: Map<UUID, NodeRailwayCrossing>,
    @get:JsonIgnore
    val stopPositions: Map<UUID, NodeStopPosition>,
    @get:JsonIgnore
    val switches: Map<UUID, NodeSwitch>,
) {
    fun nodeForStopId(gtfsId: String): Node? {
        val stopPos = stopPositions.values.find { it.gtfsId == gtfsId } ?: return null
        return nodes[stopPos.id]
    }

    @get:JsonProperty("systems")
    val jsonSystems get() = systems.values

    @get:JsonProperty("nodes")
    val jsonNodes get() = nodes.values

    @get:JsonProperty("ways")
    val jsonWays get() = ways.values

    @get:JsonProperty("milestones")
    val jsonMilestones get() = milestones.values

    @get:JsonProperty("railwayCrossings")
    val jsonRailwayCrossings get() = railwayCrossings.values

    @get:JsonProperty("stopPositions")
    val jsonStopPositions get() = stopPositions.values

    @get:JsonProperty("switches")
    val jsonSwitches get() = switches.values
}

data class TrackSystem(
    val id: UUID,
    val name: String,
    val suffix: String,
)

data class Node(
    val id: UUID,
    val point: Point,
    val systemId: UUID,
    val osmId: String?,
)

data class Way(
    val id: UUID,
    val fromNode: UUID,
    val toNode: UUID,
    val nodes: List<UUID>,
    val elevation: WayElevation,
    val service: WayService,
    val maxSpeed: Int,
    val isAtc: Boolean,
    val isBidirectional: Boolean,
    val length: Double,
    val osmId: String?,
)

data class NodeMilestone(
    val id: UUID,
    val description: String,
)

data class NodeRailwayCrossing(
    val id: UUID,
    val waypairs: Set<Pair<UUID, UUID>>,
)

data class NodeStopPosition(
    val id: UUID,
    val ref: String,
    val gtfsId: String?,
)

data class NodeSwitch(
    val id: UUID,
    val ref: String,
    val type: SwitchType,
    val turnoutSide: SwitchTurnoutSide,
    val commonWay: UUID,
    val leftWay: UUID,
    val rightWay: UUID,
)

enum class SwitchTurnoutSide {
    LEFT,
    RIGHT,
    WYE,
    ;

    companion object {
        fun fromSql(sqlValue: String) =
            when (sqlValue.lowercase()) {
                "left" -> LEFT
                "right" -> RIGHT
                "wye", "y", "fork" -> WYE
                else -> throw IllegalArgumentException("Unknown SwitchTurnoutSide $sqlValue!")
            }
    }
}

enum class SwitchType {
    DIRECT,
    FIELD,
    MANUAL,
    ;

    companion object {
        fun fromSql(sqlValue: String) =
            when (sqlValue.lowercase()) {
                "direct" -> DIRECT
                "field" -> FIELD
                "manual" -> MANUAL
                else -> throw IllegalArgumentException("Unknown SwitchType $sqlValue!")
            }
    }
}

enum class WayElevation {
    TUNNEL,
    CUTTING,
    AT_GRADE,
    VIADUCT,
    BRIDGE,
    ;

    companion object {
        fun fromSql(sqlValue: String) =
            when (sqlValue.lowercase()) {
                "tunnel" -> TUNNEL
                "cutting" -> CUTTING
                "at_grade", "at-grade" -> AT_GRADE
                "viaduct" -> VIADUCT
                "bridge" -> BRIDGE
                else -> throw IllegalArgumentException("Unknown WayElevation $sqlValue!")
            }
    }
}

enum class WayService {
    MAINLINE,
    CROSSOVER,
    SIDING,
    YARD,
    SPUR,
    ;

    companion object {
        fun fromSql(sqlValue: String) =
            when (sqlValue.lowercase()) {
                "mainline" -> MAINLINE
                "crossover" -> CROSSOVER
                "siding" -> SIDING
                "yard" -> YARD
                "spur" -> SPUR
                else -> throw IllegalArgumentException("Unknown WayService $sqlValue!")
            }
    }
}
