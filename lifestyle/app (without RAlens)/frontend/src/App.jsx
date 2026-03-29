import React, { useState } from "react";

// ─── Baked-in model weights (trained on your data) ────────────────────────
const STAGE2_SCALER = {
  mean: [0.7397, 5.1333, 4.8667, 5.0333, 5.6333, 1.4667, 1.2290, 4.7333, 3.4692, 4.9333, 2.4336, 8.6],
  scale: [0.0976, 1.6479, 1.5434, 1.8163, 1.9058, 1.0562, 0.9036, 1.9482, 1.7499, 2.0483, 1.7630, 1.3565],
};
const STAGE2_MODEL = {
  coef: [0.2877, 0.9049, 0.4870, 1.0444, 0.4956, -0.1704, 0.2088, -0.3562, -0.2672, -0.0092, -0.0601, 0.4318],
  intercept: -0.0463,
};
const STAGE1_THRESHOLD = 0.608;

function computeStage2(p1, sym) {
  const feats = [
    p1,
    parseFloat(sym.pain) || 0,
    parseFloat(sym.difficulty) || 0,
    parseFloat(sym.fatigue) || 0,
    parseFloat(sym.stiffness) || 0,
    parseFloat(sym.vigorousDays) || 0,
    parseFloat(sym.vigorousHrs) || 0,
    parseFloat(sym.moderateDays) || 0,
    parseFloat(sym.moderateHrs) || 0,
    parseFloat(sym.walkDays) || 0,
    parseFloat(sym.walkHrs) || 0,
    parseFloat(sym.sittingHrs) || 8,
  ];
  const scaled = feats.map((v, i) => (v - STAGE2_SCALER.mean[i]) / STAGE2_SCALER.scale[i]);
  const logit = scaled.reduce((s, v, i) => s + v * STAGE2_MODEL.coef[i], STAGE2_MODEL.intercept);
  return 1 / (1 + Math.exp(-logit));
}

// ─── Shared style tokens ──────────────────────────────────────────────────
const STEP_LABELS = ["Personal Info", "Lifestyle & Diet", "Weekly Symptoms", "RA Results", "Flare Forecast", "Joint Report"];

const inputStyle = {
  width: "100%", background: "#1a1a1a", border: "1px solid #333",
  borderRadius: 6, padding: "10px 14px", color: "#f0e6e0",
  fontFamily: "monospace", fontSize: 14, outline: "none",
  boxSizing: "border-box", transition: "border 0.2s",
};
const selectStyle = { ...inputStyle, cursor: "pointer" };

// ─── Reusable UI components ───────────────────────────────────────────────
function ProgressBar({ step }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 0, marginBottom: 36 }}>
      {STEP_LABELS.map((label, i) => (
        <div key={i} style={{ display: "flex", alignItems: "center", flex: i < STEP_LABELS.length - 1 ? 1 : 0 }}>
          <div style={{ display: "flex", flexDirection: "column", alignItems: "center" }}>
            <div style={{
              width: 32, height: 32, borderRadius: "50%",
              background: i <= step ? "#c0392b" : "#2d2d2d",
              border: `2px solid ${i <= step ? "#c0392b" : "#444"}`,
              display: "flex", alignItems: "center", justifyContent: "center",
              color: i <= step ? "#fff" : "#666",
              fontFamily: "'Crimson Text', Georgia, serif",
              fontSize: 13, fontWeight: 700, transition: "all 0.3s ease",
            }}>
              {i < step ? "✓" : i + 1}
            </div>
            <span style={{ fontSize: 9, color: i <= step ? "#e8c5c0" : "#555", marginTop: 4, whiteSpace: "nowrap", fontFamily: "monospace", letterSpacing: 0.5 }}>
              {label.toUpperCase()}
            </span>
          </div>
          {i < STEP_LABELS.length - 1 && (
            <div style={{ flex: 1, height: 2, background: i < step ? "#c0392b" : "#333", margin: "0 6px", marginBottom: 20, transition: "background 0.4s" }} />
          )}
        </div>
      ))}
    </div>
  );
}

function SectionTitle({ children }) {
  return (
    <h2 style={{ fontFamily: "'Crimson Text', Georgia, serif", color: "#f0e6e0", fontSize: 22, fontWeight: 600, margin: "0 0 24px", borderBottom: "1px solid #1e1e1e", paddingBottom: 12 }}>
      {children}
    </h2>
  );
}

function Field({ label, hint, children }) {
  return (
    <div style={{ marginBottom: 20 }}>
      <label style={{ display: "block", color: "#e8c5c0", fontSize: 12, letterSpacing: 2, fontFamily: "monospace", marginBottom: 6, textTransform: "uppercase" }}>
        {label}
        {hint && <span style={{ color: "#666", marginLeft: 8, textTransform: "none", letterSpacing: 0 }}>({hint})</span>}
      </label>
      {children}
    </div>
  );
}

function Input({ value, onChange, type = "text", placeholder, min, max, step }) {
  const [focused, setFocused] = useState(false);
  return (
    <input type={type} value={value} onChange={e => onChange(e.target.value)}
      placeholder={placeholder} min={min} max={max} step={step}
      style={{ ...inputStyle, border: `1px solid ${focused ? "#c0392b" : "#333"}` }}
      onFocus={() => setFocused(true)} onBlur={() => setFocused(false)} />
  );
}

function Select({ value, onChange, options }) {
  const [focused, setFocused] = useState(false);
  return (
    <select value={value} onChange={e => onChange(e.target.value)}
      style={{ ...selectStyle, border: `1px solid ${focused ? "#c0392b" : "#333"}` }}
      onFocus={() => setFocused(true)} onBlur={() => setFocused(false)}>
      <option value="">— select —</option>
      {options.map(o => <option key={o} value={o}>{o}</option>)}
    </select>
  );
}

function SliderField({ label, value, onChange, max = 10 }) {
  const pct = (parseFloat(value) || 0) / max * 100;
  const color = pct < 33 ? "#27ae60" : pct < 66 ? "#e67e22" : "#c0392b";
  return (
    <div style={{ marginBottom: 22 }}>
      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
        <label style={{ color: "#e8c5c0", fontSize: 12, letterSpacing: 1.5, fontFamily: "monospace", textTransform: "uppercase" }}>{label}</label>
        <span style={{ color, fontFamily: "'Crimson Text', Georgia, serif", fontSize: 20, fontWeight: 700, minWidth: 28, textAlign: "right" }}>{value || 0}</span>
      </div>
      <div style={{ position: "relative", height: 6, background: "#1e1e1e", borderRadius: 3, border: "1px solid #333" }}>
        <div style={{ position: "absolute", left: 0, top: 0, height: "100%", width: `${pct}%`, background: color, borderRadius: 3, transition: "width 0.15s, background 0.3s" }} />
      </div>
      <input type="range" min={0} max={max} step={1} value={value || 0}
        onChange={e => onChange(e.target.value)}
        style={{ width: "100%", marginTop: 4, accentColor: color, cursor: "pointer" }} />
      <div style={{ display: "flex", justifyContent: "space-between", fontSize: 10, color: "#555", fontFamily: "monospace" }}>
        <span>0 — None</span><span>{max} — Severe</span>
      </div>
    </div>
  );
}

function GaugeArc({ value, label, color }) {
  const r = 70, cx = 100, cy = 90;
  const angle = -Math.PI + value * Math.PI;
  const x1 = cx - r, y1 = cy;
  const x2 = cx + r * Math.cos(angle), y2 = cy + r * Math.sin(angle);
  const largeArc = value > 0.5 ? 1 : 0;
  return (
    <svg viewBox="0 0 200 110" style={{ width: "100%", maxWidth: 220 }}>
      <path d={`M ${cx - r} ${cy} A ${r} ${r} 0 0 1 ${cx + r} ${cy}`} fill="none" stroke="#1e1e1e" strokeWidth={14} />
      {value > 0 && <path d={`M ${x1} ${y1} A ${r} ${r} 0 ${largeArc} 1 ${x2} ${y2}`} fill="none" stroke={color} strokeWidth={14} strokeLinecap="round" />}
      <text x={cx} y={cy - 10} textAnchor="middle" fill={color} fontSize={28} fontFamily="'Crimson Text', Georgia, serif" fontWeight={700}>{Math.round(value * 100)}%</text>
      <text x={cx} y={cy + 12} textAnchor="middle" fill="#888" fontSize={10} fontFamily="monospace" letterSpacing={2}>{label.toUpperCase()}</text>
    </svg>
  );
}

