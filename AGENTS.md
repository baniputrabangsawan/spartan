# Repository Guidelines

## Project Overview
Cocok is a local-first Android spare-parts lookup with item matching as the main flow, plus a Cloudflare Worker foundation. One source item (photo/keyword/catalog row) → candidates → evidence comparison. Local OCR, on-device photo-shape embeddings, and keyword image search work; server authentication and cross-device sync remain unimplemented.

## Architecture & Data Flow
- Compose → `CocokViewModel` → pure Kotlin use cases over `Catalog`; writes go through `CatalogRepository` → Room. `CocokApplication` provides manual, application-scoped DI.
- Publish catalog/save success only after transaction commit. Preserve pre-commit `PRAGMA foreign_key_check` for clean rollback. Seed once using the persisted initialization flag; an emptied catalog stays empty.
- Fitment depends on part/group, vehicle, year and variant. Preserve this context in details. `BELUM_ADA_DATA` is derived, never persisted or treated as `TIDAK_COCOK`. `BEDA` requires both sides `PASTI` with disjoint groups; missing evidence yields `BELUM_TAHU`.
- Typed part numbers use exact separator/case-normalized matching. OCR correction is separate and ambiguity-checked. Internet evidence never promotes confidence; only owner-role operations set final status.
- `domain/usecase/MatchParts.kt` separates discovery, comparison, and owner evidence. Discovery/similarity never grants confidence; `VERIFIED_REPLACEMENT`/`SAME_ITEM` require owner `MatchEvidence` scoped to vehicle+year+variant, and mismatched scopes must not leak. Missing specs become physical checks, never a pass. Never let an edited/internet item inherit a catalog binding.
- `LocalVisualMatcher` runs MediaPipe ImageEmbedder with the bundled MobileNet-v3-small (`assets/mobilenet_v3_small.tflite`, `noCompress`). Cosine scores are shape similarity, not fitment probability; with no catalog photos it must report no index rather than guess. `GoogleVisualSearch` uploads a photo only after explicit per-photo consent.
- Keyword image search keeps the offscreen-WebView → Google response parser → native cards path; `fromResultUrl` reuses the same renderer for upload result pages. This web format is not a stable API, and CAPTCHA/consent must never be bypassed.
- Hono implements only `GET /v1/health`, checking D1 schema metadata and R2 access; other routes return 404. Android does not call this Worker.

## Key Directories
Under `android/app/src/main/java/tech/cocok/app/`:
- `domain/`: framework-free models, parsing/ranking, comparison use cases and repository contract.
- `data/local/`: Room entities/DAO, catalog mapping, transactional persistence.
- `data/internet/`: Google transport/parser; `ui/`: ViewModels, overlay navigation, photo handling, components/themes.

`android/app/src/test/` holds JVM regressions/fixtures; `android/app/src/androidTest/` holds real Room tests. `android/app/schemas/` holds exported schemas. Worker code and SQL live in `api/src/` and `api/migrations/`.

## Development Commands
Run commands from the indicated subproject, not the repository root.

```bash
# Android (from android/)
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests 'tech.cocok.app.data.internet.GoogleImageParserTest'
./gradlew :app:connectedDebugAndroidTest  # requires an available device/emulator
./gradlew :app:lintDebug
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> shell am start -W -n tech.cocok.app/.MainActivity

# Worker (from api/)
npm ci
npm run typecheck
npm run build
npm run migrate:local
npm run dev
```

## Code Conventions & Common Patterns
- Kotlin: official four-space style, PascalCase types/files, camelCase members, immutable snapshots. Match adjacent Indonesian/English naming; keep user-facing copy Indonesian.
- Keep domain code Android/network-free. UI uses `StateFlow`, `collectAsStateWithLifecycle`, and lifecycle-scoped coroutines; rethrow `CancellationException`. Save failures retain previous data and form input.
- Navigation uses callbacks, custom overlays and `BackHandler`, not a NavHost. Reuse theme tokens/components. Role selection and `daring` are demo controls, not authentication/connectivity checks.
- Worker: strict TypeScript ES modules, single quotes/semicolons, Hono handlers, D1 prepared statements, errors shaped as `{ error: { code, message } }`.

## Important Files
- Android entry/lifecycle: `android/app/src/main/java/tech/cocok/app/{MainActivity,CocokApplication}.kt`.
- Within that package: `ui/{CocokApp,CocokViewModel}.kt`, `domain/usecase/{SearchParts,CompareParts}.kt`.
- Worker/config: `api/src/index.ts`, `api/wrangler.jsonc`, `api/package.json`; Android config: `android/app/build.gradle.kts`.
- Behavior: `PRD.md`; UI/copy: `DESIGN.md`; implementation status: `PLAN.md`. For search, sync or security changes, read `PLAN.md`'s technical corrections: they supersede target `ARCHITECTURE.md`. Verify planned libraries/routes against code.

## Runtime/Tooling Preferences
- Use `android/gradlew`, JDK 17, SDK 35 (minimum API 26). Keep SDK paths in ignored `android/local.properties`; preserve constrained Gradle worker/heap settings.
- Worker: npm lockfile and Node ≥22.12.0 for pinned Wrangler. Preserve existing lockfiles. No standalone formatter/linter or CI exists; `lintDebug` is built-in AGP lint.
- `dev`/`migrate:local` use local staging. `build` is bundle-only dry-run; run `typecheck` separately. **`npm run deploy`/`migrate:production` target production**; staging remote scripts are `deploy:staging`/`migrate:staging`. Remote changes require authorization; MCP OAuth is not Wrangler CLI authentication.
- Keep secrets/caches/build output untracked. Add D1 migrations instead of editing applied SQL; central catalogs must not receive Android demo seeds. Room schema changes require versioning, migration and schema-export updates together.

## Testing & QA
- JUnit 4 JVM tests share demo `SeedData`; Google parser tests use recorded `android/app/src/test/resources/google-image-three-results.js`. AndroidX instrumentation tests use isolated real Room databases and teardown.
- Preserve wrong-vehicle, exact-number/OCR ambiguity, confidence/role, missing-data, and detail-context regressions. Persistence tests cover reopen, rollback, concurrent writes and initialized-empty catalogs.
- No API/UI automation suite or coverage threshold exists. JVM `isReturnDefaultValues = true` can mask Android calls; test framework behavior on-device. Worker verification needs typecheck, dry-run build and an affected local scenario.
- Run focused tests; verify changed Compose surfaces on Android. `android/captures/` is ignored historical evidence, not current proof. State untested paths explicitly; update `PLAN.md` for status and `DESIGN.md` for UI changes.
