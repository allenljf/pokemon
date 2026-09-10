# Implementation Notes

This document answers the six mandatory assignment questions. It distinguishes current implementation behavior, reported manual checks, available test results, and known limitations.

## Scope decisions and assumptions

- The dataset is fixed to the first 151 Pokémon (`GET /pokemon?limit=151`); pagination is out of scope.
- Type sections are ordered alphabetically by their English type name; within each section, Pokémon are ordered by Pokédex ID ascending. Type counts reflect locally committed Pokémon, not the final membership while loading.
- Recovery means foreground relaunch recovery, including reopening the app after a device reboot. Background scheduling, automatic sync after reboot without reopening the app, and WorkManager are out of scope unless separately agreed.
- At most five individual core requests (`/pokemon/{id}`) are in flight at once. This is not a global network limit: species and artwork requests run separately.
- The collection prefetches species data (description + `evolves_from_species`) for completed core records in batches of up to three. Opening a detail page also requests missing species data, including the pre-evolution species when applicable. Valid cached species rows are reused, and a per-species lock prevents overlapping callers from downloading the same successful payload twice. This can make text available offline even before a detail has been opened, at the cost of additional requests during collection loading.
- The n/151 indicator counts committed core records (metadata, types, and artwork URL). It does not indicate completion of species data or image downloads. Species cache validity is represented by the stored row and its payload-format version; there is no independent persisted artwork completion state.
- Each core sync run fetches the index again, then selects only `PENDING` or `RETRYABLE` records. Completed core records and valid species rows are not refreshed for upstream changes; there is no TTL or manual data-refresh policy. The species format version tracks the app's local format, not the upstream content version.
- Core HTTP 429 and 5xx failures remain eligible for a later run; other 4xx failures are terminal and skipped by subsequent runs. Retry, network recovery, or relaunch can start another run, but core failures do not have an automatic timed retry loop.

## 1. AI assistance

All production code, tests, and this document were drafted by an AI coding assistant (Codex); I did not treat its output as a finished solution. I reviewed each change, ran the app repeatedly, and directed the assistant to fix behavior that was broken or degraded the experience. The corrections I made and why:

- **Duplicate-tap handling on navigation.** The first version opened a new detail page for every tap, so a double-tap stacked several screens and forced repeated Back presses. Collection-to-detail navigation uses a 500 ms throttle plus `launchSingleTop`. Detail-to-pre-evolution navigation uses the same throttle without `launchSingleTop`, so rapid taps are suppressed while a successful navigation still creates the history entry that Back needs.
- **Offline state, detection, and automatic recovery.** The assistant only interrupted the sync on failure: there was no offline indicator, and data resumed only after the app was reopened. I added a network monitor that surfaces a top-bar status, and made the sync and the detail screen resume automatically when connectivity returns.
- **Room schema simplification.** The assistant initially produced six tables. I simplified this to four (`pokemon`, `pokemon_type`, `species`, `capture`) by folding the per-Pokémon sync checkpoint into `pokemon.coreStatus`, then prompted the assistant to rework the relationships around that design.
- **Top-bar sync progress and status.** I added the "Syncing Pokémon n/151" indicator and the offline/error chip so the user can see where synchronization stands.
- **Capture and release confirmation.** The capture/release controls overlap each Pokémon card's touch area, so it is easy to tap one unintentionally while trying to open a detail page. I added a confirmation dialog before either action changes the Pocket, so an accidental tap does not create or delete a capture.
- **Evolves-from navigation.** The assistant did not implement it; I asked for the pre-evolution item to link to that Pokémon's detail page. It uses the 500 ms throttle but deliberately does not use `launchSingleTop`, allowing Back to return to the previous Pokémon detail. `DetailScreenLayoutTest.tappingEvolvesFromLinkNavigatesToThePreEvolutionDetail` verifies that tapping Charizard's evolution item requests Charmeleon (ID 5), while `PokemonNavigationTest.navigatingToPreEvolutionCreatesDetailHistoryForBackNavigation` verifies the shared navigation helper creates a detail-history entry. The latter uses a small test `NavHost`; it does not exercise the complete production `PokemonNavHost` callback wiring.
- **Image recovery when connectivity returns.** List and detail artwork that failed to load offline stayed stuck in an error state forever. Because this is low-cost and clearly better UX, I added a reload key that re-requests artwork when the network comes back.
- **Pokémon visual identity.** I drew a reusable Poké Ball icon for the capture and release actions, and added the app's launcher and round-launcher icon assets. These changes make the app feel more recognizably Pokémon-themed without adding another dependency.