function Tag({ color, label, value }) {
  return (
    <div style={{ background: `${color}15`, border: `1px solid ${color}44`, borderRadius: 4, padding: "4px 10px" }}>
      <span style={{ color: "#666", fontFamily: "monospace", fontSize: 10, textTransform: "uppercase", letterSpacing: 1 }}>{label}: </span>
      <span style={{ color, fontFamily: "monospace", fontSize: 11, fontWeight: 700 }}>{value}</span>
    </div>
  );
}

function NavRow({ onBack, onNext, nextDisabled, nextLabel = "Continue →", loading = false }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", marginTop: 32 }}>
      {onBack
        ? <button onClick={onBack} style={{ background: "transparent", border: "1px solid #333", borderRadius: 6, padding: "10px 20px", color: "#888", fontFamily: "monospace", fontSize: 12, letterSpacing: 2, cursor: "pointer", textTransform: "uppercase" }}>← Back</button>
        : <div />}
      <button onClick={onNext} disabled={nextDisabled || loading}
        style={{ background: (nextDisabled || loading) ? "#1a1a1a" : "#c0392b", border: "none", borderRadius: 6, padding: "12px 28px", color: (nextDisabled || loading) ? "#444" : "#fff", fontFamily: "monospace", fontSize: 12, letterSpacing: 2, cursor: (nextDisabled || loading) ? "not-allowed" : "pointer", textTransform: "uppercase", transition: "background 0.2s" }}>
        {loading ? "Loading..." : nextLabel}
      </button>
    </div>
  );
}

// ─── Flare Forecast Card ──────────────────────────────────────────────────
function FlareCard({ horizon, data, isCurrent = false }) {
  const prob = data.flare_prob;
  const color = data.risk === "High" ? "#c0392b" : data.risk === "Moderate" ? "#e67e22" : "#27ae60";
  const pct = Math.round(prob * 100);
  // current_weather is flat; forecast entries nest weather under .weather
  const w = data.weather ?? data;

  return (
    <div style={{
      background: "#0f0f0f", border: `1px solid ${color}33`,
      borderTop: `3px solid ${color}`, borderRadius: 10, padding: "18px 20px",
      flex: 1, minWidth: 0,
    }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 14 }}>
        <div>
          <div style={{ color: "#888", fontFamily: "monospace", fontSize: 10, letterSpacing: 2, textTransform: "uppercase" }}>
            {isCurrent ? "Right Now" : horizon}
          </div>
          <div style={{ color, fontFamily: "'Crimson Text', Georgia, serif", fontSize: 28, fontWeight: 700, lineHeight: 1.1 }}>
            {pct}%
          </div>
          <div style={{ color, fontFamily: "monospace", fontSize: 10, letterSpacing: 1, textTransform: "uppercase" }}>
            {data.risk} Risk
          </div>
        </div>
        {w.icon && (
          <img src={`https://openweathermap.org/img/wn/${w.icon}@2x.png`} alt={w.description}
            style={{ width: 48, height: 48, filter: "brightness(0.9)" }} />
        )}
      </div>

      {/* Mini progress bar */}
      <div style={{ height: 4, background: "#1a1a1a", borderRadius: 2, marginBottom: 14 }}>
        <div style={{ height: "100%", width: `${pct}%`, background: color, borderRadius: 2, transition: "width 0.6s ease" }} />
      </div>

      {/* Weather stats */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "6px 12px" }}>
        {[
          ["🌡", `${w.temp_C}°C`],
          ["💧", `${w.humidity}%`],
          ["⊕", `${w.pressure_hPa} hPa`],
          ["💨", `${w.wind_speed_kmh} km/h`],
        ].map(([icon, val]) => (
          <div key={icon} style={{ display: "flex", gap: 6, alignItems: "center" }}>
            <span style={{ fontSize: 11 }}>{icon}</span>
            <span style={{ color: "#999", fontFamily: "monospace", fontSize: 11 }}>{val}</span>
          </div>
        ))}
      </div>
      {w.description && (
        <div style={{ color: "#555", fontFamily: "monospace", fontSize: 10, marginTop: 8, textTransform: "capitalize", letterSpacing: 1 }}>
          {w.description}
        </div>
      )}
      {data.above_threshold && (
        <div style={{ marginTop: 10, background: `${color}18`, border: `1px solid ${color}44`, borderRadius: 4, padding: "5px 8px", fontFamily: "monospace", fontSize: 10, color, letterSpacing: 1 }}>
          ⚠ FLARE LIKELY
        </div>
      )}
    </div>
  );
}

