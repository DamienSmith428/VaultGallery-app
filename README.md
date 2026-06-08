# 🔐 Vault Gallery

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-1.7-green.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-lightgrey.svg)](https://developer.android.com)

**Vault Gallery** is a high-security, privacy-focused media storage application for Android. It allows you to protect your sensitive photos and videos using industry-standard AES-256 encryption, ensuring that your private data stays private—even if your device is compromised.

---

## 🚀 Features

- **End-to-End Encryption:** Every file is encrypted using AES-256-GCM before it ever touches your storage.
- **Biometric Security:** Unlock your vault seamlessly with Fingerprint or Face ID integration.
- **Encrypted Thumbnails:** High-performance preview grid using a custom Coil fetcher that decrypts thumbnails on-the-fly entirely in memory.
- **Storage Quotas & Monitoring:** Set hard limits on how much space your vault can consume and track usage with real-time progress indicators.
- **Auto-Lock:** Customizable timers (Immediate to 15 minutes) that lock the vault the moment the app moves to the background.
- **Zero-Knowledge Viewer:** View full-resolution images and stream videos directly from their encrypted state without ever writing plaintext files to disk.
- **Recycle Bin:** Safeguard against accidental deletions with an automated purge system (30, 60, or 90 days).
- **Material 3 UI:** A modern, beautiful interface featuring Dynamic Color (Material You) support and a sleek "Cyberpunk" dark mode.
- **Smart Search:** Quickly find media by filename or tags with a debounced, indexed search engine.

---

## 🛡️ Security Architecture

Vault Gallery is designed with a "Privacy First" philosophy. Here is how we protect your data:

### 1. The Encryption Standard
We use **AES-256** in **Galois/Counter Mode (GCM)**. GCM provides not just confidentiality (encryption) but also authenticity (integrity), ensuring that your files cannot be tampered with while stored.

### 2. Hardware-Backed Key Management
Encryption keys are never stored in your app's data. They are generated and protected by the **Android Keystore System**. On modern devices, these keys are stored in a **TEE (Trusted Execution Environment)** or **StrongBox** (dedicated hardware security module), making them virtually impossible to extract.

### 3. Zero-Knowledge Principle
- **No Plaintext on Disk:** When you view a photo, it is decrypted directly into an in-memory byte array for display.
- **Streaming Decryption:** Videos are decrypted through a secure stream directly into the media player.
- **No Cloud Sync:** Your data never leaves your device. We do not use any cloud providers, meaning you are the only one with access to your files.

---

## 🛠️ Technology Stack

- **UI:** Jetpack Compose with Material 3
- **Dependency Injection:** Hilt
- **Local Database:** Room (Metadata storage)
- **Image Loading:** Coil (with custom `Fetcher` for encrypted files)
- **Video Playback:** ExoPlayer / Media3
- **Concurrency:** Kotlin Coroutines & Flow
- **Data Management:** DataStore Preferences
- **Security:** AndroidX Biometric, AndroidX Security Crypto, KeyStore

---

## 📖 Technical Reference

### Project Structure
```text
com.vaultgallery/
├── data/
│   ├── security/        # AES-256 Logic, KeyStore, Biometric & Auto-Lock
│   ├── repository/      # Central data handling
│   ├── database/        # Room entities and DAOs
│   └── MediaImporter/   # Secure import/export pipeline
├── domain/              # Pure Kotlin models
├── ui/
│   ├── screens/         # Individual Compose screens
│   ├── theme/           # Material 3 & Dynamic Color configuration
│   └── components/      # Reusable UI elements
```

### Build Instructions
1. Clone the repository.
2. Open in **Android Studio Hedgehog** (or newer).
3. Ensure you have **JDK 17** configured.
4. Click **Run** to deploy to a device with API 26+.

---

## 🤝 Contribution & Credits

**Developed by:** [DamienSmith428](https://github.com/DamienSmith428)

Contributions are welcome! If you find a bug or have a feature request, please open an issue or submit a pull request.

---

## ⚖️ License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
