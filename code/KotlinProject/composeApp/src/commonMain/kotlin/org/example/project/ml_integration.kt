package org.example.project

import kotlin.math.roundToInt

enum class RiskLevel {
    LOW, MODERATE, HIGH
}

data class ModelPrediction(
    val modelName: String,
    val probability: Double,
    val threshold: Double,
    val riskLevel: RiskLevel
)

data class CombinedRiskPrediction(
    val population: ModelPrediction,
    val symptomDerived: ModelPrediction,
    val weather: ModelPrediction,
    val overallProbability: Double,
    val overallRiskLevel: RiskLevel
)

data class MissingFieldIssue(
    val mapperName: String,
    val fields: List<String>
)

sealed class MapperResult<out T> {
    data class Success<T>(val value: T) : MapperResult<T>()
    data class Failure(val issue: MissingFieldIssue) : MapperResult<Nothing>()
}

// -----------------------------
// Raw app-layer input snapshots
// -----------------------------

data class PopulationRawInput(
    val ageYears: Int?,
    val gender: String?,
    val smokingStatus: String?,
    val physicalActivity: String?,
    val caloriesPerDay: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val caffeineG: Double?,
    val fiberG: Double?,
    val hypertension: String?,
    val diabetes: String?,
    val hyperlipidemia: String?,
    val bmi: Double? = null,
    val race: String? = null,
    val drinkingStatus: String? = null
)

data class SymptomRawInput(
    val pain: Double?,
    val stiffness: Double?,
    val fatigue: Double?,
    val physicalDifficulty: Double?,
    val vigorousDays: Double?,
    val vigorousHours: Double?,
    val moderateDays: Double?,
    val moderateHours: Double?,
    val walkingDays: Double?,
    val walkingHours: Double?,
    val sittingHoursPerWeekday: Double?,
    val previousSymptomBurden: Double?
)

data class WeatherRawInput(
    val tempC: Double?,
    val humidity: Double?,
    val pressureHpa: Double?,
    val windKmh: Double?,
    val precipitation: Double?,
    val deltaTempC: Double?,
    val deltaHumidity: Double?,
    val deltaPressureHpa: Double?,
    val deltaWindKmh: Double?,
    val pain: Double?,
    val stiffness: Double?,
    val fatigue: Double?,
    val previousSymptomBurden: Double?
)

// -----------------------------
// Model-ready payloads
// -----------------------------

data class PopulationModelInput(
    val ageYears: Int,
    val ageGroup: String,
    val smokingStatus: String,
    val ageSmokingInteraction: Double,
    val gender: String,
    val physicalActivity: String,
    val caloriesPerDay: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val caffeineG: Double,
    val fiberG: Double,
    val hypertension: String,
    val diabetes: String,
    val hyperlipidemia: String,
    val bmi: Double?,
    val race: String?,
    val drinkingStatus: String?
)

data class SymptomModelInput(
    val pain: Double,
    val stiffness: Double,
    val fatigue: Double,
    val physicalDifficulty: Double,
    val vigorousDays: Double,
    val vigorousHours: Double,
    val moderateDays: Double,
    val moderateHours: Double,
    val walkingDays: Double,
    val walkingHours: Double,
    val sittingHoursPerWeekday: Double,
    val symptomBurden: Double,
    val deltaSymptomBurden: Double
)

data class WeatherModelInput(
    val tempC: Double,
    val humidity: Double,
    val pressureHpa: Double,
    val windKmh: Double,
    val precipitation: Double,
    val deltaTempC: Double,
    val deltaHumidity: Double,
    val deltaPressureHpa: Double,
    val deltaWindKmh: Double,
    val pressureDropFlag: Double,
    val humiditySpikeFlag: Double,
    val pain: Double,
    val stiffness: Double,
    val fatigue: Double,
    val symptomBurden: Double,
    val deltaSymptomBurden: Double
)

private fun debugMissing(mapperName: String, missing: List<String>) {
    println("[ML][BLOCKED][$mapperName] Missing required fields: ${missing.joinToString(", ")}")
}

private fun classify(probability: Double): RiskLevel = when {
    probability >= 0.70 -> RiskLevel.HIGH
    probability >= 0.45 -> RiskLevel.MODERATE
    else -> RiskLevel.LOW
}

private fun ageToGroup(age: Int): String = when {
    age < 30 -> "<30"
    age <= 50 -> "30-50"
    age <= 70 -> "50-70"
    else -> "70+"
}

private fun smokingWeight(smoking: String): Double = when (smoking.lowercase()) {
    "current" -> 1.0
    "former" -> 0.6
    else -> 0.0
}

