# Recall

A private, searchable memory of your phone conversations.

## Overview

Recall reads the phone call log and, with your permission, imports any recordings
produced by your default call recorder app. Every call is stored locally with an
optional transcript and AI summary, and everything is searchable from a single screen.

**Privacy first:** all data stays on-device — call records, transcripts, analyses and
recording files are stored in app-private storage. Nothing leaves the device.

## Features

- Import your entire call history automatically (background syncing)
- Automatic language/SIM/contact resolution using the phone's existing data
- Attach call-recorder audio files (SAF) or record via microphone fallback
- Transcript + summary generation on device
- Full-text search across numbers, names, transcripts and summaries
- Optional app lock with Android Biometric unlock
- "Delete everything" wipes calls, transcripts, analyses and recordings

## Tech stack

- Kotlin, Jetpack Compose (Material 3), single-activity architecture
- MVVM + Repository pattern, Hilt for DI
- Room for local persistence
- WorkManager for background sync & pipeline jobs
- Media3 ExoPlayer for playback
- JDK 21, AGP 8.13.1, Gradle 8.14

## Build

```bash
./gradlew assembleDebug
```

Requires JDK 21 and the Android SDK (path configured in `local.properties`).

## Permissions

- `READ_CALL_LOG` – to index your call history (core feature, on-device)
- `READ_PHONE_STATE`, `RECORD_AUDIO` – optional microphone fallback recording
- `READ_CONTACTS` – resolving names for calls from your number, shown solely on device