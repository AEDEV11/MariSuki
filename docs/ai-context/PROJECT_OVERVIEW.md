# MariSuki POS project overview

**Audit basis:** source, configuration, schema history, tests, and repository artifacts inspected on 2026-10-16. The feature list below describes code present; it does not imply field validation or successful current tests.

## Product

MariSuki POS is an offline-first Android point-of-sale app for a family grocery, sari-sari, and wholesale store. Its primary operator is a non-technical store owner using a landscape tablet; phones are a secondary form factor. The app uses Philippine pesos and has Filipino and English resources.

## Architecture

- One Android application module: `:app`; package and namespace `com.aesprt.marisuki`.
- Kotlin, Jetpack Compose, Material 3, AndroidX Navigation and adaptive window size classes.
- MVVM-style ViewModels expose `StateFlow`; repositories use suspend functions, Room `Flow`s, and coroutines.
- Koin creates the database, repositories, backup manager, and ViewModels.
- Room is the operational source of truth. `PosDatabase` currently exports schema version 6; checked-in schema snapshots cover versions 1–6.
- DataStore holds app preferences, session state, and printer preferences. WorkManager schedules daily automatic backups.

## Implemented areas found in source

- Four-step first-run store setup with resumable, credential-free draft data; owner registration; cashier accounts; per-account permission sets; login, logout, session timeout, and persisted failed-attempt throttling.
- Owner-only operations and a short-lived, one-use owner-PIN approval flow for a cashier changing a selling price without that permission. Other owner-only operations remain owner-only.
- Cashier catalog, search and barcode entry, product units, cart, cash/GCash/Maya tenders, partial payment and customer credit, sales history, refunds, and receipt data.
- Product/category and barcode management, reusable category and unit selectors, guided product entry, previewed CSV/XLSX bulk insertion with downloadable templates, stock balances and movement history, supplier delivery entry, weighted purchase-cost updates, low-stock reporting, and stock valuation.
- Customers, balances, ledger entries, and payments; expenses; sales reports and CSV export.
- Manual encrypted backup/restore and device-local encrypted automatic snapshots, including product photos. Automatic snapshots retain up to seven local files.
- Receipt and product-label ESC/POS command generation; Android system printing plus direct paired Bluetooth SPP and USB bulk transports.
- The `demoDebug` variant seeds the demo store, sample data, and demo users, and includes developer controls and print-failure simulation. Staging variants and all release variants use no-op hooks and start without demo data.

## Important boundaries

- `Cash`, `GCash`, and `Maya` are recorded tender methods; no payment-provider integration was found.
- Barcode entry accepts scanner keyboard input, and ZXing is used for barcode generation/printing. A camera-based scanner was not found.
- The Room database is stored in app-private storage but is not encrypted at rest. The exported backup file format is encrypted; local automatic backups remain on the same device.
- Demo and staging use separate application IDs (`.demo` and `.staging`); debug and release use separate database names and preference/key aliases. Do not ship or rely on the checked-in debug APK for production.
- See `CURRENT_STATUS.md` for the current worktree, toolchain, risks, and verification state.
