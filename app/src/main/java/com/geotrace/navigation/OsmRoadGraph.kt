package com.geotrace.navigation

import com.geotrace.model.LocationPoint
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.PriorityQueue
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class RoadClass(val code: Byte, val defaultSpeedKmh: Float) {
    MOTORWAY(0, 100f),
    TRUNK(1, 80f),
    PRIMARY(2, 65f),
    SECONDARY(3, 55f),
    TERTIARY(4, 45f),
    UNCLASSIFIED(5, 40f),
    RESIDENTIAL(6, 30f),
    LIVING_STREET(7, 20f),
    SERVICE(8, 20f),
    TRACK(9, 18f),
    CYCLEWAY(10, 18f),
    FOOTWAY(11, 5f);

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: Byte): RoadClass = byCode[code] ?: UNCLASSIFIED
    }
}

object OsmRoadFlags {
    const val CAR = 1 shl 0
    const val MOTO = 1 shl 1
    const val BIKE = 1 shl 2
    const val WALK = 1 shl 3
    const val ROUNDABOUT = 1 shl 4
    const val UNPAVED = 1 shl 5

    fun maskForProfile(profile: NavProfile): Int = when (profile) {
        NavProfile.DRIVING -> CAR
        NavProfile.MOTORCYCLE -> MOTO
        NavProfile.CYCLING -> BIKE
        NavProfile.WALKING -> WALK
    }
}

data class RoadVertex(
    val osmNodeId: Long,
    val lat: Double,
    val lon: Double
)

data class RoadEdge(
    val fromVertex: Int,
    val toVertex: Int,
    val wayOsmId: Long,
    val streetName: String,
    val roadClass: RoadClass,
    val maxSpeedKmh: Int,
    val distanceMeters: Float,
    val flags: Int,
    val interiorLats: DoubleArray,
    val interiorLons: DoubleArray
) {
    fun allows(profile: NavProfile): Boolean = (flags and OsmRoadFlags.maskForProfile(profile)) != 0
}

class OsmRoadGraphData(
    val vertices: List<RoadVertex>,
    val edges: List<RoadEdge>
) {
    val outgoingEdgeIndices: Array<IntArray>

    init {
        val counts = IntArray(vertices.size)
        for (edge in edges) {
            if (edge.fromVertex in vertices.indices && edge.toVertex in vertices.indices) {
                counts[edge.fromVertex]++
            }
        }
        outgoingEdgeIndices = Array(vertices.size) { IntArray(counts[it]) }
        val writeIdx = IntArray(vertices.size)
        for (eIdx in edges.indices) {
            val u = edges[eIdx].fromVertex
            val v = edges[eIdx].toVertex
            if (u in vertices.indices && v in vertices.indices) {
                outgoingEdgeIndices[u][writeIdx[u]++] = eIdx
            }
        }
    }

    val isEmpty: Boolean get() = vertices.isEmpty() || edges.isEmpty()
}

object OsmRoadGraphCodec {

    private const val MAGIC = 0x47545244 // "GTRD"
    private const val VERSION = 1
    private const val COORD_SCALE = 10_000_000.0

    fun parseOverpassHighwayJson(rawJson: String): OsmRoadGraphData {
        val root = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            return OsmRoadGraphData(emptyList(), emptyList())
        }
        val elements = root.optJSONArray("elements") ?: return OsmRoadGraphData(emptyList(), emptyList())

        val nodeIndexById = HashMap<Long, Int>(elements.length() / 2)
        val nodeLatsList = ArrayList<Double>(elements.length() / 2)
        val nodeLonsList = ArrayList<Double>(elements.length() / 2)
        val rawWays = ArrayList<JSONObject>(elements.length() / 4)

        for (i in 0 until elements.length()) {
            val el = elements.optJSONObject(i) ?: continue
            when (el.optString("type")) {
                "node" -> {
                    val id = el.optLong("id", Long.MIN_VALUE)
                    val lat = el.optDouble("lat", Double.NaN)
                    val lon = el.optDouble("lon", Double.NaN)
                    if (id != Long.MIN_VALUE && lat.isFinite() && lon.isFinite()) {
                        if (!nodeIndexById.containsKey(id)) {
                            nodeIndexById[id] = nodeLatsList.size
                            nodeLatsList.add(lat)
                            nodeLonsList.add(lon)
                        }
                    }
                }
                "way" -> {
                    val tags = el.optJSONObject("tags") ?: continue
                    val highway = tags.optString("highway", "").trim().lowercase(Locale.US)
                    if (highway.isNotBlank() && isRoutableHighway(highway)) {
                        rawWays.add(el)
                    }
                }
            }
        }

        if (rawWays.isEmpty() || nodeLatsList.isEmpty()) {
            return OsmRoadGraphData(emptyList(), emptyList())
        }

        // Count node references across ways so intersections & endpoints become graph vertices
        val refCounts = HashMap<Long, Int>(nodeLatsList.size)
        val endpointSet = HashSet<Long>(rawWays.size * 2)

        data class ValidWay(
            val wayId: Long,
            val nodeIds: LongArray,
            val tags: JSONObject,
            val highway: String
        )

        val validWays = ArrayList<ValidWay>(rawWays.size)
        for (wayObj in rawWays) {
            val wayId = wayObj.optLong("id", 0L)
            val tags = wayObj.optJSONObject("tags") ?: continue
            val highway = tags.optString("highway", "").trim().lowercase(Locale.US)
            val ndArr = wayObj.optJSONArray("nodes") ?: continue
            if (ndArr.length() < 2) continue

            val filteredIds = LongArray(ndArr.length())
            var count = 0
            for (j in 0 until ndArr.length()) {
                val nid = ndArr.optLong(j, Long.MIN_VALUE)
                if (nid != Long.MIN_VALUE && nodeIndexById.containsKey(nid)) {
                    filteredIds[count++] = nid
                }
            }
            if (count < 2) continue

            val compactIds = if (count == filteredIds.size) filteredIds else filteredIds.copyOf(count)
            validWays.add(ValidWay(wayId, compactIds, tags, highway))

            endpointSet.add(compactIds.first())
            endpointSet.add(compactIds.last())
            for (nid in compactIds) {
                refCounts[nid] = (refCounts[nid] ?: 0) + 1
            }
        }

        if (validWays.isEmpty()) {
            return OsmRoadGraphData(emptyList(), emptyList())
        }

        val vertices = ArrayList<RoadVertex>(endpointSet.size + refCounts.size / 4)
        val vertexIdxByOsmId = HashMap<Long, Int>()

        fun getOrCreateVertex(osmNodeId: Long): Int {
            vertexIdxByOsmId[osmNodeId]?.let { return it }
            val rawIdx = nodeIndexById[osmNodeId] ?: return -1
            val vIdx = vertices.size
            vertexIdxByOsmId[osmNodeId] = vIdx
            vertices.add(RoadVertex(osmNodeId, nodeLatsList[rawIdx], nodeLonsList[rawIdx]))
            return vIdx
        }

        fun isVertexNode(osmNodeId: Long): Boolean {
            return (refCounts[osmNodeId] ?: 0) >= 2 || endpointSet.contains(osmNodeId)
        }

        val edges = ArrayList<RoadEdge>(validWays.size * 2)

