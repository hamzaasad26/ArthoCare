# ArthoCare / KotlinProject — AI Assistant Context Document

> **Purpose:** Onboard an AI or developer with zero prior context on this codebase.  
> **Repo root:** `KotlinProject/` (KMP app + Python ML API + Supabase SQL).  
> **Package namespace (Android):** `org.example.project` (default template naming; not yet rebranded in Gradle `applicationId`).

---

## 1. Project Overview

**ArthoCare** is a **patient-facing health companion** built as **Kotlin Multiplatform + Compose Multiplatform**, targeting **Android** and **iOS**. It supports **symptom / weekly logging**, **RA-focused predictions and insights**, **weather-aware guidance**, **reminders**, **educational and lifestyle content**, and **RA Lens** — a **range-of-motion (ROM)** capture and analysis flow that can use **phone media** or a **desktop OpenCV analyzer** driven from a **local FastAPI** service.

**Who uses it:** People living with **rheumatoid arthritis (RA)** (and related musculoskeletal tracking) and their care workflows in a **university FYP / demo** context.  
**Why:** Centralize self-reported data, model-assisted risk/interpretation, and **ROM snapshots** to support longitudinal understanding (dashboard, insights, RA predictions, RA Lens).

---

## 2. Tech Stack

### Mobile app (`composeApp`)

| Layer | Technology | Version / notes |
|-------|----------------|-----------------|
| Language | Kotlin | **2.2.20** (`libs.versions.toml`); compose compiler plugin pinned to **2.0.21** in `composeApp/build.gradle.kts` (intentionally check alignment) |
| UI | Jetpack Compose + **Compose Multiplatform** | CMP **1.9.0**; Material 3, Foundation, Material Icons Extended |
| Android Gradle Plugin | AGP | **8.12.3** |
| Android SDK | compile / target / min | **36 / 36 / 24** |
| JVM (Android) | | **11** |
| Networking | Ktor Client | **2.3.7** (core, content-negotiation, kotlinx-json on `commonMain`; Android engine on `androidMain`) |
| Serialization | kotlinx.serialization JSON | **1.6.0** |
| Coroutines | kotlinx-coroutines | **1.7.3** |
| Date/time | kotlinx-datetime | **0.6.1** |
| Lifecycle (Compose MPP) | `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` | **2.9.4** (declared; **ViewModels are not widely used** in RA Lens — most screens use `remember { mutableStateOf }`) |
| Resources | Compose Multiplatform Resources | generated `Res` / `DrawableResource` |
| Camera (Android RA Lens) | AndroidX CameraX | **1.3.4** (core, camera2, lifecycle, view) |
| Testing | kotlin-test | Via `commonTest` / `libs.kotlin.test` |

### iOS

- **ComposeApp** static framework (`iosArm64`, `iosSimulatorArm64`), `baseName = "ComposeApp"`.
- Entry via **`iosApp/`** Xcode project (required even when UI is shared).

### Backend (`ml_backend/`)

| Component | Technology | Version |
|-----------|------------|---------|
| Runtime | Python | 3.x (project uses venv; see `ml_backend/.venv` in dev) |
| API | FastAPI | **0.116.1** |
| Server | Uvicorn | **0.35.0** |
| ML / data | scikit-learn, joblib, numpy, pandas | **1.6.1**, **1.5.1**, **2.1.3**, **2.2.3** |
| Models | Pydantic | **2.11.7** |
| Uploads | python-multipart | **0.0.20** |

**Note:** RA Lens **desktop** scripts live under `ml_backend/RAlens/` (interactive OpenCV analyzers per joint). Windows spawns **new console** per subprocess for visibility.

### Database / backend-as-a-service

- **Supabase** (Postgres + REST): URL and anon key are in **`composeApp/.../supabase_config.kt`** (treat as **sensitive**; rotate for production). SQL migrations under **`supabase/migrations/`** (e.g. health timeline).

### Infra / tooling

- **Gradle** (wrapper-driven), **configuration cache** feature preview in `settings.gradle.kts` (`TYPESAFE_PROJECT_ACCESSORS`).
- **No** Docker compose or CI config in repo root from this snapshot (verify if added later).

---

## 3. Project Structure

