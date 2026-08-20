# Security notes — RgaPro

## Current controls

- The application uses `SecureMainActivity` as the production launcher.
- The legacy `rgapro_local` preferences are wrapped by `SecurePinPreferences`.
- The PIN is transparently migrated from the legacy preference into `SecurePinStore` and the legacy value is removed.
- `SecurePinStore` uses Android Keystore with AES/GCM and a 256-bit key.
- `allowBackup` is disabled in the Android manifest.
- File sharing uses a non-exported `FileProvider` with scoped app paths.

## Important architectural debt

The application still exposes legacy PIN access through the compatibility facade so the existing `MainActivity` can operate without a large rewrite. This is a compatibility layer, not plaintext storage in the normal production launcher path.

The next security refactor should remove that compatibility dependency from `MainActivity` and make `SecurePinStore` the explicit authentication boundary.

The business data layer also remains JSON/SharedPreferences based and should be migrated to Room before the application grows further.

## Release checklist

Before a production release:

1. Verify no plaintext PIN remains in `rgapro_local` after migration.
2. Verify sensitive temporary document files are removed when processing completes.
3. Add brute-force/rate-limit protection for PIN attempts.
4. Migrate business data to Room and define protection for sensitive fields.
5. Enable and test R8/shrinking in release builds.
6. Run unit, migration, OCR, biometric and release APK tests.
7. Purge any sensitive artifacts from Git history where required.
