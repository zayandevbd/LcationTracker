# Location Tracker — QA Testing Checklist

Complete testing checklist for Android QA engineers.
Run on both a physical device and emulator where indicated.

---

## 1. Functional Tests

### 1.1 App Launch
- [ ] App launches without crash on cold start
- [ ] App launches without crash on warm start (back-stack return)
- [ ] Main screen shows correct initial state: Start button enabled, Stop button disabled
- [ ] Status shows "Tracking Inactive" on first launch
- [ ] Interval displays "5 minutes" on first launch (default)

### 1.2 Start Tracking
- [ ] Tapping "Start Tracking" triggers permission request flow
- [ ] After all permissions granted, service starts successfully
- [ ] Start button becomes disabled after tracking begins
- [ ] Stop button becomes enabled after tracking begins
- [ ] Status changes to "Tracking Active" (green)
- [ ] Foreground service notification appears in notification bar
- [ ] Notification shows "Waiting for first location fix…" initially
- [ ] After first fix: notification updates with coordinates and time
- [ ] Toast appears after each location update with lat, lon, time

### 1.3 Stop Tracking
- [ ] Tapping "Stop Tracking" stops the service
- [ ] Foreground notification disappears
- [ ] Start button re-enabled, Stop button disabled
- [ ] Status reverts to "Tracking Inactive" (grey)
- [ ] Location data card hides after stop

### 1.4 Location History
- [ ] Opening History screen shows all stored records
- [ ] Records are ordered newest first
- [ ] Each card shows: timestamp, latitude, longitude, accuracy, provider
- [ ] Empty state message shown when no records exist
- [ ] Clear All button in toolbar triggers confirmation dialog
- [ ] Confirming clear all removes all records
- [ ] Cancelling clear all keeps records intact
- [ ] History updates in real-time while tracking is active (new card appears at top)

### 1.5 Settings Screen
- [ ] Spinner pre-selects the currently saved interval
- [ ] Selecting a different interval saves it immediately
- [ ] Snackbar confirms the new interval selection
- [ ] Closing settings and reopening shows the saved interval
- [ ] "Currently active" label updates after selection

### 1.6 Interval Change While Tracking
- [ ] Change interval while service is running
- [ ] Confirm service continues tracking without restart
- [ ] Main screen interval display updates after returning from Settings
- [ ] New interval is respected for subsequent location updates
- [ ] Verify all 6 interval options work: 1, 3, 5, 10, 15, 30 minutes

---

## 2. Permission Tests

### 2.1 Fine Location
- [ ] First launch shows location permission rationale dialog (if applicable)
- [ ] Granting permission proceeds to next step
- [ ] Denying shows "permission denied" Snackbar
- [ ] Permanently denying shows "Open Settings" dialog
- [ ] Tapping "Open Settings" opens app permission settings page

### 2.2 Background Location (API 29+)
- [ ] After fine location granted, background location is requested separately
- [ ] On API 30+, system settings page opens (not a dialog)
- [ ] Granting "Allow all the time" enables full background tracking
- [ ] Granting "Allow only while using app" shows advisory Snackbar
- [ ] Denying permanently shows "Open Settings" guidance

### 2.3 Notification Permission (API 33+)
- [ ] On Android 13+, notification permission is requested
- [ ] Granting shows service notification correctly
- [ ] Denying: service still runs, notification is silently suppressed
- [ ] App does not crash when notification permission is denied

### 2.4 Permission State on Resume
- [ ] Revoke location permission via system settings while app is open
- [ ] Return to app — service should stop gracefully
- [ ] App shows appropriate error/guidance

---

## 3. GPS Disabled Tests

- [ ] Disable location services before opening app
- [ ] Tapping "Start Tracking" shows GPS disabled dialog
- [ ] Tapping "Open Settings" in dialog opens Location Settings
- [ ] Enabling GPS and returning to app — tracking starts normally
- [ ] Disable GPS while tracking is active
- [ ] App shows "GPS unavailable" Toast
- [ ] Notification updates to reflect GPS unavailability
- [ ] Re-enabling GPS while service is running — updates resume automatically