```
KotlinProject/
├── composeApp/                    # Single KMP module (Android + iOS shared UI)
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/org/example/project/   # ~80+ Kotlin files: screens, stores, API, RA Lens
│       ├── androidMain/...        # Android actuals (CameraX RA Lens camera, capture flags)
│       ├── iosMain/...            # iOS actuals (camera stub, time, debug paths)
│       └── commonTest/...
├── iosApp/                        # Xcode wrapper / native entry
├── ml_backend/                    # FastAPI app (`app.py`), models, RA Lens Python scripts, logs
│   ├── app.py                     # /predict/*, /ralens/analyze-media, /ralens/desktop/start|status
│   ├── requirements.txt
│   └── RAlens/                    # OpenCV ROM analyzers + outputs
├── supabase/migrations/           # SQL for health timeline etc.
├── gradle/libs.versions.toml      # Centralized versions
├── settings.gradle.kts            # include(":composeApp") only
├── build.gradle.kts               # Root plugin aliases (apply false)
└── README.md                      # KMP basics + uvicorn one-liner for ml_backend
```

### Non-obvious architecture decisions

1. **Monolith `commonMain` package:** Almost everything is under **`org.example.project`** (flat + `screens/`, `raLens/` subpackages) — not feature modules.
2. **Dual “stores” pattern:** In-memory singleton-style objects (e.g. `WeeklyLogStore`, `RaLensStore`, `PredictionStore`, `SymptomLogStore`) + **Supabase REST** via `SupabaseClient` / `SupabaseHealthRest` and **`HealthTimelineHydrator`** after login.
3. **ML is split:** **Supabase** holds patient timeline data; **FastAPI** on the laptop holds **sklearn joblib** classifiers and **RA Lens** desktop orchestration. The app talks to FastAPI via **`MlApiService`** (Ktor), not through Supabase.
4. **RA Lens desktop vs mobile:** Controlled by **`expect fun raLensUsesInlineMobileCapture(): Boolean`** — currently **`false`** on Android and iOS actuals → **desktop analyzer path** (POST `/ralens/desktop/start`, poll `/ralens/desktop/status/{run_id}`).
5. **Legacy UI:** `FeatureScreens.kt` contains **`LegacyRaLensScreen`** (private / unused in `AppNavigation`); live RA Lens is **`RaLensScreen.kt`**.
6. **`PoseGuidanceScene` exists on disk** but **`RaLensStep`** no longer includes a pose step — guidance is **not** in the active navigation graph until re-wired.

---

## 4. Key Concepts & Domain Logic

### Health & RA vocabulary

- **ROM (range of motion):** Joint movement extent; deficits vs baselines feed **burden** and **joint scores**.
- **RA Lens:** Client flow: joint selection → capture (mobile JPEG **or** desktop OpenCV session) → processing → results. Results merge into **`RaLensStore`** and can sync to timeline (`ra_lens_session_repository`, `RomInterpretedSessionsSupabaseSync`, etc.).
- **Flare / predictions:** Stage predictors and **overall** prediction endpoints consumed from **`RAPredictionScreen`** and related analytics cards.
- **Weekly log / symptoms:** Structured logging feeding dashboard and intelligence copy via **`HealthIntelligence`**.

### Core abstractions (files to read first)

| Concept | Where |
|---------|--------|
| App navigation & `Screen` enum | `App.kt` (`AppNavigation`, `Crossfade`) |
| Auth + profile | `auth_service.kt`, `SupabaseClient`, `supabase_health_rest.kt` |
| ML HTTP API | `ml_api_service.kt` (`MlApiService` object, `Result<T>`, **`apiCall` must rethrow `CancellationException`**) |
| RA Lens UI state machine | `raLens/RaLensScreen.kt` + `RaLensStep.kt` + `raLens/scenes/*` |
| ROM interpretation / longitudinal | `rom_universal_interpreter.kt`, `InterpretedRomLocalHistory`, `RomInsightsRepository`, `rom_dummy_longitudinal.kt` (synthetic timelines for demos) |
| Dashboard insight derivation | `DashboardInsightDerivation.kt`, `DashboardScreen.kt`, `MainHomeScreen.kt` |

### RA Lens processing kinds (private enum in `RaLensScreen`)

- **`DESKTOP_WEBCAM`:** `startRaLensDesktopAnalyzer` + polling loop.  
- **`MOBILE_CAPTURE`:** `analyzeRaLensMedia` multipart JPEG.  
Server-side **`/ralens/analyze-media`** may use a **conservative placeholder estimate** for ROM when real headless analyzer is not wired (see comments in `ml_backend/app.py`).

### Desktop duplicate-start mitigation (recent)