        for (way in validWays) {
            val roadClass = mapHighwayClass(way.highway)
            val (fwdFlags, revFlags) = parseAccessFlags(way.highway, roadClass, way.tags)
            if (fwdFlags == 0 && revFlags == 0) continue

            val streetName = extractStreetName(way.tags)
            val maxSpeedKmh = parseMaxSpeedKmh(way.tags)
            val ids = way.nodeIds

            var segStartIdx = 0
            for (i in 1 until ids.size) {
                val nid = ids[i]
                if (!isVertexNode(nid) && i < ids.lastIndex) continue

                val fromV = getOrCreateVertex(ids[segStartIdx])
                val toV = getOrCreateVertex(nid)
                if (fromV >= 0 && toV >= 0 && !(fromV == toV && i - segStartIdx < 2)) {
                    val interiorCount = (i - segStartIdx - 1).coerceAtLeast(0)
                    val intLats = DoubleArray(interiorCount)
                    val intLons = DoubleArray(interiorCount)
                    for (k in 0 until interiorCount) {
                        val rawIdx = nodeIndexById[ids[segStartIdx + 1 + k]]!!
                        intLats[k] = nodeLatsList[rawIdx]
                        intLons[k] = nodeLonsList[rawIdx]
                    }

                    val distM = computePolylineMeters(
                        vertices[fromV].lat,
                        vertices[fromV].lon,
                        intLats,
                        intLons,
                        vertices[toV].lat,
                        vertices[toV].lon
                    ).toFloat().coerceAtLeast(0.5f)

                    if (fwdFlags != 0) {
                        edges.add(
                            RoadEdge(
                                fromVertex = fromV,
                                toVertex = toV,
                                wayOsmId = way.wayId,
                                streetName = streetName,
                                roadClass = roadClass,
                                maxSpeedKmh = maxSpeedKmh,
                                distanceMeters = distM,
                                flags = fwdFlags,
                                interiorLats = intLats,
                                interiorLons = intLons
                            )
                        )
                    }
                    if (revFlags != 0) {
                        edges.add(
                            RoadEdge(
                                fromVertex = toV,
                                toVertex = fromV,
                                wayOsmId = way.wayId,
                                streetName = streetName,
                                roadClass = roadClass,
                                maxSpeedKmh = maxSpeedKmh,
                                distanceMeters = distM,
                                flags = revFlags,
                                interiorLats = intLats.reversedArray(),
                                interiorLons = intLons.reversedArray()
                            )
                        )
                    }
                }
                segStartIdx = i
            }
        }

