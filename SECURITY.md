# Security policy

## Reporting a vulnerability

Please **do not open a public issue** for security problems.
Report them privately through GitHub: **Security → Report a vulnerability**
([direct link](https://github.com/Tattanu/limits-for-claude/security/advisories/new)).

Include what you found, how to reproduce it, and its impact. You will get an answer as soon as possible;
this is a volunteer project, so please allow a few days.

## What is sensitive in this app

| Asset | Where it lives |
|---|---|
| claude.ai session cookie (`sessionKey`) | WebView cookie store, app-private storage |
| Last usage snapshot and settings | App-private `SharedPreferences` |

Examples of relevant issues: anything that lets another app or a web page read the session cookie,
JavaScript-bridge (`@JavascriptInterface`) abuse, loading non-claude.ai content with the session,
backups or exports that leak the cookie.

## Supported versions

Only the latest release receives fixes.
