# Google Play listing draft — Haven

Use these when the developer account is approved. Paste into Play Console.

## App identity

- **Application ID:** `com.haven.haven` (final — do not change after first upload)
- **App name:** Haven
- **Default language:** English (United States) — or Arabic if you prefer

## Short description (max 80 characters)

```
Local app & website blocker. Privacy-focused. On-device only.
```

## Full description

```
Haven helps you block distracting or unwanted apps and websites on your Android device.

Everything runs locally on your phone. Haven does not read passwords and does not upload browsing history.

Features:
• Block apps with an on-device blocking screen
• Block adult and custom websites
• Optional Device Admin to make uninstall harder while protection is on
• Light and dark themes
• Continues after reboot when protection was enabled

Permissions (explained):
• Accessibility — detect blocked apps and website addresses in supported browsers
• Device Admin (optional) — extra confirmation before uninstall
• App list access — choose which apps to block

You stay in control. Disable protection and permissions anytime in system settings.
```

## Graphics checklist

| Asset | Spec | Notes |
|-------|------|--------|
| App icon | 512×512 PNG | Export from `assets/branding/haven-logo.png` |
| Feature graphic | 1024×500 | Logo + “Haven” + short tagline on brand background `#0A1628` |
| Phone screenshots | min 2, aim for 4–8 | Home, app list, website rules, block overlay, settings |

## Privacy policy URL

1. Host `store/privacy-policy.html` (Google Sites, GitHub Pages, Netlify, etc.)
2. Paste the public HTTPS URL into Play Console → App content → Privacy policy

## Data safety (suggested answers)

Assuming current local-only behavior:

- Does your app collect or share user data? **No**
- If Play still asks for details: no location, no personal info, no financial, no health, no messages, no photos, no files, no audio, no device IDs for advertising
- Security practices: data is not transmitted off device for Haven’s own services

Re-check this form if you later add analytics, crash reporting, ads, or accounts.

## Restricted permissions / declarations

Prepare short justifications:

### QUERY_ALL_PACKAGES
> Haven lists installed apps so the user can select which apps to block. The list stays on-device.

### Accessibility
> Haven uses Accessibility to detect when a blocked app is in the foreground and to read website addresses from supported browser address bars, then shows a blocking screen. It does not read passwords and does not upload browsing history.

### Device Admin
> Optional. Adds an extra confirmation step before uninstall while protection is enabled. Users can disable Device Admin in system settings at any time.

## Content rating

Complete the IARC questionnaire honestly. Haven is a productivity / parental-control style blocker, not a kids game.

## Release build

```bash
flutter build appbundle --release
```

Output:
`build/app/outputs/bundle/release/app-release.aab`

Upload that AAB to **Internal testing** first.

## Keystore backup (critical)

Files (gitignored — back up offline):

- `android/haven-upload-keystore.jks`
- `android/key.properties`
- `android/keystore-credentials.txt`

Losing the keystore means you cannot update the same Play listing.
