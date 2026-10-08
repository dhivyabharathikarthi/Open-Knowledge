# Open Knowledge

A minimalist, offline technical documentation browser with an integrated two-factor privacy-preserving encrypted storage layer.

---

## 1. Concept & Public Persona

Open Knowledge presents itself strictly as a clean, lightweight documentation reference application for software engineers and systems architects.

The public-facing application contains:
- **Knowledge Home**: Categorized documentation articles across Linux & Operating Systems, Transport Protocols, Distributed Systems, Cryptography, and Open Source Software.
- **Search Engine**: Local, client-side documentation search index with scoring.
- **Article Reader**: Clean typography for reading technical specs and architectural briefs.
- **Settings**: Local settings, storage metrics, and version info.

The application contains **zero** photo vault branding, lock icons, camera logos, or suspicious markers.

---

## 2. Authentication & Secret Flow Trigger

```
User Enters Configured Trigger in Search
                 │
                 ▼
       Secret Phrase Recognized
                 │
                 ▼
      System BiometricPrompt (Strong)
                 │
                 ▼
       Application PIN Entry
                 │
                 ▼
  Argon2id KEK + Keystore Cipher Unwraps VMK
                 │
                 ▼
       Private Photo Screen
```

1. **Trigger Phrase**: The user enters their secret phrase into the documentation search bar and presses Search.
2. **Local Gate Check**: The app computes an HMAC-SHA256 verifier over the normalized phrase using constant-time evaluation. The raw phrase is never stored or used as a key.
3. **Biometric Factor**: Android's `BiometricPrompt` authenticates the user against an `AndroidKeyStore` AES-256-GCM key (`BIOMETRIC_STRONG`). Device credentials fallback is prohibited.
4. **PIN Factor**: User enters a 6–12 digit numeric PIN, which derives a Key Encryption Key (KEK) via memory-hard Argon2id (32MB, 3 iterations).
5. **Key Unwrapping**: The authenticated Biometric cipher unwraps the outer envelope, and the PIN KEK unwraps the inner envelope, releasing the 256-bit Vault Master Key (VMK) into memory.

---

## 3. Storage & Encryption Layout

All private photos, thumbnails, and metadata are encrypted locally:
- **Zero Cloud**: 100% offline. No internet permission in `AndroidManifest.xml`.
- **System Photo Picker**: Uses Android's `PickMultipleVisualMedia` contract. No broad storage permissions required.
- **Envelope Encryption**: Each imported photo has a unique 256-bit AES-GCM photo key (PKEK), wrapped under the VMK.
- **Encrypted Thumbnails**: Thumbnails are downscaled, compressed, and encrypted immediately upon import. No unencrypted thumbnails or caches are ever written to disk.
- **In-Memory Rendering**: Thumbnails and full photos are decrypted on-demand directly into memory buffers and cleared when locked.

### Sandbox Directory Structure
```
app-private/
└── vault/
    ├── objects/
    │   └── <uuid>.bin       # Authenticated AES-256-GCM photo container
    └── thumbnails/
        └── <uuid>.thumb     # Authenticated AES-256-GCM thumbnail container
```

---

## 4. Lifecycle & Auto-Lock

- **Immediate Background Lock**: When the application loses focus or transitions to background (`onStop`), the session is terminated immediately:
  - VMK bytes in memory are overwritten with zeroes.
  - In-memory thumbnail caches are evicted.
  - `WindowManager.LayoutParams.FLAG_SECURE` is released.
  - The UI resets to the neutral documentation screen.
- **Recent Apps Obfuscation**: While private photos are open, `FLAG_SECURE` prevents screenshots and hides thumbnails from the Android recent-apps switcher.

---

## 5. Building & Testing

### Compilation
Verify compilation using the project Gradle build:
```bash
gradle compileDebugSources
```

### Running Unit & Robolectric Tests
Run all unit and Robolectric JVM test suites:
```bash
gradle :app:testDebugUnitTest
```

### Building Signed Release APK
To build an APK for testing outside AI Studio:

1. Generate a keystore (if not already present):
```bash
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias openknowledge
```

2. Build release APK:
```bash
gradle :app:assembleRelease
```
The output APK will be generated at `app/build/outputs/apk/release/app-release.apk`.

To build a debug APK:
```bash
gradle :app:assembleDebug
```
The output debug APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.
