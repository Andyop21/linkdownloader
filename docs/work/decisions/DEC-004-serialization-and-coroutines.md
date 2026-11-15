---
id: DEC-004
title: kotlinx.serialization for yt-dlp's JSON, kotlinx.coroutines declared directly
date: 2026-11-15
status: accepted
---

# DEC-004 — kotlinx.serialization for yt-dlp's JSON, kotlinx.coroutines declared directly

## Context

P03 builds the download pipeline. It reads yt-dlp's `-J` JSON, which is large (YouTube lists over
a hundred formats) and varies by site: some numbers are integers on one site and floats on
another, and many fields are `null` or missing. The pipeline is also a `suspend` function that P04
runs in a `CoroutineWorker`, and it must cancel cleanly.

`docs/architecture.md` already planned kotlinx.serialization. Coroutines reached the app only
transitively, through lifecycle.

## Decision

- **kotlinx-serialization-json 1.13.0** (the latest release on Maven Central on 2026-11-15) with
  the Kotlin serialization compiler plugin at the Kotlin version from the catalog. `VideoJson`
  declares only the fields the pipeline reads and is parsed with `ignoreUnknownKeys`,
  `coerceInputValues` (a `null` list becomes empty) and `allowSpecialFloatingPointValues`
  (Python can write `NaN`). Numbers are read as `Double?`.
- **kotlinx-coroutines-android** at the catalog's existing `coroutines` version (1.11.0), as a
  direct `implementation` dependency, since the `download` package uses it directly.
- **JUnit 4** (already in the catalog) as `testImplementation` for the first JVM tests.

## Consequences

- The model stays small and readable, and is parsed without reflection.
- youtubedl-android still brings jackson-databind for its own `getInfo`; the pipeline does not use
  it, so a later release build can shrink it away (P07).
- Coroutines no longer depend on what lifecycle happens to pull in.

## Alternatives considered

- **`org.json`** (in Android, as the probe uses): no types, and every field read by hand.
- **The library's own `getInfo` (jackson)**: an old jackson-databind, its own model with fields the
  pipeline does not need, and it runs its own extraction instead of saving the JSON for
  `--load-info-json`.