// -----------------------------------
// 3 mappers requested by user
// -----------------------------------

fun mapToPopulationInput(raw: PopulationRawInput): MapperResult<PopulationModelInput> {
    val missing = mutableListOf<String>()

    val age = raw.ageYears ?: run { missing += "ageYears"; 0 }
    val gender = raw.gender?.takeIf { it.isNotBlank() } ?: run { missing += "gender"; "" }
    val smoking = raw.smokingStatus?.takeIf { it.isNotBlank() } ?: run { missing += "smokingStatus"; "" }
    val activity = raw.physicalActivity?.takeIf { it.isNotBlank() } ?: run { missing += "physicalActivity"; "" }
    val calories = raw.caloriesPerDay ?: run { missing += "caloriesPerDay"; 0.0 }
    val protein = raw.proteinG ?: run { missing += "proteinG"; 0.0 }
    val carbs = raw.carbsG ?: run { missing += "carbsG"; 0.0 }
    val fat = raw.fatG ?: run { missing += "fatG"; 0.0 }
    val caffeine = raw.caffeineG ?: run { missing += "caffeineG"; 0.0 }
    val fiber = raw.fiberG ?: run { missing += "fiberG"; 0.0 }
    val htn = raw.hypertension?.takeIf { it.isNotBlank() } ?: run { missing += "hypertension"; "" }
    val dm = raw.diabetes?.takeIf { it.isNotBlank() } ?: run { missing += "diabetes"; "" }
    val hld = raw.hyperlipidemia?.takeIf { it.isNotBlank() } ?: run { missing += "hyperlipidemia"; "" }

    if (missing.isNotEmpty()) {
        debugMissing("mapToPopulationInput", missing)
        return MapperResult.Failure(MissingFieldIssue("mapToPopulationInput", missing))
    }

    val ageGroup = ageToGroup(age)
    val ageSmokingInteraction = age.toDouble() * smokingWeight(smoking)

    return MapperResult.Success(
        PopulationModelInput(
            ageYears = age,
            ageGroup = ageGroup,
            smokingStatus = smoking,
            ageSmokingInteraction = ageSmokingInteraction,
            gender = gender,
            physicalActivity = activity,
            caloriesPerDay = calories,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
            caffeineG = caffeine,
            fiberG = fiber,
            hypertension = htn,
            diabetes = dm,
            hyperlipidemia = hld,
            bmi = raw.bmi,
            race = raw.race,
            drinkingStatus = raw.drinkingStatus
        )
    )
}

fun mapToSymptomInput(raw: SymptomRawInput): MapperResult<SymptomModelInput> {
    val missing = mutableListOf<String>()

    val pain = raw.pain ?: run { missing += "pain"; 0.0 }
    val stiffness = raw.stiffness ?: run { missing += "stiffness"; 0.0 }
    val fatigue = raw.fatigue ?: run { missing += "fatigue"; 0.0 }
    val difficulty = raw.physicalDifficulty ?: run { missing += "physicalDifficulty"; 0.0 }
    val vigorousDays = raw.vigorousDays ?: run { missing += "vigorousDays"; 0.0 }
    val vigorousHours = raw.vigorousHours ?: run { missing += "vigorousHours"; 0.0 }
    val moderateDays = raw.moderateDays ?: run { missing += "moderateDays"; 0.0 }
    val moderateHours = raw.moderateHours ?: run { missing += "moderateHours"; 0.0 }
    val walkingDays = raw.walkingDays ?: run { missing += "walkingDays"; 0.0 }
    val walkingHours = raw.walkingHours ?: run { missing += "walkingHours"; 0.0 }
    val sitting = raw.sittingHoursPerWeekday ?: run { missing += "sittingHoursPerWeekday"; 0.0 }
    val previousBurden = raw.previousSymptomBurden ?: run { missing += "previousSymptomBurden"; 0.0 }

    if (missing.isNotEmpty()) {
        debugMissing("mapToSymptomInput", missing)
        return MapperResult.Failure(MissingFieldIssue("mapToSymptomInput", missing))
    }

    val symptomBurden = (pain + stiffness + fatigue) / 3.0
    val deltaSymptomBurden = symptomBurden - previousBurden

    return MapperResult.Success(
        SymptomModelInput(
            pain = pain,
            stiffness = stiffness,
            fatigue = fatigue,
            physicalDifficulty = difficulty,
            vigorousDays = vigorousDays,
            vigorousHours = vigorousHours,
            moderateDays = moderateDays,
            moderateHours = moderateHours,
            walkingDays = walkingDays,
            walkingHours = walkingHours,
            sittingHoursPerWeekday = sitting,
            symptomBurden = symptomBurden,
            deltaSymptomBurden = deltaSymptomBurden
        )
    )
}

