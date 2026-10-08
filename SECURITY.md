# Security Model & Threat Architecture: Open Knowledge

## 1. Threat Model & Security Boundaries

Open Knowledge is a privacy-focused local Android application designed to operate with a neutral technical documentation appearance while safeguarding user-imported photos through authenticated hardware-backed cryptography and multi-factor authorization.

### What the Application Protects Against
1. **Casual Device Access / Shoulder Surfing**:
   - The primary application interface appears as a neutral technical documentation viewer.
   - No vault branding, locker terminology, camera logos, or suspicious UI markers are present.
2. **Unauthorized Storage Inspection**:
   - Photos, thumbnails, and metadata are never saved unencrypted to device storage or public external storage.
   - All encrypted blobs are stored strictly in app-private sandbox storage (`context.filesDir/vault/`).
   - Every object utilizes AES-256-GCM authenticated encryption with a unique per-object nonce and 128-bit authentication tag.
3. **Single-Factor Compromise**:
   - Both factors are mandatory. The secret trigger phrase alone does not unlock content.
   - Biometric authentication alone does not unwrap the Vault Master Key (VMK).
   - Knowledge of the numeric PIN alone cannot decrypt the outer hardware-bound envelope.
4. **Offline Tampering & Ciphertext Bit-Flipping**:
   - AES-256-GCM authentication tags guarantee that any bit alteration to ciphertexts results in immediate authentication failure and payload discard.

### Limitations & Assumptions (No Unrealistic Security Claims)
- **Compromised Operating Systems**: The application assumes the underlying Android OS and kernel are uncompromised. Rooted devices, malicious kernel modules, or devices infected with firmware-level malware that intercept raw framebuffer memory or memory dumps can circumvent app-level protections.
- **Physical Shoulder Surfing during Unlocked Sessions**: If an adversary observes the screen while an authenticated session is actively open, or photographs the physical screen with an external camera, application cryptography cannot prevent disclosure.
- **Flash Storage Remanence**: The application utilizes atomic writes and secure zeroing overwrites on file deletion. However, modern wear-leveling flash controllers (eMMC / UFS) may retain physical flash blocks beyond the OS filesystem abstraction until garbage collected by the hardware controller.

---

## 2. Cryptographic Architecture & Envelope Design

### Key Hierarchy
```
Device Hardware Security Module / TEE
    │
    ▼
Android Keystore Biometric-Bound Key (AES-256-GCM)
    │  (Unlocks only via BiometricPrompt BIOMETRIC_STRONG)
    ▼
Outer Envelope Layer
    │
    ▼
Inner Envelope Layer (AES-256-GCM)
    │  (Unlocks via Argon2id PIN-Derived Key Encryption Key)
    ▼
Vault Master Key (VMK - 256-bit CSPRNG)
    │
    ▼
Per-Photo Encryption Keys (PKEK - 256-bit CSPRNG)
    ├── Photo Ciphertext (AES-256-GCM + unique 96-bit nonce)
    ├── Thumbnail Ciphertext (AES-256-GCM + unique 96-bit nonce)
    └── Metadata Ciphertext (AES-256-GCM + unique 96-bit nonce)
```

### 1. Secret Flow Trigger (Authentication Gate)
- Configured during first-run setup.
- Evaluated against a protected device-local verifier:
  $$\text{Verifier} = \text{HMAC-SHA256}_{K_{\text{device}}}(\text{normalize}(\text{Phrase}))$$
- Verified using constant-time comparison (`MessageDigest.isEqual`).
- The phrase is never stored in plaintext, never logged, and never acts as an encryption key.

### 2. Biometric Key Layer
- Managed via `AndroidKeyStore`.
- Key alias: `OpenKnowledge_BioKey_v1`.
- Configured with `setUserAuthenticationRequired(true)` and `KeyProperties.AUTH_BIOMETRIC_STRONG`.
- CryptoObject passed to `BiometricPrompt.authenticate()`.

### 3. PIN Derivation (Argon2id)
- Numeric PIN (6 to 12 digits).
- Derived via Argon2id (`Argon2BytesGenerator`):
  - Memory: 32 MB (32,768 KiB)
  - Iterations: 3
  - Parallelism: 1
  - Unique 32-byte CSPRNG salt

### 4. Container Format (`EncryptedObject`)
Every encrypted payload utilizes a structured binary container format:
```
[MAGIC (4 bytes): 0x4F, 0x4B, 0x56, 0x31 "OKV1"]
[VERSION (1 byte): 0x01]
[ALGORITHM_ID (1 byte): 0x01 (AES-256-GCM)]
[KEY_VERSION (2 bytes): 0x0001]
[OBJECT_UUID (16 bytes)]
[NONCE (12 bytes): Unique CSPRNG IV]
[PAYLOAD_LENGTH (4 bytes)]
[CIPHERTEXT + 16-BYTE AUTH TAG]
```

---

## 3. Memory & Lifecycle Hygiene

- **Session Invalidation**: When the application leaves the foreground (`Activity.onStop()`), `SecureVaultSession.lock()` is invoked immediately:
  - The Vault Master Key byte buffer is zeroized (`Arrays.fill(bytes, 0)`).
  - The in-memory decoded thumbnail cache is evicted.
  - Active screen resets to neutral documentation view.
- **Screen Capture Prevention**: `WindowManager.LayoutParams.FLAG_SECURE` is active while the private gallery or photo viewer is open to prevent screen capture and OS recent-apps task screenshot caching.
