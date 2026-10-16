package com.shiji.footprint.data

import java.time.LocalDate

data class TrackPoint(
    val t: Long,
    val lat: Double,
    val lon: Double,
    val acc: Float = 0f,
    val speed: Float = 0f,
    val alt: Double = 0.0,
    val seg: Long = 0,
)

data class DayStat(
    val date: LocalDate,
    val distanceKm: Double,
    val points: Int,
    val first: Long,
    val last: Long,
)

data class Place(
    val country: String,
    val province: String,
    val city: String,
    val t: Long = 0,
)

data class Stats(
    val totalKm: Double = 0.0,
    val todayKm: Double = 0.0,
    val areaKm2: Double = 0.0,
    val cells: Int = 0,
    val days: Int = 0,
    val points: Long = 0,
    val countries: Int = 0,
    val provinces: Int = 0,
    val cities: Int = 0,
)
