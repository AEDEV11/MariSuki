# MariSuki POS contributor notes

- Read `docs/ai-context/PROJECT_OVERVIEW.md`, `CURRENT_STATUS.md`, `UI_UX_GUIDELINES.md`, and `TESTING.md` before substantial changes.
- Preserve existing worktree changes, app data, and files under `artifacts/`. Never reset or delete a store database to make development easier.
- This is a Kotlin Android app with one `:app` module. Keep the current Compose, MVVM, Room, Coroutines/Flow, DataStore, WorkManager, and Koin patterns unless a requested change needs a deliberate architecture decision.
- Every Room schema change needs a forward migration, an exported schema snapshot under `app/schemas/`, and migration coverage. Do not add destructive migration fallbacks.
- Keep test fixtures and developer controls confined to the debug source set. Never put credentials, PINs, API keys, or secrets in project memory or release assets.
- Preserve tablet-first adaptive layouts, accessible touch targets, Inter typography, and the light/dark themes described in `docs/ai-context/UI_UX_GUIDELINES.md`.
- Use the Gradle wrapper. Run builds or tests when requested or needed for verification, and report only commands actually executed and their observed results.
