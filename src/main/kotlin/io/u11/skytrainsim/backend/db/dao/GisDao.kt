package io.u11.skytrainsim.backend.db.dao

import io.u11.skytrainsim.backend.entities.Node
import io.u11.skytrainsim.backend.entities.NodeMilestone
import io.u11.skytrainsim.backend.entities.NodeRailwayCrossing
import io.u11.skytrainsim.backend.entities.NodeStopPosition
import io.u11.skytrainsim.backend.entities.NodeSwitch
import io.u11.skytrainsim.backend.entities.SwitchTurnoutSide
import io.u11.skytrainsim.backend.entities.SwitchType
import io.u11.skytrainsim.backend.entities.TrackData
import io.u11.skytrainsim.backend.entities.TrackSystem
import io.u11.skytrainsim.backend.entities.Way
import io.u11.skytrainsim.backend.entities.WayElevation
import io.u11.skytrainsim.backend.entities.WayService
import io.u11.skytrainsim.backend.util.collectIntoMapBy
import io.u11.skytrainsim.backend.util.getUuid
import io.u11.skytrainsim.backend.util.uuidSet
import io.u11.skytrainsim.backend.util.withSpringHande
import org.geotools.geometry.jts.JTSFactoryFinder
import org.jdbi.v3.core.Jdbi
import org.locationtech.jts.geom.Coordinate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class GisDao(private val jdbi: Jdbi) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun trackData(): TrackData =
        jdbi.withSpringHande { h ->
            val geometryFactory = JTSFactoryFinder.getGeometryFactory()

            val systems =
                h
                    .createQuery("SELECT * FROM gis.systems")
                    .map { rs, _ ->
                        TrackSystem(
                            rs.getUuid("id"),
                            rs.getString("name"),
                            rs.getString("suffix"),
                        )
                    }.collectIntoMapBy { it.id }

            val nodes =
                h
                    .createQuery("SELECT id, ST_X(point) AS x, ST_Y(point) AS y, system_id, osm_id FROM gis.nodes")
                    .map { rs, _ ->
                        Node(
                            rs.getUuid("id"),
                            geometryFactory.createPoint(Coordinate(rs.getDouble("x"), rs.getDouble("y"))),
                            rs.getUuid("system_id"),
                            rs.getString("osm_id"),
                        )
                    }.collectIntoMapBy { it.id }

            // Not sure why KT doesn't like the type cast but it's safe.
            @Suppress("UNCHECKED_CAST")
            val ways =
                h
                    .createQuery("SELECT * FROM gis.combined_ways")
                    .map { rs, _ ->
                        Way(
                            rs.getUuid("id"),
                            rs.getUuid("from_node"),
                            rs.getUuid("to_node"),
                            (
                                rs.getArray("nodes")
                                    .array
                                    .let {
                                        if (it is Array<*> && it.isArrayOf<UUID>()) {
                                            it as Array<UUID>
                                        } else {
                                            throw IllegalStateException("Expected an Array<UUID>, got ${it.javaClass.name}")
                                        }
                                    }
                            ).toList(),
                            WayElevation.fromSql(rs.getString("elevation")),
                            WayService.fromSql(rs.getString("service")),
                            rs.getInt("max_speed"),
                            rs.getBoolean("is_atc"),
                            rs.getBoolean("is_bidirectional"),
                            rs.getDouble("length"),
                            rs.getString("osm_id"),
                        )
                    }.collectIntoMapBy { it.id }

            val milestones =
                h
                    .createQuery("SELECT * FROM gis.node_milestones")
                    .map { rs, _ ->
                        NodeMilestone(
                            rs.getUuid("id"),
                            rs.getString("description"),
                        )
                    }.collectIntoMapBy { it.id }

            val railwayCrossings =
                h
                    .createQuery("SELECT * FROM gis.node_railway_crossings")
                    .map { rs, _ ->
                        NodeRailwayCrossing(
                            rs.getUuid("id"),
                            setOf(
                                rs.getUuid("way_pair_1_a") to
                                    rs.getUuid("way_pair_1_b"),
                                rs.getUuid("way_pair_2_a") to
                                    rs.getUuid("way_pair_2_b"),
                            ),
                        )
                    }.collectIntoMapBy { it.id }

            val stopPositions =
                h
                    .createQuery("SELECT * FROM gis.node_stop_positions")
                    .map { rs, _ ->
                        NodeStopPosition(
                            rs.getUuid("id"),
                            rs.getString("ref"),
                            rs.getString("gtfs_id"),
                        )
                    }.collectIntoMapBy { it.id }

            val switches =
                h
                    .createQuery("SELECT * FROM gis.node_switches")
                    .map { rs, _ ->
                        NodeSwitch(
                            rs.getUuid("id"),
                            rs.getString("ref"),
                            SwitchType.fromSql(rs.getString("type")),
                            SwitchTurnoutSide.fromSql(rs.getString("turnout_side")),
                            rs.getUuid("common_way"),
                            rs.getUuid("left_way"),
                            rs.getUuid("right_way"),
                        )
                    }.collectIntoMapBy { it.id }

            TrackData(
                systems,
                nodes,
                ways,
                h.uuidSet("gis.node_buffer_stops"),
                h.uuidSet("gis.node_crossings"),
                milestones,
                railwayCrossings,
                stopPositions,
                switches,
            )
        }
}
