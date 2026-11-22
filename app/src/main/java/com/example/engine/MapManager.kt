package com.example.engine

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object MapManager {
    private const val TAU = (2.0 * PI).toFloat()
    const val LANE_LENGTH = 5

    private data class MapDefinition(
        val id: MapId,
        val name: String,
        val tileShape: TileShape,
        val n: Int,
        val gate: Int,
        val start: Int,
        val junction: List<Int> = emptyList(),
        val blurb: String,
        val styleTag: String,
        val outline: () -> List<Vec2>
    )

    private const val HEX_R = 330f
    private val SQ3 = (sqrt(3.0) / 2.0).toFloat()

    private val definitions = listOf(
        MapDefinition(
            id = MapId.ORBIT,
            name = "Orbit",
            tileShape = TileShape.CIRCLE,
            n = 40,
            gate = 10,
            start = 0,
            blurb = "A clean circle with round tiles. Smooth, fast and easy to read.",
            styleTag = "Classic Loop",
            outline = { ellipse(330f, 240f) }
        ),
        MapDefinition(
            id = MapId.CITADEL,
            name = "Citadel",
            tileShape = TileShape.SQUARE,
            n = 48,
            gate = 12,
            start = 0,
            blurb = "Square tiles along a walled rectangle. Long straights and sharp corners.",
            styleTag = "Long Straights",
            outline = {
                polygon(
                    listOf(
                        Vec2(-322f, 0f),
                        Vec2(-322f, -230f),
                        Vec2(322f, -230f),
                        Vec2(322f, 230f),
                        Vec2(-322f, 230f)
                    )
                )
            }
        ),
        MapDefinition(
            id = MapId.PRISM,
            name = "Prism",
            tileShape = TileShape.TRIANGLE,
            n = 48,
            gate = 12,
            start = 0,
            blurb = "A hexagonal ring of triangles that always point the way forward.",
            styleTag = "Directional",
            outline = {
                polygon(
                    listOf(
                        Vec2(-HEX_R, 0f),
                        Vec2(-HEX_R / 2f, -HEX_R * SQ3),
                        Vec2(HEX_R / 2f, -HEX_R * SQ3),
                        Vec2(HEX_R, 0f),
                        Vec2(HEX_R / 2f, HEX_R * SQ3),
                        Vec2(-HEX_R / 2f, HEX_R * SQ3)
                    )
                )
            }
        ),
        MapDefinition(
            id = MapId.RHOMBUS,
            name = "Rhombus",
            tileShape = TileShape.DIAMOND,
            n = 40,
            gate = 10,
            start = 0,
            blurb = "Diamond tiles on a wide rhombus. The gates sit at the sharp points.",
            styleTag = "Sharp Gates",
            outline = {
                polygon(
                    listOf(
                        Vec2(-400f, 0f),
                        Vec2(0f, -240f),
                        Vec2(400f, 0f),
                        Vec2(0f, 240f)
                    )
                )
            }
        ),
        MapDefinition(
            id = MapId.INFINITY,
            name = "Infinity",
            tileShape = TileShape.CIRCLE,
            n = 40,
            gate = 5,
            start = 5,
            junction = listOf(10, 30),
            blurb = "A figure eight with a crossroads in the middle. Everyone passes twice.",
            styleTag = "Crossroads",
            outline = { lemniscate(330f, 320f) }
        )
    )

    val metadataList: List<MapMetadata> by lazy {
        definitions.map { def ->
            val geom = build(def.id, portrait = false)
            MapMetadata(
                id = def.id,
                name = def.name,
                blurb = def.blurb,
                tileShape = def.tileShape,
                ringTiles = geom.n,
                pathLength = geom.len,
                safeCount = geom.safe.size,
                boostCount = geom.boost.size,
                hasJunction = geom.junction.isNotEmpty(),
                styleTag = def.styleTag
            )
        }
    }

    fun getMetadata(id: MapId): MapMetadata =
        metadataList.firstOrNull { it.id == id } ?: metadataList.first()

    private fun ellipse(a: Float, b: Float): List<Vec2> {
        val out = ArrayList<Vec2>(4000)
        for (i in 0 until 4000) {
            val t = (i / 4000f) * TAU
            out.add(Vec2(-a * cos(t), -b * sin(t)))
        }
        return out
    }

    private fun polygon(verts: List<Vec2>): List<Vec2> {
        val out = mutableListOf<Vec2>()
        for (i in verts.indices) {
            val a = verts[i]
            val b = verts[(i + 1) % verts.size]
            val dist = hypot(b.x - a.x, b.y - a.y)
            val steps = max(2, ceil(dist / 1.5f).toInt())
            for (k in 0 until steps) {
                val f = k.toFloat() / steps
                out.add(Vec2(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f))
            }
        }
        return out
    }

    private fun lemniscate(a: Float, h: Float): List<Vec2> {
        val out = ArrayList<Vec2>(4000)
        for (i in 0 until 4000) {
            val t = (i / 4000f) * TAU
            out.add(Vec2(-a * cos(t), (h / 2f) * sin(2f * t)))
        }
        return out
    }

    private data class Resampled(val pts: MutableList<Vec2>, val total: Float)

    private fun resample(dense: List<Vec2>, n: Int): Resampled {
        val m = dense.size
        val cum = FloatArray(m + 1)
        cum[0] = 0f
        for (i in 1..m) {
            val a = dense[i - 1]
            val b = dense[i % m]
            cum[i] = cum[i - 1] + hypot(b.x - a.x, b.y - a.y)
        }
        val total = cum[m]
        val pts = ArrayList<Vec2>(n)
        var j = 0
        for (k in 0 until n) {
            val target = (k * total) / n
            while (j < m - 1 && cum[j + 1] < target) j++
            val a = dense[j]
            val b = dense[(j + 1) % m]
            val seg = cum[j + 1] - cum[j]
            val f = if (seg != 0f) (target - cum[j]) / seg else 0f
            pts.add(Vec2(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f))
        }
        return Resampled(pts, total)
    }

    fun build(id: MapId, portrait: Boolean): MapGeometry {
        val def = definitions.firstOrNull { it.id == id } ?: definitions.first()
        var dense = def.outline()
        if (portrait) {
            dense = dense.map { Vec2(it.y, -it.x) }
        }

        val (pts, total) = resample(dense, def.n)
        val n = def.n
        val spacing = total / n
        val tileId = MutableList(n) { it }
        val junction = mutableSetOf<Int>()
        for (idx in def.junction) {
            pts[idx] = Vec2(0f, 0f)
            junction.add(idx)
        }
        if (def.junction.size >= 2) {
            tileId[def.junction[1]] = def.junction[0]
        }

        val ang = List(n) { i ->
            val a = pts[(i - 1 + n) % n]
            val b = pts[(i + 1) % n]
            atan2(b.y - a.y, b.x - a.x)
        }

        val s0 = def.start
        val start = listOf(s0, (s0 + n / 2) % n)
        val entry = start.map { s -> (s - def.gate + n) % n }
        val rl = n - def.gate + 1
        val len = rl + LANE_LENGTH

        val lanes = entry.map { e ->
            val p = pts[e]
            val dist = hypot(p.x, p.y)
            val dirX = -p.x / dist
            val dirY = -p.y / dist
            val step = min(spacing * 1.15f, (0.8f * dist) / LANE_LENGTH)
            List(LANE_LENGTH) { i ->
                Vec2(p.x + dirX * step * (i + 1), p.y + dirY * step * (i + 1))
            }
        }
        val laneDir = entry.mapIndexed { pIdx, e ->
            val a = pts[e]
            val b = lanes[pIdx][0]
            atan2(b.y - a.y, b.x - a.x)
        }

        val aSafe = (n * 0.2f).roundToInt()
        val bSafe = (n * 0.3f).roundToInt()
        val safe = listOf(0, n / 2, aSafe, bSafe, aSafe + n / 2, bSafe + n / 2)
            .map { (s0 + it) % n }
            .toSet()

        val bo = (n * 0.075f).roundToInt()
        val bp = (n * 0.425f).roundToInt()
        val boost = listOf(bo, bp, bo + n / 2, bp + n / 2)
            .map { (s0 + it) % n }
            .toSet()

        val tileR = spacing * 0.36f
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (p in dense) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        val pad = tileR * 2.2f

        return MapGeometry(
            id = def.id,
            name = def.name,
            blurb = def.blurb,
            tileShape = def.tileShape,
            portrait = portrait,
            n = n,
            gate = def.gate,
            rl = rl,
            len = len,
            home = len - 1,
            lane = LANE_LENGTH,
            pts = pts,
            ang = ang,
            tileId = tileId,
            junction = junction,
            start = start,
            entry = entry,
            lanes = lanes,
            laneDir = laneDir,
            safe = safe,
            boost = boost,
            spacing = spacing,
            tileR = tileR,
            dense = dense,
            bounds = Bounds(minX - pad, maxX + pad, minY - pad, maxY + pad)
        )
    }
}
