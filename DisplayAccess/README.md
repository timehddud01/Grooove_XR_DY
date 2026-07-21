# Display Access App

A sample app demonstrating how to connect to display-capable Meta AI glasses, start a session for a selected device, attach the display capability, and render guided content on the glasses.

## Features

- Register and connect to Meta wearable devices
- Select a specific display-capable device from the device list
- Automatically move to the samples list when a device session starts
- Browse the sample entry screen even before the display capability is ready
- Keep "Try it" disabled until the display capability is ready
- Send a guided car maintenance experience to the glasses
- Recognize nearby music using the phone microphone through a backend ACRCloud proxy
- Open firmware and DAT glasses app update flows when required

## Prerequisites

- Android Studio Narwhal (2025.1.1) or newer
- JDK 17 or newer
- Android SDK 36 or newer
- Meta Wearables Device Access Toolkit (included as a dependency)
- Display-capable Meta AI glasses for end-to-end testing

## Setup

1. Open the project in Android Studio or use the Gradle wrapper.
2. Add your app credentials to `local.properties` or export them in your environment.
3. Build and run the sample.

Example `local.properties` values:

```properties
github_token=YOUR_GITHUB_TOKEN
mwdat_application_id=YOUR_APPLICATION_ID
mwdat_client_token=YOUR_CLIENT_TOKEN
music_recognition_proxy_url=http://10.0.2.2:8787
```

`10.0.2.2` is reachable only from the Android Emulator. For a physical phone on the same
trusted Wi-Fi network, replace it with the development computer's LAN address, for example
`http://192.168.0.10:8787`. Use an HTTPS deployment when the phone cannot reach that computer
directly.

## Building the app

### Using Android Studio

1. Clone this repository
1. Open the project in Android Studio
1. Add your personal access token (classic) to the `local.properties` file (see [SDK for Android setup](https://wearables.developer.meta.com/docs/develop/dat/build-integration-android#step-2-add-the-sdk-to-gradle))
1. Click **File** > **Sync Project with Gradle Files**
1. Click **Run** > **Run...** > **app**

### Using Gradle

```bash
./gradlew installDebug
```

## Running the app

1. Launch the app on your Android device.
1. Complete app registration when prompted.
1. Tap a connected display-capable device in the list.
1. The app opens the samples list automatically after the session starts.
1. "Try it" stays disabled while the display capability is preparing.
1. Once the display is ready, tap "Try it" to send the tutorial flow to the glasses.
1. If a firmware update is required, tap "Update firmware" on the connection screen.
1. If session start reports that the app on the glasses is outdated, tap "Update app on glasses" on the connection screen.

The **Music** tab does not require glasses. Start `../music-recognition-proxy`, grant microphone permission, and tap **음악 인식**. Each tap records a fresh 12-second sample. A physical phone needs a backend URL reachable from the phone; use HTTPS outside a trusted local development network.

## Architecture

- `app/src/main/java/.../MainActivity.kt`: App entry point and runtime permission handling
- `app/src/main/java/.../wearables/WearablesViewModel.kt`: Registration and device observation state
- `app/src/main/java/.../display/DisplayViewModel.kt`: Session lifecycle, capability attachment, and display content
- `app/src/main/java/.../ui/AppScaffold.kt`: Navigation between settings and samples, including automatic handoff after session start

## Permissions

- `BLUETOOTH_CONNECT`: Required to communicate with paired wearable devices
- `BLUETOOTH`: Required for Bluetooth-based device discovery and connectivity on supported Android versions
- `INTERNET`: Required by the DAT stack and related services used during wearable communication
- `RECORD_AUDIO`: Required only when the user starts phone music recognition

## Troubleshooting

For issues related to the Meta Wearables Device Access Toolkit, please refer to the [developer documentation](https://wearables.developer.meta.com/docs/develop/dat/) or visit our [discussions forum](https://github.com/facebook/meta-wearables-dat-android/discussions)

## License

This source code is licensed under the license found in the LICENSE file in the root directory of this source tree.
