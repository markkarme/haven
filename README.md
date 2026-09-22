# Haven

Privacy-focused local content and application blocker for Android.

**Flutter** handles UI, navigation, SQLite rules, and settings.  
**Kotlin** handles Accessibility app blocking, VPN/DNS website filtering, Device Admin, and reboot recovery.

No backend. No browsing history collection or upload.

## Features

- Adult + custom website blocking (local DNS VPN)
- App blocking via AccessibilityService + overlay
- Optional Device Admin uninstall friction
- Survives app close and device reboot (when protection was enabled)
- Light / dark themes

## Run

```bash
flutter pub get
flutter run
```

## First-run checklist (device)

1. Open Haven → complete setup.
2. Enable **Accessibility** (select Haven).
3. Grant **VPN** permission.
4. Optionally enable **Device Admin** and notifications.
5. Turn on **Enable protection** in Protection Settings.
6. Block a test app and open it → blocking screen.
7. Add a test domain / visit an adult domain → DNS NXDOMAIN / fail to load.
8. Force-stop Flutter UI; blocking should continue.
9. Reboot; VPN should restore if protection was on.

## Architecture

- `lib/data/` — SQLite models & repositories
- `lib/features/` — screens
- `lib/services/native/` — MethodChannel bridge
- `android/.../kotlin/com/haven/haven/` — native services
