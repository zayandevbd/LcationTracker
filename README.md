# Location Tracker — Android App

A production-quality background location tracking application for Android, built with Java, MVVM architecture, Room database, and a Foreground Service.

---

## 🎥 Demo

▶ **Watch Demo Video**

[Click here to view demo](media/Location.mp4)
## Features

- Continuous background location tracking that survives:
  - App minimized
  - Screen off
  - App removed from Recents
  - Device reboot
- Configurable update interval: 5, 10, 15, or 30 minutes
- Changes apply immediately without restarting the service
- Persistent foreground service with live notification
- Full location history stored in Room database
- History screen with RecyclerView (newest first)
- Toast on every location update (lat, lon, time)
- Runtime permission handling for all Android versions (24–36)
- GPS disabled detection with dialog redirect
- Battery optimization guidance
- Dark mode support

---

## Architecture

```
MVVM + Repository Pattern

UI Layer        → MainActivity, HistoryActivity, SettingsActivity
ViewModel       → LocationViewModel (LiveData, survives rotation)
Repository      → LocationRepository (single source of truth, threading)
Database        → Room (LocationEntity, LocationDao, AppDatabase)
Service         → LocationTrackingService (Foreground, START_STICKY)
Location        → LocationHelper (FusedLocationProviderClient)
Permissions     → PermissionManager (ActivityResultLauncher, multi-step flow)
Settings        → AppSettings (SharedPreferences wrapper)
Receivers       → BootReceiver, ServiceRestartReceiver
Notifications   → NotificationHelper (channel + persistent notification)
Utils           → Constants, ServiceUtils
```

---

## Package Structure

```
com.zayan.locationtracker/
├── database/
│   ├── AppDatabase.java
│   ├── dao/LocationDao.java
│   └── entity/LocationEntity.java
├── repository/
│   └── LocationRepository.java
├── viewmodel/
│   ├── LocationViewModel.java
│   └── LocationViewModelFactory.java
├── service/
│   └── LocationTrackingService.java
├── receiver/
│   ├── BootReceiver.java
│   └── ServiceRestartReceiver.java
├── ui/
│   ├── main/MainActivity.java
│   ├── history/HistoryActivity.java
│   ├── history/LocationHistoryAdapter.java
│   └── settings/SettingsActivity.java
├── permissions/
│   └── PermissionManager.java
├── location/
│   └── LocationHelper.java
├── notification/
│   └── NotificationHelper.java
├── settings/
│   └── AppSettings.java
└── utils/
    ├── Constants.java
    └── ServiceUtils.java
```

---

## Tech Stack

| Component | Library / API |
|---|---|
| Language | Java 11 |
| UI | XML layouts, ViewBinding |
| Architecture | MVVM + Repository |
| Database | Room 2.7.1 |
| Location | Google Play Services Location 21.3.0 |
| Lifecycle | AndroidX Lifecycle 2.9.1 |
| Background | Foreground Service + AlarmManager |
| DI | Manual (no Hilt/Dagger) |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 (Android 16) |

---

## Requirements

- Android Studio Meerkat or later
- JDK 11+
- Android device or emulator with Google Play Services
- Gradle 9.2.1 (wrapper included)

---

## Build Instructions

### Debug APK

```bash
# From the project root directory
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Release APK

1. Create a keystore (one-time setup):
```bash
keytool -genkey -v -keystore location_tracker.jks \
  -alias location_tracker -keyalg RSA -keysize 2048 -validity 10000
```

2. Add signing config to `app/build.gradle.kts`:
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("location_tracker.jks")
            storePassword = "your_store_password"
            keyAlias = "location_tracker"
            keyPassword = "your_key_password"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

3. Build:
```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

### Install directly to connected device

```bash
./gradlew installDebug
```

---

## Permissions Explained

| Permission | Purpose | When Requested |
|---|---|---|
| ACCESS_FINE_LOCATION | GPS-accuracy tracking | Runtime — on Start Tracking tap |
| ACCESS_COARSE_LOCATION | Network fallback | Runtime — same dialog |
| ACCESS_BACKGROUND_LOCATION | Tracking when screen off | Runtime — separate dialog (API 29+) |
| FOREGROUND_SERVICE | Run foreground service | Install-time (normal permission) |
| FOREGROUND_SERVICE_LOCATION | Location foreground service type | Install-time (API 34+) |
| POST_NOTIFICATIONS | Show service notification | Runtime — API 33+ only |
| RECEIVE_BOOT_COMPLETED | Restart after reboot | Install-time (normal permission) |
| WAKE_LOCK | Keep CPU awake for callbacks | Install-time (normal permission) |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Battery whitelist prompt | Shown as advisory dialog |

---

## How Background Tracking Survives

1. **Foreground Service** — Android cannot kill a foreground service without user action
2. **START_STICKY** — OS restarts the service automatically after OOM kills
3. **stopWithTask="false"** — service survives app removal from Recents
4. **onTaskRemoved() + AlarmManager** — schedules restart if OEM task killer fires
5. **BootReceiver** — restarts service after device reboot if `wasTracking` flag is set
6. **Battery Optimization whitelist** — user guided to exempt app from Doze

---

## Suggested Git Commit History

```
feat: initial project setup with MVVM architecture and Room database

feat: add LocationEntity, LocationDao, AppDatabase with singleton pattern

feat: implement LocationRepository with ExecutorService threading

feat: add LocationViewModel and ViewModelFactory

feat: add Constants, ServiceUtils, AppSettings utility classes

feat: implement NotificationHelper with channel creation and update support

feat: implement PermissionManager with multi-step permission flow

feat: implement LocationHelper with FusedLocationProviderClient

feat: implement LocationTrackingService with START_STICKY and restart logic

feat: add BootReceiver and ServiceRestartReceiver

feat: implement MainActivity with permission flow and service controls

feat: implement HistoryActivity with RecyclerView and DiffUtil adapter

feat: implement SettingsActivity with live interval update

feat: add all XML layouts (main, history, settings, item_location)

feat: add complete strings, colors, themes and drawable resources

chore: add proguard rules for Room, ViewModel and service classes

docs: add README with architecture overview and build instructions
```

---

## Testing Notes

- Test on a physical device for real GPS behavior — emulators use mock locations
- Test interval changes while service is running (verify no tracking gap)
- Test reboot with tracking active — service should auto-restart
- Test "Remove from Recents" — service should restart within ~1 second
- Enable battery optimization for the app and verify tracking continues
- Test on Android 14+ for `FOREGROUND_SERVICE_LOCATION` permission behavior
- Deny background location — verify foreground tracking still works

---

## Known Limitations

- No data export (CSV/JSON) — planned future feature
- No date-range filtering on history screen — DAO method provided, UI not implemented
- Database has no row limit — very long tracking sessions (weeks/months) will grow the DB
- Background location on Android 12+ requires user to manually select "Allow all the time"

---

## License

MIT License — free to use, modify, and distribute.
