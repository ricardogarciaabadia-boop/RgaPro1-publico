# Security notes — RgaPro

## Current controls

- `SecureMainActivity` is the launcher and now requires a six-digit PIN before showing the cartera.
- The session relocks after five minutes without interaction and after returning from the background once that timeout has elapsed.
- PIN attempts are delayed with an increasing lockout after repeated failures.
- The PIN is migrated from the legacy preference into `SecurePinStore`, which uses AES/GCM and Android Keystore.
- The active `rgapro_clients` JSON is transparently migrated and encrypted with a separate AES/GCM key in Android Keystore.
- New captures and archived documents are stored under internal app storage.
- `FileProvider` is non-exported and grants access only to dedicated internal folders; narrow legacy external folders remain enabled so prior documents still open.
- Screenshots and recent-app previews are blocked while the app is open; Android backup is disabled.
- Cleartext network traffic is explicitly disabled. The current app does not declare `INTERNET` or implement a remote API.

These controls have been added in the current source changes but have not yet been compiled or verified on a device.

## Important architectural debt

The app still contains the old `MainActivity` and legacy `policies` / `users_json` stores. The current PIN and cartera wrappers do not encrypt those legacy stores; they must be migrated or retired before those routes are reactivated.

Existing documents in the former external app folders remain readable through limited legacy `FileProvider` paths. A one-time copy into internal storage and safe cleanup of those old files is still required.

New archived documents use the app-private internal sandbox but are not yet encrypted file-by-file. This protects them from ordinary apps, not from a compromised/rooted device or a process with access to the app sandbox.

The active cartera is encrypted JSON in `SharedPreferences`; it should ultimately move to a single Room repository with encrypted document storage and explicit migrations.

## Release checklist

Before a production release:

1. Build and test the PIN gate, lockout, idle timeout, and legacy PIN migration on a device.
2. Test encryption migration, corrupted/missing Keystore keys, and recovery without overwriting encrypted data.
3. Migrate old external documents into internal storage, then remove legacy provider paths when no longer needed.
4. Remove or encrypt legacy `policies` and `users_json` data before reactivating the old UI.
5. Move the cartera to Room and define database/document migrations.
6. Enable R8 for release and test a signed release APK; do not use a debug APK with real customer records.
7. Run unit, migration, OCR, and security checks, then purge sensitive artifacts from Git history where required.