        return OsmRoadGraphData(vertices, edges)
    }

    fun writeBinary(graph: OsmRoadGraphData, outputStream: OutputStream) {
        val stringToIdx = LinkedHashMap<String, Int>()
        val strings = ArrayList<String>()
        fun intern(s: String): Int {
            return stringToIdx.getOrPut(s) {
                val idx = strings.size
                strings.add(s)
                idx
            }
        }
        val edgeNameIndices = IntArray(graph.edges.size) { i -> intern(graph.edges[i].streetName) }

        DataOutputStream(BufferedOutputStream(outputStream, 16_384)).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(graph.vertices.size)
            out.writeInt(strings.size)
            out.writeInt(graph.edges.size)

            for (v in graph.vertices) {
                out.writeLong(v.osmNodeId)
                out.writeInt((v.lat * COORD_SCALE).roundToInt())
                out.writeInt((v.lon * COORD_SCALE).roundToInt())
            }

            for (s in strings) {
                val bytes = s.toByteArray(Charsets.UTF_8)
                out.writeShort(bytes.size.coerceAtMost(Short.MAX_VALUE.toInt()))
                if (bytes.isNotEmpty()) {
                    out.write(bytes, 0, bytes.size.coerceAtMost(Short.MAX_VALUE.toInt()))
                }
            }

            for (i in graph.edges.indices) {
                val e = graph.edges[i]
                out.writeInt(e.fromVertex)
                out.writeInt(e.toVertex)
                out.writeLong(e.wayOsmId)
                out.writeInt(edgeNameIndices[i])
                out.writeByte(e.roadClass.code.toInt())
                out.writeShort(e.maxSpeedKmh.coerceIn(0, 500))
                out.writeFloat(e.distanceMeters)
                out.writeShort(e.flags)
                val nInt = e.interiorLats.size.coerceAtMost(Short.MAX_VALUE.toInt())
                out.writeShort(nInt)
                for (k in 0 until nInt) {
                    out.writeInt((e.interiorLats[k] * COORD_SCALE).roundToInt())
                    out.writeInt((e.interiorLons[k] * COORD_SCALE).roundToInt())
                }
            }
            out.flush()
        }
    }

    fun readBinary(inputStream: InputStream): OsmRoadGraphData {
        DataInputStream(BufferedInputStream(inputStream, 16_384)).use { input ->
            val magic = input.readInt()
            require(magic == MAGIC) { "Invalid .gtroad file header" }
            val version = input.readInt()
            require(version == VERSION) { "Unsupported .gtroad version $version" }

            val vertexCount = input.readInt()
            val stringCount = input.readInt()
            val edgeCount = input.readInt()

            val vertices = ArrayList<RoadVertex>(vertexCount)
            for (i in 0 until vertexCount) {
                val osmId = input.readLong()
                val lat = input.readInt() / COORD_SCALE
                val lon = input.readInt() / COORD_SCALE
                vertices.add(RoadVertex(osmId, lat, lon))
            }

            val strings = ArrayList<String>(stringCount)
            for (i in 0 until stringCount) {
                val len = input.readUnsignedShort()
                if (len == 0) {
                    strings.add("")
                } else {
                    val buf = ByteArray(len)
                    input.readFully(buf)
                    strings.add(String(buf, Charsets.UTF_8))
                }
            }

            val edges = ArrayList<RoadEdge>(edgeCount)
            for (i in 0 until edgeCount) {
                val fromV = input.readInt()
                val toV = input.readInt()
                val wayId = input.readLong()
                val nameIdx = input.readInt()
                val rcCode = input.readByte()
                val maxSpeed = input.readUnsignedShort()
                val distM = input.readFloat()
                val flags = input.readUnsignedShort()
                val nInt = input.readUnsignedShort()
                val intLats = DoubleArray(nInt)
                val intLons = DoubleArray(nInt)
                for (k in 0 until nInt) {
                    intLats[k] = input.readInt() / COORD_SCALE
                    intLons[k] = input.readInt() / COORD_SCALE
                }
                edges.add(
                    RoadEdge(
                        fromVertex = fromV,
                        toVertex = toV,
                        wayOsmId = wayId,
                        streetName = strings.getOrElse(nameIdx) { "" },
                        roadClass = RoadClass.fromCode(rcCode),
                        maxSpeedKmh = maxSpeed,
                        distanceMeters = distM,
                        flags = flags,
                        interiorLats = intLats,
                        interiorLons = intLons
                    )
                )
            }
            return OsmRoadGraphData(vertices, edges)
        }
    }

    /**
     * Merges multiple region/corridor road graphs into one connected network,
     * stitching adjacent or overlapping packs cleanly via 64-bit OSM intersection IDs.
     */
    fun mergeGraphs(graphs: List<OsmRoadGraphData>): OsmRoadGraphData {
        if (graphs.isEmpty()) return OsmRoadGraphData(emptyList(), emptyList())
        if (graphs.size == 1) return graphs.first()

        val mergedVertices = ArrayList<RoadVertex>()
        val osmIdToMergedIdx = HashMap<Long, Int>()
        val mergedEdges = ArrayList<RoadEdge>()
        val seenEdgeKeys = HashSet<String>()

        for (g in graphs) {
            if (g.isEmpty) continue
            val localToMerged = IntArray(g.vertices.size)
            for (i in g.vertices.indices) {
                val v = g.vertices[i]
                localToMerged[i] = osmIdToMergedIdx.getOrPut(v.osmNodeId) {
                    val idx = mergedVertices.size
                    mergedVertices.add(v)
                    idx
                }
            }
            for (e in g.edges) {
                val newFrom = localToMerged[e.fromVertex]
                val newTo = localToMerged[e.toVertex]
                val fromOsm = g.vertices[e.fromVertex].osmNodeId
                val toOsm = g.vertices[e.toVertex].osmNodeId
                val key = "${fromOsm}_${toOsm}_${e.wayOsmId}_${e.interiorLats.size}_${e.distanceMeters.roundToInt()}"
                if (seenEdgeKeys.add(key)) {
                    mergedEdges.add(e.copy(fromVertex = newFrom, toVertex = newTo))
                }
            }
        }
        return OsmRoadGraphData(mergedVertices, mergedEdges)
    }

    private fun isRoutableHighway(highway: String): Boolean {
        return highway !in setOf(
            "proposed",
            "construction",
            "abandoned",
            "razed",
            "bus_guideway",
            "raceway",
            "corridor",
            "elevator",
            "platform"
        )
    }

    private fun mapHighwayClass(highway: String): RoadClass = when (highway) {
        "motorway", "motorway_link" -> RoadClass.MOTORWAY
        "trunk", "trunk_link" -> RoadClass.TRUNK
        "primary", "primary_link" -> RoadClass.PRIMARY
        "secondary", "secondary_link" -> RoadClass.SECONDARY
        "tertiary", "tertiary_link" -> RoadClass.TERTIARY
        "residential" -> RoadClass.RESIDENTIAL
        "living_street" -> RoadClass.LIVING_STREET
        "service" -> RoadClass.SERVICE
        "track" -> RoadClass.TRACK
        "cycleway" -> RoadClass.CYCLEWAY
        "footway", "pedestrian", "path", "steps" -> RoadClass.FOOTWAY
        else -> RoadClass.UNCLASSIFIED
    }

    private fun parseAccessFlags(highway: String, roadClass: RoadClass, tags: JSONObject): Pair<Int, Int> {
        val access = tags.optString("access").trim().lowercase(Locale.US)
        val motorVehicle = tags.optString("motor_vehicle").trim().lowercase(Locale.US)
        val motorcar = tags.optString("motorcar").trim().lowercase(Locale.US)
        val motorcycle = tags.optString("motorcycle").trim().lowercase(Locale.US)
        val bicycle = tags.optString("bicycle").trim().lowercase(Locale.US)
        val foot = tags.optString("foot").trim().lowercase(Locale.US)
        val junction = tags.optString("junction").trim().lowercase(Locale.US)
        val surface = tags.optString("surface").trim().lowercase(Locale.US)

        val generalBlocked = access == "no" || access == "private"
        val motorBlocked = generalBlocked ||
            motorVehicle in setOf("no", "private") ||
            roadClass == RoadClass.CYCLEWAY ||
            roadClass == RoadClass.FOOTWAY ||
            highway == "steps"

        var allowCar = !motorBlocked && motorcar !in setOf("no", "private")
        var allowMoto = !motorBlocked && motorcycle !in setOf("no", "private")
        if (motorcar in setOf("yes", "permissive", "designated")) allowCar = true
        if (motorcycle in setOf("yes", "permissive", "designated")) allowMoto = true

        var allowBike = !generalBlocked &&
            highway != "steps" &&
            roadClass != RoadClass.MOTORWAY &&
            bicycle !in setOf("no", "private", "dismount")
        if (roadClass == RoadClass.FOOTWAY && highway !in setOf("path", "pedestrian") && bicycle != "yes" && bicycle != "designated") {
            allowBike = false
        }
        if (bicycle in setOf("yes", "designated", "permissive")) allowBike = true

        var allowWalk = !generalBlocked &&
            roadClass != RoadClass.MOTORWAY &&
            foot !in setOf("no", "private")
        if (foot in setOf("yes", "designated", "permissive")) allowWalk = true

        val isRoundabout = junction == "roundabout" || junction == "circular"
        val isUnpaved = roadClass == RoadClass.TRACK ||
            highway == "path" ||
            surface in setOf("unpaved", "gravel", "fine_gravel", "compacted", "dirt", "earth", "mud", "sand", "grass", "ground")

        val commonMeta = (if (isRoundabout) OsmRoadFlags.ROUNDABOUT else 0) or
            (if (isUnpaved) OsmRoadFlags.UNPAVED else 0)

        val onewayRaw = tags.optString("oneway").trim().lowercase(Locale.US)
        val motorDir = when {
            onewayRaw in setOf("yes", "true", "1") -> 1
            onewayRaw in setOf("-1", "reverse") -> -1
            onewayRaw == "no" -> 0
            isRoundabout || roadClass == RoadClass.MOTORWAY -> 1
            else -> 0
        }
        val bikeOnewayRaw = tags.optString("oneway:bicycle").trim().lowercase(Locale.US)
        val cyclewayTag = tags.optString("cycleway").trim().lowercase(Locale.US) +
            " " + tags.optString("cycleway:left").trim().lowercase(Locale.US) +
            " " + tags.optString("cycleway:right").trim().lowercase(Locale.US)
        val bikeDir = when {
            bikeOnewayRaw == "no" || cyclewayTag.contains("opposite") -> 0
            else -> motorDir
        }

        var fwdFlags = 0
        var revFlags = 0

        if (allowCar) {
            if (motorDir >= 0) fwdFlags = fwdFlags or OsmRoadFlags.CAR
            if (motorDir <= 0) revFlags = revFlags or OsmRoadFlags.CAR
        }
        if (allowMoto) {
            if (motorDir >= 0) fwdFlags = fwdFlags or OsmRoadFlags.MOTO
            if (motorDir <= 0) revFlags = revFlags or OsmRoadFlags.MOTO
        }
        if (allowBike) {
            if (bikeDir >= 0) fwdFlags = fwdFlags or OsmRoadFlags.BIKE
            if (bikeDir <= 0) revFlags = revFlags or OsmRoadFlags.BIKE
        }
        if (allowWalk) {
            fwdFlags = fwdFlags or OsmRoadFlags.WALK
            revFlags = revFlags or OsmRoadFlags.WALK
        }

        if (fwdFlags != 0) fwdFlags = fwdFlags or commonMeta
        if (revFlags != 0) revFlags = revFlags or commonMeta
        return fwdFlags to revFlags
    }

    private fun extractStreetName(tags: JSONObject): String {
        val name = tags.optString("name", "").trim()
        if (name.isNotBlank()) return name
        val ref = tags.optString("ref", "").trim()
        if (ref.isNotBlank()) return ref
        return ""
    }

    private fun parseMaxSpeedKmh(tags: JSONObject): Int {
        val raw = tags.optString("maxspeed", "").trim().lowercase(Locale.US)
        if (raw.isBlank()) return 0
        val digits = Regex("""\d+""").find(raw)?.value?.toIntOrNull() ?: return 0
        val kmh = if (raw.contains("mph")) (digits * 1.60934).roundToInt() else digits
        return kmh.coerceIn(5, 140)
    }

    private fun computePolylineMeters(
        startLat: Double,
        startLon: Double,
        intLats: DoubleArray,
        intLons: DoubleArray,
        endLat: Double,
        endLon: Double
    ): Double {
        var total = 0.0
        var prevLat = startLat
        var prevLon = startLon
        for (i in intLats.indices) {
            total += haversineMeters(prevLat, prevLon, intLats[i], intLons[i])
            prevLat = intLats[i]
            prevLon = intLons[i]
        }
        total += haversineMeters(prevLat, prevLon, endLat, endLon)
        return total
    }
}

