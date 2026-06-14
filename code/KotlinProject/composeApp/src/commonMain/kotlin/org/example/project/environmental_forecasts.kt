package org.example.project

/**
 * Shared environmental fixture used by [HealthIntelligence] and [WeatherAlertsScreen].
 * When live weather is wired in, replace this single provider — narratives stay coherent via HealthIntelligence.
 */
data class WeatherForecast(
    val label: String,
    val risk: Int,
    val riskLevel: String,
    val temperature: Double,
    val humidity: Int,
    val pressure: Int,
    val windSpeed: Double,
    val condition: String
)

object SharedEnvironmentalSignals {
    val standardForecastWindows: List<WeatherForecast> = listOf(
        WeatherForecast(
            label = "RIGHT NOW",
            risk = 21,
            riskLevel = "Low",
            temperature = 31.9,
            humidity = 33,
            pressure = 1010,
            windSpeed = 4.8,
            condition = "Clear Sky"
        ),
        WeatherForecast(
            label = "24HR",
            risk = 29,
            riskLevel = "Low",
            temperature = 26.2,
            humidity = 45,
            pressure = 1012,
            windSpeed = 9.0,
            condition = "Broken Clouds"
        ),
        WeatherForecast(
            label = "48HR",
            risk = 80,
            riskLevel = "High",
            temperature = 23.4,
            humidity = 72,
            pressure = 1013,
            windSpeed = 11.8,
            condition = "Overcast Clouds"
        ),
        WeatherForecast(
            label = "72HR",
            risk = 66,
            riskLevel = "Moderate",
            temperature = 23.9,
            humidity = 68,
            pressure = 1012,
            windSpeed = 7.6,
            condition = "Overcast Clouds"
        )
    )

    fun maxForecastRisk(): Int =
        standardForecastWindows.maxOfOrNull { it.risk } ?: 0

    fun anyElevatedForecast(minRisk: Int = 70): Boolean =
        standardForecastWindows.any { it.risk >= minRisk }

    /** Narrative aligned with humidity trajectory across forecast windows (same numbers Weather UI shows). */
    fun humidityLinkedComfortSentence(): String {
        val windows = standardForecastWindows
        if (windows.size < 2) return "Review forecast windows for humidity and pressure shifts."
        val nowHum = windows[0].humidity
        val peakHum = windows.maxOf { it.humidity }
        val peak = windows.firstOrNull { it.humidity == peakHum } ?: windows.last()
        return when {
            peakHum >= nowHum + 20 ->
                "Humidity is expected to climb toward ${peakHum}% (${peak.label}); this often worsens stiffness for inflammatory arthritis."
            peakHum >= nowHum + 10 ->
                "Humidity is trending upward (${nowHum}% → ${peakHum}%); plan lighter activity if joints feel sensitive."
            anyElevatedForecast(70) ->
                "Forecast shows elevated flare-load windows; weather stress may add to your modeled flare estimate."
            else ->
                "Current outlook shows moderate environmental stress; keep usual pacing unless symptoms spike."
        }
    }
}
