<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Language-Kotlin_2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" />
  <img src="https://img.shields.io/badge/AI-Google_Gemini-8E75B2?style=for-the-badge&logo=googlegemini&logoColor=white" />
  <img src="https://img.shields.io/badge/Backend-Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black" />
  <img src="https://img.shields.io/badge/Min_SDK-24_(Android_7.0)-34A853?style=for-the-badge" />
</p>

<h1 align="center">🛣️ RaahMitra</h1>
<h3 align="center"><em>Your Intelligent Road Safety Companion</em></h3>

<p align="center">
  <strong>RaahMitra</strong> (Hindi: राहमित्र — "Road Friend") is an AI-powered Android app that detects potholes and speed breakers in real-time using phone sensors, and lets citizens report civic infrastructure issues with Gemini AI-assisted image analysis — all built natively in Kotlin with Jetpack Compose.
</p>

<br/>

---

<br/>

## 🎯 The Problem

Indian roads are notoriously unpredictable. Potholes cause thousands of accidents every year, and broken civic infrastructure — overflowing drains, damaged water tanks, flooded streets — often goes unreported for months because the reporting process is tedious and disconnected.

**RaahMitra** tackles both sides of this problem:

1. **Passive Detection** — Mount your phone, drive normally, and the app automatically detects potholes and speed breakers using accelerometer + gravity sensors, logging each one with GPS coordinates to a shared Firebase database.

2. **Active Reporting** — Snap a photo of any civic issue. Gemini AI analyzes the image, auto-categorizes it, and writes a description for you. One tap to submit — location, evidence, and details are sent to Firestore for tracking.

<br/>

---

<br/>

## ✨ Features at a Glance

### 🚗 Drive Mode — Real-Time Road Anomaly Detection

| Capability | Details |
|:---|:---|
| **Sensor Fusion** | Combines `LINEAR_ACCELERATION`, `GRAVITY`, and `ACCELEROMETER` sensors to compute true vertical (Z-axis) force independent of phone orientation |
| **Pothole vs Speed Breaker** | Classifies anomalies by analyzing the temporal ordering of acceleration peaks — a dip-then-bump pattern indicates a pothole, while bump-then-dip signals a speed breaker |
| **Buffered Detection** | Maintains a sliding window of 40 sensor readings to filter noise and detect sustained impacts above a ±3.5 m/s² threshold |
| **Live Sensor Graph** | Custom Canvas-based real-time graph showing Z-axis acceleration data with 150-point rolling history, annotated with detection markers and GPS coordinates |
| **Firebase Logging** | Detected potholes are automatically pushed to Firebase Realtime Database with lat/lng and timestamp, building a crowd-sourced pothole map |
| **Alert System** | Plays a notification sound and displays a full-screen visual alert on detection, with a 1.5-second cooldown to prevent duplicate triggers |
| **Swipe-to-Drive** | A custom haptic swipe gesture to activate driving mode — prevents accidental activation |
| **GPS Tracking** | Polls device location every 2 seconds during drive mode using Google Fused Location Provider |

### 📸 Report Issue — AI-Powered Civic Reporting

| Capability | Details |
|:---|:---|
| **Camera Integration** | Capture evidence photos directly within the app via Android's `TakePicturePreview` contract |
| **Gemini AI Analysis** | Sends the captured image to Google's Gemini generative model, which auto-selects the issue category and writes a 2-sentence description |
| **Smart Categories** | Pre-defined categories: Overflow Trash Bin, Flood, Poor Build Canal, Water Tank Issue, Other — the AI maps images to the closest match |
| **Auto Location** | Fetches GPS coordinates on photo capture and reverse-geocodes them to a human-readable address using Android's `Geocoder` |
| **Firestore Submission** | Reports are stored with user name, category, AI-generated description, location, timestamp, and a `pending` status flag |
| **Confirmation Dialog** | A pre-submit review dialog lets users verify all details before sending |

### 📋 Report Status — Track Your Submissions

| Capability | Details |
|:---|:---|
| **Report History** | View all submitted reports in a scrollable list with description, date, and status indicators |
| **Status Tracking** | Each report shows its current state: Pending, In Review, or Resolved — with color-coded badges |
| **Detail View** | Tap any report to see full evidence (photo), description, location, and status |
| **Delete Reports** | Long-press any report card to trigger a delete confirmation dialog |