object LocalOsmGraphRouter {

    private const val MAX_SNAP_METERS = 1500.0
    private const val SNAP_CANDIDATE_COUNT = 6
    private const val MAX_EXPANSIONS = 250_000

    private data class EdgeProjection(
        val edgeIdx: Int,
        val distToRoadMeters: Double,
        val projLat: Double,
        val projLon: Double,
        val segIndex: Int, // index within edge polyline [0..numPts-2]
        val distFromStartMeters: Double,
        val distToEndMeters: Double
    )

    private data class AStarNode(
        val vertexIdx: Int,
        val fSec: Double
    ) : Comparable<AStarNode> {
        override fun compareTo(other: AStarNode): Int = fSec.compareTo(other.fSec)
    }

    private data class PathSegment(
        val edgeIdx: Int,
        val streetName: String,
        val roadClass: RoadClass,
        val flags: Int,
        val latPoints: DoubleArray,
        val lonPoints: DoubleArray,
        val distanceMeters: Double,
        val durationSeconds: Double
    )

    fun route(
        graph: OsmRoadGraphData,
        originLat: Double,
        originLon: Double,
        destinationName: String,
        destinationLat: Double,
        destinationLon: Double,
        profile: NavProfile,
        includeAlternatives: Boolean = true
    ): Result<List<NavRoute>> {
        if (graph.isEmpty) {
            return Result.failure(IllegalStateException("Offline road graph is empty"))
        }

        val originSnaps = findNearestEdges(graph, originLat, originLon, profile)
        if (originSnaps.isEmpty()) {
            return Result.failure(IllegalStateException("Origin is >1.5 km away from any offline mapped ${profile.label.lowercase(Locale.US)} road"))
        }
        val destSnaps = findNearestEdges(graph, destinationLat, destinationLon, profile)
        if (destSnaps.isEmpty()) {
            return Result.failure(IllegalStateException("Destination is >1.5 km away from any offline mapped ${profile.label.lowercase(Locale.US)} road"))
        }

        val primarySegments = solveSingleRoute(
            graph = graph,
            originSnaps = originSnaps,
            destSnaps = destSnaps,
            destLat = destinationLat,
            destLon = destinationLon,
            profile = profile,
            penalizedEdgeIndices = emptySet()
        ) ?: return Result.failure(IllegalStateException("No connected offline road path found for ${profile.label}"))

        val routes = mutableListOf<NavRoute>()
        val nowMs = System.currentTimeMillis()
        val primaryRoute = buildNavRoute(
            segments = primarySegments,
            originLat = originLat,
            originLon = originLon,
            destinationName = destinationName,
            destinationLat = destinationLat,
            destinationLon = destinationLon,
            profile = profile,
            graph = graph,
            nowMs = nowMs,
            suffix = "0"
        )
        routes.add(primaryRoute)

        if (includeAlternatives && primarySegments.isNotEmpty()) {
            val primaryEdgeSet = primarySegments.map { it.edgeIdx }.toSet()
            val altSegments = solveSingleRoute(
                graph = graph,
                originSnaps = originSnaps,
                destSnaps = destSnaps,
                destLat = destinationLat,
                destLon = destinationLon,
                profile = profile,
                penalizedEdgeIndices = primaryEdgeSet
            )
            if (altSegments != null) {
                val altEdgeSet = altSegments.map { it.edgeIdx }.toSet()
                val sharedCount = altEdgeSet.count { it in primaryEdgeSet }
                val overlapRatio = sharedCount.toDouble() / max(1, altEdgeSet.size)
                val altRoute = buildNavRoute(
                    segments = altSegments,
                    originLat = originLat,
                    originLon = originLon,
                    destinationName = destinationName,
                    destinationLat = destinationLat,
                    destinationLon = destinationLon,
                    profile = profile,
                    graph = graph,
                    nowMs = nowMs,
                    suffix = "alt1"
                )
                if (overlapRatio < 0.78 && altRoute.totalDurationSeconds <= primaryRoute.totalDurationSeconds * 1.75) {
                    routes.add(altRoute)
                }
            }
        }

        return Result.success(routes)
    }