fun mapToWeatherInput(raw: WeatherRawInput): MapperResult<WeatherModelInput> {
    val missing = mutableListOf<String>()

    val temp = raw.tempC ?: run { missing += "tempC"; 0.0 }
    val humidity = raw.humidity ?: run { missing += "humidity"; 0.0 }
    val pressure = raw.pressureHpa ?: run { missing += "pressureHpa"; 0.0 }
    val wind = raw.windKmh ?: run { missing += "windKmh"; 0.0 }
    val precipitation = raw.precipitation ?: run { missing += "precipitation"; 0.0 }
    val dTemp = raw.deltaTempC ?: run { missing += "deltaTempC"; 0.0 }
    val dHumidity = raw.deltaHumidity ?: run { missing += "deltaHumidity"; 0.0 }
    val dPressure = raw.deltaPressureHpa ?: run { missing += "deltaPressureHpa"; 0.0 }
    val dWind = raw.deltaWindKmh ?: run { missing += "deltaWindKmh"; 0.0 }
    val pain = raw.pain ?: run { missing += "pain"; 0.0 }
    val stiffness = raw.stiffness ?: run { missing += "stiffness"; 0.0 }
    val fatigue = raw.fatigue ?: run { missing += "fatigue"; 0.0 }
    val previousBurden = raw.previousSymptomBurden ?: run { missing += "previousSymptomBurden"; 0.0 }

    if (missing.isNotEmpty()) {
        debugMissing("mapToWeatherInput", missing)
        return MapperResult.Failure(MissingFieldIssue("mapToWeatherInput", missing))
    }

    val pressureDropFlag = if (dPressure <= -2.0) 1.0 else 0.0
    val humiditySpikeFlag = if (dHumidity >= 10.0) 1.0 else 0.0
    val symptomBurden = (pain + stiffness + fatigue) / 3.0
    val deltaSymptomBurden = symptomBurden - previousBurden

    return MapperResult.Success(
        WeatherModelInput(
            tempC = temp,
            humidity = humidity,
            pressureHpa = pressure,
            windKmh = wind,
            precipitation = precipitation,
            deltaTempC = dTemp,
            deltaHumidity = dHumidity,
            deltaPressureHpa = dPressure,
            deltaWindKmh = dWind,
            pressureDropFlag = pressureDropFlag,
            humiditySpikeFlag = humiditySpikeFlag,
            pain = pain,
            stiffness = stiffness,
            fatigue = fatigue,
            symptomBurden = symptomBurden,
            deltaSymptomBurden = deltaSymptomBurden
        )
    )
}

// -----------------------------------
// Temporary hardcoded model scoring
// Replace with Python API integration
// -----------------------------------

object MlRiskEngine {
    private const val STAGE1_THRESHOLD = 0.608
    private const val STAGE3_THRESHOLD = 0.70

    // TODO: replace with /predict/stage1 endpoint call
    fun predictPopulationRisk(input: PopulationModelInput): ModelPrediction {
        val smoking = smokingWeight(input.smokingStatus)
        val ageNorm = (input.ageYears.coerceIn(18, 90) - 18) / 72.0
        val activityPenalty = when (input.physicalActivity.lowercase()) {
            "sedentary" -> 0.18
            "moderate" -> 0.10
            else -> 0.04
        }
        val comorbidityPenalty =
            (if (input.hypertension.equals("yes", true)) 0.08 else 0.0) +
            (if (input.hyperlipidemia.equals("yes", true)) 0.06 else 0.0) +
            (if (input.diabetes.equals("diabetic", true)) 0.10 else if (input.diabetes.equals("pre", true)) 0.05 else 0.0)

        val probability = (0.20 + ageNorm * 0.24 + smoking * 0.20 + activityPenalty + comorbidityPenalty)
            .coerceIn(0.0, 1.0)

        return ModelPrediction(
            modelName = "arthocare_lr_smoteenn_final.pkl",
            probability = probability,
            threshold = STAGE1_THRESHOLD,
            riskLevel = if (probability >= STAGE1_THRESHOLD) RiskLevel.HIGH else RiskLevel.LOW
        )
    }