## 2. Duplicate capture model

Each capture is an independent `capture` row with a UUID `id`, the `pokemonId`, and a `capturedAt` timestamp. Capturing the same species twice inserts two rows with distinct IDs; the Pocket query orders by `capturedAt DESC, rowid DESC` so the newest is first and equal timestamps stay deterministic. Release deletes exactly one `id`, leaving other captures of the same species intact.

Alternatives considered and rejected:

- A Boolean `captured` flag cannot represent duplicates.
- A per-species count loses individual timestamps and cannot identify which entry to release.
- A JSON list of timestamps on the Pokémon row would avoid a second table, but individual captures would need extra parsing and identity rules for ordering and reliable release. Separate rows keep those operations straightforward.

## 3. Interruption and resume verification

Automated evidence:

- `PokemonSynchronizerTest` recreates a synchronizer over the same in-memory fake store and verifies that cancellation leaves an uncommitted ID pending and the next instance requests only incomplete IDs. This models retained state; the Room disk-reopen test below supplies actual disk-persistence evidence.
- `RoomPokemonStoreTest.diskReopenRetainsCheckpointsAndOnlySelectsIncompleteIds` closes and reopens a real on-disk Room database and asserts only incomplete IDs are selected after relaunch.
- `RoomPokemonStoreTest.commitCore_rollsBackRowsMembershipsAndCompletionWhenTheTransactionFails` proves a failed core transaction does not mark a phase complete.

Manual checks I actually ran on an emulator:

- Launched and let the download run partway, then backgrounded the app: downloading continued during this check. Sync runs in the ViewModel scope; continued execution is not guaranteed if the process is stopped.
- Launched and let the download run partway, then swiped the app away and reopened: sync continues from the persisted checkpoint instead of restarting.
- With the download still incomplete, swiped the app away, rebooted the device, and then reopened the app: the remaining download continued after reopening.
- Launched and let the download run partway, then disabled the network: the top bar shows the offline state; reconnecting resumes the download automatically.
- Offline, opened a detail page: Pokémon whose core data was already preloaded show cached core content; ones that were not show an error. Description and evolution content depend on separately cached species data and the pre-evolution's core record. Reconnecting inside the detail page restores missing species content when the requests succeed.
- Broke the API endpoints (changed the base path to simulate a down server): the list fails to download; when only the species endpoint fails, the list still builds and the detail page shows the data-error notice instead of crashing.
- Detail grouping: cached core content can appear before species content. The core response supplies types and the artwork URL; Coil retrieves the image bytes separately. An image URL alone does not make artwork available offline, and even previously loaded images depend on the cache remaining available.
- On the Pixel 10 Pro Fold emulator, checked the collection and detail screens while folding/unfolding the device and rotating between portrait and landscape. The layouts remained usable without visible clipping or overlap.

I have not recorded a scripted OS-level force-stop mid-wave with the committed/pending IDs logged; the manual checks above cover swipe-away and device reboot followed by reopening the app.

## 4. Deliberately excluded work

- WorkManager, scheduled sync, and automatic sync after reboot without reopening the app: outside the agreed foreground-recovery scope.
- Pagination beyond the fixed 151-item dataset.
- A search/filter UI: not required, and type-section browsing covers a fixed 151-item dataset.
- Gradle modularization and benchmark infrastructure: deferred to keep the two-screen assignment focused on its core flows.
- Multilingual content and manual refresh of completed data: deferred beyond this submission; the proposed next steps are described below.

## 5. Current weakness and one more day

The main technical weakness is offline artwork: image URLs are persisted, but the image files still depend on Coil's cache. A durable image store with a persisted completion state remains unfinished work.

With one more day, I would first address the remaining local code-review items:

- **P2 — Navigation regression coverage.** Replace the focused test `NavHost` with coverage that mounts the production `PokemonNavHost`, triggers the actual pre-evolution flow, and verifies that Back returns to the previous detail. This would protect the production callback and throttle wiring, not only the shared navigation helper.
- **P3 — Test-graph duplication and consistency.** Avoid repeating the detail route and argument configuration in a separate test graph, so it cannot drift from production; also align the instrumentation test class with the adjacent `AndroidJUnit4` runner convention.

