# ArthoCare - Project Structure Summary

A Kotlin Multiplatform Mobile (KMM) application for managing Rheumatoid Arthritis (RA), built with Jetpack Compose Multiplatform. The app targets both Android and iOS platforms with shared business logic and UI code.

## 📱 Project Overview

ArthoCare is a comprehensive mobile health application designed to help users manage their Rheumatoid Arthritis condition. It provides features for daily symptom tracking, predictions, diet planning, weather alerts, and more.

## 🛠 Technology Stack

- **Language**: Kotlin
- **Framework**: Kotlin Multiplatform Mobile (KMM)
- **UI**: Jetpack Compose Multiplatform
- **Design System**: Material 3
- **Build System**: Gradle with Kotlin DSL
- **Platforms**: Android (minSdk 24, targetSdk 36) & iOS

## 📁 Project Structure

```
KotlinProject/
├── composeApp/                    # Main application module
│   ├── src/
│   │   ├── commonMain/            # Shared code for all platforms
│   │   │   ├── kotlin/
│   │   │   │   └── org/example/project/
│   │   │   │       ├── App.kt              # Main app entry & navigation
│   │   │   │       ├── LoginScreen.kt      # Login UI
│   │   │   │       ├── SignUpScreen.kt     # User registration UI
│   │   │   │       ├── DashboardScreen.kt   # Main dashboard with feature cards
│   │   │   │       ├── DailyLogScreen.kt    # Daily symptom tracking
│   │   │   │       ├── FeatureScreens.kt    # All feature screens (RA Predictions, Diet, etc.)
│   │   │   │       ├── Theme.kt             # App theme & color scheme
│   │   │   │       ├── Color.kt            # Color definitions
│   │   │   │       └── Platform.kt          # Platform detection
│   │   │   └── composeResources/  # Shared resources (images, etc.)
│   │   │
│   │   ├── androidMain/           # Android-specific code
│   │   │   ├── kotlin/
│   │   │   │   └── org/example/project/
│   │   │   │       ├── MainActivity.kt     # Android entry point
│   │   │   │       └── Platform.android.kt # Android platform implementation
│   │   │   ├── AndroidManifest.xml        # Android app configuration
│   │   │   └── res/                        # Android resources (icons, strings)
│   │   │
│   │   ├── iosMain/               # iOS-specific code
│   │   │   └── kotlin/
│   │   │       └── org/example/project/
│   │   │           ├── MainViewController.kt # iOS entry point
│   │   │           └── Platform.ios.kt      # iOS platform implementation
│   │   │
│   │   └── commonTest/            # Shared test code
│   │
│   └── build.gradle.kts           # Module build configuration
│
├── iosApp/                        # iOS native app wrapper
│   └── iosApp/                    # SwiftUI entry point for iOS
│
├── gradle/                        # Gradle configuration
│   ├── libs.versions.toml         # Dependency version catalog
│   └── wrapper/                   # Gradle wrapper files
│
├── build.gradle.kts               # Root build configuration
├── settings.gradle.kts            # Project settings
├── gradlew / gradlew.bat          # Gradle wrapper scripts
└── local.properties              # Local build properties (SDK paths, etc.)
```

## 🎯 Key Components

### Navigation System
- **Location**: `composeApp/src/commonMain/kotlin/org/example/project/App.kt`
- **Implementation**: Custom state-based navigation using `Crossfade` animations
- **Screens**: Defined in `Screen` enum with 11 different screens

### UI Screens

#### Authentication
- **LoginScreen.kt**: User login with hardcoded credentials (name: "heer", password: "1234")
- **SignUpScreen.kt**: Comprehensive user registration form with medical history fields

#### Main Features
- **DashboardScreen.kt**: Grid-based dashboard with 8 feature cards
- **DailyLogScreen.kt**: Daily symptom tracking (pain level, fatigue, notes) ✅ *Fully functional*
- **FeatureScreens.kt**: Contains placeholder screens for:
  - RA Predictions
  - Diet Plans
  - Weather Alerts
  - Reminders
  - RA Lens (camera functionality)
  - Tips/Exercises
  - Settings

### Styling
- **Theme.kt**: Material 3 theme with purple/lavender color scheme
- **Color.kt**: Custom color definitions
- Supports both light and dark modes

## 🚀 Features

### ✅ Implemented
- User authentication (login/signup)
- Dashboard navigation
- Daily log tracking (pain level, fatigue, notes)
- Cross-platform UI (Android & iOS)

### 🚧 In Development (Placeholders)
- RA flare-up predictions
- Diet plans and recipes
- Weather alerts for RA symptoms
- Medication/appointment reminders
- RA Lens (camera-based joint analysis)
- Exercise tips and recommendations
- User settings

## 🏃 Running the Application

### Prerequisites
- Android Studio (latest version recommended)
- JDK 11 or higher
- Android SDK (API 24+)
- For iOS: Xcode (macOS only)

### Android

#### Option 1: Android Studio
1. Open the project in Android Studio
2. Wait for Gradle sync to complete
3. Create/start an Android emulator (API 24+)
4. Click Run ▶️ or press `Shift + F10`

#### Option 2: Command Line
```bash
# Build debug APK
.\gradlew.bat :composeApp:assembleDebug

# Install on connected device/emulator
.\gradlew.bat :composeApp:installDebug
```

### iOS (macOS only)
1. Open `iosApp/iosApp.xcodeproj` in Xcode
2. Select a simulator or device
3. Click Run ▶️

## 🔐 Test Credentials

- **Name**: `heer`
- **Password**: `1234`

## 📦 Dependencies

Key dependencies are managed in `gradle/libs.versions.toml`:
- Compose Multiplatform 1.9.0
- Kotlin 2.2.20
- Material 3
- AndroidX Lifecycle
- AndroidX Activity Compose

## 🏗 Build Configuration

- **Package**: `org.example.project`
- **Min SDK**: Android 24
- **Target SDK**: Android 36
- **Compile SDK**: Android 36
- **JVM Target**: 11

## 📝 Development Status

**Current Phase**: Early Development
- Core UI structure: ✅ Complete
- Navigation system: ✅ Complete
- Daily log feature: ✅ Functional
- Other features: 🚧 Placeholder screens (UI ready, logic pending)

## 🔄 Project Architecture

The app follows a simple state-based architecture:
- **State Management**: `remember` and `mutableStateOf` for local state
- **Navigation**: Custom `Screen` enum with `Crossfade` transitions
- **UI Framework**: Declarative UI with Jetpack Compose
- **Code Sharing**: ~90% code shared between Android and iOS

## 📚 Additional Resources

- [Kotlin Multiplatform Documentation](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform Documentation](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Material 3 Design](https://m3.material.io/)

---

**Note**: This is a Final Year Project (FYP) for university coursework focused on Rheumatoid Arthritis management.

