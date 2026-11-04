# Limits for Claude

🇬🇧 [English](README.md) — la versione inglese è quella di riferimento per chi contribuisce.

**Widget Android per la schermata Home che mostra quanto hai usato dei tuoi limiti di Claude**:
sessione di 5 ore, limite settimanale, limiti per modello, uso extra e ripartizione per prodotto
(Claude Code, chat, Cowork…). Si può disegnare ad anelli, batterie, colonne o barre, come il widget della batteria.

Funziona con qualsiasi account e piano (Free, Pro, Max, Team): mostra i limiti che ha il tuo piano.

> **Non ufficiale.** Non è affiliata, approvata né supportata da Anthropic. Legge gli stessi dati di
> [claude.ai/settings/usage](https://claude.ai/settings/usage) tramite un'interfaccia **non documentata**,
> che può cambiare in qualsiasi momento. Vedi [Limiti](#limiti).

## Funzioni

| Area | Cosa offre |
|---|---|
| Limiti | Tutti quelli indicati dal tuo piano: sessione 5 ore, settimanale, settimanale per modello (es. Opus), uso extra se attivo |
| Contatori per prodotto | Claude Code, Chat, Cowork, Altro: come parte del limite settimanale (predefinito) o come quota del tuo uso settimanale |
| Valore mostrato | Percentuale **usata** (0% = niente usato, 100% = limite raggiunto) e tempo all'azzeramento |
| Stili | Anelli · Batterie (si svuotano man mano che consumi) · Colonne · Barre |
| Impaginazione | Il widget è disegnato alla sua misura reale: elementi grandi se sono pochi e piccoli se sono tanti; si ridisegna quando lo ridimensioni |
| Colori | Semaforo (verde < 50% usato, arancio < 80%, rosso ≥ 80%) o un colore unico; colore e opacità dello sfondo (0–100%), colore del testo; chiaro/scuro automatico |
| Aggiornamento | Ogni 1, 2, 5, 10, 15, 30 o 60 minuti (predefinito 5); tocco nell'angolo in alto a destra = aggiorna subito |
| Accesso | La vera pagina di login di claude.ai dentro l'app, una sola volta; i link delle email si aprono direttamente nell'app o vengono presi dagli appunti |
| Lingue | Italiano, inglese |

## Installazione

Servono Android 8.0 o successivo e un account Claude.

1. Scarica l'APK (il file di installazione):
   - **Stabile:** [Releases](https://github.com/Tattanu/limits-for-claude/releases) → `LimitsForClaude-x.y.z.apk`
   - **Ultima versione di `main`:** [Actions](https://github.com/Tattanu/limits-for-claude/actions/workflows/build.yml) → ultima esecuzione verde → *Artifacts* (serve l'accesso a GitHub; è uno zip che contiene l'APK)
2. Apri l'APK sul telefono e, se Android lo chiede, consenti l'installazione da quell'app (non è su Google Play).
3. Gli aggiornamenti si installano sopra la versione precedente: tutte le versioni ufficiali sono firmate con la stessa chiave.

## Uso

1. Apri **Limits for Claude** → **Accedi con claude.ai**.
   - **Email (consigliato):** scegli *Continua con l'email*. Quando arriva l'email, tocca il link
     (funziona dopo aver attivato *Apri i link di accesso delle email direttamente qui*, impostazione di Android da fare una volta: la apre l'app),
     oppure tieni premuto il link, copialo e torna nell'app: viene preso da solo.
   - **Google:** funziona, ma Google chiede la password perché Android non condivide gli account del telefono con le pagine mostrate dentro le app
     ([regola di Google](https://developers.googleblog.com/en/upcoming-security-changes-to-googles-oauth-20-authorization-endpoint-in-embedded-webviews/)).
     Il gestore delle password (Google, Samsung Pass…) può inserirla con l'impronta.
2. Tieni premuto sulla Home → **Widget** → **Limits for Claude** e ridimensionalo come vuoi.
3. Personalizza tutto nell'app: l'anteprima in alto si aggiorna dal vivo.

| Tocco sul widget | Azione |
|---|---|
| Angolo in alto a destra | Aggiorna subito |
| Altrove | Apre l'app (o la pagina di accesso se non sei collegato) |

## Come funziona

- La pagina di accesso di claude.ai è mostrata in una WebView (un mini-browser interno all'app), che salva il cookie di sessione.
- A ogni aggiornamento, una WebView invisibile con gli stessi cookie chiama `claude.ai/api/organizations` e poi
  `claude.ai/api/organizations/{id}/usage`. Serve la WebView perché Cloudflare risponde `403` alle richieste dirette fatte senza browser.
- La risposta contiene l'elenco generico `limits` (tipo, percentuale, azzeramento) e `seven_day_breakdown` (quota per prodotto).
- Sotto i 15 minuti l'aggiornamento usa un allarme che **non sveglia** il telefono, quindi niente consumo a schermo spento.
  WorkManager (il programmatore di attività di Android) aggiorna ogni 15 minuti come riserva.

Dettagli, scelte progettuali e alternative scartate: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) (in inglese).

## Privacy e sicurezza

| Tema | Fatto |
|---|---|
| Server contattati | Solo `claude.ai` (più il controllo Cloudflare). Niente statistiche, tracciamento o server di terzi |
| Dati letti | Elenco organizzazioni e percentuali di utilizzo. Niente chat, messaggi, file o dati di pagamento |
| Dati salvati sul telefono | Cookie di sessione (archivio della WebView), ultimo rilevamento e impostazioni (preferenze private dell'app). Backup di Android disattivato |
| Permessi | `INTERNET`, `ACCESS_NETWORK_STATE` |
| Esci | Cancella cookie e dati salvati |

## Limiti

- **Interfaccia non documentata:** se Anthropic la cambia, il widget può mostrare un errore finché non si aggiorna il codice.
  Segnalalo con una [issue *API changed*](https://github.com/Tattanu/limits-for-claude/issues/new?template=api_change.yml).
- **Termini di Anthropic:** da febbraio 2026 vietano l'uso dei token OAuth dei piani Free/Pro/Max in prodotti diversi da Claude Code e claude.ai
  ([articolo](https://www.theregister.com/2026/02/20/anthropic_clarifies_ban_third_party_claude_access)).
  Per questo l'app **non** usa token né OAuth: legge solo la pagina di utilizzo della tua sessione claude.ai.
- **Verificato finora:** accesso con email e limiti di sessione e settimanali di un piano **Pro** (novembre 2026).
  **Non ancora verificato:** risposte dei piani Max, Team ed Enterprise; presenza della ripartizione per prodotto nella risposta di claude.ai;
  formato esatto dei link di accesso email (l'apertura diretta presuppone `https://claude.ai/magic-link…`; il metodo degli appunti funziona con qualsiasi link claude.ai).

## Compilare dal codice

Servono JDK 17 e Android SDK con la piattaforma 35 (Android Studio li include).

```bash
git clone https://github.com/Tattanu/limits-for-claude.git
cd limits-for-claude
./gradlew :app:testReleaseUnitTest   # test automatici
./gradlew :app:assembleDebug         # APK in app/build/outputs/apk/debug/
```

## Contribuire

Ogni contributo è benvenuto: segnalazioni, conferme da altri piani, traduzioni, nuovi stili, codice.
Guida: [CONTRIBUTING.md](CONTRIBUTING.md) · Regole di comportamento: [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) ·
Sicurezza: [SECURITY.md](SECURITY.md) · Modifiche: [CHANGELOG.md](CHANGELOG.md).

## Stato

| Data | Stato |
|---|---|
| 2026-11-04 | Versione 0.1.6: login claude.ai e lettura dei limiti verificati sul telefono (piano Pro). Nuovi stili, contatori per prodotto e login automatico compilati ma ancora da provare sul dispositivo. Nessuna release stabile pubblicata |

## Licenza

[MIT](LICENSE) © 2026 Tattanu e collaboratori.
"Claude" e "Anthropic" sono marchi di Anthropic, PBC; qui sono usati solo per descrivere con cosa funziona l'app.
