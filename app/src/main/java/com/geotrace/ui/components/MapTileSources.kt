package com.geotrace.ui.components

import com.geotrace.model.MapTileType
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

/**
 * Shared osmdroid tile source definitions used by both the live MapView and OfflineMapManager
 * so downloaded tiles resolve cleanly under identical tile source names when offline.
 */
object MapTileSources {

    val esriWorldImagery: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "EsriWorldImagery",
        0,
        19,
        256,
        ".jpg",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
        "Tiles © Esri — Source: Esri, Maxar, Earthstar Geographics",
        TileSourcePolicy(2, TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL)
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            return "$baseUrl$z/$y/$x"
        }
    }

    val hybridLabelsOverlay: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "CartoLightLabels",
        0,
        19,
        256,
        ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/light_only_labels/",
            "https://b.basemaps.cartocdn.com/light_only_labels/",
            "https://c.basemaps.cartocdn.com/light_only_labels/"
        ),
        "© OpenStreetMap contributors © CARTO",
        TileSourcePolicy(2, TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL)
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$z/$x/$y.png"
        }
    }

    val humanitarianOsm: OnlineTileSourceBase = XYTileSource(
        "HumanitarianOSM",
        0,
        19,
        256,
        ".png",
        arrayOf(
            "https://a.tile.openstreetmap.fr/hot/",
            "https://b.tile.openstreetmap.fr/hot/"
        ),
        "© OpenStreetMap contributors, Humanitarian OpenStreetMap Team",
        TileSourcePolicy(2, TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL)
    )

    val openTopoDownloadable: OnlineTileSourceBase = XYTileSource(
        "OpenTopoMap",
        0,
        17,
        256,
        ".png",
        arrayOf(
            "https://a.tile.opentopomap.org/",
            "https://b.tile.opentopomap.org/",
            "https://c.tile.opentopomap.org/"
        ),
        "© OpenStreetMap contributors, SRTM | OpenTopoMap",
        TileSourcePolicy(2, TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL)
    )

    fun baseTileSourceFor(type: MapTileType): ITileSource {
        return when (type) {
            MapTileType.STANDARD -> TileSourceFactory.MAPNIK
            MapTileType.SATELLITE -> esriWorldImagery
            MapTileType.HYBRID -> esriWorldImagery
            MapTileType.TOPO -> openTopoDownloadable
            MapTileType.HUMANITARIAN -> humanitarianOsm
        }
    }

    fun overlayTileSourceFor(type: MapTileType): OnlineTileSourceBase? {
        return if (type == MapTileType.HYBRID) hybridLabelsOverlay else null
    }
}