---

## 4. Background Tests

### 4.1 App Minimized
- [ ] Start tracking, press Home button
- [ ] Wait for one full interval (e.g. 1 minute with interval set to 1 min)
- [ ] Return to app — new location record should exist in History
- [ ] Toast was shown (verify via notification log or logcat)

### 4.2 Screen Off
- [ ] Start tracking, turn off screen
- [ ] Wait for 2+ intervals
- [ ] Turn on screen — location records exist in History
- [ ] Service notification still visible in notification bar

### 4.3 Navigation Away
- [ ] Start tracking
- [ ] Open other apps, use device normally for several minutes
- [ ] Return to Location Tracker — records present, tracking still active

---

## 5. Kill App Tests

### 5.1 Remove from Recents
- [ ] Start tracking
- [ ] Swipe app away from Recent Apps screen
- [ ] Wait ~2 seconds
- [ ] Verify service notification reappears (service restarted)
- [ ] Check History — new records continue to be added
- [ ] Open app — UI shows correct "Tracking Active" state

### 5.2 Force Stop via Settings
- [ ] Start tracking
- [ ] Go to Settings → Apps → Location Tracker → Force Stop
- [ ] Service notification disappears (expected — force stop kills everything)
- [ ] Reopen app — app starts fresh, tracking shown as inactive
- [ ] Start tracking again — works normally

### 5.3 System OOM Kill (Simulate)
- [ ] Start tracking
- [ ] Use developer tools or stress-test apps to trigger memory pressure
- [ ] Verify service restarts via START_STICKY mechanism
- [ ] Check that location records continue to be recorded

---

## 6. Reboot Tests

- [ ] Start tracking (ensure wasTracking flag is set)
- [ ] Reboot device
- [ ] After reboot and unlock — verify service notification appears automatically
- [ ] Check History — new records being added without user opening app
- [ ] Stop tracking, then reboot — service should NOT restart
- [ ] Verify BootReceiver handles the QUICKBOOT_POWERON action (HTC devices)

---

## 7. Battery Optimization Tests

- [ ] First tap of "Start Tracking" — battery optimization dialog appears (if not whitelisted)
- [ ] Tapping "Open Settings" in dialog opens correct battery settings page
- [ ] App added to battery whitelist — dialog does not show again
- [ ] Enable battery saver mode — verify service continues running
- [ ] Enable aggressive Doze (via `adb shell dumpsys deviceidle force-idle`) — verify tracking continues
- [ ] Test on MIUI/OneUI/ColorOS device — verify OEM power manager doesn't kill service

---

## 8. Room Database Tests

- [ ] Every location update creates exactly one new record in History
- [ ] Records survive app restart (data persisted correctly)
- [ ] Records survive device reboot
- [ ] Timestamps are correct (not 0 or epoch start)
- [ ] Latitude and longitude values have 6 decimal places of precision
- [ ] Accuracy values are positive (negative accuracy = unavailable, shown as N/A)
- [ ] Provider field is not null or empty
- [ ] Clear All removes all records (count becomes 0)
- [ ] After clear, new tracking session adds fresh records correctly
- [ ] Database not queried on main thread (no StrictMode violations)

---

## 9. Foreground Service Tests

- [ ] Service starts within 5 seconds of tapping Start (no ANR)
- [ ] Notification appears immediately (FOREGROUND_SERVICE_IMMEDIATE behavior)
- [ ] Notification is ongoing (cannot be swiped away)
- [ ] Tapping notification opens MainActivity
- [ ] Notification updates with new coordinates after each fix
- [ ] Service stops cleanly — notification dismissed, no zombie process
- [ ] Only one service instance running at any time (no duplicates)
- [ ] Service uses START_STICKY — verify restart after system kill

---

## 10. Android Version Tests

