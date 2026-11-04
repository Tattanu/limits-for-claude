# Contributing

Thank you for helping! This project is small and friendly: every contribution counts,
from a one-line report to a new drawing style.

## Ways to help

| You can… | How |
|---|---|
| Report a bug | [Bug report](https://github.com/Tattanu/limits-for-claude/issues/new?template=bug_report.yml) |
| Tell us the widget broke after a claude.ai change | [API changed](https://github.com/Tattanu/limits-for-claude/issues/new?template=api_change.yml) — the most valuable report |
| Confirm it works with **Max / Team / Enterprise / Free** | Open an issue with a screenshot (hide anything personal) — only Pro is verified so far |
| Suggest a feature | [Feature request](https://github.com/Tattanu/limits-for-claude/issues/new?template=feature_request.yml) |
| Translate | Add `app/src/main/res/values-<lang>/strings.xml` (see [ARCHITECTURE §8](docs/ARCHITECTURE.md#8-how-to)) |
| Write code | Pick an issue, comment that you are on it, open a pull request |

## Development setup

1. Install JDK 17 and the Android SDK (platform 35) — Android Studio includes both.
2. `git clone https://github.com/Tattanu/limits-for-claude.git`
3. `./gradlew :app:testDebugUnitTest :app:assembleDebug`
4. Install `app/build/outputs/apk/debug/app-debug.apk` on a device or emulator (Android 8.0+), sign in, add the widget.

A debug build has a different signature than official releases: uninstall the release before installing it, and vice versa.
No device? Every pull request is built by CI and the APK is attached to the run as an artifact.

Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) first: it maps every file and explains the non-obvious choices.

## Workflow

1. Fork, then create a branch from `main`: `fix/short-description` or `feat/short-description`.
2. Keep each pull request focused on one change.
3. Run the unit tests. If you touch `UsageParser`, add a test and, if useful, a sample JSON in `app/src/test/resources/`.
4. If you change visible text, update **both** `values/strings.xml` (English) and `values-it/strings.xml` (Italian; ask for help if you do not speak Italian).
5. If you change behaviour, update `README.md`, `docs/ARCHITECTURE.md` and the *Unreleased* section of `CHANGELOG.md`.
6. Open the pull request and fill in the template. CI must be green.

Commit messages: short imperative summary (`Add column style`, `Fix reset time for weekly limit`), details in the body if needed.

## Code style

- Kotlin official style (`kotlin.code.style=official`), 4-space indent, see `.editorconfig`.
- No new runtime dependencies without discussing it first in an issue: the APK is ~2.3 MB and should stay small.
- UI is built in code (no XML layouts except the widget); keep it that way for consistency.
- Comments explain *why*, not *what*.

## Scope — what will not be accepted

- Sign-in or data access with Claude Code / Claude OAuth tokens or API keys of consumer plans: it violates Anthropic's consumer terms (see README → Limitations).
- Anything that sends user data anywhere other than claude.ai (analytics, crash reporting to third parties, own servers).
- Reading chats, messages or any data beyond usage limits.

## Releases (maintainers)

1. Move the *Unreleased* entries in `CHANGELOG.md` under the new version.
2. `./scripts/release.sh 0.2.0` — checks that `main` is clean and in sync and that the changelog has the section, then pushes the tag `v0.2.0`; CI builds, signs and publishes the GitHub Release with the APK.
3. The signing key is kept offline by the maintainer and stored only as repository secrets (`KEYSTORE_B64`, `KEYSTORE_PASSWORD`). Losing it means users must uninstall to update.

## Code of Conduct

Everyone taking part follows the [Code of Conduct](CODE_OF_CONDUCT.md).