<br/>

---

<br/>

## 🏗️ Architecture & Tech Stack

```
┌─────────────────────────────────────────────────────────┐
│                      PRESENTATION                       │
│                                                         │
│   MainActivity          Jetpack Compose UI Layer        │
│   ├─ SensorManager      ├─ HomeScreen (Drive Mode)      │
│   │  ├─ Accelerometer   │  ├─ ContinuousSensorGraph     │
│   │  ├─ Gyroscope       │  ├─ CarDisplayCard             │
│   │  └─ Gravity         │  └─ SwipeToDriveButton         │
│   │                     ├─ ReportIssueScreen             │
│   └─ Sensor States ──►  │  ├─ Camera + Gemini AI         │
│                         │  └─ Firestore Submission       │
│                         └─ ReportStatusScreen            │
│                            ├─ ReportListView             │
│                            └─ ReportDetailView           │
├─────────────────────────────────────────────────────────┤
│                      NAVIGATION                         │
│                                                         │
│   AnimatedNavHost (Accompanist)                         │
│   ├─ mainscreen ──► HomeScreen                          │
│   ├─ report_problem ──► ReportIssueScreen               │
│   └─ status ──► ReportStatusScreen                      │
│                                                         │
│   FloatingBottomBar (Custom Card-based Nav)             │
│   ├─ Drive Mode  │  Report  │  Quiz (Placeholder)      │
├─────────────────────────────────────────────────────────┤
│                       BACKEND                           │
│                                                         │
│   Firebase Realtime DB        Firebase Firestore        │
│   └─ /potholes               └─ /reports                │
│      ├─ type: "POTHOLE"         ├─ category             │
│      ├─ latitude                ├─ description           │
│      ├─ longitude               ├─ location              │
│      └─ timestamp               ├─ imageUrl              │
│                                 ├─ status: "pending"     │
│   Google Gemini AI              └─ timestamp             │
│   └─ Image → Category +                                 │
│      Description generation                             │
├─────────────────────────────────────────────────────────┤
│                    DEVICE HARDWARE                       │
│                                                         │
│   Sensors              GPS                Camera        │
│   ├─ Accelerometer     Fused Location     TakePicture   │
│   ├─ Gyroscope         Provider           Preview       │
│   └─ Gravity           (2s polling)       Contract      │
└─────────────────────────────────────────────────────────┘
```

| Layer | Technology | Purpose |
|:---|:---|:---|
| **Language** | Kotlin 2.0.21 | Primary development language |
| **UI Framework** | Jetpack Compose + Material 3 | Declarative UI with dark theme |
| **Navigation** | Accompanist Navigation Animation | Animated screen transitions (slide + fade) |
| **AI** | Google Generative AI SDK 0.9.0 (Gemini) | Image analysis for report auto-categorization |
| **Database** | Firebase Realtime Database | Real-time pothole coordinate logging |
| **Database** | Firebase Firestore | Civic issue report storage and tracking |
| **Storage** | Firebase Storage | Report evidence image storage (available) |
| **Analytics** | Firebase Analytics | Usage tracking |
| **Location** | Google Play Services Location 21.0.1 | GPS coordinates via Fused Location Provider |
| **Sensors** | Android SensorManager | Accelerometer, Gyroscope, Gravity sensors |
| **Images** | Coil Compose 2.4.0 | Image loading and display |
| **Secrets** | Secrets Gradle Plugin 2.0.1 | Secure API key management |
| **Min SDK** | 24 (Android 7.0 Nougat) | Broad device compatibility |
| **Target SDK** | 36 | Latest Android platform features |

<br/>

---

<br/>

## 📂 Project Structure