### API 24–25 (Android 7.0–7.1)
- [ ] App installs and runs
- [ ] No background location separate request (not required)
- [ ] startService() used (not startForegroundService)
- [ ] Notification displays correctly

### API 26–27 (Android 8.0–8.1)
- [ ] startForegroundService() used
- [ ] Notification channel created
- [ ] startForeground() called within 5 seconds
- [ ] Background execution limits handled

### API 28 (Android 9)
- [ ] FOREGROUND_SERVICE permission working
- [ ] Foreground service starts without SecurityException

### API 29–30 (Android 10–11)
- [ ] Background location requested separately
- [ ] foregroundServiceType="location" works
- [ ] Location in background works after "Allow all the time" grant

### API 31–32 (Android 12–12L)
- [ ] FLAG_IMMUTABLE on all PendingIntents
- [ ] Foreground service notification delay (10s) bypassed by FOREGROUND_SERVICE_IMMEDIATE
- [ ] Exact alarm permission for restart scheduler

### API 33 (Android 13)
- [ ] POST_NOTIFICATIONS requested at runtime
- [ ] Service notification shown after permission granted
- [ ] Service runs (no crash) when notification permission denied

### API 34 (Android 14)
- [ ] FOREGROUND_SERVICE_LOCATION permission declared
- [ ] No SecurityException on startForeground()
- [ ] Foreground service type enforcement passes

### API 35–36 (Android 15–16)
- [ ] All above behaviors retained
- [ ] No new permission or API deprecation crashes
- [ ] App targets API 36 correctly

---

## 11. Stress Tests

- [ ] Run tracking at 1-minute interval for 1 hour — verify ~60 records
- [ ] Run tracking for 24 hours — verify no memory leaks (use Android Studio Memory Profiler)
- [ ] Rapidly tap Start/Stop 20 times — no duplicate services, no crashes
- [ ] Open and close History screen 50 times while tracking — no memory leak
- [ ] Rotate screen on all three activities — no data loss, no crash
- [ ] Change interval rapidly 10 times in Settings — service accepts final value

---

## 12. Memory Tests

- [ ] Use Android Studio Memory Profiler during active tracking
- [ ] Heap does not grow unboundedly over time
- [ ] No Activity leaks after rotation (verify with LeakCanary if available)
- [ ] No Context leaks in service or repository
- [ ] GC runs are not unusually frequent during tracking

---

## 13. Edge Cases

- [ ] Start tracking with no SIM card and no WiFi (GPS-only mode) — works
- [ ] Start tracking indoors (poor GPS signal) — handles gracefully
- [ ] Device in airplane mode — location unavailable handled, service stays alive
- [ ] Change system time/timezone while tracking — timestamps remain correct (uses GPS time)
- [ ] App with 0 storage space — insert failure handled gracefully (no crash)
- [ ] Very large history (1000+ records) — RecyclerView scrolls smoothly
- [ ] Open History while clearing all simultaneously — no crash, UI recovers
- [ ] Install, grant permissions, then clear app data — starts fresh correctly
- [ ] Interval spinner: select same interval already active — no unnecessary service update
- [ ] Grant only coarse (not fine) location — tracking still starts with reduced accuracy

---

## Logcat Filters for Debugging

```
# All app logs
tag:LocationTracker

# Service only
tag:LocationTracker/Service

# Location updates only
tag:LocationTracker/LocationHelper

# Permission flow
tag:LocationTracker/PermissionManager

# Database
tag:LocationTracker/AppDatabase
```

---

## ADB Commands Useful for Testing

```bash
# Simulate device idle / Doze mode
adb shell dumpsys deviceidle force-idle

# Exit Doze mode
adb shell dumpsys deviceidle unforce

# Check running services
adb shell dumpsys activity services com.zayan.locationtracker

# Mock a location (requires developer options > mock location app)
adb shell geo fix -122.084 37.422

# Check battery optimization status
adb shell dumpsys deviceidle whitelist

# Trigger boot completed broadcast (test BootReceiver)
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED \
  -p com.zayan.locationtracker
```
