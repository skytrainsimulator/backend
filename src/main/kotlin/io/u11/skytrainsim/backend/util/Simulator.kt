@file:Suppress("UnstableApiUsage")

package io.u11.skytrainsim.backend.util

import io.u11.skytrainsim.backend.entities.timeline.NodeTrackPosition
import io.u11.skytrainsim.backend.entities.timeline.PathingWay
import io.u11.skytrainsim.backend.entities.timeline.TrackPosition
import io.u11.skytrainsim.backend.entities.timeline.WayTrackPosition
import org.jgrapht.Graph

val Collection<PathingWay>.totalLength get() = this.sumOf { it.length }
val Collection<PathingWay>.totalStaticWeight get() = this.sumOf { it.staticWeight }

fun List<PathingWay>.positionAlong(distanceInMeters: Double): TrackPosition {
    if (distanceInMeters > this.totalLength || distanceInMeters < 0) {
        throw IllegalArgumentException(
            "Expected distance in 0..${this.totalLength}, got $distanceInMeters",
        )
    }
    var distanceAlong = distanceInMeters
    for (way in this) {
        if (distanceAlong <= way.length) {
            val percentAlongWay = distanceAlong / way.length
            return WayTrackPosition(way.wayId, if (way.contraflow) way.from - percentAlongWay else way.from + percentAlongWay)
        }
        distanceAlong -= way.length
    }
    throw IllegalStateException("Could not find position $distanceInMeters along path $this!")
}

val List<PathingWay>.fromPosition: TrackPosition get() =
    this.first().run {
        if (from == 0.0) {
            NodeTrackPosition(backingWay.fromNode)
        } else if (from == 1.0) {
            NodeTrackPosition(backingWay.toNode)
        } else {
            WayTrackPosition(wayId, from)
        }
    }
val List<PathingWay>.toPosition: TrackPosition get() =
    this.last().run {
        if (from == 0.0) {
            NodeTrackPosition(backingWay.toNode)
        } else if (from == 1.0) {
            NodeTrackPosition(backingWay.fromNode)
        } else {
            WayTrackPosition(wayId, to)
        }
    }

fun Graph<TrackPosition, PathingWay>.splitAt(split: WayTrackPosition) {
    if (this.containsVertex(split)) return

    val originalEdges = this.edgeSet().filter { it.wayId == split.wayId && split.position in it.encompassedRange }
    if (originalEdges.isEmpty()) throw IllegalArgumentException("No way encompasses $split!")
    this.addVertex(split)
    for (way in originalEdges) {
        val from = this.getEdgeSource(way)
        val to = this.getEdgeTarget(way)
        this.addEdge(from, split, way.copy(to = split.position))
        this.addEdge(split, to, way.copy(from = split.position))
    }
}
