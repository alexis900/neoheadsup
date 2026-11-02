# AGENTS.md

This file contains working instructions for AI coding agents and human contributors. It is
kept in English on purpose; user-facing documentation remains in Spanish.

## Project overview

NeoHeadsUp is a small Android app that reminds the user to look up when they walk while
using the phone. It is inspired by Digital Wellbeing Heads Up, but it is an independent
implementation with no Google branding or proprietary code.

- Visible name: **NeoHeadsUp**
- Package / `applicationId` / `namespace`: `io.alexis900.neoheadsup`
- Repository: `alexis900/neoheadsup` (private)
- Primary documentation language: Spanish
- License: none; all rights reserved unless a license is added explicitly

## Product constraints

Do not add any of the following without an explicit product decision:

- Internet access or a backend
- GPS or location permissions
- Accessibility services
- Overlays or full-screen alerts
- Invasive or unnecessary permissions
- Automatic data upload or analytics

The app must remain local-only. State belongs in the app's private `SharedPreferences`.

## Detection rules

The detection core is deliberately conservative:

- A walking session is confirmed only after **15 seconds** of continuous WALKING evidence.
- A stop is confirmed only after **10 seconds** without walking evidence.
- The default reminder cooldown is **15 minutes**.
- A session with no recent evidence expires after **10 minutes**.
- An alert is never shown for an unconfirmed session, a session with a pending stop, or a
  session whose evidence is stale.
- Duplicate and out-of-order activity events are rejected.
- Reboots invalidate the previous session. App updates may keep it because the monotonic
  clock remains valid.
- The notification is shown only while the screen is on and unlocked.

Do not weaken these rules to make tests pass. If a timing constant changes, update the
constants, unit tests, README, and this file together.

## Architecture

Keep the existing architecture unless there is a clear, documented reason to change it.

Pure Kotlin detection core (no Android dependencies):

- `app/src/main/kotlin/io/alexis900/neoheadsup/detection/SessionConfig.kt`
- `.../SessionState.kt`
- `.../SessionSnapshot.kt`
- `.../DetectionEvent.kt`
- `.../SessionController.kt`
- `.../AlertDecider.kt`
- `.../SessionUpdate.kt`

Android integration:

- `WalkingTransitionReceiver.kt`: receives Activity Recognition transitions
- `WalkingDetector.kt`: registers/unregisters Play Services
- `DetectionEngine.kt`: connects the core to Android, persistence, and diagnostics
- `SessionStore.kt`: persists the session snapshot
- `RecheckScheduler.kt` / `RecheckReceiver.kt`: confirmation, stop, and expiry checks; an
  in-process `Handler` runs the short confirmation window and `AlarmManager` is the fallback
- `UserPresentReceiver.kt`: rechecks after unlock
- `BootReceiver.kt`: handles reboot and package replacement
- `NotificationActionReceiver.kt`: snooze and disable actions
- `HeadsUpNotifier.kt`: applies the alert policy and posts notifications
- `Diag.kt`: Logcat diagnostics and the latest event shown in the UI
- `MainActivity.kt`: permissions, enable/disable, cooldown, test, diagnostics, and the
  floating settings button
- `SettingsActivity.kt`: theme selector, installed version, and repository link
- `ThemeManager.kt` / `NeoHeadsUpApp.kt`: apply the stored theme through `AppCompatDelegate`
- `Prefs.kt`: private preferences, including the `system`/`light`/`dark` theme mode

The UI uses Android Views, XML, and **Material Components 3** (`AppCompatActivity`,
`Theme.Material3.DayNight.NoActionBar`, Material cards, buttons, switches, exposed dropdowns,
FAB, toolbar, and dialogs). It does not use Jetpack Compose. Do not migrate to Compose as part
of an unrelated fix. If a migration is requested, treat it as a separate change with its own
validation plan.

## Build and test commands

Use JDK 17 and a configured Android SDK. `local.properties` must not be committed.

```bash
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
```

Expected local environment used during development:

```bash
export JAVA_HOME=/tmp/opencode/jdk
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/Android/Sdk"
```

Build outputs:

- App APK: `app/build/outputs/apk/debug/app-debug.apk`
- Test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

