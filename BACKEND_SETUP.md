# Binace Smart Earn — Backend & Security Setup Guide

This document describes the Firebase, Cloud Functions, App Check, FCM, and Security Rules configuration required for the upgraded **Binace Smart Earn** Android application (`com.aistudio.binacesmartearn.kxmpzq`).

---

## 1. Firebase Project & Firestore Database

- **Firebase Project ID**: `gen-lang-client-0743144659`
- **Named Firestore Database**: `ai-studio-android-binacesm-06c4f3c1-efe0-4b45-b1d5-721359895f56`
- **Bootstrap Admin Email**: `masterfoodsstore@gmail.com`

---

## 2. Deploy Firestore & Storage Security Rules

Firestore security rules live in `firestore.rules` and enforce zero-trust RBAC across:
- Live Customer Support (`supportTickets`, `supportTickets/*/messages`, `supportAgents`, `userDevices`)
- VIP Membership (`vipConfigs`, `vipMemberships`, `vipHistory`)
- Global Leaderboard (`leaderboardEntries`, `leaderboardPrizes`)
- Notification Center (`notifications`, `announcements`, `announcementReads`)
- Account Security (`securityProfiles`, `securityDevices`, `securityEvents`, `securityOtps`)

Run rules verification locally:
```bash
firebase emulators:exec --only firestore "FIRESTORE_EMULATOR_HOST=127.0.0.1:8085 node --test firestore.test.js"
```

Deploy rules:
```bash
firebase deploy --only firestore:rules,storage
```

---

## 3. Deploy Cloud Functions (Server-Side Calculations, Triggers & OTP)

All server-side triggers and HTTPS callable functions live in `functions/index.js`:

- `onSupportTicketCreated` & `onSupportMessageCreated`: Sends FCM push notifications for new offline tickets and live chat replies.
- `onTaskCompletionClaimed`, `onDepositReviewed`, `onReferralQualified`: Server-authoritative recalculation of verified VIP tier progression and Daily / Weekly / Monthly / Referral / VIP leaderboard rankings.
- `onAnnouncementWritten` & `dispatchScheduledAnnouncements` (every 5 minutes): Publishes scheduled broadcasts and dispatches multi-device FCM push alerts.
- `onSecurityDeviceCreated`: Records suspicious login alerts and pushes real-time notifications when a new unrecognized device signs in.
- `syncVipAndLeaderboard`: Authenticated callable to resync the caller's VIP & leaderboard document.
- `rebuildAllVipAndLeaderboard`: Admin-only callable to recompute all users' VIP tiers & rankings.
- `requestOtpChallenge` & `verifyOtpChallenge`: Server-generated cryptographically random 6-digit OTP challenges delivered via FCM push and validated server-side with attempt throttling (max 5 attempts, 5-minute expiry).

Install dependencies and deploy:
```bash
cd functions
npm install
firebase deploy --only functions
```

Make sure the Firebase Cloud Functions runtime service account has permissions to read/write the named Firestore database (`ai-studio-android-binacesm-06c4f3c1-efe0-4b45-b1d5-721359895f56`) and send FCM messages (`roles/datastore.user` and `roles/firebase.sdkAdminServiceAgent`).

---

## 4. Enable Firebase App Check (Play Integrity & Debug Tokens)

`app/src/main/java/com/example/data/remote/AppCheckConfig.kt` automatically initializes App Check on launch before any Compose UI or Firebase calls:
- **Release builds**: `PlayIntegrityAppCheckProviderFactory`
- **Debug builds**: `DebugAppCheckProviderFactory` (with support for the `FIREBASE_APPCHECK_DEBUG_TOKEN` Intent extra persisted synchronously).

To enable enforcement in Firebase Console:
1. Open **Firebase Console -> App Check -> Apps**.
2. Register Android app `com.aistudio.binacesmartearn.kxmpzq` with **Play Integrity** using the release keystore's SHA-256 certificate fingerprint.
3. For local debug testing, add your Logcat debug UUID token (or CI secret) under **Manage debug tokens**.
4. Enforce App Check for **Cloud Firestore**, **Cloud Storage**, and **Cloud Functions** after confirming valid tokens appear in App Check metrics.

---

## 5. GitHub Release APK Signing Secrets

`app/build.gradle.kts` preserves release signing via environment/Gradle properties consumed by `.github/workflows/android-build.yml`:
- `RELEASE_STORE_FILE` (or `ANDROID_KEYSTORE_BASE64`)
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Ensure the Google OAuth Web/Android client IDs in Firebase Authentication include the release keystore SHA-1 / SHA-256 fingerprints so Google Sign-In (`CredentialManager`) succeeds in release APKs as well as debug builds.
