# Release notes

## v1.2.0 — October 20, 2026

Time taken directly from the GNSS receiver clock, which is disciplined by the satellites' atomic
clocks, and a fixed clock display.

APK: `dist/SatTemps-v1.2.0-debug.apk` (or `./gradlew assembleDebug`).

### New features

- New "GNSS atomic" time source: the app reads raw GNSS measurements (`GnssClock`) and computes
  GPS time (`timeNanos - fullBiasNanos - biasNanos`), converted to UTC with the receiver's leap
  seconds (18 s by default).
- The time is tied to the phone's internal clock through `elapsedRealtimeNanos`, without the
  latency of the position fix.
- Priority order: GNSS atomic, then GNSS fix, then NTP, then the phone clock. If raw measurements
  stop for more than 10 s, the app falls back to the next source.
- Time tab: source name in plain words, GNSS clock uncertainty (ns, µs or ms), GPS - UTC offset
  and raw measurement status.
- Raw measurement diagnostics: no measurement received, measurements received but GPS time not
  computed yet, GNSS clock not linked to the phone clock, not provided, Android 11 required, or
  active; with the number of raw signals received.

### Fixes

- The time with milliseconds no longer wraps to a second line or overlaps the seconds: its size
  adapts to the screen width and the phone's font size.

### Limitations

- Raw measurements: Android 11 minimum and a phone that provides them. Otherwise, the app uses the
  other sources as before.
- The phone's system clock is not changed (the permission is reserved for system apps): only the
  time shown by SatTemps is corrected.
- Since the phone clock is only read to the millisecond, the total uncertainty shown does not go
  below ± 1 ms.

### Verification

- 70 unit tests (including the `GnssClock` computation and source priority), `lint` and build
  checked. The GNSS atomic source was confirmed active on a real phone, outdoors; its actual
  accuracy has not been measured.

## v1.1.1 — October 19, 2026

Maintenance release: same behavior as v1.1.0, published from the main branch.

APK: `dist/SatTemps-v1.1.1-debug.apk` (or `./gradlew assembleDebug`).

### Fixes

- The version number shown by Android is now `1.1.1` (version code 3); previous APKs still
  reported `1.0`.
- The v1.1.0 improvements (error messages, French/English, battery-aware refresh rates, tests)
  are merged into the main branch.
- 54 unit tests, `lint` and build checked on the main branch.

## v1.1.0 — August 30, 2026

Reliability release: no feature removed, but errors are finally explained on screen, texts are
translatable, power use is lower and calculations are covered by tests.

APK: `dist/SatTemps-v1.1.0-debug.apk` (or `./gradlew assembleDebug`).

### Improvements

**Error messages and states**
- Location: service missing, permission denied (with direct access to settings if permanently
  denied), GPS disabled, fix search in progress, signal lost with the age of the last fix.
- Mobile network: telephony unavailable, permission denied, no cell reported by the modem, read
  failure.
- Time: NTP server unreachable or invalid response, sync-in-progress indicator, retries spaced out
  progressively when out of coverage.
- Compass: sensor missing, registration failure, calibration needed.
- 360° scan: rotation coverage is shown and flagged as insufficient below 70 %.

**Languages**
- All texts are in `res/values/strings.xml` (French) and `res/values-en/strings.xml` (English);
  numbers, dates and times follow the phone's language.

**Battery**
- Clock at 40 ms with the screen on, 200 ms in power-saving mode, 1 s in the background.
- Modem polled every second during the 360° scan, every 5 s when idle; GNSS and pointing
  calculations slowed down when not in the foreground.

**Code quality**
- 54 JVM unit tests: geostationary pointing, GNSS sky analysis, NTP encoding, time source
  priority and retry spacing, 360° directional scan, azimuth conversions.
- Calculation logic separated from Android APIs (`SkyAnalysis`, `NtpTimestamp`, `Azimuth`) and
  KDoc on the main classes.

### Unchanged limitations

The v1.0.0 limitations still apply. Field tests on several phones, regions and modems remain to
be done on real hardware.

## v1.0.0 — August 30, 2026

First release of **SatTemps**: exact time and best antenna orientation (GNSS satellite,
geostationary dish, 5G / LTE cell tower).

APK: `app/build/outputs/apk/debug/app-debug.apk` (`./gradlew assembleDebug`).

### New features

**Exact time to the millisecond**
- `HH:mm:ss.SSS` clock in local time and UTC.
- Ranked time reference: GNSS time from the satellite fix, otherwise NTP (`time.google.com`,
  `pool.ntp.org`, `time.cloudflare.com`), otherwise the phone clock.
- Measured offset from the phone clock, estimated uncertainty, age of the last sync and manual
  resync.

**GNSS sky and best orientation**
- Polar sky view (zenith at the center, horizon at the edge) oriented to true north with the
  compass corrected for magnetic declination.
- GPS, Galileo, GLONASS, BeiDou, QZSS, SBAS and NavIC satellites: azimuth, elevation, C/N0, use
  in the fix; color and size based on signal quality.
- Best sky direction computed as a signal-weighted vector average of azimuths, with the rotation
  angle to apply.

**Geostationary dish pointing**
- Azimuth (true north), elevation, LNB tilt (skew) and slant range computed from the GPS position
  for 12 common satellites (Astra 19.2E, Hotbird 13E, Astra 2/3, Eutelsat 5W and 7W, Nilesat 201,
  Turksat 4A, Intelsat 20, SES 4, Hispasat 30W, Galaxy 19).
- Automatic selection of the highest satellite in the sky and hiding of those below the horizon.

**5G / LTE mobile network**
- Operator, data network technology, serving cell and neighbor cells.
- Radio measurements: SS-RSRP / RSRP, RSRQ, SINR, bands (n78, B7...), NCI / CI / PCI / TAC
  identifiers.
- 360° directional scan: signal power sampled per 15° sector while the user turns around, with
  coverage rate, best sector and rotation to apply.

### Technical details

- Kotlin, Jetpack Compose (Material 3), `StateFlow` + `ViewModel` architecture.
- minSdk 26, targetSdk 34, Java 17, AGP 8.5.2, Gradle 8.7.
- Permissions: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `READ_PHONE_STATE`, `INTERNET`.
- Built-in SNTP client, no third-party network dependency.

### Known limitations

- Neighbor cells depend on the modem and manufacturer: some phones only report the serving cell.
- The cell tower direction is an estimate based on the power measured during rotation (the body
  blocks the signal on the opposite side), not a triangulation.
- The list of geostationary satellites is static (Europe, Africa, part of the Americas).
- Android does not expose the leap second: the time follows the UTC scale.
- Not tested on real hardware: GPS, compass and 5G modem cannot be simulated in the build
  environment.
