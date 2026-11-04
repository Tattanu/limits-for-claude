# Changelog

All notable changes to this project. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions follow [Semantic Versioning](https://semver.org/). CI builds of `main` are numbered `0.1.<run>`.

## [Unreleased]

### Fixed
- Tapping the widget's top-right corner (and the 1–10 min timer) did not refresh: the work was handed to WorkManager, which can postpone it. It now runs immediately in the widget receiver.

### Added
- *Last attempt* line in the app: time, source (widget tap, timer, app, background) and outcome of the latest refresh.
- Collaboration files: MIT license, contributing guide, architecture document, security policy, code of conduct, issue and pull-request templates, Dependabot.
- Gradle wrapper 8.10.2 (checksum-verified) and unit tests for the usage parser; CI runs them on every push and pull request.

## [0.1.6] – 2026-11-04

### Added
- Four styles: rings, batteries, columns, bars.
- Per-product counters (Claude Code, Chat, Cowork, Other) from the weekly breakdown, as part of the weekly limit or as a share of weekly usage.
- Show/hide switch for every item; colors (traffic light or single color), background color and opacity, text color; live preview in the app.
- Refresh every 1, 2, 5, 10, 15, 30 or 60 minutes (non-waking alarm under 15 minutes).
- Email sign-in links open directly in the app (one-time Android setting) or are picked up from the clipboard.

### Changed
- The widget shows the percentage **used** (was: remaining); traffic-light colors inverted accordingly.
- The whole widget is drawn at its real size: items scale with their number and with resizing.

## [0.1.5] – 2026-11-04

### Removed
- Access-token mode: Anthropic's consumer terms forbid subscription OAuth tokens outside Claude Code and claude.ai.

### Added
- “Paste link from email” button on the sign-in page.

## [0.1.4] – 2026-11-04

### Fixed
- On Android 15+ (edge-to-edge) the sign-in button and status were hidden behind the title bar.

## [0.1.2] – 2026-11-04

### Added
- First version: claude.ai sign-in in a WebView, invisible-WebView fetch of the usage endpoint, ring widget with remaining percentage, English and Italian.