Install and run the device smoke test:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  io.alexis900.neoheadsup.test/io.alexis900.neoheadsup.WalkingTestRunner
```

The device smoke test uses synthetic transitions. It is useful, but it does **not** replace a
real-walk validation on a physical device.

Check real diagnostics with:

```bash
adb logcat -s NeoHeadsUp
```

## Testing expectations

Before claiming a code change is complete:

1. Unit tests pass: `./gradlew testDebugUnitTest`.
2. The debug APK and test APK build.
3. Android lint has no errors.
4. If detection behavior changed, run the instrumented smoke test on a device. The test
   checks that the 15 s and 10 s confirmation windows stay within tolerance, so an inexact
   alarm cannot silently replace the in-process tick.
5. If behavior depends on real motion, ask the user to perform a real walk while the app is
   enabled through the UI, not by writing `enabled=true` directly.
6. Verify the persisted state and relevant diagnostic lines.

Remember that Activity Recognition can report false transitions. The state machine adds
confirmation windows and evidence expiry, but it cannot make the underlying platform sensor
perfect. Document this limitation instead of hiding it.

## Coding conventions

- Keep the detection core pure and JVM-testable.
- Use Kotlin coroutines/tasks only where the existing Android APIs require them.
- Keep comments focused on why a decision exists, not on restating the code.
- Put every user-facing string in both `app/src/main/res/values/strings.xml` (English base)
  and `app/src/main/res/values-es/strings.xml` (Spanish). Do not hardcode UI text in Kotlin
  or layout files.
- Keep `app/src/main/res/xml/locales_config.xml` and the manifest `android:localeConfig`
  attribute in sync with every supported language.
- Keep the UI colors in `values/colors.xml` and `values-night/colors.xml`; do not hardcode
  colors in layouts or Kotlin. The app exposes **System / Light / Dark** in its own settings,
  stores the choice in `SharedPreferences`, and applies it with `AppCompatDelegate` before
  activities are created. The brand color stays constant for the launcher, notification icon,
  and floating settings button.
- Use Material 3 components and attributes (`Theme.Material3.*`, `?attr/colorSurface`,
  `MaterialCardView`, `MaterialButton`, `MaterialSwitch`, `MaterialAutoCompleteTextView`,
  `MaterialAlertDialogBuilder`) instead of custom backgrounds for ordinary controls.
- Settings live in `SettingsActivity` and open from the main screen's floating gear button;
  do not reintroduce a navigation drawer or hamburger menu.
- Preserve the diagnostic format: timestamp, transition/tick, activity, previous and new
  state, accepted/rejected reason, and alert decision reason.
- Keep notification channel id `heads_up` and notification id `2001` unless there is a
  migration reason to change them.
- Keep `SharedPreferences` names in the `neoheadsup_*` namespace.
- Do not log secrets or user content.

## Commit conventions

All commits must follow the **Conventional Commits** specification:

```text
<type>(<optional scope>): <description>

[optional body]

[optional footer(s)]
```

Use one of these types:

- `feat`: a user-facing feature
- `fix`: a bug fix
- `docs`: documentation only
- `test`: tests only
- `refactor`: code change that neither fixes a bug nor adds a feature
- `perf`: performance improvement
- `build`: build system or dependency changes
- `ci`: CI configuration changes
- `chore`: maintenance that does not fit another type

Rules:

- Write the description in the imperative mood, lowercase, and without a trailing period.
- Keep the subject concise, ideally under 72 characters.
- Use a scope when it clarifies the affected area, for example `detection`, `alarm`, `ui`,
  `notifications`, `docs`, or `agents`.
- Mark breaking changes with `!` after the type/scope and explain them in a
  `BREAKING CHANGE:` footer.
- Reference issues in the footer when applicable.
- Do not use vague messages such as `changes`, `fixes`, or `update`.

Examples:

```text
feat(detection): require continuous walking evidence
fix(alarm): reschedule confirmation after a missed tick
docs: update Pixel 7a validation checklist
test(detection): cover stale session expiry
refactor(notifications): centralize alert policy checks
chore: update Gradle wrapper
```

## Repository and Git rules

- The repository is private. Do not publish it publicly without explicit user approval.
- Do not add a license unless the user asks for one.
- Never commit:
  - `local.properties`
  - signing keys or keystores
  - tokens, credentials, or `.env` files
  - build outputs such as `app/build/`
  - `.gradle/` caches
- Do not use `git push --force`.
- Do not overwrite or delete unrelated user changes.
- Before reporting success, verify the remote state with `git ls-remote` or the GitHub API and
  confirm that local `HEAD` matches `origin/main`.

## Current validation status

Validated on a Pixel 7a:

- New package installs and appears as NeoHeadsUp.
- Runtime permissions and notification channel work.
- Synthetic transition smoke test passes, including the 15 s / 10 s timing tolerances.
- A 90-second seated baseline produced no walking transition and no alert.
- A real walk confirmed the session after 15.007 s and posted the reminder; the stop returned
  the state to `IDLE` after 10.004 s.
- The alert was not repeated during the same walk (`reason=cooldown`).
- The snooze and disable notification actions worked; re-enabling the feature cleared snooze
  and restored activity-recognition registration.
- English and Spanish UI, notification actions, and permission dialogs were verified on the
  device, including Android's per-app language selector.
- Material 3 main and settings screens render correctly in light and dark mode; the exposed
  cooldown and theme dropdowns, floating settings button, toolbar back navigation, version
  row, repository link, and Material permission dialog were exercised on the device.
- The theme selector persists `system`/`light`/`dark`, and the System option follows the
  device night-mode changes.

Still pending before calling the physical validation complete:

- Rapid enter/exit behavior with real motion.
- Lock/unlock behavior while walking.
- Cooldown across two separate walks (a same-walk cooldown denial was observed).
- Reboot behavior.
- Full review of the Pixel 7a checklist in `README.md`.

## Useful files

- `README.md`: Spanish product and validation documentation
- `app/build.gradle`: package, SDK levels, dependencies, and test runner
- `app/src/main/AndroidManifest.xml`: permissions and components
- `app/src/main/res/layout/activity_main.xml`: Material 3 main screen
- `app/src/main/res/layout/activity_settings.xml`: Material 3 settings screen
- `app/src/main/res/values/themes.xml` and `values-night/themes.xml`: Material 3 themes
- `app/src/main/res/values/colors.xml` and `values-night/colors.xml`: light/dark palettes
- `app/src/main/kotlin/io/alexis900/neoheadsup/detection/`: pure detection core
- `app/src/test/kotlin/io/alexis900/neoheadsup/detection/DetectionCoreTest.kt`: JVM tests
- `app/src/androidTest/kotlin/io/alexis900/neoheadsup/WalkingTestRunner.kt`: device smoke test