// ─── Main App ─────────────────────────────────────────────────────────────
export default function RAApp() {
  const [step, setStep] = useState(0);
  const [animIn, setAnimIn] = useState(true);
  const [name, setName] = useState("");

  const [demo, setDemo] = useState({
    age: "", gender: "", race: "", bmi: "",
    physicalActivity: "", smokingStatus: "", drinkingStatus: "",
    calories: "", proteins: "", carbs: "", fat: "", caffeine: "", fiber: "",
    hypertension: "", diabetes: "", hyperlipidemia: "",
  });

  const [sym, setSym] = useState({
    pain: 0, difficulty: 0, fatigue: 0, stiffness: 0,
    vigorousDays: 0, vigorousHrs: 0,
    moderateDays: 0, moderateHrs: 0,
    walkDays: 0, walkHrs: 0, sittingHrs: 8,
  });

  // Stage 1+2 results
  const [result, setResult] = useState(null);

  // Stage 3 flare state
  const [flareData, setFlareData] = useState(null);
  const [flareLoading, setFlareLoading] = useState(false);
  const [flareError, setFlareError] = useState("");
  // geoStatus: 'idle' | 'detecting' | 'confirming' | 'confirmed' | 'manual'
  const [geoStatus, setGeoStatus] = useState("idle");
  const [detectedLocation, setDetectedLocation] = useState(null);
  const [confirmedLocation, setConfirmedLocation] = useState(null);
  const [manualCity, setManualCity] = useState("");

  const setD = k => v => setDemo(p => ({ ...p, [k]: v }));
  const setS = k => v => setSym(p => ({ ...p, [k]: parseFloat(v) || 0 }));

  function goTo(n) {
    setAnimIn(false);
    setTimeout(() => { setStep(n); setAnimIn(true); }, 200);
  }
  const nextStep = () => goTo(step + 1);
  const prevStep = () => goTo(step - 1);

  // ── Stage 1 + 2 ────────────────────────────────────────────────────────
  async function computeResults() {
    const payload = {
      Age: parseFloat(demo.age),
      Gender: demo.gender,
      Race: demo.race,
      BMI: parseFloat(demo.bmi),
      PhysicalActivity: demo.physicalActivity,
      SmokingStatus: demo.smokingStatus,
      DrinkingStatus: demo.drinkingStatus,
      CalorieConsumption: parseFloat(demo.calories) || 0,
      ProteinConsumption: parseFloat(demo.proteins) || 0,
      CarbohydrateConsumption: parseFloat(demo.carbs) || 0,
      FatConsumption: parseFloat(demo.fat) || 0,
      CaffeineConsumption: parseFloat(demo.caffeine) || 0,
      FiberConsumption: parseFloat(demo.fiber) || 0,
      Hypertension: demo.hypertension,
      Diabetes: demo.diabetes,
      Hyperlipidemia: demo.hyperlipidemia,
    };
    const res = await fetch("http://localhost:5000/predict_stage1", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    const data = await res.json();
    const p1 = data.p_stage1;
    const p2 = computeStage2(p1, sym);
    const combined = 0.45 * p1 + 0.55 * p2;
    const riskLevel = combined >= 0.70 ? "High" : combined >= 0.45 ? "Moderate" : "Low";
    const severityLabel = p2 >= 0.5 ? "Moderate–Severe" : "Mild";
    setResult({ p1, p2, combined, riskLevel, severityLabel });
    nextStep();
  }

  // ── Stage 3: Flare forecast ────────────────────────────────────────────
  // ── Geolocation: auto-detect then reverse-geocode via OpenWeather ──────────
  async function detectLocation() {
    if (!navigator.geolocation) {
      setGeoStatus("manual");
      setFlareError("Geolocation not supported by your browser.");
      return;
    }
    setGeoStatus("detecting");
    setFlareError("");
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        try {
          const { latitude: lat, longitude: lon } = pos.coords;
          const res = await fetch(
            `https://api.openweathermap.org/geo/1.0/reverse?lat=${lat}&lon=${lon}&limit=1&appid=44a3285b9a5c4fd3a297093473e448bc`
          );
          const data = await res.json();
          const place = data[0] ?? {};
          setDetectedLocation({
            city: place.name ?? "Unknown",
            country: place.country ?? "",
            lat,
            lon,
          });
          setGeoStatus("confirming");
        } catch {
          setGeoStatus("manual");
          setFlareError("Could not resolve your location. Enter city manually.");
        }
      },
      () => {
        setGeoStatus("manual");
        setFlareError("Location access denied. Enter your city manually below.");
      },
      { timeout: 10000 }
    );
  }

  async function fetchFlareForLocation(loc) {
    setFlareLoading(true);
    setFlareError("");
    setFlareData(null);
    try {
      const flareRes = await fetch("http://localhost:5000/predict_flare", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          lat: loc.lat,
          lon: loc.lon,
          pain: sym.pain,
          stiffness: sym.stiffness,
          fatigue: sym.fatigue,
          prev_symptom_burden: (sym.pain + sym.stiffness + sym.fatigue) / 3,
        }),
      });
      if (!flareRes.ok) { const e = await flareRes.json(); throw new Error(e.error || "Prediction failed"); }
      const fd = await flareRes.json();
      setFlareData({ ...fd, city: loc.city, country: loc.country });
      setConfirmedLocation(loc);
      setGeoStatus("confirmed");
    } catch (err) {
      setFlareError(err.message);
    } finally {
      setFlareLoading(false);
    }
  }

  async function fetchFlareManual() {
    if (!manualCity.trim()) { setFlareError("Please enter a city name."); return; }
    setFlareLoading(true);
    setFlareError("");
    setFlareData(null);
    try {
      const geoRes = await fetch(`http://localhost:5000/geocode?city=${encodeURIComponent(manualCity)}`);
      if (!geoRes.ok) { const e = await geoRes.json(); throw new Error(e.error || "City not found"); }
      const geo = await geoRes.json();
      await fetchFlareForLocation({ city: geo.city, country: geo.country, lat: geo.lat, lon: geo.lon });
    } catch (err) {
      setFlareError(err.message);
      setFlareLoading(false);
    }
  }

  const riskColor = !result ? "#888" : result.riskLevel === "High" ? "#c0392b" : result.riskLevel === "Moderate" ? "#e67e22" : "#27ae60";

  // ── Joint ROM probabilities ────────────────────────────────────────────
  const JOINTS = ["hand", "wrist", "elbow", "shoulder", "knee", "ankle"];
  const [jointProbs, setJointProbs] = useState({ hand: "", wrist: "", elbow: "", shoulder: "", knee: "", ankle: "" });
  const setJP = k => v => {
    const n = parseFloat(v);
    if (v === "" || (n >= 0 && n <= 1)) setJointProbs(p => ({ ...p, [k]: v }));
  };
  const jointReport = flareData
    ? JOINTS.map(j => {
        const jp = parseFloat(jointProbs[j]) || 0;
        return {
          joint: j,
          jp,
          "24hr": +(jp * flareData.forecast["24hr"].flare_prob).toFixed(3),
          "48hr": +(jp * flareData.forecast["48hr"].flare_prob).toFixed(3),
          "72hr": +(jp * flareData.forecast["72hr"].flare_prob).toFixed(3),
        };
      })
    : [];

  const canNext0 = name && demo.age && demo.gender && demo.race && demo.bmi;
  const canNext1 = demo.physicalActivity && demo.smokingStatus && demo.drinkingStatus &&
    demo.calories && demo.hypertension && demo.diabetes && demo.hyperlipidemia;

  function resetAll() {
    setStep(0); setResult(null); setName(""); setFlareData(null); setFlareError("");
    setGeoStatus("idle"); setDetectedLocation(null); setConfirmedLocation(null); setManualCity(""); setJointProbs({ hand: "", wrist: "", elbow: "", shoulder: "", knee: "", ankle: "" });
    setDemo({ age: "", gender: "", race: "", bmi: "", physicalActivity: "", smokingStatus: "", drinkingStatus: "", calories: "", proteins: "", carbs: "", fat: "", caffeine: "", fiber: "", hypertension: "", diabetes: "", hyperlipidemia: "" });
    setSym({ pain: 0, difficulty: 0, fatigue: 0, stiffness: 0, vigorousDays: 0, vigorousHrs: 0, moderateDays: 0, moderateHrs: 0, walkDays: 0, walkHrs: 0, sittingHrs: 8 });
    setAnimIn(true);
  }

  return (
    <div style={{
      minHeight: "100vh", background: "#0f0f0f",
      backgroundImage: "radial-gradient(ellipse at 20% 20%, #1a0a08 0%, transparent 60%), radial-gradient(ellipse at 80% 80%, #0a0f1a 0%, transparent 60%)",
      display: "flex", alignItems: "flex-start", justifyContent: "center",
      padding: "40px 16px", fontFamily: "Georgia, serif",
    }}>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Crimson+Text:ital,wght@0,400;0,600;0,700;1,400&display=swap');
        input[type=range]::-webkit-slider-thumb { width: 18px; height: 18px; }
        @keyframes spin { to { transform: rotate(360deg); } }
        * { box-sizing: border-box; }
        select option { background: #1a1a1a; }
      `}</style>

      <div style={{ width: "100%", maxWidth: step === 4 ? 820 : 640 }}>

        {/* Header */}
        <div style={{ textAlign: "center", marginBottom: 44 }}>
          <div style={{ display: "inline-flex", alignItems: "center", gap: 12, marginBottom: 8 }}>
            <div style={{ width: 3, height: 36, background: "#c0392b", borderRadius: 2 }} />
            <h1 style={{ margin: 0, fontFamily: "'Crimson Text', Georgia, serif", fontSize: 32, color: "#f0e6e0", fontWeight: 600, letterSpacing: -0.5 }}>
              ArthoCare
            </h1>
            <div style={{ width: 3, height: 36, background: "#c0392b", borderRadius: 2 }} />
          </div>
          <p style={{ color: "#666", fontSize: 12, letterSpacing: 3, fontFamily: "monospace", margin: 0, textTransform: "uppercase" }}>
            Three-Stage RA Risk & Flare Prediction
          </p>
        </div>

        {/* Card */}
        <div style={{
          background: "#141414", border: "1px solid #222", borderRadius: 16,
          padding: "36px 40px",
          boxShadow: "0 20px 60px rgba(0,0,0,0.6), inset 0 1px 0 rgba(255,255,255,0.03)",
          opacity: animIn ? 1 : 0, transform: animIn ? "translateY(0)" : "translateY(10px)",
          transition: "opacity 0.25s ease, transform 0.25s ease",
        }}>
          <ProgressBar step={step} />

          {/* ── STEP 0: Personal Info ── */}
          {step === 0 && (
            <div>
              <SectionTitle>Personal Information</SectionTitle>
              <Field label="Full Name">
                <Input value={name} onChange={setName} placeholder="Enter your name" />
              </Field>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
                <Field label="Age"><Input value={demo.age} onChange={setD("age")} type="number" placeholder="e.g. 45" min={1} max={120} /></Field>
                <Field label="BMI"><Input value={demo.bmi} onChange={setD("bmi")} type="number" placeholder="e.g. 24.5" min={10} max={60} step={0.1} /></Field>
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
                <Field label="Gender"><Select value={demo.gender} onChange={setD("gender")} options={["Male", "Female"]} /></Field>
                <Field label="Race / Ethnicity"><Select value={demo.race} onChange={setD("race")} options={["Asian", "Mexican American", "Non hispanic white", "Non hispanic black", "Other"]} /></Field>
              </div>
              <NavRow onNext={nextStep} nextDisabled={!canNext0} />
            </div>
          )}

          {/* ── STEP 1: Lifestyle & Diet ── */}
          {step === 1 && (
            <div>
              <SectionTitle>Lifestyle & Diet</SectionTitle>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 16 }}>
                <Field label="Physical Activity"><Select value={demo.physicalActivity} onChange={setD("physicalActivity")} options={["Sedentary", "Moderate", "Active"]} /></Field>
                <Field label="Smoking"><Select value={demo.smokingStatus} onChange={setD("smokingStatus")} options={["Never", "Former", "Current"]} /></Field>
                <Field label="Drinking"><Select value={demo.drinkingStatus} onChange={setD("drinkingStatus")} options={["Never", "Moderate", "Heavy"]} /></Field>
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 16 }}>
                <Field label="Calories / day" hint="kcal"><Input value={demo.calories} onChange={setD("calories")} type="number" placeholder="2000" /></Field>
                <Field label="Protein / day" hint="g"><Input value={demo.proteins} onChange={setD("proteins")} type="number" placeholder="70" /></Field>
                <Field label="Carbs / day" hint="g"><Input value={demo.carbs} onChange={setD("carbs")} type="number" placeholder="250" /></Field>
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 16 }}>
                <Field label="Fat / day" hint="g"><Input value={demo.fat} onChange={setD("fat")} type="number" placeholder="65" /></Field>
                <Field label="Caffeine / day" hint="mg"><Input value={demo.caffeine} onChange={setD("caffeine")} type="number" placeholder="200" /></Field>
                <Field label="Fiber / day" hint="g"><Input value={demo.fiber} onChange={setD("fiber")} type="number" placeholder="25" /></Field>
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 16 }}>
                <Field label="Hypertension"><Select value={demo.hypertension} onChange={setD("hypertension")} options={["No", "Yes"]} /></Field>
                <Field label="Diabetes"><Select value={demo.diabetes} onChange={setD("diabetes")} options={["Normal", "Prediabetic", "Diabetic"]} /></Field>
                <Field label="Hyperlipidemia"><Select value={demo.hyperlipidemia} onChange={setD("hyperlipidemia")} options={["No", "Yes"]} /></Field>
              </div>
              <NavRow onBack={prevStep} onNext={nextStep} nextDisabled={!canNext1} />
            </div>
          )}

          {/* ── STEP 2: Weekly Symptoms ── */}
          {step === 2 && (
            <div>
              <SectionTitle>Weekly Symptom Log</SectionTitle>
              <p style={{ color: "#666", fontSize: 13, fontFamily: "monospace", marginBottom: 24, lineHeight: 1.6 }}>
                Rate your experience during the <span style={{ color: "#e8c5c0" }}>last 7 days</span> on a scale of 0–10.
              </p>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 32px" }}>
                <SliderField label="Pain" value={sym.pain} onChange={setS("pain")} />
                <SliderField label="Stiffness" value={sym.stiffness} onChange={setS("stiffness")} />
                <SliderField label="Fatigue" value={sym.fatigue} onChange={setS("fatigue")} />
                <SliderField label="Physical Difficulty" value={sym.difficulty} onChange={setS("difficulty")} />
              </div>
              <div style={{ margin: "24px 0 8px", borderTop: "1px solid #222", paddingTop: 24 }}>
                <p style={{ color: "#888", fontSize: 11, letterSpacing: 2, fontFamily: "monospace", textTransform: "uppercase", marginBottom: 20 }}>
                  Physical Activity Details (Last 7 Days)
                </p>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 16 }}>
                  <Field label="Vigorous Days"><Input value={sym.vigorousDays} onChange={setS("vigorousDays")} type="number" placeholder="0" min={0} max={7} /></Field>
                  <Field label="Vigorous Hrs"><Input value={sym.vigorousHrs} onChange={setS("vigorousHrs")} type="number" placeholder="0" min={0} step={0.25} /></Field>
                  <Field label="Moderate Days"><Input value={sym.moderateDays} onChange={setS("moderateDays")} type="number" placeholder="0" min={0} max={7} /></Field>
                  <Field label="Moderate Hrs"><Input value={sym.moderateHrs} onChange={setS("moderateHrs")} type="number" placeholder="0" min={0} step={0.25} /></Field>
                  <Field label="Walking Days"><Input value={sym.walkDays} onChange={setS("walkDays")} type="number" placeholder="0" min={0} max={7} /></Field>
                  <Field label="Walking Hrs"><Input value={sym.walkHrs} onChange={setS("walkHrs")} type="number" placeholder="0" min={0} step={0.25} /></Field>
                </div>
                <Field label="Sitting Hrs / Weekday">
                  <Input value={sym.sittingHrs} onChange={setS("sittingHrs")} type="number" placeholder="8" min={0} max={24} step={0.5} />
                </Field>
              </div>
              <NavRow onBack={prevStep} onNext={computeResults} nextLabel="Compute Risk →" />
            </div>
          )}

          {/* ── STEP 3: RA Results ── */}
          {step === 3 && result && (
            <div>
              <SectionTitle>Risk Assessment for {name || "Patient"}</SectionTitle>

              <div style={{ display: "flex", justifyContent: "space-around", alignItems: "center", margin: "24px 0 32px" }}>
                <div style={{ textAlign: "center" }}>
                  <GaugeArc value={result.p1} label="Population Risk" color="#e67e22" />
                  <p style={{ color: "#666", fontSize: 11, fontFamily: "monospace", marginTop: 4 }}>Stage 1 · NHANES Model</p>
                </div>
                <div style={{ width: 1, height: 100, background: "#222" }} />
                <div style={{ textAlign: "center" }}>
                  <GaugeArc value={result.p2} label="Severity Risk" color="#c0392b" />
                  <p style={{ color: "#666", fontSize: 11, fontFamily: "monospace", marginTop: 4 }}>Stage 2 · Symptom Model</p>
                </div>
              </div>

              <div style={{ background: "#0a0a0a", border: `1px solid ${riskColor}44`, borderLeft: `4px solid ${riskColor}`, borderRadius: 10, padding: "20px 24px", marginBottom: 24 }}>
                <div style={{ display: "flex", alignItems: "baseline", gap: 16, marginBottom: 12 }}>
                  <span style={{ fontFamily: "'Crimson Text', Georgia, serif", fontSize: 48, fontWeight: 700, color: riskColor, lineHeight: 1 }}>
                    {Math.round(result.combined * 100)}%
                  </span>
                  <div>
                    <div style={{ color: riskColor, fontFamily: "monospace", fontSize: 13, letterSpacing: 2, textTransform: "uppercase" }}>{result.riskLevel} Risk</div>
                    <div style={{ color: "#666", fontFamily: "monospace", fontSize: 11 }}>Combined RA Risk Score</div>
                  </div>
                </div>
                <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                  <Tag color="#e67e22" label="Population screening" value={result.p1 >= STAGE1_THRESHOLD ? "Above threshold" : "Below threshold"} />
                  <Tag color="#c0392b" label="Symptom burden" value={result.severityLabel} />
                </div>
              </div>

              <div style={{ background: "#0f0f0f", border: "1px solid #1e1e1e", borderRadius: 8, padding: 20, marginBottom: 24 }}>
                <p style={{ color: "#888", fontSize: 11, letterSpacing: 2, fontFamily: "monospace", textTransform: "uppercase", marginBottom: 12 }}>Clinical Interpretation</p>
                <p style={{ color: "#d0c8c0", fontSize: 15, lineHeight: 1.7, fontFamily: "'Crimson Text', Georgia, serif", margin: 0 }}>
                  {result.riskLevel === "High"
                    ? `${name || "This patient"}'s demographic and lifestyle profile places them above the population screening threshold, and their self-reported symptom burden is classified as ${result.severityLabel.toLowerCase()}. Clinical evaluation including anti-CCP antibodies and rheumatologist referral is strongly recommended.`
                    : result.riskLevel === "Moderate"
                    ? `${name || "This patient"} shows a moderate combined risk profile. Their ${result.severityLabel.toLowerCase()} symptom burden warrants monitoring. Consider periodic reassessment and lifestyle modifications.`
                    : `${name || "This patient"}'s current profile reflects a low combined risk for Rheumatoid Arthritis with ${result.severityLabel.toLowerCase()} symptom burden. Maintain healthy lifestyle habits and reassess if symptoms change.`}
                </p>
              </div>

              <p style={{ color: "#444", fontSize: 11, fontFamily: "monospace", lineHeight: 1.6, borderTop: "1px solid #1a1a1a", paddingTop: 16, marginBottom: 0 }}>
                ⚠ Research and screening purposes only. Not medical advice.
              </p>

              <NavRow onBack={prevStep} onNext={nextStep} nextLabel="Check Flare Forecast →" />
            </div>
          )}

          {/* ── STEP 4: Flare Forecast ── */}
          {step === 4 && (
            <div>
              <SectionTitle>Flare Forecast for {name || "Patient"}</SectionTitle>

              {/* RA risk summary pill */}
              {result && (
                <div style={{ display: "flex", gap: 10, marginBottom: 28, flexWrap: "wrap" }}>
                  <Tag color={riskColor} label="RA Risk" value={`${Math.round(result.combined * 100)}% · ${result.riskLevel}`} />
                  <Tag color="#888" label="Symptom burden" value={result.severityLabel} />
                </div>
              )}

              {/* Location detection */}
              <div style={{ marginBottom: 24 }}>
                <p style={{ color: "#888", fontSize: 12, fontFamily: "monospace", letterSpacing: 1, marginBottom: 16, lineHeight: 1.7 }}>
                  We'll use your location to fetch live weather and predict flare risk for the next 24, 48, and 72 hours.
                  Weather changes — especially sudden pressure drops — are the primary trigger.
                </p>

                {/* IDLE: show detect button */}
                {(geoStatus === "idle" || geoStatus === "manual") && !confirmedLocation && (
                  <div>
                    <button onClick={detectLocation} disabled={geoStatus === "detecting"}
                      style={{ width: "100%", background: "#1a0a08", border: "1px solid #c0392b66", borderRadius: 8, padding: "14px", color: "#e8c5c0", fontFamily: "monospace", fontSize: 12, letterSpacing: 2, cursor: "pointer", textTransform: "uppercase", marginBottom: 16 }}>
                      📍 Detect My Location
                    </button>
                    <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 12 }}>
                      <div style={{ flex: 1, height: 1, background: "#222" }} />
                      <span style={{ color: "#444", fontFamily: "monospace", fontSize: 10, letterSpacing: 2 }}>OR ENTER MANUALLY</span>
                      <div style={{ flex: 1, height: 1, background: "#222" }} />
                    </div>
                    <div style={{ display: "flex", gap: 10 }}>
                      <div style={{ flex: 1 }}>
                        <Input value={manualCity} onChange={setManualCity} placeholder="e.g. Islamabad, London, Karachi" />
                      </div>
                      <button onClick={fetchFlareManual} disabled={flareLoading || !manualCity.trim()}
                        style={{ background: (flareLoading || !manualCity.trim()) ? "#1a1a1a" : "#c0392b", border: "none", borderRadius: 6, padding: "10px 18px", color: (flareLoading || !manualCity.trim()) ? "#444" : "#fff", fontFamily: "monospace", fontSize: 11, letterSpacing: 1, cursor: (flareLoading || !manualCity.trim()) ? "not-allowed" : "pointer", whiteSpace: "nowrap", textTransform: "uppercase" }}>
                        {flareLoading ? "..." : "Get Forecast"}
                      </button>
                    </div>
                  </div>
                )}

                {/* DETECTING: spinner */}
                {geoStatus === "detecting" && (
                  <div style={{ background: "#0f0f0f", border: "1px solid #333", borderRadius: 8, padding: "18px 20px", display: "flex", alignItems: "center", gap: 14 }}>
                    <div style={{ width: 18, height: 18, border: "2px solid #c0392b", borderTopColor: "transparent", borderRadius: "50%", animation: "spin 0.8s linear infinite", flexShrink: 0 }} />
                    <span style={{ color: "#888", fontFamily: "monospace", fontSize: 12, letterSpacing: 1 }}>Detecting your location...</span>
                  </div>
                )}

                {/* CONFIRMING: show detected location, ask to confirm */}
                {geoStatus === "confirming" && detectedLocation && (
                  <div style={{ background: "#0a0f0a", border: "1px solid #27ae6066", borderRadius: 8, padding: "18px 20px" }}>
                    <p style={{ color: "#888", fontFamily: "monospace", fontSize: 10, letterSpacing: 2, textTransform: "uppercase", margin: "0 0 10px" }}>Location Detected</p>
                    <p style={{ fontFamily: "'Crimson Text', Georgia, serif", fontSize: 22, color: "#e8c5c0", margin: "0 0 16px" }}>
                      📍 {detectedLocation.city}{detectedLocation.country ? `, ${detectedLocation.country}` : ""}
                    </p>
                    <p style={{ color: "#666", fontFamily: "monospace", fontSize: 11, margin: "0 0 18px" }}>
                      {detectedLocation.lat.toFixed(4)}°, {detectedLocation.lon.toFixed(4)}°
                    </p>
                    <div style={{ display: "flex", gap: 10 }}>
                      <button onClick={() => fetchFlareForLocation(detectedLocation)} disabled={flareLoading}
                        style={{ flex: 1, background: "#c0392b", border: "none", borderRadius: 6, padding: "11px", color: "#fff", fontFamily: "monospace", fontSize: 12, letterSpacing: 2, cursor: "pointer", textTransform: "uppercase" }}>
                        {flareLoading ? "Fetching..." : "✓ Yes, Get Forecast"}
                      </button>
                      <button onClick={() => { setGeoStatus("manual"); setDetectedLocation(null); }}
                        style={{ flex: 1, background: "transparent", border: "1px solid #333", borderRadius: 6, padding: "11px", color: "#888", fontFamily: "monospace", fontSize: 12, letterSpacing: 2, cursor: "pointer", textTransform: "uppercase" }}>
                        ✕ Wrong Location
                      </button>
                    </div>
                  </div>
                )}

                {/* CONFIRMED: show confirmed location with option to change */}
                {geoStatus === "confirmed" && confirmedLocation && !flareLoading && (
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", background: "#0f0f0f", border: "1px solid #27ae6033", borderRadius: 8, padding: "12px 16px" }}>
                    <span style={{ color: "#e8c5c0", fontFamily: "monospace", fontSize: 12 }}>
                      📍 {confirmedLocation.city}{confirmedLocation.country ? `, ${confirmedLocation.country}` : ""}
                    </span>
                    <button onClick={() => { setGeoStatus("idle"); setConfirmedLocation(null); setFlareData(null); }}
                      style={{ background: "transparent", border: "1px solid #333", borderRadius: 4, padding: "4px 10px", color: "#666", fontFamily: "monospace", fontSize: 10, letterSpacing: 1, cursor: "pointer", textTransform: "uppercase" }}>
                      Change
                    </button>
                  </div>
                )}

                {flareError && (
                  <div style={{ marginTop: 12, background: "#1a0a08", border: "1px solid #c0392b44", borderRadius: 6, padding: "10px 14px", color: "#e74c3c", fontFamily: "monospace", fontSize: 12 }}>
                    ✕ {flareError}
                  </div>
                )}
              </div>

              {/* Loading skeleton */}
              {flareLoading && (
                <div style={{ display: "flex", gap: 12 }}>
                  {["24hr", "48hr", "72hr"].map(h => (
                    <div key={h} style={{ flex: 1, height: 180, background: "#0f0f0f", border: "1px solid #222", borderRadius: 10, animation: "pulse 1.5s infinite" }} />
                  ))}
                </div>
              )}

              {/* Forecast results */}
              {flareData && !flareLoading && (
                <div>
                  {/* Location + trend banner */}
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
                    <div style={{ color: "#e8c5c0", fontFamily: "'Crimson Text', Georgia, serif", fontSize: 18 }}>
                      📍 {flareData.city}{flareData.country ? `, ${flareData.country}` : ""}
                    </div>
                    <div style={{ display: "flex", gap: 8 }}>
                      <Tag color="#888" label="48hr trend" value={flareData.trend_48hr} />
                      <Tag color="#888" label="72hr trend" value={flareData.trend_72hr} />
                    </div>
                  </div>

                  {/* Current + forecast cards */}
                  <div style={{ display: "flex", gap: 12, marginBottom: 20, flexWrap: "wrap" }}>
                    <FlareCard horizon="Now" data={flareData.current_weather} isCurrent={true} />
                    {["24hr", "48hr", "72hr"].map(h => (
                      <FlareCard key={h} horizon={h} data={flareData.forecast[h]} />
                    ))}
                  </div>

                  {/* Pressure drop warning */}
                  {["24hr", "48hr", "72hr"].some(h => flareData.forecast[h]?.above_threshold) && (
                    <div style={{ background: "#1a0808", border: "1px solid #c0392b55", borderRadius: 8, padding: "14px 18px", marginBottom: 20 }}>
                      <p style={{ color: "#e8c5c0", fontFamily: "'Crimson Text', Georgia, serif", fontSize: 16, margin: "0 0 6px" }}>
                        ⚠ Flare risk detected in the forecast window
                      </p>
                      <p style={{ color: "#888", fontFamily: "monospace", fontSize: 11, margin: 0, lineHeight: 1.7 }}>
                        Consider resting, staying warm, and avoiding strenuous activity during high-risk periods.
                        Consult your rheumatologist if symptoms worsen. This prediction is based on weather patterns
                        and your current symptom burden — not a clinical diagnosis.
                      </p>
                    </div>
                  )}

                  {/* Model footnote */}
                  <p style={{ color: "#333", fontSize: 10, fontFamily: "monospace", lineHeight: 1.6 }}>
                    Stage-3 model · n=30 RA patients · LOOCV AUC {flareData.model_auc ?? "0.946"} · Threshold {flareData.threshold_used ?? "0.5"}
                  </p>
                </div>
              )}

              {/* Nav */}
              <div style={{ display: "flex", justifyContent: "space-between", marginTop: 28 }}>
                <button onClick={prevStep} style={{ background: "transparent", border: "1px solid #333", borderRadius: 6, padding: "10px 20px", color: "#888", fontFamily: "monospace", fontSize: 12, letterSpacing: 2, cursor: "pointer", textTransform: "uppercase" }}>
                  ← Back
                </button>
                <div style={{ display: "flex", gap: 10 }}>
                  {flareData && (
                    <button onClick={nextStep}
                      style={{ background: "#c0392b", border: "none", borderRadius: 6, padding: "10px 20px", color: "#fff", fontFamily: "monospace", fontSize: 12, letterSpacing: 2, cursor: "pointer", textTransform: "uppercase" }}>
                      Joint Report →
                    </button>
                  )}
                  <button onClick={resetAll} style={{ background: "transparent", border: "1px solid #333", borderRadius: 6, padding: "10px 20px", color: "#888", fontFamily: "monospace", fontSize: 12, letterSpacing: 2, cursor: "pointer", textTransform: "uppercase" }}>
                    New Assessment
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* ── STEP 5: Joint ROM Input + Report ── */}
          {step === 5 && (
            <div>
              <SectionTitle>Joint Flare Report for {name || "Patient"}</SectionTitle>

              {/* summary pills */}
              {result && flareData && (
                <div style={{ display: "flex", gap: 8, marginBottom: 24, flexWrap: "wrap" }}>
                  <Tag color={riskColor} label="RA Risk" value={`${Math.round(result.combined * 100)}% · ${result.riskLevel}`} />
                  <Tag color="#888" label="Location" value={`${flareData.city ?? ""}${flareData.country ? ", " + flareData.country : ""}`} />
                  <Tag color="#888" label="48hr weather flare" value={`${Math.round(flareData.forecast["48hr"].flare_prob * 100)}%`} />
                </div>
              )}

              {/* ROM input section */}
              <div style={{ background: "#0f0f0f", border: "1px solid #1e1e1e", borderRadius: 10, padding: "20px 24px", marginBottom: 28 }}>
                <p style={{ color: "#888", fontSize: 11, letterSpacing: 2, fontFamily: "monospace", textTransform: "uppercase", margin: "0 0 6px" }}>Step 1 — Enter ROM-based Joint Probabilities</p>
                <p style={{ color: "#555", fontSize: 12, fontFamily: "monospace", margin: "0 0 20px", lineHeight: 1.6 }}>
                  Enter a value between 0 and 1 for each joint (from your ROM assessment). Leave blank to exclude a joint.
                </p>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 14 }}>
                  {JOINTS.map(j => (
                    <div key={j}>
                      <label style={{ display: "block", color: "#e8c5c0", fontSize: 11, letterSpacing: 2, fontFamily: "monospace", marginBottom: 6, textTransform: "uppercase" }}>{j}</label>
                      <input
                        type="number" min={0} max={1} step={0.01}
                        value={jointProbs[j]}
                        onChange={e => setJP(j)(e.target.value)}
                        placeholder="0.00 – 1.00"
                        style={{ ...inputStyle, fontSize: 13 }}
                      />
                    </div>
                  ))}
                </div>
              </div>

              {/* Only show report if at least one joint entered */}
              {JOINTS.some(j => parseFloat(jointProbs[j]) > 0) && flareData && (
                <JointReport jointReport={jointReport} flareData={flareData} />
              )}

              <div style={{ display: "flex", justifyContent: "space-between", marginTop: 28 }}>
                <button onClick={prevStep} style={{ background: "transparent", border: "1px solid #333", borderRadius: 6, padding: "10px 20px", color: "#888", fontFamily: "monospace", fontSize: 12, letterSpacing: 2, cursor: "pointer", textTransform: "uppercase" }}>← Back</button>
                <button onClick={resetAll} style={{ background: "transparent", border: "1px solid #333", borderRadius: 6, padding: "10px 20px", color: "#888", fontFamily: "monospace", fontSize: 12, letterSpacing: 2, cursor: "pointer", textTransform: "uppercase" }}>New Assessment</button>
              </div>
            </div>
          )}

          {/* ── STEP 5: Joint ROM Input + Report ── */}
          {step === 5 && (
            <div>
              <SectionTitle>Joint Flare Report for {name || "Patient"}</SectionTitle>

              {/* summary pills */}
              {result && flareData && (
                <div style={{ display: "flex", gap: 8, marginBottom: 24, flexWrap: "wrap" }}>
                  <Tag color={riskColor} label="RA Risk" value={`${Math.round(result.combined * 100)}% · ${result.riskLevel}`} />
                  <Tag color="#888" label="Location" value={`${flareData.city ?? ""}${flareData.country ? ", " + flareData.country : ""}`} />
                  <Tag color="#888" label="48hr weather flare" value={`${Math.round(flareData.forecast["48hr"].flare_prob * 100)}%`} />
                </div>
              )}

              {/* ROM input */}
              <div style={{ background: "#0f0f0f", border: "1px solid #1e1e1e", borderRadius: 10, padding: "20px 24px", marginBottom: 28 }}>
                <p style={{ color: "#888", fontSize: 11, letterSpacing: 2, fontFamily: "monospace", textTransform: "uppercase", margin: "0 0 6px" }}>Enter ROM-based Joint Probabilities</p>
                <p style={{ color: "#555", fontSize: 12, fontFamily: "monospace", margin: "0 0 20px", lineHeight: 1.6 }}>
                  Enter a value between 0 and 1 for each joint from your ROM assessment. Leave blank to exclude.
                </p>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 14 }}>
                  {JOINTS.map(j => (
                    <div key={j}>
                      <label style={{ display: "block", color: "#e8c5c0", fontSize: 11, letterSpacing: 2, fontFamily: "monospace", marginBottom: 6, textTransform: "uppercase" }}>{j}</label>
                      <input
                        type="number" min={0} max={1} step={0.01}
                        value={jointProbs[j]}
                        onChange={e => setJP(j)(e.target.value)}
                        placeholder="0.00 – 1.00"
                        style={{ ...inputStyle, fontSize: 13 }}
                      />
                    </div>
                  ))}
                </div>
              </div>

              {JOINTS.some(j => parseFloat(jointProbs[j]) > 0) && flareData && (
                <JointReport jointReport={jointReport} flareData={flareData} />
              )}

              <div style={{ display: "flex", justifyContent: "space-between", marginTop: 28 }}>
                <button onClick={prevStep} style={{ background: "transparent", border: "1px solid #333", borderRadius: 6, padding: "10px 20px", color: "#888", fontFamily: "monospace", fontSize: 12, letterSpacing: 2, cursor: "pointer", textTransform: "uppercase" }}>← Back</button>
                <button onClick={resetAll} style={{ background: "transparent", border: "1px solid #333", borderRadius: 6, padding: "10px 20px", color: "#888", fontFamily: "monospace", fontSize: 12, letterSpacing: 2, cursor: "pointer", textTransform: "uppercase" }}>New Assessment</button>
              </div>
            </div>
          )}

        </div>

        <p style={{ textAlign: "center", color: "#2a2a2a", fontSize: 11, fontFamily: "monospace", marginTop: 24 }}>
          NHANES LR · SMOTEENN · STAGE-2 AUC 0.946 · STAGE-3 WEATHER FLARE MODEL
        </p>
      </div>
    </div>
  );
}

