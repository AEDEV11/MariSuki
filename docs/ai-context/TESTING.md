# Testing and verification

## Test coverage present in the repository

- Sixteen local JVM test methods across `PosMoneyTest`, `EscPosCommandBuilderTest`, `ProductBarcodeTest`, and `ProductImportTest`.
- Thirty-one Android instrumentation test methods across `AuthRepositoryInstrumentedTest`, `AuthScreensInstrumentedTest`, `PosDatabaseMigrationTest`, `PosRepositoryInstrumentedTest`, and `ProductCatalogImportInstrumentedTest`.
- Existing coverage includes money/quantity parsing, ESC/POS command construction, barcode encoding, Room migration 3→6 and record preservation, authentication/session behavior, repository permissions, sale/stock/credit idempotency, reporting, delivery costing, and refunds.
- Compose UI tests cover typed username login with keyboard submission and the complete initial setup flow, using an in-memory database and isolated preference stores. No backup/restore round-trip or physical printer test was found.

## Useful commands

Run from the repository root with the checked-in wrapper:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

Instrumentation requires an available Android emulator or connected device. Avoid install, clear-data, uninstall, or database-reset operations on a device containing store data unless the user explicitly asks and a recovery plan is established.

## Manual verification priorities

1. Run the migration test on a device/emulator and verify preserved records through every supported historical schema path.
2. Exercise encrypted export and restore using a disposable test database; verify wrong-password failure, photo references, and recovery after interruption.
3. Validate the UI across compact phone and tablet widths, portrait and landscape, light and dark mode, and increased font scale. Exercise keyboard/focus transitions rather than screenshot-only review.
4. Test Bluetooth and USB printers against the actual target hardware and verify receipts and labels.

Record command, date, device/AVD, and result when verification is performed. Historical results in screenshot notes are context only; they are not current test results.

## Authentication UI verification — 2026-10-16

- `./gradlew :app:assembleStagingDebug :app:assembleStagingDebugAndroidTest :app:testStagingDebugUnitTest --console=plain` succeeded; all 7 JVM tests passed.
- On the connected Samsung SM-A366B, the 10 `AuthRepositoryInstrumentedTest` methods passed, including normalized typed usernames, cashier PINs, disabled accounts, and shared login-attempt throttling.
- The final `AuthScreensInstrumentedTest` run through `adb shell am instrument -w -r -e class com.aesprt.marisuki.AuthScreensInstrumentedTest com.aesprt.marisuki.staging.test/androidx.test.runner.AndroidJUnitRunner` passed both tests. These exercise typed username login using the keyboard and full initial setup, including language switching and resetting scroll between steps. Earlier harness and language-switch failures were corrected before this final run.
- Phone captures of login, welcome, store details, owner credentials, preferences, and completion were visually inspected. Tablet and dark-mode visual verification remain outstanding for this refresh.
- The final staging debug APK was installed on the physical Samsung with `adb install -r --user 0`; app data was retained. No AVD was used, and tests used in-memory databases and isolated preference files.

## Sales catalog UI verification — 2026-10-16

- `./gradlew :app:assembleStagingDebug :app:testStagingDebugUnitTest --console=plain` succeeded; all 7 JVM tests passed.
- On the Samsung SM-A366B, inspected the updated sales catalog with an existing product photo and opened/dismissed its selling-unit dialog. Confirmed the full-width mint image area, separate transparent favorite control, readable unit conversions/prices, and cart empty state. The final polish shortens the empty-cart label and uses correct singular/plural product counts.
- No checkout, favorite changes, product edits, database reset, or AVD interaction was performed. Tablet, dark-mode, and increased-font visual checks remain outstanding for this refresh.
- Installed the final staging debug APK with `adb install -r --user 0` on the same physical Samsung and reopened the app; existing app data was preserved.

## Cart and product management UI verification — 2026-10-16

