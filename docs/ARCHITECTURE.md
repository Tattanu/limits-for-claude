# Architecture

This document explains how the app works, why it is built this way, and where to change things.
Read it before larger contributions. The code is small (≈ 1,300 lines of Kotlin, no UI framework,
one runtime library besides Kotlin: AndroidX WorkManager).

## 1. Data flow

```
Sign-in (once)                 Refresh (every N minutes)
──────────────                 ─────────────────────────
LoginActivity                  Scheduler ─ alarm / WorkManager
  WebView → claude.ai/login       │
  sessionKey cookie appears       ▼
  → Prefs.setWeb()             UsageWorker → UsageRepository.refresh()
                                  │
                                  ▼
                               WebFetcher (invisible WebView, same cookies)
                                  │  GET /api/organizations
                                  │  GET /api/organizations/{uuid}/usage
                                  ▼
                               UsageParser → Snapshot → Prefs (JSON)
                                  │
                                  ▼
                               WidgetRenderer → WidgetDraw → bitmap → RemoteViews → launcher
```

## 2. Components

| File | Responsibility | Notes |
|---|---|---|
| `LoginActivity` | Shows `https://claude.ai/login` in a WebView. Finishes when the `sessionKey` cookie exists and the page is no longer a login page | Accepts sign-in links via an intent filter (`https://claude.ai/magic-link…`) and from the clipboard when the window regains focus |
| `WebFetcher` | Creates an off-screen WebView on the main thread, loads `https://claude.ai/api/organizations` (passes the Cloudflare check if needed), then runs a small JavaScript `fetch()` for the usage endpoint and returns the result through a `@JavascriptInterface` | 45 s timeout. Chooses the organization: saved one → `lastActiveOrg` cookie → first with the `chat` capability → first |
| `UsageParser` (`Model.kt`) | JSON → `Snapshot` (`limits`, `products`, `plan`, `fetchedAt`) | Pure Kotlin + `org.json`; covered by unit tests |
| `Items` | `Snapshot` + settings → list of `Item` (what to draw), labels, time formatting | Per-product value = `share × weekly_used / 100` when "part of the weekly limit" is on |
| `WidgetDraw` | Draws the entire widget on a `Canvas`: background, header, and one of 4 styles | Has no Android widget dependencies; also used for the in-app preview |
| `WidgetRenderer` | For each widget id: reads its size from `AppWidgetManager` options, renders the bitmap, wires click targets | Bitmap capped at 1.4 Mpx to stay inside the launcher memory budget |
| `UsageWidgetProvider` | Widget lifecycle: `onUpdate`, `onAppWidgetOptionsChanged` (resize → redraw), refresh tap, alarm tick | |
| `Scheduler` (`UsageWorker.kt`) | Periodic WorkManager job (≥ 15 min) + non-waking alarm for 1–10 min | See §5 |
| `Prefs` | All persistence (`SharedPreferences` named `limits`) | Keys starting with `s_` are user settings and survive sign-out |
| `MainActivity` | Single screen built in code: account, live preview, items, style, colors, refresh | No XML layouts, no AppCompat/Material dependency |

## 3. The usage endpoint

`GET https://claude.ai/api/organizations/{uuid}/usage`, authenticated by the claude.ai session cookie.
Undocumented; observed in November 2026. Fields the app uses:

```jsonc
{
  "limits": [                                   // preferred: one entry per limit of the plan
    { "kind": "session",    "percent": 38, "resets_at": "2026-11-04T20:49:59.75+00:00", "scope": null },
    { "kind": "weekly_all", "percent": 59, "resets_at": "2026-11-07T03:59:59.75+00:00", "scope": null }
  ],
  "five_hour":  { "utilization": 38.0, "resets_at": "…" },   // legacy fixed keys, used only if "limits" is missing
  "seven_day":  { "utilization": 59.0, "resets_at": "…" },
  "seven_day_opus": null, "seven_day_sonnet": null,           // null = not part of this plan
  "extra_usage": { "is_enabled": false, "utilization": 0.0 }, // shown only when enabled
  "seven_day_breakdown": {                                    // per-product share of the weekly usage
    "rows": [ { "key": "claude_code", "display_name": "Claude Code", "percent": 83 }, … ]
  }
}
```

Known `kind` values and their labels live in `Items.limitLabel`. Unknown kinds are still shown, with a
prettified name (`weekly_foo` → "Weekly foo"); if a `scope` is present the kind becomes `kind:scope`.
Sample responses used by the tests: `app/src/test/resources/`.

The plan name (Pro/Max/Team/Enterprise/Free) is guessed from the organization `capabilities` and
`rate_limit_tier` (`Plans.label`).