// ─── Joint recommendations lookup ────────────────────────────────────────
const JOINT_RECS = {
  hand: {
    high:     ["Rest hands completely — avoid gripping or pinching", "Apply warm compress for 15 min before activity", "Use adaptive utensils and jar openers", "Consider resting splint at night"],
    moderate: ["Limit repetitive hand movements", "Take frequent breaks during fine motor tasks", "Gentle finger range-of-motion exercises"],
    low:      ["Maintain gentle hand exercises", "Avoid sustained gripping for extended periods"],
  },
  wrist: {
    high:     ["Wear wrist splint to stabilise the joint", "Avoid weight-bearing on wrists (push-ups, planks)", "Apply cold compress if swollen, warm if stiff", "Elevate hands when resting"],
    moderate: ["Reduce keyboard/mouse usage; use wrist rest", "Avoid lifting heavy objects", "Gentle wrist circles 2–3× daily"],
    low:      ["Continue normal activity with awareness", "Brief wrist stretch every hour at desk"],
  },
  elbow: {
    high:     ["Avoid carrying heavy loads", "Use elbow pad for protection", "Do not fully extend or lock elbow under load", "Apply warm compress before movement"],
    moderate: ["Reduce pulling and pushing activities", "Avoid overhead reaching with heavy objects"],
    low:      ["Gentle elbow flexion/extension exercises", "Monitor for stiffness after prolonged rest"],
  },
  shoulder: {
    high:     ["Avoid overhead activities entirely", "Use a sling if pain is significant", "Sleep with a pillow under the affected arm", "Apply heat before gentle pendulum exercises"],
    moderate: ["Limit overhead reaching and lifting above shoulder height", "Perform gentle pendulum swings twice daily"],
    low:      ["Maintain shoulder mobility with gentle circles", "Avoid sleeping on the affected side"],
  },
  knee: {
    high:     ["Avoid stairs where possible — use elevator", "Use a knee brace for support during walking", "Apply ice after activity (15 min on, 15 off)", "Rest with leg elevated above heart level"],
    moderate: ["Reduce walking distance; avoid prolonged standing", "Use walking aid if needed", "Straight-leg raises to maintain quad strength without joint load"],
    low:      ["Maintain gentle low-impact activity (swimming, cycling)", "Avoid kneeling on hard surfaces"],
  },
  ankle: {
    high:     ["Minimise weight-bearing — use crutches or cane if needed", "Wear supportive footwear at all times", "Apply ice 15 min after any walking", "Elevate foot when seated"],
    moderate: ["Avoid uneven terrain and stairs where possible", "Wear cushioned shoes with good ankle support"],
    low:      ["Ankle circles and calf raises to maintain mobility", "Wear supportive footwear during exercise"],
  },
};

