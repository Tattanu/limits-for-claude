# Limits for Claude — widget Android dei limiti di utilizzo

*Unofficial Android home-screen widget that shows how much of your Claude usage limits is left
(5-hour session, weekly, model-specific, extra usage), drawn as rings like a battery widget.
Works with any account and plan (Free/Pro/Max/Team). Not affiliated with Anthropic.*

**Scopo:** widget per la schermata Home, in stile widget batteria Samsung. Mostra la percentuale **rimanente**
di ogni limite di utilizzo dell'account Claude. Pensato anche per altre persone, altri telefoni Android
(8.0 o successivo), altri account e altri piani. Creato il 2026-11-04.

## Come funziona
| Parte | Dettaglio |
|---|---|
| Fonte dati, modalità **login** (principale) | Pagina di login vera di claude.ai dentro l'app. Poi una WebView invisibile (mini-browser interno) chiama `claude.ai/api/organizations/{org}/usage`, gli stessi dati di claude.ai/settings/usage. Serve la WebView perché Cloudflare blocca le richieste dirette (verificato: HTTP 403 "Just a moment") |
| Fonte dati, modalità **token** (avanzata) | `api.anthropic.com/api/oauth/usage` con un token OAuth di Claude Code (verificato con l'account Pro: HTTP 200). Il token di `~/.claude/.credentials.json` scade dopo alcune ore |
| Formato | Usa l'elenco generico `limits` della risposta, quindi mostra solo i limiti del piano dell'utente. Se manca, usa le chiavi `five_hour`, `seven_day`, `seven_day_opus`, ecc. Mostra fino a 4 anelli |
| Colori anello | verde > 50% rimanente, arancio 21–50%, rosso ≤ 20% |
| Aggiornamento | WorkManager (programmatore di Android) ogni 15/30/60 minuti, solo con rete; il tocco sull'intestazione del widget aggiorna subito, il tocco sul resto apre l'app |
| Lingue | inglese + italiano |
| Dati salvati | solo sul telefono (preferenze private dell'app, backup disattivato) |

## File
| File | Cosa fa |
|---|---|
| `app/src/main/java/.../Model.kt` | Modello dati e lettura della risposta JSON (`UsageParser`), riconoscimento piano |
| `.../WebFetcher.kt` | Richiesta tramite WebView invisibile (modalità login) |
| `.../TokenApi.kt` | Richiesta con token (modalità avanzata) |
| `.../WidgetRenderer.kt` | Disegno del widget e degli anelli |
| `.../UsageWidgetProvider.kt`, `UsageWorker.kt`, `UsageRepository.kt` | Widget, aggiornamento periodico, salvataggio |
| `.../MainActivity.kt`, `LoginActivity.kt` | Schermata app e login |
| `app/src/main/res/` | Layout widget, testi EN/IT, colori chiaro/scuro, icona |
| `.github/workflows/build.yml` | Compilazione su GitHub Actions: APK a ogni push su `main`; release pubblica a ogni tag `v*` |

## Chiave di firma (IMPORTANTE)
`/root/.config/widget-limiti-claude/release.p12` + `password.txt` (fuori da /sdcard, non nel repository).
È caricata nei segreti GitHub `KEYSTORE_B64` e `KEYSTORE_PASSWORD`. **Va conservata:** se si perde,
gli aggiornamenti non si installano più sopra la versione precedente.

## Uso
1. Installare l'APK (da GitHub → Actions → artifact, oppure Releases).
2. Aprire l'app → **Accedi con claude.ai** (se Google è bloccato: "Continua con l'email").
3. Tenere premuto sulla Home → Widget → Limits for Claude.

## Stato
2026-11-04: codice scritto, in attesa della prima compilazione su GitHub Actions.
