# Current project status

**Audit date:** 2026-10-16 (Asia/Manila)  
**Evidence:** current source/configuration inspection, repository status, installed SDK inventory, and one local debug assembly. Historical notes below are labeled as such.

## Repository and files

- Branch `main`, at `83cec26` (`first commit`), tracking `origin/main`. Only this local branch was listed.
- Before the audit build, `gradle.properties` and `gradle/wrapper/gradle-wrapper.properties` were already modified. Keep those changes: they raise the wrapper from Gradle 9.6.0 to 9.8.1 and add Gradle memory/worker/cache settings.
- Android Studio metadata is also currently modified: `.idea/deploymentTargetSelector.xml`, `.idea/gradle.xml`, `.idea/misc.xml`. Four IDE files are untracked: `.idea/codeStyles/Project.xml`, `.idea/codeStyles/codeStyleConfig.xml`, `.idea/deviceManager.xml`, and `.idea/vcs.xml`. These were preserved and not edited as part of the audit.
- The recovery memory files are new: root `AGENTS.md` and the four documents in `docs/ai-context/`.
- No README or existing `AGENTS.md`/project documentation was present before this recovery. There are 154 tracked repository files. `artifacts/` contains a checked-in debug APK, a screen-capture archive, screenshots, and screenshot notes.
- The newly assembled APK is ignored at `app/build/outputs/apk/debug/app-debug.apk`; the checked-in APK under `artifacts/` was not replaced. Their SHA-256 values differ.

## Verified implementation

- Single-module Android application (`:app`), Kotlin/Compose Material 3, MVVM-style ViewModels, Room, coroutines/Flow, DataStore, WorkManager, and Koin.
- Room database version 6, with migrations 1→2→3→4→5→6 and exported schema snapshots 1–6. No destructive migration fallback was found. `PosStoragePaths` copies an older database into app-private storage only after a WAL checkpoint and integrity check, retaining the source if the copy fails.
- Setup, local owner/cashier authentication, staff permissions, rate limiting, encrypted session marker, owner-only operations, and one-time owner-PIN approval for a restricted selling-price change are implemented.
- Source includes the cashier, product and barcode management, shared base-unit stock, supplier deliveries, customers/credit payments, expenses, refunds, sales reports/CSV, encrypted backup/restore, and printing paths described in `PROJECT_OVERVIEW.md`.
- `demoDebug` is the only variant that includes initial demo users/data and developer controls. Staging debug and every release variant use no-op hooks. Demo and staging application IDs end in `.demo` and `.staging` so their local app data stays isolated.
- Inter font files (regular, medium, semibold, bold), the SIL Open Font License text, a MariSuki logo, and launcher assets are present. Inter is applied by the Compose typography definition.
- Adaptive navigation uses bottom navigation at compact widths and a rail at wider widths. The cashier source has a persistent checkout pane for expanded layouts, an adaptive product grid, and a cart sheet for compact layouts.

## Verification performed in this audit

- Environment: Fedora Linux 44; OpenJDK 25.0.4.1 (Red Hat); Gradle wrapper 9.8.1. `gradle/gradle-daemon-jvm.properties` requests Java 25. The `JAVA_HOME` environment variable is unset.
- Android SDK is available under `~/Android/Sdk`; platform `android-37.0` and Build Tools `36.0.0` are installed. `local.properties` points to that SDK and is correctly ignored by Git.
- `adb` is available and currently sees a Samsung SM-A366B device. AVD definitions `Medium_Phone_API_37.0` and `Pixel_Tablet` are listed; no emulator appeared in the connected-device list during the audit. `sdkmanager` was not found on `PATH` and no SDK `cmdline-tools` directory was found.
- `./gradlew --version` succeeded and reported Gradle 9.8.1 running with Java 25.
- `./gradlew :app:assembleDebug` **succeeded** (39 tasks, 19m 24s, first/cold build). The generated APK is under ignored `app/build/outputs/apk/debug/`.
- `./gradlew :app:assembleDemoDebug :app:assembleDemoRelease :app:assembleStagingDebug :app:assembleStagingRelease` **succeeded** (16m 26s). APK inspection found demo fixture strings only in `demoDebug`; release APKs are unsigned because no release signing config is set.
- Build warnings: the configured `android.disallowKotlinSourceSets=false` option is experimental; two native libraries could not be stripped and were packaged unchanged; two deprecated API warnings were emitted in `AuthScreens.kt` and `CreditScreen.kt`; Gradle reports deprecated features that will be incompatible with Gradle 10.
- No JVM or instrumentation tests, standalone lint task, app install, or on-device interaction tests were run for the flavor change. Release assembly ran the `lintVital` tasks. Test files are present; source contains seven JVM test methods and twenty Android instrumentation methods.

## UI and test status

- `artifacts/marisuki-screenshots/SCREENSHOT_NOTES.txt` documents tablet captures at 1280×800 dp, dark-mode captures, and one 360×800 dp compact cashier viewport override. These are historical captures, not a fresh verification of the current build.
- The notes report input-focus ANRs during some popup/dialog transitions under high emulator CPU load. They also state that final-build post-login navigation, setup and all dialogs were not covered. Treat the cause as unconfirmed until reproduced.
- Instrumented tests cover authentication, repository permission/transaction behavior, and a migration from schema 3 through 6. There are no Compose UI tests or backup round-trip tests in the tree. Printer transport has no hardware verification.

## Gaps and risks to prioritize

1. **Protect store data across device changes.** Room data is in app-private SQLite and is not encrypted at rest. Automatic encrypted snapshots are retained locally on the same device, so they do not by themselves recover data after a laptop/tablet reset, app-data removal, or device loss. Make and retain a manual encrypted export outside the device before any migration or reinstall; test restore on disposable data.
2. **Review Android OS backup scope.** The manifest sets `allowBackup="true"`; the referenced backup-rule XML files are template files with no explicit include/exclude rules. Review actual platform backup behavior for customer, sales, staff-verifier, and session data, and set an intentional policy.
3. **Run the existing tests.** Start with local unit tests, then instrumented tests on an available device/AVD. The source test suite has not been run in this recovered environment.
4. **Recheck keyboard/focus interactions.** Reproduce the reported popup/dialog focus ANRs on a less-loaded emulator or physical tablet, then verify the final build after any fix.
5. **Complete responsive and hardware validation.** Cover phone/tablet widths and orientations, increased font scale, full setup/forms, and real Bluetooth/USB printer hardware.
6. **Review debug distribution boundaries.** The tracked APK is a debug artifact. Keep debug fixtures and their built-in test accounts out of production distribution.

No application feature code, Room data, schema snapshots, or checked-in artifacts were intentionally modified during this recovery. Wait for the user to review this status before implementing additional features.

## Product catalogs and bulk import update — 2026-10-17

Reusable categories and unit names, guided product entry, and previewed CSV/XLSX bulk insertion with in-app template downloads are implemented. The feature reuses `categories`, `product_units`, and `app_settings`; Room remains at version 6. Staging debug was installed on the connected physical Samsung without clearing app data. Final staging/demo debug assembly, 16 JVM tests, and 18 isolated on-device catalog/repository tests passed. See `TESTING.md` and `PRODUCT_IMPORT.md` for observed coverage and remaining manual checks.