    private fun solveSingleRoute(
        graph: OsmRoadGraphData,
        originSnaps: List<EdgeProjection>,
        destSnaps: List<EdgeProjection>,
        destLat: Double,
        destLon: Double,
        profile: NavProfile,
        penalizedEdgeIndices: Set<Int>
    ): List<PathSegment>? {
        var bestTotalSec = Double.POSITIVE_INFINITY
        var bestDirect: PathSegment? = null

        // Check direct same-edge forward traversal (origin -> destination on same street segment)
        for (oSnap in originSnaps) {
            for (dSnap in destSnaps) {
                if (oSnap.edgeIdx == dSnap.edgeIdx && oSnap.distFromStartMeters <= dSnap.distFromStartMeters + 2.0) {
                    val e = graph.edges[oSnap.edgeIdx]
                    val distM = (dSnap.distFromStartMeters - oSnap.distFromStartMeters).coerceAtLeast(1.0)
                    val durS = distM / effectiveSpeedMps(profile, e.roadClass, e.maxSpeedKmh, e.flags)
                    val snapCost = snapPenaltySeconds(oSnap.distToRoadMeters) + snapPenaltySeconds(dSnap.distToRoadMeters)
                    val penalty = if (oSnap.edgeIdx in penalizedEdgeIndices) durS * 0.95 else 0.0
                    val totalCost = durS + snapCost + penalty
                    if (totalCost < bestTotalSec) {
                        bestTotalSec = totalCost
                        val (lats, lons) = sliceEdgePoints(graph, oSnap.edgeIdx, oSnap.segIndex, oSnap.projLat, oSnap.projLon, dSnap.segIndex, dSnap.projLat, dSnap.projLon)
                        bestDirect = PathSegment(
                            edgeIdx = oSnap.edgeIdx,
                            streetName = e.streetName,
                            roadClass = e.roadClass,
                            flags = e.flags,
                            latPoints = lats,
                            lonPoints = lons,
                            distanceMeters = distM,
                            durationSeconds = durS
                        )
                    }
                }
            }
        }

        val destByFromVertex = HashMap<Int, MutableList<EdgeProjection>>(destSnaps.size)
        for (dSnap in destSnaps) {
            val u = graph.edges[dSnap.edgeIdx].fromVertex
            destByFromVertex.getOrPut(u) { mutableListOf() }.add(dSnap)
        }

        val nV = graph.vertices.size
        val bestG = DoubleArray(nV) { Double.POSITIVE_INFINITY }
        val parentVertex = IntArray(nV) { -1 }
        val parentEdgeIdx = IntArray(nV) { -1 }
        val startSnapForVertex = arrayOfNulls<EdgeProjection>(nV)
        val closed = BooleanArray(nV)

        val maxSpeed = maxHeuristicSpeedMps(profile)
        val pq = PriorityQueue<AStarNode>(min(nV, 1024).coerceAtLeast(16))

        for (oSnap in originSnaps) {
            val e = graph.edges[oSnap.edgeIdx]
            val toV = e.toVertex
            val remDist = oSnap.distToEndMeters.coerceAtLeast(0.5)
            val edgeCost = (remDist / effectiveSpeedMps(profile, e.roadClass, e.maxSpeedKmh, e.flags)) *
                (if (oSnap.edgeIdx in penalizedEdgeIndices) 1.95 else 1.0)
            val g0 = snapPenaltySeconds(oSnap.distToRoadMeters) + edgeCost
            if (g0 < bestG[toV]) {
                bestG[toV] = g0
                parentVertex[toV] = -1
                parentEdgeIdx[toV] = -1
                startSnapForVertex[toV] = oSnap
                val v = graph.vertices[toV]
                val h = haversineMeters(v.lat, v.lon, destLat, destLon) / maxSpeed
                pq.add(AStarNode(toV, g0 + h))
            }
        }

        var bestGoalVertex = -1
        var bestGoalDestSnap: EdgeProjection? = null
        var expansions = 0

        while (pq.isNotEmpty() && expansions < MAX_EXPANSIONS) {
            val cur = pq.poll() ?: break
            val u = cur.vertexIdx
            if (closed[u]) continue
            if (cur.fSec > bestTotalSec) break
            closed[u] = true
            expansions++

            // Check destination exit snaps starting at u
            destByFromVertex[u]?.forEach { dSnap ->
                val e = graph.edges[dSnap.edgeIdx]
                val endDist = dSnap.distFromStartMeters.coerceAtLeast(0.5)
                val endCost = (endDist / effectiveSpeedMps(profile, e.roadClass, e.maxSpeedKmh, e.flags)) *
                    (if (dSnap.edgeIdx in penalizedEdgeIndices) 1.95 else 1.0) +
                    snapPenaltySeconds(dSnap.distToRoadMeters)
                val candidateTotal = bestG[u] + endCost
                if (candidateTotal < bestTotalSec) {
                    bestTotalSec = candidateTotal
                    bestGoalVertex = u
                    bestGoalDestSnap = dSnap
                    bestDirect = null
                }
            }

            val incomingEdgeIdx = parentEdgeIdx[u]
            val outgoing = graph.outgoingEdgeIndices[u]
            for (eIdx in outgoing) {
                val e = graph.edges[eIdx]
                if (!e.allows(profile)) continue
                val v = e.toVertex
                if (closed[v]) continue

                var stepSec = e.distanceMeters.toDouble() / effectiveSpeedMps(profile, e.roadClass, e.maxSpeedKmh, e.flags)
                if (eIdx in penalizedEdgeIndices) stepSec *= 1.95
                // Small realism turn/hierarchy penalty to discourage alley zig-zags
                if (incomingEdgeIdx >= 0) {
                    val prevEdge = graph.edges[incomingEdgeIdx]
                    if (prevEdge.wayOsmId != e.wayOsmId) {
                        stepSec += 1.2
                    }
                    if (profile == NavProfile.DRIVING &&
                        e.roadClass == RoadClass.SERVICE &&
                        prevEdge.roadClass != RoadClass.SERVICE
                    ) {
                        stepSec += 4.5
                    }
                }

                val tentativeG = bestG[u] + stepSec
                if (tentativeG < bestG[v]) {
                    bestG[v] = tentativeG
                    parentVertex[v] = u
                    parentEdgeIdx[v] = eIdx
                    startSnapForVertex[v] = null
                    val nextV = graph.vertices[v]
                    val h = haversineMeters(nextV.lat, nextV.lon, destLat, destLon) / maxSpeed
                    pq.add(AStarNode(v, tentativeG + h))
                }
            }
        }

        if (bestDirect != null) {
            return listOf(bestDirect)
        }
        if (bestGoalVertex < 0 || bestGoalDestSnap == null) return null

        // Reconstruct path backwards from bestGoalVertex
        val edgeChain = ArrayList<Int>()
        var curr = bestGoalVertex
        var originSnap: EdgeProjection? = null
        while (curr >= 0) {
            val sSnap = startSnapForVertex[curr]
            if (sSnap != null) {
                originSnap = sSnap
                break
            }
            val pEdge = parentEdgeIdx[curr]
            val pVert = parentVertex[curr]
            if (pEdge < 0 || pVert < 0) break
            edgeChain.add(pEdge)
            curr = pVert
        }
        val firstSnap = originSnap ?: return null
        edgeChain.reverse()

        val segments = ArrayList<PathSegment>(edgeChain.size + 2)

        // 1. Entry partial segment (oSnap.proj -> oSnap.edge.toVertex)
        run {
            val e = graph.edges[firstSnap.edgeIdx]
            val full = fullEdgePoints(graph, firstSnap.edgeIdx)
            val (lats, lons) = sliceEdgePoints(
                graph = graph,
                edgeIdx = firstSnap.edgeIdx,
                fromSegIdx = firstSnap.segIndex,
                fromLat = firstSnap.projLat,
                fromLon = firstSnap.projLon,
                toSegIdx = full.first.lastIndex - 1,
                toLat = full.first.last(),
                toLon = full.second.last()
            )
            val distM = computeArrayDistance(lats, lons).coerceAtLeast(0.5)
            val speed = effectiveSpeedMps(profile, e.roadClass, e.maxSpeedKmh, e.flags)
            segments.add(
                PathSegment(
                    edgeIdx = firstSnap.edgeIdx,
                    streetName = e.streetName,
                    roadClass = e.roadClass,
                    flags = e.flags,
                    latPoints = lats,
                    lonPoints = lons,
                    distanceMeters = distM,
                    durationSeconds = distM / speed
                )
            )
        }

        // 2. Intermediate full graph edges
        for (eIdx in edgeChain) {
            val e = graph.edges[eIdx]
            val (lats, lons) = fullEdgePoints(graph, eIdx)
            val distM = e.distanceMeters.toDouble().coerceAtLeast(0.5)
            val speed = effectiveSpeedMps(profile, e.roadClass, e.maxSpeedKmh, e.flags)
            segments.add(
                PathSegment(
                    edgeIdx = eIdx,
                    streetName = e.streetName,
                    roadClass = e.roadClass,
                    flags = e.flags,
                    latPoints = lats,
                    lonPoints = lons,
                    distanceMeters = distM,
                    durationSeconds = distM / speed
                )
            )
        }

        // 3. Final exit partial segment (dSnap.edge.fromVertex -> dSnap.proj)
        run {
            val e = graph.edges[bestGoalDestSnap.edgeIdx]
            val full = fullEdgePoints(graph, bestGoalDestSnap.edgeIdx)
            val (lats, lons) = sliceEdgePoints(
                graph = graph,
                edgeIdx = bestGoalDestSnap.edgeIdx,
                fromSegIdx = 0,
                fromLat = full.first.first(),
                fromLon = full.second.first(),
                toSegIdx = bestGoalDestSnap.segIndex,
                toLat = bestGoalDestSnap.projLat,
                toLon = bestGoalDestSnap.projLon
            )
            val distM = computeArrayDistance(lats, lons).coerceAtLeast(0.5)
            val speed = effectiveSpeedMps(profile, e.roadClass, e.maxSpeedKmh, e.flags)
            segments.add(
                PathSegment(
                    edgeIdx = bestGoalDestSnap.edgeIdx,
                    streetName = e.streetName,
                    roadClass = e.roadClass,
                    flags = e.flags,
                    latPoints = lats,
                    lonPoints = lons,
                    distanceMeters = distM,
                    durationSeconds = distM / speed
                )
            )
        }

        return segments
    }