- **Client:** Separate `LaunchedEffect` keys for desktop vs mobile so **`captureBytes` does not restart** desktop POST.  
- **Server:** `start_ralens_desktop` uses a **lock** + reuse of an existing **RUNNING** run with the same **(joints tuple, patient_id)** fingerprint to avoid double `Popen`/terminals.

---

## 5. Current State

### Working (typical demo path)

- **Auth:** Sign-up / login against Supabase-backed user + profile tables (see `auth_service.kt`).
- **Navigation:** Tabbed home (`MainHomeScreen`), deep links to feature screens via `Screen` enum in `App.kt`.
- **Dashboard / Insights / RA Predictions:** Rich UI with charts, cards, and store-driven data (plus dummy longitudinal ROM where enabled).
- **RA Lens (happy path):** Joint selection → capture step → auto desktop session (when flag false) → processing spinner → results cards; **`MlApiService`** talks to **`http://10.0.2.2:8000`** on emulator.
- **Android RA Lens camera:** Live preview + capture when `raLensUsesInlineMobileCapture() == true` (not default).

### In progress / partial

- **iOS RA Lens camera:** Stub UI text in `ra_lens_camera.ios.kt` — not full parity with Android.
- **Motion / video guides:** `PoseGuidanceScene` + `PoseGuide.kt` data exist; **not** hooked into `RaLensScreen` flow (planned: wrist flexion/extension/radial/ulnar videos).
- **Production hardening:** Secrets in source (`supabase_config.kt`), default ML base URL, `applicationId` still `org.example.project`.

### Broken / missing / sharp edges

- **Physical device → ML backend:** Must set **`MlApiService.baseUrlOverride`** to LAN IP (documented in `ml_api_service.kt` connection error hint).
- **Gradle JVM:** AGP **8.12.3** requires **JDK 11+**; builds fail on **JDK 8** hosts.
- **No shared KMP video player** in dependencies (ExoPlayer/Coil not declared) — any video work will need **`expect`/`actual`** or new deps.
- **Legacy RA Lens screen** in `FeatureScreens.kt` should not be confused with the live flow.

---

## 6. Active Problems / What We’re Working On Right Now

> *Update this section per sprint; below reflects recent repo focus.*

1. **RA Lens reliability & lifecycle**  
   - Fixed: **`CancellationException` swallowed** in `MlApiService.apiCall` (caused state updates after leaving composition).  
   - Fixed: **Duplicate `/ralens/desktop/start`** (Compose effect keys + server-side dedupe).  
   - Fixed (Android camera): **post-dispose** camera listener / `LaunchedEffect` capture guard in `ra_lens_camera.android.kt`.

2. **RA Lens motion guidance (next)**  
   - Add **video-based** motion guides for **wrist** (flexion, extension, radial/ulnar) into placeholder UI.  
   - **Constraint:** UI/placement only — do **not** change ROM scoring, backend scoring rules, Supabase schema, analytics pipelines, or ML training without explicit approval.

3. **Optional / unknown without runtime**  
   - iOS parity for camera + any new video actuals.  
   - Emulator vs physical networking to `ml_backend`.

---

## 7. Conventions & Patterns

### Naming

- **Screens:** `*Screen.kt` composables (`DashboardScreen`, `RaLensScreen`).  
- **Stores:** `*_store.kt` singleton objects (`RaLensStore`, `WeeklyLogStore`).  
- **Repositories:** `*_repository.kt`.  
- **RA Lens subfolder:** `raLens/` for UI + `RaLensStep`; platform bits `ra_lens_*.kt` (snake_case) with **`expect`/`actual`**.

### State management

- **Primary:** `remember { mutableStateOf(...) }` in composables + **`LaunchedEffect`** for async (RA Lens uses **multiple** keyed effects — be careful adding keys that restart network work).  
- **Global / session:** Object singletons (`AuthService`, stores).  
- **ViewModel:** Library on classpath; **not** the dominant pattern in RA Lens today.

### API calls

- **`MlApiService`:** Static object, **`suspend`** functions returning **`Result<T>`**.  
- **`apiCall`:** Wraps non-cancellation errors in `Result.failure`; **rethrows `kotlinx.coroutines.CancellationException`** so Compose cancellation works.

### UI organization

