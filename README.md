# WiFi Network Importer

A utility app for Android to bulk-import Wi-Fi networks from a CSV file. Designed for modern Android versions where programmatic network addition is restricted for security.

## Testing Status
This application was successfully tested in **December 2025** on a Google Pixel device running **GrapheneOS** (based on Android 16).

## Features

- **CSV Import**: Select a file from device storage using the system file picker.
- **Material You**: Supports dynamic colors and light/dark modes.
- **Bulk Processing**: Efficiently handles large lists of networks.
- **GPLv3 Licensed**: Open source and free to modify.

## CSV Format

The app expects a CSV file with a header row and three columns: `SSID, PASSWORD, SECURITY`.

| Column | Description |
| :--- | :--- |
| **SSID** | The name of the Wi-Fi network. |
| **PASSWORD** | The network passphrase (can be empty for OPEN networks). |
| **SECURITY** | The encryption type. |

### Supported Security Types
`WPA`, `WPA2`, `WPA3`, `WPA-PSK`, `WPA_PERSONAL`, `WPA_WPA2_PERSONAL`, `WPA3_PERSONAL`, `OPEN`, `NONE`, `UNKNOWN` (treated as Open).

## Supported Android Versions

- **Minimum SDK**: Android 16 (API 36)
- **Target SDK**: Android 16 (API 36)
*Note: While the core APIs work on Android 11+, this project is specifically configured for the Android 16 environment.*

## Import Methods

The app provides two distinct ways to import networks:

### 1. Network Suggestions (`WifiNetworkSuggestion` API)
- **How it works**: The app "suggests" a list of networks to the Android system.
- **User Experience**: One-time permission request. The system automatically connects to these networks when they are in range.
- **Best for**: Very large lists (e.g., 300+ networks) where manual approval is impractical.

### 2. Add Networks Intent (`ACTION_WIFI_ADD_NETWORKS`)
- **How it works**: Uses a system-level Intent to save networks directly to the device's saved networks list.
- **User Experience**: Triggers a system dialog. In this app, networks are sent in **batches of 5** for user approval.
- **Best for**: Ensuring networks are permanently saved in the system settings, provided the user is willing to tap "Save" for each batch.

## License

This project is licensed under the **GNU General Public License v3.0**. See the `LICENSE` file for details.