    private fun buildNavRoute(
        segments: List<PathSegment>,
        originLat: Double,
        originLon: Double,
        destinationName: String,
        destinationLat: Double,
        destinationLon: Double,
        profile: NavProfile,
        graph: OsmRoadGraphData,
        nowMs: Long,
        suffix: String
    ): NavRoute {
        val routeId = "osm_graph_${nowMs}_${suffix}_${UUID.randomUUID().toString().take(6)}"
        val points = ArrayList<LocationPoint>(segments.size * 3)

        val segStartPointIdx = IntArray(segments.size)
        val segEndPointIdx = IntArray(segments.size)

        for (sIdx in segments.indices) {
            val seg = segments[sIdx]
            if (points.isEmpty()) {
                for (k in seg.latPoints.indices) {
                    points.add(
                        LocationPoint(
                            latitude = seg.latPoints[k],
                            longitude = seg.lonPoints[k],
                            accuracy = 5f,
                            provider = "offline_graph",
                            timestamp = nowMs,
                            sessionId = routeId
                        )
                    )
                }
                segStartPointIdx[sIdx] = 0
                segEndPointIdx[sIdx] = points.lastIndex
            } else {
                segStartPointIdx[sIdx] = points.lastIndex
                val lastPt = points.last()
                val startK = if (haversineMeters(lastPt.latitude, lastPt.longitude, seg.latPoints[0], seg.lonPoints[0]) < 1.5) 1 else 0
                for (k in startK until seg.latPoints.size) {
                    points.add(
                        LocationPoint(
                            latitude = seg.latPoints[k],
                            longitude = seg.lonPoints[k],
                            accuracy = 5f,
                            provider = "offline_graph",
                            timestamp = nowMs,
                            sessionId = routeId
                        )
                    )
                }
                segEndPointIdx[sIdx] = points.lastIndex.coerceAtLeast(segStartPointIdx[sIdx])
            }
        }

        val steps = extractSteps(
            segments = segments,
            segStartPointIdx = segStartPointIdx,
            segEndPointIdx = segEndPointIdx,
            points = points,
            destinationName = destinationName,
            graph = graph
        )

        val totalDist = segments.sumOf { it.distanceMeters }
        val totalDur = segments.sumOf { it.durationSeconds }

        return NavRoute(
            id = routeId,
            profile = profile,
            originLat = originLat,
            originLon = originLon,
            destinationName = destinationName,
            destinationLat = destinationLat,
            destinationLon = destinationLon,
            points = points,
            steps = steps,
            totalDistanceMeters = totalDist,
            totalDurationSeconds = totalDur,
            source = NavRouteSource.OFFLINE_OSM_GRAPH,
            createdAtMs = nowMs
        )
    }

    private fun extractSteps(
        segments: List<PathSegment>,
        segStartPointIdx: IntArray,
        segEndPointIdx: IntArray,
        points: List<LocationPoint>,
        destinationName: String,
        graph: OsmRoadGraphData
    ): List<NavStep> {
        if (segments.isEmpty() || points.size < 2) {
            return listOf(
                NavStep(
                    index = 0,
                    maneuverType = NavManeuverType.ARRIVE,
                    instruction = "Arrive at $destinationName",
                    verbalPrompt = "You have arrived at $destinationName",
                    streetName = destinationName,
                    distanceMeters = 0.0,
                    durationSeconds = 0.0,
                    beginPointIndex = 0,
                    endPointIndex = points.lastIndex.coerceAtLeast(0)
                )
            )
        }

        data class GroupAcc(
            val maneuverType: NavManeuverType,
            var streetName: String,
            val beginPtIdx: Int,
            var endPtIdx: Int,
            var distM: Double,
            var durS: Double
        )

        val groups = mutableListOf<GroupAcc>()
        val firstSeg = segments[0]
        groups.add(
            GroupAcc(
                maneuverType = NavManeuverType.START,
                streetName = firstSeg.streetName,
                beginPtIdx = segStartPointIdx[0],
                endPtIdx = segEndPointIdx[0],
                distM = firstSeg.distanceMeters,
                durS = firstSeg.durationSeconds
            )
        )

        for (i in 1 until segments.size) {
            val prev = segments[i - 1]
            val cur = segments[i]
            val prevOutBearing = endingBearing(prev.latPoints, prev.lonPoints)
            val curInBearing = startingBearing(cur.latPoints, cur.lonPoints)
            val signedDelta = signedBearingDeltaDegrees(prevOutBearing, curInBearing)
            val absDelta = abs(signedDelta)

            val enteringRoundabout = (prev.flags and OsmRoadFlags.ROUNDABOUT) == 0 &&
                (cur.flags and OsmRoadFlags.ROUNDABOUT) != 0
            val stayingInRoundabout = (prev.flags and OsmRoadFlags.ROUNDABOUT) != 0 &&
                (cur.flags and OsmRoadFlags.ROUNDABOUT) != 0
            val exitingRoundabout = (prev.flags and OsmRoadFlags.ROUNDABOUT) != 0 &&
                (cur.flags and OsmRoadFlags.ROUNDABOUT) == 0

            val junctionVertex = graph.edges[cur.edgeIdx].fromVertex
            val junctionBranchCount = graph.outgoingEdgeIndices.getOrNull(junctionVertex)?.size ?: 0
            val streetChanged = prev.streetName.isNotBlank() &&
                cur.streetName.isNotBlank() &&
                !prev.streetName.equals(cur.streetName, ignoreCase = true)

            val shouldSplit = when {
                stayingInRoundabout -> false
                enteringRoundabout || exitingRoundabout -> true
                absDelta >= 28.0 -> true
                streetChanged && (absDelta >= 16.0 || junctionBranchCount >= 3) -> true
                else -> false
            }

            if (!shouldSplit) {
                val last = groups.last()
                last.endPtIdx = segEndPointIdx[i]
                last.distM += cur.distanceMeters
                last.durS += cur.durationSeconds
                if (last.streetName.isBlank() && cur.streetName.isNotBlank()) {
                    last.streetName = cur.streetName
                }
            } else {
                val maneuver = when {
                    enteringRoundabout -> NavManeuverType.ROUNDABOUT_ENTER
                    exitingRoundabout -> NavManeuverType.ROUNDABOUT_EXIT
                    absDelta >= 158.0 -> NavManeuverType.UTURN
                    signedDelta <= -122.0 -> NavManeuverType.SHARP_LEFT
                    signedDelta <= -50.0 -> NavManeuverType.LEFT
                    signedDelta <= -20.0 -> NavManeuverType.SLIGHT_LEFT
                    signedDelta >= 122.0 -> NavManeuverType.SHARP_RIGHT
                    signedDelta >= 50.0 -> NavManeuverType.RIGHT
                    signedDelta >= 20.0 -> NavManeuverType.SLIGHT_RIGHT
                    else -> NavManeuverType.STRAIGHT
                }
                groups.add(
                    GroupAcc(
                        maneuverType = maneuver,
                        streetName = cur.streetName,
                        beginPtIdx = segStartPointIdx[i],
                        endPtIdx = segEndPointIdx[i],
                        distM = cur.distanceMeters,
                        durS = cur.durationSeconds
                    )
                )
            }
        }

        val steps = ArrayList<NavStep>(groups.size + 1)
        for (idx in groups.indices) {
            val g = groups[idx]
            val bIdx = g.beginPtIdx.coerceIn(0, points.lastIndex)
            val eIdx = g.endPtIdx.coerceIn(bIdx, points.lastIndex)
            val startHeading = if (bIdx < points.lastIndex) {
                cardinalWord(bearingDegrees(points[bIdx].latitude, points[bIdx].longitude, points[bIdx + 1].latitude, points[bIdx + 1].longitude))
            } else ""
            val instr = formatInstruction(g.maneuverType, g.streetName, startHeading)
            steps.add(
                NavStep(
                    index = idx,
                    maneuverType = g.maneuverType,
                    instruction = instr,
                    verbalPrompt = instr,
                    streetName = g.streetName,
                    distanceMeters = g.distM,
                    durationSeconds = g.durS,
                    beginPointIndex = bIdx,
                    endPointIndex = eIdx
                )
            )
        }

        steps.add(
            NavStep(
                index = steps.size,
                maneuverType = NavManeuverType.ARRIVE,
                instruction = "Arrive at $destinationName",
                verbalPrompt = "You have arrived at $destinationName",
                streetName = destinationName,
                distanceMeters = 0.0,
                durationSeconds = 0.0,
                beginPointIndex = points.lastIndex,
                endPointIndex = points.lastIndex
            )
        )
        return steps
    }

