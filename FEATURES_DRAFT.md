# Enhanced mpv-android feature branch

This branch adds first-pass implementations of all six requested feature areas. **It is a draft and has not passed compilation or Android device testing.** Do not distribute it as a stable release yet.

## Included

- MediaStore library with search, folder filters, thumbnails and local playback URI handling
- Continue Watching listing and resume prompt based on bounded on-device playback history
- Rounded, darker playback controls with a highlighted seekbar
- Configurable double-tap seek interval (5–60 s) and pinch-to-zoom in the existing gesture handler
- Subtitle size, color, outline, shadow and vertical-position controls
- OpenSubtitles API search and download into app-private storage with immediate `sub-add` loading

## User controls

Open **Browse video library** from the app home screen. Grant video-library permission when prompted. The original file/document picker still supports files not indexed by MediaStore.

During playback open the upper-right menu for **Subtitle appearance**, **Find and download subtitles**, or **Double-tap seek settings**. OpenSubtitles requires a user-supplied API key, and the download flow may require an authentication bearer token. No credentials are saved by this feature.

Style controls work on unstyled text subtitles. Embedded styled ASS and bitmap tracks can ignore formatting options. Two-finger pinch changes mpv's `video-zoom` property.

## Privacy

History stays in app-local SharedPreferences, up to 100 records. OpenSubtitles searches send the title and language chosen by the user to that provider. Downloaded subtitle files are stored under app-private `files/downloaded_subtitles`.

## Before merging

1. **Compile:** Follow `buildscripts/README.md` on Linux or macOS to build native dependencies, then build Android debug APKs. The project explicitly states Windows/WSL native build is unsupported.
2. **Library:** Test Android 6, 12L, 13, 14+; full/limited/denied media permissions, missing thumbnail, SAF picker fallback, video selection, and folders.
3. **History:** Pause/end playback, reopen, verify resume and Continue Watching after force-stop; verify no accidental history for failed opens.
4. **Playback:** Seekbar, hardware decoder, PiP, TV remote, gestures, rotation, video zoom and subtitle timing.
5. **Subtitles:** Test OpenSubtitles key, authentication, rate-limit/errors, missing results, different languages, and downloading a valid SRT. Check for API behavior changes.
6. **UI:** Test portrait, landscape, small displays, larger-font accessibility, touch and Android TV.
7. **CI:** Ensure GitHub Actions is enabled for this fork. The existing `.github/workflows/build.yml` is configured to build native code and upload APK artifacts after a push or PR.

## Known limitations

- The library is a simple RecyclerView, not a full-featured file explorer; the original file picker provides filesystem navigation.
- History is saved when the activity pauses and is tied to the current playback intent; playlist item transitions need additional verification.
- Zoom is not yet configurable and may interact with some touch overlays.
- Subtitle API reliability depends on the provider's plan, rate limits, and available subtitle formats.
- This branch does not include a successfully compiled or signed release APK.