```
app/src/main/java/com/example/raahmitra/
│
├── MainActivity.kt                         # Entry point
│   ├── Initializes hardware sensors (Accelerometer, Gyroscope, Gravity)
│   ├── Passes raw sensor data as Compose state to UI
│   ├── Configures high refresh rate display mode
│   └── Forces light mode via AppCompatDelegate
│
├── mainui/
│   ├── screenalign.kt                      # Navigation host + floating bottom bar
│   │   ├── screen_align() — AnimatedNavHost with 3 routes
│   │   ├── FloatingBottomBar() — Custom card-based navigation
│   │   ├── BottomBarItem() — Animated icon + label component
│   │   └── Hoisted state for passing report data between screens
│   │
│   ├── drive_mode.kt                       # Drive Mode — core detection engine
│   │   ├── HomeScreen() — Main driving interface
│   │   ├── Sensor fusion: LINEAR_ACCELERATION + GRAVITY fallback
│   │   ├── Vertical acceleration extraction (dot product with gravity vector)
│   │   ├── Sliding buffer (40 samples) with threshold detection (±3.5 m/s²)
│   │   ├── Pothole vs Speed Breaker classification (temporal peak analysis)
│   │   ├── Firebase Realtime DB push for pothole coordinates
│   │   ├── ContinuousSensorGraph() — Custom Canvas real-time graph
│   │   ├── SwipeToDriveButton() — Haptic swipe gesture activation
│   │   ├── CarDisplayCard() — Animated alert/status display
│   │   └── TopHeader() — App branding bar
│   │
│   ├── reporting_screen.kt                 # Report Issue — AI-assisted civic reporting
│   │   ├── ReportIssueScreen() — Full reporting workflow
│   │   ├── Gemini AI integration — image → category + description
│   │   ├── Camera capture via TakePicturePreview
│   │   ├── GPS reverse geocoding for location address
│   │   ├── Firestore submission with confirmation dialog
│   │   ├── EvidenceSection() — Photo capture/preview UI
│   │   ├── LocationCard() — GPS address display
│   │   └── DescriptionInput() — Styled text field
│   │
│   └── status_screen.kt                   # Report Status — submission tracker
│       ├── ReportStatusScreen() — List/detail router
│       ├── ReportListView() — LazyColumn with long-press delete
│       ├── ReportItemCard() — Status-colored report cards
│       ├── ReportDetailView() — Full evidence + details view
│       ├── StatusBanner() — Status/ID display
│       └── DetailCard() — Icon + text detail rows
│
└── ui/theme/
    ├── Color.kt                            # Material color palette
    ├── Theme.kt                            # App-wide Material 3 theme
    └── Type.kt                             # Typography definitions
```

<br/>

---

<br/>

## 🔬 How Pothole Detection Works

The detection engine is the heart of RaahMitra. Here's how it distinguishes a pothole from a speed breaker using only your phone's sensors:

**Step 1 — Isolate Vertical Force**

The phone can be in any orientation (flat on dashboard, tilted in a mount, etc.). To get pure vertical acceleration regardless of orientation, the app computes the dot product of the linear acceleration vector with the gravity vector and normalizes it:

```
verticalAccel = (ax·gx + ay·gy + az·gz) / |g|
```

This gives a single scalar value representing how hard the vehicle is being pushed up or down — independent of phone tilt.

**Step 2 — Buffer & Threshold**

Raw sensor data is noisy. The app maintains a sliding buffer of 40 readings (sampled at `SENSOR_DELAY_GAME` rate). A detection is triggered only when:
- The buffer is full (40 samples)
- The maximum value exceeds +3.5 m/s²
- The minimum value drops below −3.5 m/s²
- At least 1.5 seconds have passed since the last detection

**Step 3 — Classify the Anomaly**

The temporal ordering of peaks reveals the type of road anomaly:

| Pattern | Classification | Reasoning |
|:---|:---|:---|
| Max peak **before** min peak | **Speed Breaker** | Car rises (upward force) then drops (downward force) |
| Min peak **before** max peak | **Pothole** | Car dips (downward force) then rebounds (upward force) |

**Step 4 — Log & Alert**

Only potholes with valid GPS coordinates are pushed to Firebase Realtime Database. A notification sound plays, a visual alert appears for 3 seconds, and the detection point is annotated on the live sensor graph.

<br/>

---

<br/>

## 🚀 Getting Started

### Prerequisites

