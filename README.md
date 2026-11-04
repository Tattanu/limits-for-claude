# Limits for Claude — widget Android dei limiti di utilizzo

*Unofficial Android home-screen widget that shows how much of your Claude usage limits is left
(5-hour session, weekly, model-specific, extra usage), drawn as rings like a battery widget.
Works with any account and plan (Free/Pro/Max/Team). Sign in once with the real claude.ai page; the app only reads
the usage data of claude.ai/settings/usage. Not affiliated with Anthropic.*

**Scopo:** widget per la schermata Home, in stile widget batteria Samsung. Mostra la percentuale **rimanente**
di ogni limite di utilizzo dell'account Claude. Pensato anche per altre persone, altri telefoni Android
(8.0 o successivo), altri account e altri piani. Creato il 2026-11-04.

## Come funziona
| Parte | Dettaglio |
|---|---|
| Fonte dati, modalità **login** (principale) | Pagina di login vera di claude.ai dentro l'app. Poi una WebView invisibile (mini-browser interno) chiama `claude.ai/api/organizations/{org}/usage`, gli stessi dati di claude.ai/settings/usage. Serve la WebView perché Cloudflare blocca le richieste dirette (verificato: HTTP 403 "Just a moment") |
| Modalità token | **Tolta il 2026-11-04** (scelta dell'utente): i Termini Anthropic da febbraio 2026 vietano i token OAuth dei piani Free/Pro/Max fuori da Claude Code e claude.ai. Il file `TokenApi.kt` resta nella cronologia git |
| Login | Una sola volta. Google nella WebView chiede la password (Google non condivide gli account del telefono con le WebView e vieta il suo OAuth nelle WebView dal 2021). Consigliato "Continua con l'email"; se arriva un link: copiarlo e usare il pulsante "Incolla link dall'email" |
| Formato | Usa l'elenco generico `limits` della risposta, quindi mostra solo i limiti del piano dell'utente. Se manca, usa le chiavi `five_hour`, `seven_day`, `seven_day_opus`, ecc. Mostra fino a 4 anelli |
| Cosa mostra | % **usata** (dal 2026-11-04 v0.1.6). Limiti del piano + contatori per prodotto (Code, Chat, Cowork, Altro) da `seven_day_breakdown`: di base come parte del limite settimanale (quota × % settimana), oppure come quota dell'uso settimanale. Ogni elemento si attiva o disattiva nell'app |
| Disegno | Tutto il widget è un'unica immagine disegnata alla misura reale del widget (`WidgetDraw.kt`), ridisegnata quando si ridimensiona: righe e colonne scelte per avere gli elementi più grandi possibile. Massimo 1,4 milioni di pixel |
| Stili | Anelli, Batterie (si svuotano), Colonne, Barre |
| Colori | Semaforo per % usata (verde < 50, arancio < 80, rosso ≥ 80) o colore unico; sfondo (automatico chiaro/scuro o 7 colori), opacità 0–100%, colore testo |
| Aggiornamento | 1/2/5/10/15/30/60 min (5 di base). Sotto 15 min: allarme che non sveglia il telefono (`setWindow` su `ELAPSED_REALTIME`), quindi solo a schermo acceso. Più WorkManager ogni 15 min come riserva. Tocco nell'angolo in alto a destra = aggiorna subito; tocco altrove = app |
| Login automatico | Link email `claude.ai/magic-link…` apribile direttamente nell'app (da abilitare una volta: pulsante nella pagina di login → impostazioni Android "Apri link supportati"); oppure link copiato negli appunti, preso da solo al ritorno nell'app. Il percorso `/magic-link` è una supposizione non verificata |
| Lingue | inglese + italiano |
| Dati salvati | solo sul telefono (preferenze private dell'app, backup disattivato) |

## File
| File | Cosa fa |
|---|---|
| `app/src/main/java/.../Model.kt` | Modello dati e lettura della risposta JSON (`UsageParser`), riconoscimento piano |
| `.../WebFetcher.kt` | Richiesta tramite WebView invisibile (modalità login) |
| `.../WidgetDraw.kt`, `Items.kt`, `WidgetRenderer.kt` | Disegno dei 4 stili, elenco elementi, invio al widget per ogni misura |
| `.../UsageWidgetProvider.kt`, `UsageWorker.kt` (anche `Scheduler`), `UsageRepository.kt` | Widget, ridimensionamento, aggiornamento, salvataggio |
| `.../Prefs.kt` | Account e personalizzazioni (chiavi `s_*`, restano dopo l'uscita dall'account) |
| `.../MainActivity.kt`, `LoginActivity.kt` | Schermata app e login (con pulsante incolla-link) |
| `.../Insets.kt` | Margini per Android 15 (app a tutto schermo) |
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
2026-11-04: repository pubblico https://github.com/Tattanu/limits-for-claude ; compilazione su Actions riuscita (v0.1.2, APK 1,7 MB firmato con la chiave definitiva, copiato in /sdcard/Download). Da provare sul telefono: login claude.ai, widget. Release pubblica (tag `v0.1.0`) dopo la prova. Segreti caricati con `~/segreti.sh`.
