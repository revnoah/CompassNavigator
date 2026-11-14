# Compass Navigator

Compass Navigator is an Android compass that displays magnetic heading alongside geographic (true north) heading and location information. Its portrait screen combines a rotating compass dial with coordinates, altitude, accuracy, and magnetic declination.

## About

Use Compass Navigator to read the direction your phone is facing and compare magnetic north with true north at your current location. The app uses Android's rotation vector sensor when available, falling back to accelerometer and magnetometer readings. Location supplies the position and time used by Android's geomagnetic model to calculate declination.

This is a single-screen compass, without maps, destination search, route guidance, saved waypoints, or location history. The declared app version is `1.0` (`versionCode` 1).

## Features

- Rotating dial with cardinal and intercardinal directions and a numeric magnetic heading.
- Separate magnetic and geographic heading readouts, corrected for local declination once a location is available.
- Latitude and longitude to five decimal places, altitude and reported accuracy in meters, and location provider information.
- GPS and network location updates, accepting either precise or approximate location permission.
- Status messages for missing compass sensors, denied location permission, and unavailable location services.

## Built with

Kotlin and Android Views, with a custom Canvas-based `CompassView` and Android sensor, location, and geomagnetic APIs. The project declares Kotlin 2.0.21, Android Gradle Plugin 8.13.2, and Gradle 8.13 through the included wrapper; dependency versions live in [the version catalog](gradle/libs.versions.toml).

## Getting started

### Prerequisites

- Android Studio with support for Android Gradle Plugin 8.13 and Android SDK Platform 36.
- JDK 17 to run the build, as specified by the [AGP 8.13 compatibility documentation](https://developer.android.com/build/releases/agp-8-13-0-release-notes). The app's Java/Kotlin bytecode target is 11; that is separate from the build JDK.
- An Android 7.0 (API 24) or newer device. The app compiles and targets API 36.
- For real compass readings, a device providing a rotation vector sensor or both an accelerometer and magnetometer. An emulator can exercise the UI, but physical hardware is needed to assess real compass behavior.
- Internet access for the initial Gradle and dependency downloads from the configured repositories.

### Installation

1. Open this project directory in Android Studio, choosing the folder containing `settings.gradle.kts`.
2. Install SDK Platform 36 through SDK Manager, select JDK 17 as the Gradle JDK, and sync the project. Android Studio can create `local.properties` with your machine's SDK location.
3. Select the `app` module and a connected Android device, then use **Run** to install and launch the debug app.

To build a debug APK from the project root:

```sh
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. With an authorized device connected and available to Android's device tools, install it with:

```sh
./gradlew :app:installDebug
```

Then open **Compass Navigator** on the device. On Windows, use `gradlew.bat` in place of `./gradlew`. These commands describe the configured Android workflow; no build or installation was performed during repository setup.

## Configuration

There are no API keys, accounts, environment files, or remote service endpoints to configure. The machine-specific SDK path belongs in `local.properties`, which is ignored. Keep Gradle wrapper and dependency declarations with the project.

Location access is requested at launch. Precise or approximate permission enables geographic information; denying permission leaves the magnetic compass available. Enable the device's location services to receive fixes. To change a previous permission choice, use Android's app settings.

The app requests GPS and network updates while active, with requested minimum intervals of one second and one meter. It first uses the newest available last-known location, without checking its age, so displayed information may initially be stale. Sensor listeners and location updates are removed when the activity pauses.

## Usage

1. Launch the app on a device with compass sensors, enable location services, and grant location access if you want coordinates and true north.
2. Hold the phone approximately flat and turn it. Read the large degree value and direction label for the magnetic heading; the dial rotates as the heading changes.
3. Once a location appears, compare **MAGNETIC** with **GEOGRAPHIC**. The latter adds **DECLINATION** to the magnetic heading. Check **LAT / LON**, **ALT / ACCURACY**, and **Source** for the associated fix.

Until a location is obtained, declination remains zero: the geographic value can equal the magnetic heading even though it is labeled `true`. Use the presence of location information to tell whether a correction has been calculated. Missing altitude is shown as unknown. Before sensor acquisition, some panel values retain their initial zero values; use the main heading and sensor status to judge availability.

Nearby magnetic interference and sensor quality affect readings. The fallback magnetometer path can display a figure-eight calibration prompt when the sensor reports unreliable accuracy. This implementation has no dedicated calibration screen.

Readings are held in memory; the app does not implement saving, exporting, or transmitting location history. Its manifest declares location permissions and no Internet permission. Android's selected location providers may use device network services independently of the app.

## Development & testing

Run these commands from the project root after configuring the SDK and JDK:

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The connected test task requires a running emulator or connected device. Existing tests are template checks: local integer addition and an instrumented application-package assertion. They do not validate heading calculations, declination, sensor fallback, permission handling, or screen layout. Build, lint, and test results have not been verified as part of this setup.

The main implementation is in [MainActivity.kt](app/src/main/java/com/revnoah/compassnavigator/MainActivity.kt), which manages permissions, sensor readings, and location updates, and [CompassView.kt](app/src/main/java/com/revnoah/compassnavigator/CompassView.kt), which renders the screen.

## License

Original project code is available under the [MIT License](LICENSE), copyright 2026 [Noah Stewart](https://noahjstewart.com). Bundled Gradle wrapper scripts retain their Apache-2.0 notices; third-party dependencies and assets retain their respective licenses.