- Android Studio Ladybug or later
- A Firebase project with **Realtime Database** and **Firestore** enabled
- A **Google Gemini API key** (get one from [Google AI Studio](https://aistudio.google.com/))

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/bluedorsey/RaahMitra_app.git
   ```

2. **Open in Android Studio** and let Gradle sync complete.

3. **Firebase Configuration**
   - The repo includes a `google-services.json` in the `app/` directory. For your own Firebase project, replace it with yours from the [Firebase Console](https://console.firebase.google.com/) (package name: `com.example.raahmitra`).
   - Enable **Realtime Database** — the app writes to a `/potholes` node.
   - Enable **Cloud Firestore** — the app writes to a `reports` collection.

4. **Gemini API Key**
   - Create a `local.properties` file (or use the Secrets Gradle Plugin) and add your Gemini API key.
   - Update the API key in `reporting_screen.kt` → `GenerativeModel` constructor.

5. **Build & Run** on a physical device (sensors are required — emulators won't detect potholes!). Minimum API 24 (Android 7.0).

### Permissions Required

| Permission | Purpose |
|:---|:---|
| `ACCESS_FINE_LOCATION` | GPS coordinates for pothole logging and report location |
| `ACCESS_COARSE_LOCATION` | Fallback location provider |
| `CAMERA` | Capture evidence photos for civic issue reports |
| `INTERNET` | Firebase communication and Gemini API calls |

All permissions are requested at runtime when the relevant feature is first used.

<br/>

---

<br/>

## 📦 Pre-built APK

A release APK is available at `app/release/app-release.apk` (~14 MB) for quick testing without building from source.

> **Note:** You'll still need a configured Firebase backend for full functionality.

<br/>

---

<br/>

## 🗺️ Firebase Data Schema

### Realtime Database — `/potholes/{pushId}`

```json
{
  "type": "POTHOLE",
  "latitude": 22.7196,
  "longitude": 75.8577,
  "timestamp": 1711108200000
}
```

### Firestore — `reports/{autoId}`

```json
{
  "userName": "Ashutosh",
  "category": "Flood",
  "description": "AI-generated description of the issue...",
  "location": "123 MG Road, Indore, MP 452001",
  "imageUrl": "Image upload skipped (Text only mode)",
  "timestamp": "Firestore Timestamp",
  "status": "pending"
}
```

<br/>

---

<br/>

## 🎨 Design Language

RaahMitra uses a dark-first design with a carefully chosen palette optimized for glanceable in-car usage:

| Token | Hex | Role |
|:---|:---|:---|
| `DarkBackground` | `#0F131A` | Primary surface — reduces eye strain while driving |
| `CardBackground` | `#1C222E` | Elevated card surfaces |
| `PrimaryBlue` | `#2962FF` | Interactive elements, CTAs, navigation highlights |
| `ActiveGreen` | `#4CAF50` | Active/safe state (driving mode on) |
| `AlertRed` | `#E53935` | Danger/alert state (pothole detected) |
| `GraphLineColor` | `#00E5FF` | Live sensor graph trace — high contrast cyan |
| `AiPurple` | `#D500F9` | Gemini AI action buttons |

The floating bottom navigation bar uses a pill-shaped `Card` with rounded corners, creating a modern overlay that doesn't interfere with the main content while driving.

<br/>

---

<br/>

## 🔮 Roadmap

- [ ] **Pothole Heatmap** — Visualize crowd-sourced pothole data on Google Maps
- [ ] **Road Safety Quiz** — Gamified road safety education (placeholder tab exists)
- [ ] **Firebase Auth** — Replace hardcoded user name with real authentication
- [ ] **Image Upload** — Upload evidence photos to Firebase Storage (currently text-only mode)
- [ ] **Background Detection** — Run sensor monitoring as a foreground service
- [ ] **Offline Support** — Queue reports and pothole detections for sync when connectivity returns
- [ ] **Admin Dashboard** — Web panel for authorities to review and resolve reports
- [ ] **Severity Scoring** — Classify pothole severity based on acceleration magnitude

<br/>

---

<br/>

## 🤝 Contributing

Contributions are welcome! If you'd like to improve RaahMitra:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/heatmap-view`)
3. Commit your changes (`git commit -m 'Add pothole heatmap overlay'`)
4. Push to the branch (`git push origin feature/heatmap-view`)
5. Open a Pull Request

<br/>



---

<p align="center">
  <strong>RaahMitra</strong> — because every road deserves a friend. 🛣️
</p>