function getRecs(joint, prob) {
  const r = JOINT_RECS[joint];
  if (!r) return [];
  if (prob >= 0.6) return r.high;
  if (prob >= 0.3) return r.moderate;
  return r.low;
}

function riskColor(p) {
  return p >= 0.6 ? "#c0392b" : p >= 0.3 ? "#e67e22" : "#27ae60";
}
function riskLabel(p) {
  return p >= 0.6 ? "High" : p >= 0.3 ? "Moderate" : "Low";
}

// ─── Body Diagram SVG ────────────────────────────────────────────────────
// Joints mapped to (cx,cy) on a 120×260 coordinate space
const JOINT_COORDS = {
  shoulder: [{ cx: 28, cy: 72 }, { cx: 92, cy: 72 }],
  elbow:    [{ cx: 18, cy: 112 }, { cx: 102, cy: 112 }],
  wrist:    [{ cx: 12, cy: 148 }, { cx: 108, cy: 148 }],
  hand:     [{ cx: 10, cy: 168 }, { cx: 110, cy: 168 }],
  knee:     [{ cx: 42, cy: 196 }, { cx: 78, cy: 196 }],
  ankle:    [{ cx: 40, cy: 238 }, { cx: 80, cy: 238 }],
};

function BodyDiagram({ jointReport }) {
  const probMap = Object.fromEntries(jointReport.map(r => [r.joint, r["48hr"]]));
  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center" }}>
      <p style={{ color: "#555", fontSize: 10, fontFamily: "monospace", letterSpacing: 2, textTransform: "uppercase", marginBottom: 8 }}>48hr Risk · Body Map</p>
      <svg viewBox="0 0 120 270" style={{ width: 140, height: 310 }}>
        {/* Body outline — simple stick figure */}
        {/* Head */}
        <circle cx="60" cy="22" r="14" fill="none" stroke="#2a2a2a" strokeWidth="2" />
        {/* Neck + torso */}
        <line x1="60" y1="36" x2="60" y2="170" stroke="#2a2a2a" strokeWidth="2" />
        {/* Shoulder line */}
        <line x1="28" y1="72" x2="92" y2="72" stroke="#2a2a2a" strokeWidth="2" />
        {/* Left arm */}
        <line x1="28" y1="72" x2="18" y2="112" stroke="#2a2a2a" strokeWidth="2" />
        <line x1="18" y1="112" x2="12" y2="148" stroke="#2a2a2a" strokeWidth="2" />
        <line x1="12" y1="148" x2="10" y2="168" stroke="#2a2a2a" strokeWidth="2" />
        {/* Right arm */}
        <line x1="92" y1="72" x2="102" y2="112" stroke="#2a2a2a" strokeWidth="2" />
        <line x1="102" y1="112" x2="108" y2="148" stroke="#2a2a2a" strokeWidth="2" />
        <line x1="108" y1="148" x2="110" y2="168" stroke="#2a2a2a" strokeWidth="2" />
        {/* Hip line */}
        <line x1="42" y1="170" x2="78" y2="170" stroke="#2a2a2a" strokeWidth="2" />
        {/* Left leg */}
        <line x1="42" y1="170" x2="42" y2="196" stroke="#2a2a2a" strokeWidth="2" />
        <line x1="42" y1="196" x2="40" y2="238" stroke="#2a2a2a" strokeWidth="2" />
        {/* Right leg */}
        <line x1="78" y1="170" x2="78" y2="196" stroke="#2a2a2a" strokeWidth="2" />
        <line x1="78" y1="196" x2="80" y2="238" stroke="#2a2a2a" strokeWidth="2" />
        {/* Feet */}
        <line x1="40" y1="238" x2="32" y2="248" stroke="#2a2a2a" strokeWidth="2" />
        <line x1="80" y1="238" x2="88" y2="248" stroke="#2a2a2a" strokeWidth="2" />

        {/* Joint dots */}
        {Object.entries(JOINT_COORDS).map(([joint, pts]) =>
          pts.map((pt, i) => {
            const p = probMap[joint];
            if (p === undefined) return null;
            const c = riskColor(p);
            return (
              <g key={`${joint}-${i}`}>
                <circle cx={pt.cx} cy={pt.cy} r={7} fill={c} fillOpacity={0.25} stroke={c} strokeWidth={2} />
                <circle cx={pt.cx} cy={pt.cy} r={3} fill={c} />
              </g>
            );
          })
        )}
      </svg>
      {/* Legend */}
      <div style={{ display: "flex", gap: 12, marginTop: 8 }}>
        {[["#27ae60","Low"],["#e67e22","Moderate"],["#c0392b","High"]].map(([c,l]) => (
          <div key={l} style={{ display: "flex", alignItems: "center", gap: 4 }}>
            <div style={{ width: 8, height: 8, borderRadius: "50%", background: c }} />
            <span style={{ color: "#555", fontFamily: "monospace", fontSize: 9, letterSpacing: 1 }}>{l}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── Joint Card ───────────────────────────────────────────────────────────
function JointCard({ row }) {
  const worst = Math.max(row["24hr"], row["48hr"], row["72hr"]);
  const c = riskColor(worst);
  const recs = getRecs(row.joint, worst);
  const [open, setOpen] = React.useState(false);

  return (
    <div style={{ background: "#0f0f0f", border: `1px solid ${c}33`, borderTop: `3px solid ${c}`, borderRadius: 10, padding: "16px 18px" }}>
      {/* Header */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 12 }}>
        <div>
          <div style={{ color: c, fontFamily: "monospace", fontSize: 10, letterSpacing: 2, textTransform: "uppercase", marginBottom: 2 }}>{riskLabel(worst)} Risk</div>
          <div style={{ color: "#f0e6e0", fontFamily: "'Crimson Text', Georgia, serif", fontSize: 20, fontWeight: 600, textTransform: "capitalize" }}>{row.joint}</div>
          <div style={{ color: "#666", fontFamily: "monospace", fontSize: 10, marginTop: 2 }}>ROM prob: {row.jp.toFixed(2)}</div>
        </div>
        <div style={{ textAlign: "right" }}>
          <div style={{ color: c, fontFamily: "'Crimson Text', Georgia, serif", fontSize: 28, fontWeight: 700, lineHeight: 1 }}>{Math.round(worst * 100)}%</div>
          <div style={{ color: "#555", fontFamily: "monospace", fontSize: 9 }}>peak risk</div>
        </div>
      </div>

      {/* 24/48/72 mini bars */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8, marginBottom: 12 }}>
        {["24hr","48hr","72hr"].map(h => {
          const v = row[h];
          const hc = riskColor(v);
          return (
            <div key={h} style={{ textAlign: "center" }}>
              <div style={{ color: "#555", fontFamily: "monospace", fontSize: 9, letterSpacing: 1, marginBottom: 4 }}>{h}</div>
              <div style={{ height: 4, background: "#1a1a1a", borderRadius: 2, marginBottom: 4 }}>
                <div style={{ height: "100%", width: `${Math.round(v*100)}%`, background: hc, borderRadius: 2 }} />
              </div>
              <div style={{ color: hc, fontFamily: "monospace", fontSize: 11, fontWeight: 700 }}>{Math.round(v * 100)}%</div>
            </div>
          );
        })}
      </div>

      {/* Recommendations toggle */}
      <button onClick={() => setOpen(o => !o)}
        style={{ width: "100%", background: `${c}10`, border: `1px solid ${c}33`, borderRadius: 6, padding: "7px 10px", color: c, fontFamily: "monospace", fontSize: 10, letterSpacing: 1, cursor: "pointer", textTransform: "uppercase", textAlign: "left" }}>
        {open ? "▲ Hide" : "▼ Show"} Recommendations ({recs.length})
      </button>
      {open && (
        <ul style={{ margin: "10px 0 0", paddingLeft: 18 }}>
          {recs.map((r, i) => (
            <li key={i} style={{ color: "#c8bdb0", fontFamily: "'Crimson Text', Georgia, serif", fontSize: 14, lineHeight: 1.7, marginBottom: 4 }}>{r}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

// ─── Joint Report (body diagram + cards) ─────────────────────────────────
function JointReport({ jointReport, flareData }) {
  const active = jointReport.filter(r => r.jp > 0);
  if (!active.length) return null;

  return (
    <div>
      <div style={{ display: "flex", gap: 28, alignItems: "flex-start", marginBottom: 28 }}>
        {/* Body diagram */}
        <div style={{ flexShrink: 0 }}>
          <BodyDiagram jointReport={active} />
        </div>

        {/* Right: worst joint summary */}
        <div style={{ flex: 1 }}>
          <p style={{ color: "#888", fontSize: 11, fontFamily: "monospace", letterSpacing: 2, textTransform: "uppercase", marginBottom: 14 }}>48hr Risk Summary</p>
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {[...active].sort((a,b) => b["48hr"] - a["48hr"]).map(r => (
              <div key={r.joint} style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <span style={{ color: "#888", fontFamily: "monospace", fontSize: 11, width: 72, textTransform: "capitalize" }}>{r.joint}</span>
                <div style={{ flex: 1, height: 6, background: "#1a1a1a", borderRadius: 3 }}>
                  <div style={{ height: "100%", width: `${Math.round(r["48hr"]*100)}%`, background: riskColor(r["48hr"]), borderRadius: 3, transition: "width 0.5s ease" }} />
                </div>
                <span style={{ color: riskColor(r["48hr"]), fontFamily: "monospace", fontSize: 11, fontWeight: 700, width: 36, textAlign: "right" }}>{Math.round(r["48hr"]*100)}%</span>
              </div>
            ))}
          </div>

          {/* Weather context note */}
          <div style={{ marginTop: 18, background: "#0a0a0a", border: "1px solid #1e1e1e", borderRadius: 6, padding: "10px 14px" }}>
            <p style={{ color: "#555", fontFamily: "monospace", fontSize: 10, margin: 0, lineHeight: 1.7 }}>
              Joint risk = ROM probability × weather flare probability per window.<br/>
              Weather: {flareData.forecast["48hr"].weather?.description ?? "—"} · {flareData.forecast["48hr"].weather?.pressure_hPa ?? "—"} hPa · {flareData.forecast["48hr"].weather?.humidity ?? "—"}% humidity
            </p>
          </div>
        </div>
      </div>

      {/* Per-joint cards */}
      <p style={{ color: "#888", fontSize: 11, fontFamily: "monospace", letterSpacing: 2, textTransform: "uppercase", marginBottom: 14 }}>Per-Joint Forecast & Recommendations</p>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
        {active.map(r => <JointCard key={r.joint} row={r} />)}
      </div>

      <p style={{ color: "#333", fontSize: 10, fontFamily: "monospace", marginTop: 20, lineHeight: 1.6 }}>
        ⚠ For research purposes only. Not a substitute for clinical evaluation.
      </p>
    </div>
  );
}