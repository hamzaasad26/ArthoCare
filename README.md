<div align="center">

# 🦴 Arthocare
### AI-Driven Medical Application for Arthritis Management

[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS-blue)](https://github.com/HEERHARISH1/arthocare)
[![ML Model](https://img.shields.io/badge/ML%20Accuracy-85%25%2B-green)](https://github.com/HEERHARISH1/arthocare)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)
[![Status](https://img.shields.io/badge/Status-In%20Development-orange)](https://github.com/HEERHARISH1/arthocare)
[![FAST-NUCES](https://img.shields.io/badge/Institution-FAST--NUCES%20Islamabad-red)](https://isb.nu.edu.pk/)

*Empowering arthritis patients with AI-powered self-monitoring, early detection, and personalized care — right from their smartphone.*

</div>

---

## 📋 Table of Contents

- [About](#-about)
- [Problem Statement](#-problem-statement)
- [Features](#-features)
- [Tech Stack](#-tech-stack)
- [Architecture](#-architecture)
- [Installation](#-installation)
- [Team](#-team)
- [Project Supervision](#-project-supervision)
- [Project Timeline](#-project-timeline)
- [Current Status](#-current-status)
- [Future Enhancements](#-future-enhancements)
- [Known Issues](#-known-issues)
- [Contributing](#-contributing)
- [License](#-license)
- [Acknowledgments](#-acknowledgments)
- [Contact](#-contact--support)

---

## 🏥 About

**Arthocare** is an AI-powered mobile application (Android/iOS) designed to provide end-to-end clinical support for arthritis patients. It leverages computer vision and machine learning to analyze joint images, detect inflammation levels, track symptom progression, deliver personalized exercise recommendations, and manage medication reminders — all from a smartphone.

This is a **Final Year Project (FYP)** developed at FAST-NUCES, Islamabad (September 2025 – June 2026).

---

## ❓ Problem Statement

Arthritis patients face significant challenges in:
- **Early detection** — lack of accessible diagnostic tools outside clinics
- **Severity monitoring** — no easy way to track disease progression at home
- **Daily management** — difficulty maintaining exercise routines and medication schedules
- **Doctor communication** — limited structured data sharing with healthcare providers

Existing solutions lack AI-driven personalized care and real-time symptom tracking via mobile platforms, especially for underserved communities.

---

## ✨ Features

### 🤖 AI-Powered Joint Analysis
- Computer vision model trained on medical imaging datasets
- Real-time inflammation detection from smartphone camera
- Severity classification: **Mild · Moderate · Severe**
- TensorFlow Lite integration for on-device (edge) inference

### 📊 Symptom Tracking Dashboard
- Daily pain level monitoring
- Movement range tracking
- Visual analytics and trend graphs
- Data export for sharing with doctors

### 💊 Personalized Care Recommendations
- ML-based exercise suggestions tailored to severity level
- Medication reminders with dosage tracking
- Diet recommendations based on user profile

### 🩺 Doctor-Patient Communication
- Secure data sharing with healthcare providers
- Appointment scheduling and reminders
- Progress report generation

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| **Mobile Frontend** | React Native |
| **ML Model** | TensorFlow, TensorFlow Lite, MobileNetV2 (Transfer Learning) |
| **Computer Vision** | OpenCV, CNN for joint image classification |
| **Backend API** | Flask (Python) |
| **Database** | Firebase (cloud), SQLite (local) |
| **Authentication** | Firebase Auth |
| **Deployment** | On-device inference (TensorFlow Lite) |
| **Other Tools** | Android Studio, Python, Kotlin |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────┐
│              Mobile App (React Native)       │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐  │
│  │ Joint    │  │Symptom   │  │Medication │  │
│  │ Scanner  │  │Tracker   │  │Reminders  │  │
│  └────┬─────┘  └────┬─────┘  └─────┬─────┘  │
└───────┼─────────────┼──────────────┼─────────┘
        │             │              │
        ▼             ▼              ▼
┌───────────────────────────────────────────┐
│           Flask REST API (Backend)         │
│  ┌──────────────┐    ┌───────────────────┐ │
│  │ CV Pipeline  │    │  Firebase (Auth + │ │
│  │ (OpenCV +    │    │  Cloud Storage)   │ │
│  │  TFLite)     │    └───────────────────┘ │
│  └──────────────┘                          │
└───────────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────────┐
│         ML Model (TensorFlow Lite)         │
│  CNN + MobileNetV2 — 85%+ Accuracy         │
│  Severity: Mild | Moderate | Severe        │
└───────────────────────────────────────────┘
```

---

## ⚙️ Installation

### Prerequisites
- Node.js >= 16
- Python >= 3.9
- Android Studio / Xcode
- Firebase account
- React Native CLI


### Backend Setup (Flask API)
```bash
cd backend
pip install -r requirements.txt
python app.py
```

### Mobile App Setup (React Native)
```bash
cd mobile
npm install
npx react-native run-android   # For Android
npx react-native run-ios       # For iOS
```

### Environment Variables
Create a `.env` file in `/backend`:
```
FIREBASE_API_KEY=your_key
FIREBASE_PROJECT_ID=your_project_id
MODEL_PATH=./models/arthocare_model.tflite
```

---

## 👥 Team

Meet the amazing team behind Arthocare:

<table>
  <tr>
    <td align="center">
      <a href="https://github.com/HEERHARISH1">
        <img src="https://github.com/HEERHARISH1.png" width="100px;" alt="Heer Lohana"/>
        <br />
        <sub><b>Heer Lohana</b></sub>
      </a>
      <br />
      <a href="https://www.linkedin.com/in/heer-harish/">LinkedIn</a> •
      <a href="https://github.com/HEERHARISH1">GitHub</a> •
      <a href="mailto:heerlohana1761@gmail.com">Email</a>
      <br />
      <sub>AI/ML Engineer • Backend Developer • App developer  </sub>
      <br />
      <sub>📞 +92 303 9049119</sub>
    </td>
    <td align="center">
      <a href="https://www.linkedin.com/in/umema-ashar-2004ua">
        <img src="https://via.placeholder.com/100" width="100px;" alt="Umema Ashar"/>
        <br />
        <sub><b>Umema Ashar</b></sub>
      </a>
      <br />
      <a href="https://www.linkedin.com/in/umema-ashar-2004ua">LinkedIn</a> •
      <a href="mailto:umema2004@gmail.com">Email</a>
      <br />
      <sub>AI/ML Engineer • Backend Developer • App developer</sub>
      <br />
      <sub>📞 +92 300 8420208</sub>
    </td>
    <td align="center">
      <a href="https://www.linkedin.com/in/hamza-asad-6bb307253/">
        <img src="https://via.placeholder.com/100" width="100px;" alt="Hamza Asad"/>
        <br />
        <sub><b>Hamza Asad</b></sub>
      </a>
      <br />
      <a href="https://www.linkedin.com/in/hamza-asad-6bb307253/">LinkedIn</a> •
      <a href="mailto:hamza26asad@gmail.com">Email</a>
      <br />
      <sub>AI/ML Engineer • Backend Developer • App developer</sub>
      <br />
      <sub>📞 +92 333 4365190</sub>
    </td>
  </tr>
</table>

### Roles & Contributions



## 🎓 Project Supervision

**Supervisor:** Dr. Muhammad Faisal Cheema
**Title:** Assistant Professor & Director, KDD Research Lab
**Department:** Computer Science
**Institution:** FAST-NUCES, Islamabad

---





=

## 🙏 Acknowledgments

We would like to express our gratitude to:

- **FAST-NUCES** for providing the platform and resources
- **Dr. Muhammad Faisal Cheema** for guidance and mentorship throughout the project
- **KDD Research Lab** for research support and facilities
- **Healthcare Professionals** who provided domain expertise
- **Dataset Providers** (Kaggle, UCI ML Repository) for training data
- **Open Source Community** for amazing tools and libraries


## 📞 Contact & Support

### Team

| Name |  Email | LinkedIn | Phone |
|---|---|---|---|
| Heer Lohana  | heerlohana1761@gmail.com | [LinkedIn](https://www.linkedin.com/in/heer-harish/) | +92 303 9049119 |
| Umema Ashar =| umema2004@gmail.com | [LinkedIn](https://www.linkedin.com/in/umema-ashar-2004ua) | +92 300 8420208 |
| Hamza Asad  | hamza26asad@gmail.com | [LinkedIn](https://www.linkedin.com/in/hamza-asad-6bb307253/) | +92 333 4365190 |


---

<div align="center">
<sub>© 2026 Arthocare Team | FAST-NUCES Islamabad | Final Year Project</sub>
</div>