    private fun formatInstruction(type: NavManeuverType, street: String, cardinal: String): String {
        val cleanStreet = street.trim()
        val onto = if (cleanStreet.isNotBlank()) " onto $cleanStreet" else ""
        val on = if (cleanStreet.isNotBlank()) " on $cleanStreet" else ""
        return when (type) {
            NavManeuverType.START -> {
                val dir = if (cardinal.isNotBlank()) "Head $cardinal" else "Head"
                "$dir$on"
            }
            NavManeuverType.STRAIGHT -> "Continue straight$onto"
            NavManeuverType.SLIGHT_LEFT -> "Keep slight left$onto"
            NavManeuverType.LEFT -> "Turn left$onto"
            NavManeuverType.SHARP_LEFT -> "Turn sharp left$onto"
            NavManeuverType.SLIGHT_RIGHT -> "Keep slight right$onto"
            NavManeuverType.RIGHT -> "Turn right$onto"
            NavManeuverType.SHARP_RIGHT -> "Turn sharp right$onto"
            NavManeuverType.UTURN -> "Make a U-turn$onto"
            NavManeuverType.ROUNDABOUT_ENTER -> "Enter roundabout$onto"
            NavManeuverType.ROUNDABOUT_EXIT -> "Exit roundabout$onto"
            NavManeuverType.MERGE_KEEP -> "Merge$onto"
            NavManeuverType.ARRIVE -> "Arrive at destination"
        }
    }

    private fun findNearestEdges(
        graph: OsmRoadGraphData,
        lat: Double,
        lon: Double,
        profile: NavProfile
    ): List<EdgeProjection> {
        val candidates = ArrayList<EdgeProjection>(SNAP_CANDIDATE_COUNT + 1)
        val latTolDeg = MAX_SNAP_METERS / 111_000.0
        val lonTolDeg = MAX_SNAP_METERS / (111_000.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.15))