- `./gradlew :app:assembleStagingDebug :app:testStagingDebugUnitTest --console=plain` succeeded; all 7 JVM tests passed.
- Installed staging debug on the connected Samsung SM-A366B using `adb install -r --user 0`, preserving app records. No AVD was used.
- Visually checked the product list and the new phone product-detail sheet. Confirmed readable enabled cards, full product photos, prices, stock badges, barcode, inventory metrics, and available actions.
- Added one existing Pack to the unsaved cart to match the supplied example, inspected the cart/payment layout, and opened/dismissed the amount keyboard. Confirmed the total and checkout action remain visible above the keyboard. No checkout, stock update, favorite change, product edit, or database reset was performed; the unsaved Pack remains in the cart.
- Tablet, dark-mode, increased-font, and external printer checks were not run for this refresh.

## Product dialogs and selected-chip UI verification — 2026-10-16

- `./gradlew :app:assembleStagingDebug :app:testStagingDebugUnitTest --console=plain` succeeded; all 7 JVM tests passed.
- Installed staging debug on the Samsung SM-A366B using `adb install -r --user 0`; no app data was cleared and no AVD was used.
- Phone screenshots confirmed the grouped product-form sections, branded header, consistent material fields, and white product-label dialog with checked selected size and right-aligned footer buttons. The current product form was left open without saving or discarding its contents.
- Product action buttons now use right-aligned wrapping rows and consistent corners. Selected Tingi/Wholesale chips show a check icon. Stock and product confirmation/approval dialogs use the same surface/icon styling. These changes compile; separate on-device interaction checks of every confirmation, stock action, and sale-type chip were not performed.
- No printing, product save, stock adjustment, checkout, or data reset was triggered by these checks. Tablet, dark-mode, increased-font, and physical printer validation remain outstanding.

### 2026-10-16 — Mobile product actions and Utang refresh

- Product action buttons fill the mobile width; expanded windows retain wrapping, right-aligned actions.
- Utang now has an emerald balance summary, customer balance cards, full-width payment actions, and phone-accessible ledger history. Customer and payment forms share a responsive white dialog frame, primary icon badges, and Material fields. Payment chips show a selected check; valid amounts preview the remaining balance. Existing permission gates, payment limits, idempotency keys, and repository callbacks are preserved.
- Executed `./gradlew :app:assembleStagingDebug :app:testStagingDebugUnitTest --console=plain`: BUILD SUCCESSFUL; 7 tests, 0 failures/errors/skips. `git diff --check` passed for edited tracked source/resource files.
- Installed with `adb -s adb-RRCY5099DHB-xEKG59._adb-tls-connect._tcp install -r --user 0 app/build/outputs/apk/staging/debug/app-staging-debug.apk`: Success on physical Samsung SM_A366B; no AVD used, app data retained.
- Visually checked Utang, Add customer, payment, phone ledger, and all full-width product actions on the connected phone. Opened/dismissed forms without saving customers or posting payments; did not change stock, print, archive, or favorite products.
- Tablet, dark theme, large font, and keyboard-open layouts were not manually checked in this pass.

## More tools and stock UI verification — 2026-10-16