With one more day, I would prioritize exploring Traditional Chinese and English support. My nephew loves Pokémon, and I would like him to enjoy using the app. The species API already provides Traditional Chinese Pokémon names and descriptions, so I would use that content directly without model-generated translation.

The main challenge is the data design and loading order. Core data currently provides the English Pokémon name, while the Chinese name only becomes available after species data has downloaded. I would rethink how the tables and queries relate Pokémon identity to localized display text, keeping stable IDs for relationships and storing names and descriptions by language. Collection, capture, and evolution views would need to resolve the selected language rather than always display the core English name. I would also decide how names should appear while species data is still loading or unavailable offline, so localization does not block the progressive display of core data. I would use the extra day to work through this design and validate a small end-to-end implementation, including language switching, missing localized content, and cached offline display.

If time remained, I would prototype a manual refresh button for previously downloaded data, in case PokéAPI's content changes. It would fetch updated core and species content while retaining usable local data until successful replacement, preserve capture records, prevent duplicate refresh runs, and show progress and failures. This is separate from the existing Retry flow, which resumes missing or retryable work. I would verify that a failed refresh leaves existing content and captures intact. Completing both features and durable artwork storage would exceed the scope I would promise for one extra day.

## 6. Actual effort versus estimate

My original estimate was about one week of calendar time, roughly 8 focused hours. Actual focused time was about 12 hours spread over the available days. I underestimated the time needed to validate network failures, recovery, and interaction edge cases. These issues became clearer during hands-on testing and required several rounds of refinement.

Using AI made it faster to produce an initial implementation, but it also made regression checking more important. A change proposed to fix one problem could unintentionally degrade a working flow elsewhere, so I sometimes had to repair behavior that had previously worked. The later iterations therefore needed more time to retest the affected flow and nearby flows, rather than assuming that a targeted change was safe. In hindsight, I should build a thin end-to-end version of the main flow earlier and include error handling, regression checks, and verification in the estimate. Preparing these notes also took time: I had to check the app's actual behavior and reconcile the documentation with the implementation rather than rely on an AI-generated summary.

## Test commands and coverage

Commands:

- JVM unit tests, with each test name and result: `./gradlew testDebugUnitTest --rerun-tasks --console=plain`
- Instrumentation tests, with each test name and result (require an emulator/device): `./gradlew connectedDebugAndroidTest --rerun-tasks --info --console=plain`
- Debug build: `./gradlew assembleDebug`

Automated coverage in source:

- `PokemonSynchronizerTest` (13): five-core-request concurrency limit, serialized runs avoid duplicate successful core fetches, cancellation leaves an ID pending, retryable vs terminal failure classification (a 500 is retried on a later run; a 404 is skipped), offline stops further dispatch, progress from committed records, index failure publication, and relaunch-selects-only-incomplete. The implementation also treats 429 as retryable, but this suite has no dedicated 429 case.
- `SyncFailureTest` (4): DNS/transport failures map to offline; HTTP and unexpected errors map to a readable API error.
- `CollectionViewModelTest` (6): type ordering and dual-type membership, partial content through a failed sync and retry, first-launch offline and API-error empty states, network-recovery restart plus image reload key, and capture persistence across ViewModel recreation.
- `DetailViewModelTest` (6): cached core with unavailable species, offline uncached detail, API-error detail, never-downloaded detail, network-recovery reload plus image reload key, and pre-evolution rendering.
- `PokemonRepositoryCaptureTest` (4): cached species without a network request, duplicate captures with deterministic ordering, releasing one of two captures, and capture persistence across repository recreation.
- `RoomPokemonStoreTest` (instrumentation, 8): checkpoint/reseeding semantics, stale type-membership replacement, atomic publish, transaction rollback, hidden placeholders, failed-replacement preservation, disk-reopen resume, and capture-to-artwork-URL join (not image-byte persistence).
- `CollectionScreenTest` / `DetailScreenLayoutTest` (instrumentation, 12): capture/release confirmation, pocket navigation, offline and API error states with Retry, detail element ordering, evolves-from callback behavior, and header/layout bounds.
- `PokemonNavigationTest` (instrumentation, 1): detail-to-pre-evolution navigation through the shared helper creates a back-stack entry, and popping it returns to the previous detail. The test uses a focused test `NavHost`, not the complete production `PokemonNavHost` flow.