    // Derived symptom score (no standalone symptom .pkl in current model set)
    fun deriveSymptomRisk(input: SymptomModelInput): ModelPrediction {
        val burdenScore = (input.symptomBurden / 10.0).coerceIn(0.0, 1.0)
        val changeScore = ((input.deltaSymptomBurden + 5.0) / 10.0).coerceIn(0.0, 1.0)
        val inactivityScore = (input.sittingHoursPerWeekday / 12.0).coerceIn(0.0, 1.0)
        val probability = (0.45 * burdenScore + 0.30 * changeScore + 0.25 * inactivityScore).coerceIn(0.0, 1.0)

        return ModelPrediction(
            modelName = "derived_symptom_score",
            probability = probability,
            threshold = 0.50,
            riskLevel = classify(probability)
        )
    }

    // TODO: replace with /predict/stage3 endpoint call
    fun predictWeatherRisk(input: WeatherModelInput): ModelPrediction {
        val weatherPressureScore = if (input.pressureDropFlag > 0.5) 0.25 else 0.0
        val humiditySpikeScore = if (input.humiditySpikeFlag > 0.5) 0.15 else 0.0
        val symptomScore = (input.symptomBurden / 10.0) * 0.35
        val deltaSymptomScore = ((input.deltaSymptomBurden + 5.0) / 10.0) * 0.10
        val precipitationScore = (input.precipitation / 20.0).coerceIn(0.0, 1.0) * 0.15
        val probability = (weatherPressureScore + humiditySpikeScore + symptomScore + deltaSymptomScore + precipitationScore)
            .coerceIn(0.0, 1.0)

        return ModelPrediction(
            modelName = "arthocare_stage3_flare.pkl",
            probability = probability,
            threshold = STAGE3_THRESHOLD,
            riskLevel = classify(probability)
        )
    }

    fun combine(
        population: ModelPrediction,
        symptomDerived: ModelPrediction,
        weather: ModelPrediction
    ): CombinedRiskPrediction {
        val overall = ((population.probability + symptomDerived.probability + weather.probability) / 3.0)
            .coerceIn(0.0, 1.0)
        return CombinedRiskPrediction(
            population = population,
            symptomDerived = symptomDerived,
            weather = weather,
            overallProbability = overall,
            overallRiskLevel = classify(overall)
        )
    }
}

// -----------------------------------
// Hardcoded weather adapter for now
// -----------------------------------

fun hardcodedWeatherNow(
    pain: Double,
    stiffness: Double,
    fatigue: Double,
    previousSymptomBurden: Double
): WeatherRawInput {
    val temp = 23.4
    val humidity = 35.0
    val pressure = 1013.1
    val wind = 11.8
    val precipitation = 0.2

    val previousTemp = 26.2
    val previousHumidity = 25.0
    val previousPressure = 1011.6
    val previousWind = 9.0

    return WeatherRawInput(
        tempC = temp,
        humidity = humidity,
        pressureHpa = pressure,
        windKmh = wind,
        precipitation = precipitation,
        deltaTempC = temp - previousTemp,
        deltaHumidity = humidity - previousHumidity,
        deltaPressureHpa = pressure - previousPressure,
        deltaWindKmh = wind - previousWind,
        pain = pain,
        stiffness = stiffness,
        fatigue = fatigue,
        previousSymptomBurden = previousSymptomBurden
    )
}

fun formatRiskPercent(probability: Double): Int = (probability * 100.0).roundToInt()

fun PopulationModelInput.toStage1ApiRequest(): Stage1ApiRequest = Stage1ApiRequest(
    ageYears = ageYears,
    smokingStatus = smokingStatus,
    gender = gender,
    physicalActivity = physicalActivity,
    caloriesPerDay = caloriesPerDay,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    caffeineG = caffeineG,
    fiberG = fiberG,
    hypertension = hypertension,
    diabetes = diabetes,
    hyperlipidemia = hyperlipidemia,
    bmi = bmi,
    race = race,
    drinkingStatus = drinkingStatus
)

fun WeatherModelInput.toStage3ApiRequest(): Stage3ApiRequest = Stage3ApiRequest(
    tempC = tempC,
    humidity = humidity,
    pressureHpa = pressureHpa,
    windSpeedKmh = windKmh,
    precipIntensity = precipitation,
    deltaTempC = deltaTempC,
    deltaHumidity = deltaHumidity,
    deltaPressureHpa = deltaPressureHpa,
    deltaWindSpeedKmh = deltaWindKmh,
    pressureDropFlag = pressureDropFlag,
    humiditySpikeFlag = humiditySpikeFlag,
    pain = pain,
    stiffness = stiffness,
    fatigue = fatigue,
    symptomBurden = symptomBurden,
    deltaSymptomBurden = deltaSymptomBurden
)

