package com.elendheim.anomalies.game

import java.util.Calendar

/**
 * Works out what kind of place the player is standing in and what part of the day it is.
 *
 * The time band comes straight off the device clock. The biome is derived from the
 * coordinates themselves rather than from downloaded map features, which means it works
 * in airplane mode and in a tunnel. It is stable per cell, so standing still never
 * reshuffles the world, and moving a couple of streets over can change what turns up.
 */
object BiomeSensor {

    /** Roughly a hundred metre square. The biome holds steady inside one cell. */
    private const val CELL = 1000.0

    fun biomeAt(lat: Double, lng: Double): Biome {
        val cellLat = Math.floor(lat * CELL).toLong()
        val cellLng = Math.floor(lng * CELL).toLong()
        // A cheap integer mix so neighbouring cells land in different buckets instead of
        // forming stripes across the map.
        var h = cellLat * -0x61c8864680b583ebL
        h = h xor (cellLng * -0x7ee3623a03d3c83fL)
        h = h xor (h ushr 29)
        h *= 0x27d4eb2f165667c5L
        h = h xor (h ushr 32)
        val bucket = ((h ushr 1) % 100L).toInt()
        // Urban and green make up most of the world, water and dry are the pockets.
        return when {
            bucket < 38 -> Biome.URBAN
            bucket < 72 -> Biome.GREEN
            bucket < 88 -> Biome.WATER
            else -> Biome.DRY
        }
    }

    fun timeBandNow(nowMillis: Long = System.currentTimeMillis()): TimeBand {
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return when (calendar.get(Calendar.HOUR_OF_DAY)) {
            in 7..16 -> TimeBand.DAY
            in 17..21 -> TimeBand.DUSK
            else -> TimeBand.NIGHT
        }
    }

    /** True when the creature is standing in the kind of place it likes best. */
    fun isFavoured(multiplier: Double): Boolean = multiplier >= 1.5
}
