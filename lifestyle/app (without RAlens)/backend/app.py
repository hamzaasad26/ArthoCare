from flask import Flask, request, jsonify
from flask_cors import CORS
import joblib, pandas as pd, numpy as np
import requests, json, os
from datetime import datetime

app = Flask(__name__)
CORS(app)

# ── API Key ────────────────────────────────────────────────────────────────
# Option A: set as environment variable (recommended)
#   Windows: set OPENWEATHER_KEY=your_key_here
# Option B: paste key directly as fallback (for local dev only)
OPENWEATHER_KEY = os.environ.get("OPENWEATHER_KEY", "PASTE_YOUR_KEY_HERE")

# ── Load Stage-1 models ────────────────────────────────────────────────────
preprocessor = joblib.load("arthocare_preprocessor_final.pkl")
final_clf     = joblib.load("arthocare_lr_smoteenn_final.pkl")

# ── Load Stage-3 models ────────────────────────────────────────────────────
stage3_model  = joblib.load("arthocare_stage3_flare.pkl")
stage3_scaler = joblib.load("arthocare_stage3_scaler.pkl")

with open("arthocare_stage3_meta.json") as f:
    stage3_meta = json.load(f)

FEATURE_COLS       = stage3_meta["feature_cols"]
OPTIMAL_THRESHOLD  = stage3_meta["optimal_threshold"]

print("✓ Stage-1 models loaded")
print("✓ Stage-3 flare model loaded")
print(f"  Threshold: {OPTIMAL_THRESHOLD}  |  Features: {len(FEATURE_COLS)}")


# ══════════════════════════════════════════════════════════════════════════
# STAGE-1 ENDPOINT  (unchanged)
# ══════════════════════════════════════════════════════════════════════════

def predict_ra(data_dict):
    input_data = pd.DataFrame([data_dict])
    input_data["AgeGroup"] = pd.cut(
        input_data["Age"],
        bins=[0, 30, 50, 70, 200],
        labels=["<30", "30-50", "50-70", "70+"]
    )
    input_data["Age_Smoke"] = (
        input_data["AgeGroup"].astype(str) + "_" +
        input_data["SmokingStatus"].astype(str)
    )
    processed = preprocessor.transform(input_data)
    return float(final_clf.predict_proba(processed)[:, 1][0])


@app.route("/predict_stage1", methods=["POST"])
def predict_stage1():
    body = request.json
    prob = predict_ra(body)
    return jsonify({"p_stage1": prob})


# ══════════════════════════════════════════════════════════════════════════
# WEATHER HELPERS
# ══════════════════════════════════════════════════════════════════════════

def fetch_current_weather(lat, lon):
    """Fetch current weather from OpenWeather Current Weather API."""
    url = "https://api.openweathermap.org/data/2.5/weather"
    params = {
        "lat":   lat,
        "lon":   lon,
        "appid": OPENWEATHER_KEY,
        "units": "metric",
    }
    r = requests.get(url, params=params, timeout=10)
    r.raise_for_status()
    d = r.json()

    return {
        "temp_C":          d["main"]["temp"],
        "feels_like":      d["main"]["feels_like"],
        "humidity":        d["main"]["humidity"],
        "pressure_hPa":    d["main"]["pressure"],
        "wind_speed_kmh":  round(d["wind"]["speed"] * 3.6, 2),   # m/s → km/h
        "precip_intensity": d.get("rain", {}).get("1h", 0),
        "description":     d["weather"][0]["description"],
        "icon":            d["weather"][0]["icon"],
        "city":            d.get("name", ""),
        "timestamp":       datetime.utcnow().isoformat(),
    }