- Added shared white tool cards, primary icon badges, checked selection chips, and a responsive dialog frame with independently scrolling content and fixed actions. Applied the visual system to the More menu, inventory and stock adjustment, supplier deliveries, expenses, settings, backup/restore confirmations, report cards/periods, printer configuration, and staff forms/permission groups.
- Staging debug now shows read-only build and device information in Developer settings. Demo developer controls retain their existing debug-only actions and now use the shared card/dialog styling. Release developer hooks remain unchanged.
- Executed `./gradlew :app:assembleStagingDebug :app:assembleDemoDebug :app:testStagingDebugUnitTest --console=plain`: final BUILD SUCCESSFUL; 7 tests, 0 failures/errors/skips. Targeted `git diff --check` passed. No schema, repository, financial calculation, or authentication behavior was intentionally changed.
- Installed staging with `adb -s adb-RRCY5099DHB-xEKG59._adb-tls-connect._tcp install -r --user 0 app/build/outputs/apk/staging/debug/app-staging-debug.apk` on physical Samsung SM-A366B, retaining app data. No AVD used.
- Visually inspected More menu, inventory, stock dialog, supplier-delivery form, expense form, reports, app settings, printer settings, backup/restore screen, staff list/editor, and staging Developer settings. Entered an unsaved stock quantity to check keyboard layout, then dismissed it; actions remained visible above the keyboard. Did not save stock, expenses, deliveries, staff, settings, payments, or restore data; did not send print jobs or run demo developer actions.
- The final polish removes a repeated staff heading, clarifies the backup export heading, and aligns operational button shapes. Tablet, dark theme, increased font scale, backup round-trip, and printer hardware were not manually tested in this pass. Demo developer UI was build-verified only.

## Saved catalogs and bulk product import — 2026-10-17

- Added reusable category, stock-unit and selling-unit names; guided Material selectors and entry explanations; CSV and Excel `.xlsx` template downloads; and row-by-row bulk-insert review. Names remain saved after product edits. Unit conversions/prices stay specific to each product. Existing Room schema 6 and backup tables are unchanged; no migration was necessary. See `PRODUCT_IMPORT.md` for the format and transaction contract.
- Executed `./gradlew :app:assembleStagingDebug :app:assembleDemoDebug :app:assembleStagingDebugAndroidTest :app:testStagingDebugUnitTest --console=plain`: final BUILD SUCCESSFUL. All 16 local tests passed, including 9 new parser/template tests. They cover quoted CSV, BOM, header order, decimal/whole-unit rules, duplicate barcodes, incomplete units, malformed/oversized files, sparse/shared/inline Excel cells, formula/DTD rejection, and an empty downloadable Excel template that does not import its Guide examples.
- Installed staging debug and its instrumentation APK on physical Samsung SM-A366B using `adb -s adb-RRCY5099DHB-xEKG59._adb-tls-connect._tcp install -r --user 0 ...`; both reported Success. No AVD used. Store app data was retained.
- Ran `adb -s adb-RRCY5099DHB-xEKG59._adb-tls-connect._tcp shell am instrument -w -r -e class com.aesprt.marisuki.ProductCatalogImportInstrumentedTest,com.aesprt.marisuki.PosRepositoryInstrumentedTest com.aesprt.marisuki.staging.test/androidx.test.runner.AndroidJUnitRunner`: **OK (18 tests)**. The 7 new tests cover saved choices without a product, exact opening stock/units, repeat/reordered batch idempotency, names surviving a product edit, whole-batch rollback, existing barcode conflicts, restricted stock/cost permissions, and Android reading of the packaged template. The other 11 tests provide regression coverage for existing product/stock, checkout, supplier, expense, refund and report behavior. All used in-memory databases.
- Visually inspected the phone bulk-add dialog, template guidance, invalid-file review, row details, category selector, Add Category dialog, stock-unit options, pricing/stock guidance, and selling-unit editor. Saved both templates through Android's document picker to uniquely named temporary files. The CSV had exactly one heading row and 15 columns; the downloaded XLSX matched the bundled asset byte for byte. Reviewed a temporary CSV with one valid and one deliberately invalid row; displayed counts/details/errors were correct and Import stayed disabled. Did not commit the preview or save changes to a real product or catalog choice. Closed the form without editing and removed only the three self-created device verification files, preserving the user's existing downloads.
- The Excel template was authored, inspected and rendered with the spreadsheet artifact tool. Barcode input cells use Text formatting; the Products sheet starts blank. The separate bilingual Guide includes examples and remains outside import data.
- Targeted `git diff --check` passed for edited tracked source/resource files. Tablet, dark mode, increased font scale, and a 1,000-row import on real store data were not manually exercised. No real-store bulk import, stock write, restore, or payment was performed during verification.