        for (eIdx in graph.edges.indices) {
            val e = graph.edges[eIdx]
            if (!e.allows(profile)) continue

            val fromV = graph.vertices[e.fromVertex]
            val toV = graph.vertices[e.toVertex]
            // Fast bounding-box rejection before computing exact segment projection
            var minLat = min(fromV.lat, toV.lat)
            var maxLat = max(fromV.lat, toV.lat)
            var minLon = min(fromV.lon, toV.lon)
            var maxLon = max(fromV.lon, toV.lon)
            for (k in e.interiorLats.indices) {
                if (e.interiorLats[k] < minLat) minLat = e.interiorLats[k]
                if (e.interiorLats[k] > maxLat) maxLat = e.interiorLats[k]
                if (e.interiorLons[k] < minLon) minLon = e.interiorLons[k]
                if (e.interiorLons[k] > maxLon) maxLon = e.interiorLons[k]
            }
            if (lat < minLat - latTolDeg || lat > maxLat + latTolDeg || lon < minLon - lonTolDeg || lon > maxLon + lonTolDeg) {
                continue
            }

            val proj = projectOntoEdge(graph, eIdx, lat, lon)
            if (proj.distToRoadMeters <= MAX_SNAP_METERS) {
                candidates.add(proj)
                candidates.sortBy { it.distToRoadMeters }
                if (candidates.size > SNAP_CANDIDATE_COUNT) {
                    candidates.removeAt(candidates.lastIndex)
                }
            }
        }
        return candidates
    }

    private fun projectOntoEdge(graph: OsmRoadGraphData, edgeIdx: Int, pLat: Double, pLon: Double): EdgeProjection {
        val (lats, lons) = fullEdgePoints(graph, edgeIdx)
        val refLatRad = Math.toRadians(pLat)
        val metersPerDegLat = 111_132.92
        val metersPerDegLon = (111_412.84 * cos(refLatRad)).coerceAtLeast(1.0)

        var bestDist = Double.POSITIVE_INFINITY
        var bestSeg = 0
        var bestProjLat = lats[0]
        var bestProjLon = lons[0]
        var bestDistFromStart = 0.0
        var totalLength = 0.0

        val cumDist = DoubleArray(lats.size)
        for (i in 0 until lats.lastIndex) {
            val aLat = lats[i]
            val aLon = lons[i]
            val bLat = lats[i + 1]
            val bLon = lons[i + 1]
            val segLen = haversineMeters(aLat, aLon, bLat, bLon)
            cumDist[i + 1] = cumDist[i] + segLen
            totalLength += segLen

            val bx = (bLon - aLon) * metersPerDegLon
            val by = (bLat - aLat) * metersPerDegLat
            val px = (pLon - aLon) * metersPerDegLon
            val py = (pLat - aLat) * metersPerDegLat
            val lenSq = bx * bx + by * by
            val t = if (lenSq < 1e-6) 0.0 else ((px * bx + py * by) / lenSq).coerceIn(0.0, 1.0)
            val dx = px - t * bx
            val dy = py - t * by
            val d = sqrt(dx * dx + dy * dy)
            if (d < bestDist) {
                bestDist = d
                bestSeg = i
                bestProjLat = aLat + t * (bLat - aLat)
                bestProjLon = aLon + t * (bLon - aLon)
                bestDistFromStart = cumDist[i] + t * segLen
            }
        }

        return EdgeProjection(
            edgeIdx = edgeIdx,
            distToRoadMeters = bestDist,
            projLat = bestProjLat,
            projLon = bestProjLon,
            segIndex = bestSeg,
            distFromStartMeters = bestDistFromStart,
            distToEndMeters = (totalLength - bestDistFromStart).coerceAtLeast(0.0)
        )
    }

    private fun fullEdgePoints(graph: OsmRoadGraphData, edgeIdx: Int): Pair<DoubleArray, DoubleArray> {
        val e = graph.edges[edgeIdx]
        val fromV = graph.vertices[e.fromVertex]
        val toV = graph.vertices[e.toVertex]
        val n = 2 + e.interiorLats.size
        val lats = DoubleArray(n)
        val lons = DoubleArray(n)
        lats[0] = fromV.lat
        lons[0] = fromV.lon
        for (k in e.interiorLats.indices) {
            lats[k + 1] = e.interiorLats[k]
            lons[k + 1] = e.interiorLons[k]
        }
        lats[n - 1] = toV.lat
        lons[n - 1] = toV.lon
        return lats to lons
    }

    private fun sliceEdgePoints(
        graph: OsmRoadGraphData,
        edgeIdx: Int,
        fromSegIdx: Int,
        fromLat: Double,
        fromLon: Double,
        toSegIdx: Int,
        toLat: Double,
        toLon: Double
    ): Pair<DoubleArray, DoubleArray> {
        val (fullLats, fullLons) = fullEdgePoints(graph, edgeIdx)
        val lats = ArrayList<Double>(fullLats.size)
        val lons = ArrayList<Double>(fullLons.size)
        lats.add(fromLat)
        lons.add(fromLon)
        val midStart = (fromSegIdx + 1).coerceAtLeast(1)
        val midEnd = toSegIdx.coerceAtMost(fullLats.lastIndex - 1)
        for (i in midStart..midEnd) {
            lats.add(fullLats[i])
            lons.add(fullLons[i])
        }
        if (haversineMeters(lats.last(), lons.last(), toLat, toLon) > 0.3) {
            lats.add(toLat)
            lons.add(toLon)
        } else if (lats.size == 1) {
            lats.add(toLat)
            lons.add(toLon)
        }
        return lats.toDoubleArray() to lons.toDoubleArray()
    }

    private fun computeArrayDistance(lats: DoubleArray, lons: DoubleArray): Double {
        var total = 0.0
        for (i in 0 until lats.lastIndex) {
            total += haversineMeters(lats[i], lons[i], lats[i + 1], lons[i + 1])
        }
        return total
    }

    private fun snapPenaltySeconds(distMeters: Double): Double {
        val transfer = distMeters / 1.1
        val offRoadBias = if (distMeters > 25.0) (distMeters - 25.0) * 1.2 else 0.0
        return transfer + offRoadBias
    }

    private fun maxHeuristicSpeedMps(profile: NavProfile): Double = when (profile) {
        NavProfile.DRIVING -> 34.0
        NavProfile.MOTORCYCLE -> 31.0
        NavProfile.CYCLING -> 9.0
        NavProfile.WALKING -> 1.7
    }

    private fun effectiveSpeedMps(profile: NavProfile, roadClass: RoadClass, maxSpeedKmh: Int, flags: Int): Double {
        val unpavedFactor = if ((flags and OsmRoadFlags.UNPAVED) != 0) 0.55 else 1.0
        return when (profile) {
            NavProfile.DRIVING -> {
                val baseKmh = if (maxSpeedKmh > 0) min(roadClass.defaultSpeedKmh, maxSpeedKmh.toFloat()) else roadClass.defaultSpeedKmh
                val urbanRealism = baseKmh * 0.85 * unpavedFactor
                (urbanRealism / 3.6).coerceIn(2.2, 33.3)
            }
            NavProfile.MOTORCYCLE -> {
                val baseKmh = if (maxSpeedKmh > 0) min(roadClass.defaultSpeedKmh * 0.95f, maxSpeedKmh.toFloat()) else roadClass.defaultSpeedKmh * 0.92f
                ((baseKmh * 0.85 * unpavedFactor) / 3.6).coerceIn(2.2, 30.0)
            }
            NavProfile.CYCLING -> {
                val kmh = when (roadClass) {
                    RoadClass.CYCLEWAY -> 19.0
                    RoadClass.FOOTWAY -> 10.0
                    RoadClass.TRACK -> 13.0
                    else -> 17.5 * unpavedFactor
                }
                (kmh / 3.6).coerceIn(2.0, 8.5)
            }
            NavProfile.WALKING -> {
                val kmh = if ((flags and OsmRoadFlags.UNPAVED) != 0) 4.2 else 5.0
                kmh / 3.6
            }
        }
    }

    private fun startingBearing(lats: DoubleArray, lons: DoubleArray): Double {
        for (i in 1 until lats.size) {
            if (haversineMeters(lats[0], lons[0], lats[i], lons[i]) >= 2.0) {
                return bearingDegrees(lats[0], lons[0], lats[i], lons[i])
            }
        }
        return bearingDegrees(lats.first(), lons.first(), lats.last(), lons.last())
    }

    private fun endingBearing(lats: DoubleArray, lons: DoubleArray): Double {
        val last = lats.lastIndex
        for (i in (last - 1) downTo 0) {
            if (haversineMeters(lats[i], lons[i], lats[last], lons[last]) >= 2.0) {
                return bearingDegrees(lats[i], lons[i], lats[last], lons[last])
            }
        }
        return bearingDegrees(lats.first(), lons.first(), lats.last(), lons.last())
    }

    private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun signedBearingDeltaDegrees(fromDeg: Double, toDeg: Double): Double {
        return ((toDeg - fromDeg + 540.0) % 360.0) - 180.0
    }

    private fun cardinalWord(deg: Double): String {
        val dirs = arrayOf("north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest")
        val idx = (((deg + 22.5) % 360.0) / 45.0).toInt().coerceIn(0, 7)
        return dirs[idx]
    }
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = (lat2 - lat1) * PI / 180.0
    val dLon = (lon2 - lon1) * PI / 180.0
    val phi1 = lat1 * PI / 180.0
    val phi2 = lat2 * PI / 180.0
    val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
        cos(phi1) * cos(phi2) * sin(dLon / 2.0) * sin(dLon / 2.0)
    return r * 2.0 * atan2(sqrt(a), sqrt((1.0 - a).coerceAtLeast(0.0)))
}