## 4. Drawing (`WidgetDraw`)

The widget layout is a single `ImageView` plus an invisible touch area. Everything visible is drawn into
one bitmap whose size is the widget's real size (`OPTION_APPWIDGET_MIN_WIDTH` × `OPTION_APPWIDGET_MAX_HEIGHT`,
the portrait size, in dp × density). This is what makes the layout fully adaptive and the styles free-form,
which `RemoteViews` alone cannot do.

| Style | Layout algorithm |
|---|---|
| Rings, Batteries | Try every column count 1…n; for each, the shape diameter is `min(cellWidth·0.9/widthFactor, cellHeight/(1.06+0.24·textLines))`; keep the largest. Last row is centered. Text sizes scale with the shape and are clamped (9–16 dp) |
| Columns | One column per item across the width; bar width 42% of the column (6–40 dp); fill from the bottom |
| Bars | Rows of ≥ 30 dp; extra columns when there are more items than rows fit, or when the widget is very wide |

Colors: traffic light thresholds are in `WidgetDraw.colorFor` (50 / 80). With background opacity below 45 %
the text becomes white with a shadow, because the wallpaper shows through.

## 5. Refresh scheduling

| Interval | Mechanism | Behaviour |
|---|---|---|
| 1–10 min | `AlarmManager.setWindow(ELAPSED_REALTIME, …)` — **not** a wake-up alarm, no exact-alarm permission | Fires only while the device is awake; while asleep it is delivered at next wake. No battery cost with the screen off |
| ≥ 15 min, and always as a fallback | WorkManager periodic work (network required) | Survives reboots and process death |
| On demand | Tap on the top-right corner, *Refresh now* in the app, after sign-in, when a widget is added and data is older than the interval | One-time WorkManager job (`KEEP`, so taps do not pile up) |

Each refresh creates and destroys one off-screen WebView (≈ 1–3 s).

## 6. Persistence (`SharedPreferences` "limits")

| Key | Content |
|---|---|
| `mode` | `"web"` when signed in |
| `org` | Selected organization uuid |
| `snapshot` | Last `Snapshot` as JSON |
| `error` | Last error message, cleared on success |
| `s_style`, `s_traffic`, `s_accent`, `s_bg`, `s_opacity`, `s_text`, `s_header`, `s_reset`, `s_prod_limit`, `s_hidden`, `s_interval` | Settings |

The session cookie lives in the WebView cookie store (app-private). `allowBackup="false"`.

## 7. Decisions and rejected alternatives

| Option | Status | Reason |
|---|---|---|
| Plain HTTP to claude.ai | Rejected | Cloudflare returns `403 Just a moment…` to non-browser clients |
| `api.anthropic.com/api/oauth/usage` with a Claude Code OAuth token | **Removed** in 0.1.5 | Works technically, but Anthropic's consumer terms (Feb 2026) forbid using Free/Pro/Max OAuth tokens outside Claude Code and claude.ai |
| OAuth sign-in in a Chrome Custom Tab (phone Google accounts, fingerprint) | Rejected | Would need Claude Code's OAuth client → same terms problem. A Custom Tab cannot hand claude.ai cookies to the app |
| Google sign-in with the phone's accounts inside the WebView | Not possible | Google blocks OAuth in embedded WebViews and does not expose device accounts to them |
| `RemoteViews` layouts with fixed cells | Replaced in 0.1.6 | Could not scale items with their number or offer custom shapes |
| Exact alarms for sub-15-minute refresh | Rejected | Needs the `SCHEDULE_EXACT_ALARM` permission and wakes the device; a non-waking window alarm is enough for a widget |

## 8. How to…

**Add a style.** Add a value to `Style` (`Prefs.kt`), a `draw…` function and a branch in `WidgetDraw.render`,
a label string in `values/strings.xml` and `values-it/strings.xml`, and the label to the `choice(…)` list in
`MainActivity` (order must match `Style.values()`).

**Add a translation.** Copy `app/src/main/res/values/strings.xml` to `values-<lang>/strings.xml` and translate.
Keep placeholders such as `%1$d` and escape apostrophes (`\'`).

**Support a new limit kind.** Add its label in `Items.limitLabel` (+ strings) and a test case in `UsageParserTest`.

**Investigate an "API changed" report.** Ask for the JSON from `claude.ai/api/organizations/<id>/usage`
(opened in a desktop browser while signed in), add it under `app/src/test/resources/`, write a failing test, fix `UsageParser`.

**Debug on a device.** `adb logcat` while tapping refresh; WebView pages can be inspected from desktop Chrome at
`chrome://inspect` if you temporarily call `WebView.setWebContentsDebuggingEnabled(true)` in a debug build.
