# AGENTS.md

## Overview
Jauge: Android home screen widgets (Glance) tracking Claude, ChatGPT Codex and Nous Portal usage. Owner: Sygix. UI languages: English (default) and French.

## Code conventions
- Kotlin, Compose for the app, Glance for widgets; minSdk 31
- No comments in code or tests, except the license header of each file
- Every `.kt` / `.kts` file starts with this header, then a blank line:
  ```
  /*
   * Copyright (C) 2026 Sygix
   * SPDX-License-Identifier: AGPL-3.0-or-later
   */
  ```
- No user-facing text in Kotlin: strings go to `values/strings.xml` and `values-fr/strings.xml`; stored data (snapshots, errors) stays language-neutral (enums, codes)
- Widget classes must stay distinct after R8 (`-keep` rule in `proguard-rules.pro`): Glance finds widgets by class name
- Any widget layout change: run `./gradlew testDebugUnitTest` and check `app/build/widget-previews/*-matrix.png` for clipped content

## Git
- Conventional Commits in English; branches `feat/`, `fix/`, `chore/`; rebase only, signed commits
- `main` is protected: pull request with the `test` check, linear history, signatures
- Releases: signed `vX.Y.Z` tag, built and signed by `release.yml` in the `production` environment

## Public repository
Never push secrets (keystore, tokens), local paths, account names or raw API responses. Release signing secrets live in the `production` environment only.
