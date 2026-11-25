package com.geotrace.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceSearchOverpassParserTest {

    @Test
    fun `categorizeAndScoreOverpassElement prioritizes survival POIs and generates fallback names`() {
        val unnamedWater = JSONObject("""{"amenity":"drinking_water"}""")
        val (waterName, waterCat, waterPrio) = PlaceSearchManager.categorizeAndScoreOverpassElement(unnamedWater)
        assertEquals("Drinking Water", waterName)
        assertEquals("Drinking water", waterCat)
        assertEquals(100, waterPrio)

        val unnamedFuel = JSONObject("""{"amenity":"fuel"}""")
        val (fuelName, _, fuelPrio) = PlaceSearchManager.categorizeAndScoreOverpassElement(unnamedFuel)
        assertEquals("Fuel Station", fuelName)
        assertEquals(90, fuelPrio)

        val lodge = JSONObject("""{"name":"Equator Eco Lodge","tourism":"camp_site"}""")
        val (lodgeName, _, lodgePrio) = PlaceSearchManager.categorizeAndScoreOverpassElement(lodge)
        assertEquals("Equator Eco Lodge", lodgeName)
        assertEquals(85, lodgePrio)

        val genericRoad = JSONObject("""{"highway":"track"}""")
        val (roadName, _, roadPrio) = PlaceSearchManager.categorizeAndScoreOverpassElement(genericRoad)
        assertNull(roadName)
        assertEquals(20, roadPrio)
    }

    @Test
    fun `parseOverpassElementsJson orders medical and water first, deduplicates, and ignores invalid coords`() {
        val json = """
            {
              "elements": [
                {
                  "type": "node",
                  "id": 1,
                  "lat": 0.3100,
                  "lon": 32.5800,
                  "tags": { "name": "Alpha Supermarket", "shop": "supermarket" }
                },
                {
                  "type": "way",
                  "id": 2,
                  "center": { "lat": 0.3150, "lon": 32.5850 },
                  "tags": { "amenity": "hospital", "name": "Zeta Clinic & Hospital", "addr:city": "Kampala" }
                },
                {
                  "type": "node",
                  "id": 3,
                  "lat": 0.3120,
                  "lon": 32.5820,
                  "tags": { "natural": "spring" }
                },
                {
                  "type": "node",
                  "id": 4,
                  "lat": 0.3150,
                  "lon": 32.5850,
                  "tags": { "amenity": "hospital", "name": "Zeta Clinic & Hospital" }
                },
                {
                  "type": "node",
                  "id": 5,
                  "lat": 120.0,
                  "lon": 32.5850,
                  "tags": { "amenity": "fuel", "name": "Out of Bounds" }
                }
              ]
            }
        """.trimIndent()

        val pois = PlaceSearchManager.parseOverpassElementsJson(
            responseText = json,
            regionId = "reg-kampala",
            regionName = "Kampala"
        )

        // Duplicate Zeta Clinic and Out of Bounds skipped -> 3 unique POIs remain
        assertEquals(3, pois.size)
        // Highest priority (100) sorted alphabetically: "Natural Spring" before "Zeta Clinic & Hospital", then shop (40)
        assertEquals("Natural Spring", pois[0].name)
        assertEquals("Zeta Clinic & Hospital", pois[1].name)
        assertTrue(pois[1].subtitle.contains("Kampala"))
        assertEquals("Alpha Supermarket", pois[2].name)
    }

    @Test
    fun `parseOverpassElementsJson keeps higher priority POI when generic POI appears first at same coordinate`() {
        val json = """
            {
              "elements": [
                {
                  "type": "node",
                  "id": 10,
                  "lat": 0.3100,
                  "lon": 32.5800,
                  "tags": { "name": "Community Borehole", "highway": "track" }
                },
                {
                  "type": "node",
                  "id": 11,
                  "lat": 0.3100,
                  "lon": 32.5800,
                  "tags": { "amenity": "drinking_water", "name": "Community Borehole" }
                }
              ]
            }
        """.trimIndent()

        val pois = PlaceSearchManager.parseOverpassElementsJson(
            responseText = json,
            regionId = "reg-1",
            regionName = "Region"
        )
        assertEquals(1, pois.size)
        assertEquals("Community Borehole", pois[0].name)
        assertEquals("Drinking water", pois[0].category)
    }

    @Test
    fun `parseOverpassElementsJson handles malformed JSON without throwing`() {
        val empty = PlaceSearchManager.parseOverpassElementsJson("{not valid json", "r1", "R1")
        assertTrue(empty.isEmpty())
    }

    @Test
    fun `parseDirectCoordinates prioritizes q param over geo 0 0 and rejects bare Null Island`() {
        val androidGeoIntent = PlaceSearchManager.parseDirectCoordinates("geo:0,0?q=-0.5947,30.6545(Base Camp)")
        assertEquals(-0.5947, androidGeoIntent!!.latitude, 1e-4)
        assertEquals(30.6545, androidGeoIntent.longitude, 1e-4)

        val bareNullIsland = PlaceSearchManager.parseDirectCoordinates("geo:0,0")
        assertNull(bareNullIsland)

        val mapsAt = PlaceSearchManager.parseDirectCoordinates("https://maps.google.com/@-0.5947,30.6545,15z")
        assertEquals(-0.5947, mapsAt!!.latitude, 1e-4)
        assertEquals(30.6545, mapsAt.longitude, 1e-4)
    }
}