- **Theming:** `AppTheme`, `AppDark` palette; many scaffolds use **transparent** container to show gradient from root `Surface` in `AppNavigation`.  
- **Shared chrome:** `FeatureTopAppBar` in `FeatureScreens.kt`.  
- **Feature entry:** `AppNavigation` `when (screen)` branches.

### Lint / format

- No project-specific **detekt/ktlint** config observed in root snapshot; rely on IDE defaults.

---

## 8. External Dependencies & Integrations

| Integration | Role | Client entry |
|-------------|------|----------------|
| **Supabase** | Auth-ish user rows, profiles, REST timeline tables | `SupabaseConfig`, `SupabaseClient`, `SupabaseHealthRest`, `AuthService.attachSupabaseAccessToken` |
| **FastAPI (`ml_backend`)** | `/predict/stage1`, `/predict/stage3`, `/predict/overall`, RA Lens `/ralens/*` | `MlApiService` → `http://10.0.2.2:8000` (emulator loopback to host) |
| **AndroidX CameraX** | RA Lens live capture | `ra_lens_camera.android.kt` |
| **OpenCV Python scripts** | Desktop ROM capture (per joint) | Spawned by `ml_backend/app.py` `_run_desktop_batch` |

**REST shape:** Ktor `post`/`get` with JSON bodies or **multipart** for `analyzeRaLensMedia`.

---

## 9. Environment & Setup

### Android app

```text
.\gradlew.bat :composeApp:assembleDebug
```
(macOS/Linux: `./gradlew :composeApp:assembleDebug`)

- **JDK:** 11 or newer (AGP requirement).  
- **Android Studio / Cursor:** Open repo root; run configuration on `composeApp`.

### iOS app

- Open **`iosApp/`** in Xcode per `README.md`, or IDE run config for KMP iOS target.

### ML backend

From `README.md` (adjust path):

```bash
cd ml_backend
python -m uvicorn app:app --host 0.0.0.0 --port 8000 --reload
```

- Install deps: **`pip install -r ml_backend/requirements.txt`** (prefer venv).  
- **Desktop RA Lens:** scripts under `ml_backend/RAlens/`; Windows may open **new console** per analyzer process.

### Key configuration names (do not commit real secrets in new files)

| Name / location | Purpose |
|-----------------|--------|
| `SupabaseConfig.SUPABASE_URL` | Supabase project URL |
| `SupabaseConfig.SUPABASE_KEY` | Supabase anon (or similar) API key |
| `MlApiService.baseUrlOverride` | Override ML host (physical phone → laptop LAN IP) |
| `ARTHOCARE_*` env vars | Optional paths for models/logs in `ml_backend/app.py` (model dir, log dir, uploads) |

### Gotchas

- **10.0.2.2** only works from **Android emulator** to host machine `localhost:8000`.  
- **Supabase keys in repo:** Security debt — use env/build config for any public fork.  
- **`--reload`** spawns reloader process (normal uvicorn behavior).  
- **Duplicate desktop starts:** mitigated in app + backend; still avoid redundant `LaunchedEffect` keys when extending RA Lens.

---

## 10. Anything Else Relevant

### Constraints already decided

- **Do not casually rewrite** ROM merge logic (`RaLensStore.mergeAnalysis`), **universal interpreter**, or **dummy longitudinal** data contracts without understanding dashboard/insights consumers.  
- **RA Lens active flow** is **`RaLensScreen`**, not `LegacyRaLensScreen`.  
- **Pose guidance step** was **removed from `RaLensStep`** intentionally; re-adding is a **product decision**, not just a composable drop-in.  
- **Cancellation:** Never catch `CancellationException` inside generic `Throwable` handlers in shared API wrappers.

### FYP / product notes

- Project name in Gradle may still read **KotlinProject** / **`org.example.project`** — branding may lag product name **ArthoCare**.  
- **Medical disclaimer:** UI presents health insights; ensure regulatory/clinical disclaimers match institution requirements before any real patient use.

### When helping on new work

1. Read **`App.kt`** for navigation boundaries.  
2. For RA Lens: **`RaLensScreen.kt`** + **`ml_api_service.kt`** + relevant **`ml_backend/app.py`** routes.  
3. For patient data: **`HealthTimelineHydrator`**, **`supabase_health_rest.kt`**, stores.  
4. Prefer **minimal diffs** aligned with existing naming and `mutableStateOf` patterns unless explicitly migrating to ViewModel.

---

*End of document — update `libs.versions.toml`, `composeApp/build.gradle.kts`, and **§6** whenever the stack or active focus changes.*
