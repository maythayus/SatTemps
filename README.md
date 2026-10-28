# SatTemps

Android app (Kotlin / Jetpack Compose) that shows the exact time (hour, minute, second, millisecond) and the best orientation to pick up:
- GNSS satellites (GPS, Galileo, GLONASS, BeiDou),
- a geostationary satellite (dish: azimuth, elevation, LNB tilt),
- the strongest 5G NR / 4G LTE cell tower.

## Features

### "Time" tab
- Millisecond clock based on GNSS time from the satellite fix (first choice), otherwise an NTP server (`time.google.com`, `pool.ntp.org`, `time.cloudflare.com`), otherwise the phone clock.
- Shows the measured offset from the phone clock and the estimated uncertainty.
- Local time + UTC, GPS position (latitude, longitude, altitude, accuracy).

### "GPS Sky" tab
- Polar sky view: each satellite is placed by its azimuth and elevation; color and size show signal quality (C/N0 in dB-Hz).
- The view rotates with the phone (compass corrected for magnetic declination: true north).
- Best direction = signal-weighted vector average of the azimuths, with the rotation angle to apply.

### "Dish" tab
- From the GPS position, computes pointing toward a dozen common geostationary satellites (Astra 19.2E, Hotbird 13E, Eutelsat 5W/7W, Nilesat, Turksat...).
- Azimuth (true north), elevation, LNB tilt (skew), slant range, highest satellite.

### "Network" tab
- Operator, technology (5G NR / LTE / UMTS / GSM), serving cell and neighbor cells: power (SS-RSRP / RSRP), quality (RSRQ), SINR, bands, identifiers (NCI, PCI, TAC).
- 360° directional scan: start the scan and slowly turn around; the app samples signal power per 15° sector and shows the direction of the best-received tower, along with the rotation to apply.

## Build

Requirements: JDK 17 and the Android SDK (platform 34, build-tools 34).

```bash
# Set the SDK path
echo "sdk.dir=/path/to/Android/sdk" > local.properties

./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Install on a phone connected over USB (USB debugging enabled):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tests

The calculations (geostationary pointing, GNSS sky analysis, NTP encoding, time source priority, 360° directional scan, UI azimuths) are covered by JVM unit tests, no emulator needed:

```bash
./gradlew test
./gradlew lint
```

Field tests (several phones, several regions, different modems) still need to be done on real devices.

## Languages

All displayed texts are in `app/src/main/res/values/strings.xml` (French, default language) and `app/src/main/res/values-en/strings.xml` (English). To add a language, copy the file into a `values-xx` folder.

## Permissions

| Permission | Usage |
|---|---|
| `ACCESS_FINE_LOCATION` | GPS position, satellite status, cell information |
| `READ_PHONE_STATE` | network technology and cell identifiers |
| `INTERNET` | NTP synchronization |

## Time accuracy

- GNSS: the GPS fix carries a UTC timestamp derived from the satellites' atomic clocks. It is compared with the phone's monotonic clock (`elapsedRealtimeNanos`) to remove the fix reporting latency; typical accuracy is a few tens of milliseconds.
- NTP: standard SNTP algorithm; the displayed uncertainty is half the round-trip time.
- Android does not expose the leap second; the displayed time follows the UTC scale.

## Known limitations

- Neighbor cell information depends on the modem and manufacturer; some phones only report the serving cell.
- The cell tower direction is inferred from the power measured during rotation (the user's body blocks the signal on the opposite side): it is an estimate, not a triangulation.
- The list of geostationary satellites is static and covers Europe, Africa and part of the Americas.