def fetch_forecast_weather(lat, lon):
    """
    Fetch 5-day / 3-hourly forecast from OpenWeather (free tier).
    Returns aggregated snapshots at ~24hr, ~48hr, ~72hr.
    """
    url = "https://api.openweathermap.org/data/2.5/forecast"
    params = {
        "lat":   lat,
        "lon":   lon,
        "appid": OPENWEATHER_KEY,
        "units": "metric",
    }
    r = requests.get(url, params=params, timeout=10)
    r.raise_for_status()
    data = r.json()["list"]   # list of 3-hourly forecasts

    def avg_window(entries):
        """Average weather values across a list of 3-hr forecast entries."""
        return {
            "temp_C":          np.mean([e["main"]["temp"]     for e in entries]),
            "humidity":        np.mean([e["main"]["humidity"] for e in entries]),
            "pressure_hPa":    np.mean([e["main"]["pressure"] for e in entries]),
            "wind_speed_kmh":  np.mean([e["wind"]["speed"] * 3.6 for e in entries]),
            "precip_intensity": np.mean([e.get("rain", {}).get("3h", 0) for e in entries]),
            "description":     entries[len(entries)//2]["weather"][0]["description"],
            "icon":            entries[len(entries)//2]["weather"][0]["icon"],
        }

    # Each entry covers 3 hours; 8 entries ≈ 24 hr window
    windows = {
        "24hr": data[0:8]   if len(data) >= 8  else data,
        "48hr": data[8:16]  if len(data) >= 16 else data[-8:],
        "72hr": data[16:24] if len(data) >= 24 else data[-8:],
    }
    return {k: avg_window(v) for k, v in windows.items()}


def engineer_features(current, prev, pain, stiffness, fatigue, prev_symptom_burden):
    """
    Build the exact 16-feature vector the Stage-3 model expects.
    Mirrors the feature engineering done in the training notebook.
    """
    delta_pressure = current["pressure_hPa"] - prev["pressure_hPa"]
    delta_humidity = current["humidity"]      - prev["humidity"]
    delta_temp     = current["temp_C"]        - prev["temp_C"]
    delta_wind     = current["wind_speed_kmh"]- prev["wind_speed_kmh"]

    symptom_burden       = (pain + stiffness + fatigue) / 3
    delta_symptom_burden = symptom_burden - prev_symptom_burden

    features = [
        # Absolute weather
        current["temp_C"],
        current["humidity"],
        current["pressure_hPa"],
        current["wind_speed_kmh"],
        current["precip_intensity"],
        # Weather deltas
        delta_temp,
        delta_humidity,
        delta_pressure,
        delta_wind,
        # Clinical flags
        1 if delta_pressure < -2 else 0,   # pressure_drop_flag
        1 if delta_humidity  >  5 else 0,  # humidity_spike_flag
        # Symptom state
        pain,
        stiffness,
        fatigue,
        symptom_burden,
        # Symptom trajectory
        delta_symptom_burden,
    ]
    return features, symptom_burden


def run_stage3(features):
    """Scale features and return flare probability from Stage-3 model."""
    X = np.array(features).reshape(1, -1)
    X_scaled = stage3_scaler.transform(X)
    prob = float(stage3_model.predict_proba(X_scaled)[0][1])
    return prob


def risk_label(prob):
    if prob >= 0.70:
        return "High"
    elif prob >= 0.45:
        return "Moderate"
    else:
        return "Low"


def trend_arrow(current_prob, future_prob):
    diff = future_prob - current_prob
    if diff >  0.08: return "↑ Worsening"
    if diff < -0.08: return "↓ Improving"
    return "→ Stable"


# ══════════════════════════════════════════════════════════════════════════
# STAGE-3 ENDPOINT
# ══════════════════════════════════════════════════════════════════════════

@app.route("/predict_flare", methods=["POST"])
def predict_flare():
    """
    Expected request body:
    {
        "lat": 33.72,
        "lon": 73.04,
        "pain": 6,
        "stiffness": 7,
        "fatigue": 5,

        // Optional: previous weather snapshot so we can compute deltas.
        // If omitted, deltas are assumed 0 (first reading).
        "prev_weather": {
            "temp_C": 23, "humidity": 55, "pressure_hPa": 1018,
            "wind_speed_kmh": 10, "precip_intensity": 0
        },
        "prev_symptom_burden": 5.0
    }

    Returns:
    {
        "current_weather": { ... },
        "forecast": {
            "24hr": { "flare_prob": 0.62, "risk": "Moderate", "weather": {...} },
            "48hr": { "flare_prob": 0.78, "risk": "High",     "weather": {...} },
            "72hr": { "flare_prob": 0.55, "risk": "Moderate", "weather": {...} }
        },
        "trend_48hr": "↑ Worsening",
        "trend_72hr": "→ Stable",
        "threshold_used": 0.5
    }
    """
    body = request.json

    # ── Validate required fields ───────────────────────────────────────────
    required = ["lat", "lon", "pain", "stiffness", "fatigue"]
    missing  = [k for k in required if k not in body]
    if missing:
        return jsonify({"error": f"Missing fields: {missing}"}), 400

    lat              = float(body["lat"])
    lon              = float(body["lon"])
    pain             = float(body["pain"])
    stiffness        = float(body["stiffness"])
    fatigue          = float(body["fatigue"])
    prev_symptom_burden = float(body.get("prev_symptom_burden",
                                         (pain + stiffness + fatigue) / 3))

    # ── Default previous weather (no delta if first reading) ──────────────
    NULL_WEATHER = {
        "temp_C": 0, "humidity": 0, "pressure_hPa": 0,
        "wind_speed_kmh": 0, "precip_intensity": 0,
    }
    prev_weather = body.get("prev_weather", None)

    # ── Fetch weather data ─────────────────────────────────────────────────
    try:
        current_weather  = fetch_current_weather(lat, lon)
        forecast_weather = fetch_forecast_weather(lat, lon)
    except requests.exceptions.HTTPError as e:
        return jsonify({"error": f"OpenWeather API error: {str(e)}"}), 502
    except requests.exceptions.ConnectionError:
        return jsonify({"error": "Cannot reach OpenWeather API. Check your internet connection."}), 503

    # Use current as "previous" if no prev_weather supplied (first reading)
    if prev_weather is None:
        prev_weather = {k: current_weather[k] for k in NULL_WEATHER}

    # ── Run Stage-3 for each horizon ──────────────────────────────────────
    results = {}

    for horizon, weather_snap in forecast_weather.items():
        # For 24hr: compare forecast against current
        # For 48hr: compare forecast against 24hr forecast
        # For 72hr: compare forecast against 48hr forecast
        if horizon == "24hr":
            base = current_weather
        elif horizon == "48hr":
            base = forecast_weather["24hr"]
        else:
            base = forecast_weather["48hr"]

        feats, sym_burden = engineer_features(
            current        = weather_snap,
            prev           = base,
            pain           = pain,
            stiffness      = stiffness,
            fatigue        = fatigue,
            prev_symptom_burden = prev_symptom_burden,
        )
        prob = run_stage3(feats)

        results[horizon] = {
            "flare_prob":    round(prob, 3),
            "risk":          risk_label(prob),
            "above_threshold": prob >= OPTIMAL_THRESHOLD,
            "weather": {
                "temp_C":          round(weather_snap["temp_C"], 1),
                "humidity":        round(weather_snap["humidity"], 1),
                "pressure_hPa":    round(weather_snap["pressure_hPa"], 1),
                "wind_speed_kmh":  round(weather_snap["wind_speed_kmh"], 1),
                "precip_intensity": round(weather_snap["precip_intensity"], 2),
                "description":     weather_snap["description"],
                "icon":            weather_snap["icon"],
            },
        }

    # Current-conditions prediction (baseline)
    base_feats, _ = engineer_features(
        current             = current_weather,
        prev                = prev_weather,
        pain                = pain,
        stiffness           = stiffness,
        fatigue             = fatigue,
        prev_symptom_burden = prev_symptom_burden,
    )
    current_prob = run_stage3(base_feats)

    return jsonify({
        "current_weather": {
            "temp_C":          round(current_weather["temp_C"], 1),
            "humidity":        current_weather["humidity"],
            "pressure_hPa":    current_weather["pressure_hPa"],
            "wind_speed_kmh":  round(current_weather["wind_speed_kmh"], 1),
            "precip_intensity": current_weather["precip_intensity"],
            "description":     current_weather["description"],
            "icon":            current_weather["icon"],
            "city":            current_weather["city"],
            "flare_prob":      round(current_prob, 3),
            "risk":            risk_label(current_prob),
        },
        "forecast":      results,
        "trend_48hr":    trend_arrow(current_prob, results["48hr"]["flare_prob"]),
        "trend_72hr":    trend_arrow(results["48hr"]["flare_prob"], results["72hr"]["flare_prob"]),
        "threshold_used": OPTIMAL_THRESHOLD,
        "model_auc":      stage3_meta.get("loocv_auc"),
    })


# ══════════════════════════════════════════════════════════════════════════
# UTILITY: resolve city name → lat/lon (optional convenience endpoint)
# ══════════════════════════════════════════════════════════════════════════

@app.route("/geocode", methods=["GET"])
def geocode():
    """
    GET /geocode?city=Islamabad
    Returns lat/lon so the frontend can pass coordinates to /predict_flare.
    """
    city = request.args.get("city", "").strip()
    if not city:
        return jsonify({"error": "Provide ?city=CityName"}), 400

    url = "http://api.openweathermap.org/geo/1.0/direct"
    params = {"q": city, "limit": 1, "appid": OPENWEATHER_KEY}
    r = requests.get(url, params=params, timeout=10)
    r.raise_for_status()
    results = r.json()
    if not results:
        return jsonify({"error": f"City '{city}' not found"}), 404

    return jsonify({
        "city":    results[0].get("name"),
        "country": results[0].get("country"),
        "lat":     results[0]["lat"],
        "lon":     results[0]["lon"],
    })


# ══════════════════════════════════════════════════════════════════════════
if __name__ == "__main__":
    app.run(port=5000, debug=True)