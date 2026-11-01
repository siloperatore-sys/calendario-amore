# Calendarietto 🎀

Calendario condiviso cozy e kawaii per Android: legge Google Calendar, suona una sveglia a schermo intero prima degli impegni, manda un riepilogo ogni mattina e aggiunge gli eventi a voce.

## Cosa fa

- **Google Calendar, condiviso.** Legge i calendari degli account Google già presenti sul telefono, compresi quelli condivisi (per esempio un calendario di coppia). Gli eventi creati qui finiscono su Google e compaiono anche sull'altro telefono.
- **Più account insieme.** Se sul telefono ci sono più account Google, i loro calendari si vedono tutti insieme. Ogni calendario si può nascondere o rendere muto.
- **Sveglia a schermo intero.** Suona prima di ogni impegno (5, 10, 15 minuti… a scelta), anche a telefono bloccato, con i tasti "Ho capito!" e "Posticipa".
- **Riepilogo del mattino.** Una notifica all'ora che scegli con gli impegni del giorno.
- **Comandi vocali in italiano.** Tocca 🎤 e di' per esempio:
  - «dentista martedì alle 15»
  - «cena da mamma sabato alle 20 e 30»
  - «compleanno di Giulia il 12 dicembre» (diventa un evento di tutto il giorno)
  - «riunione dalle 10 alle 11:30 giovedì»
  - «palestra domani sera per un'ora e mezza»

  Prima di salvare vedi sempre cosa ha capito e puoi correggerlo.
- **Due mascotte.** Robottino (blu notte e azzurro) o Unicorno (lilla, menta e rosa): ognuno sceglie la sua sul proprio telefono e i colori dell'app cambiano di conseguenza.

## Come ottenere l'APK con GitHub (senza installare nulla)

1. Crea un account su github.com, se non ce l'hai.
2. Crea un nuovo repository (pulsante **New**), chiamalo `calendarietto` e impostalo come **Private**.
3. Nella pagina del repository vuoto scegli **uploading an existing file**. Trascina **tutto il contenuto** della cartella `calendarietto` (non la cartella stessa), compresa la cartella nascosta `.github`, poi premi **Commit changes**.
   - Su Mac le cartelle che iniziano col punto sono nascoste: in Finder premi `Cmd + Shift + .` per vederle.
   - Se l'upload dal browser salta `.github`, crea il file a mano con **Add file → Create new file**, nome `.github/workflows/build.yml`, e incolla il contenuto del file.
4. Apri la scheda **Actions**: parte la build «Costruisci APK» e dura circa 5-8 minuti.
5. Al termine, nella pagina principale del repository, a destra, compare **Releases**. Aprila dal telefono e scarica `Calendarietto.apk`.

Ogni volta che modifichi un file su GitHub viene creata una nuova versione, che si installa sopra quella vecchia senza perdere le impostazioni.

## Installazione sul telefono

1. Apri `Calendarietto.apk`. Android chiede di consentire l'installazione da questa fonte (il browser o i file): conferma.
2. Al primo avvio scegli il nome e la mascotte, poi tocca **Attiva** su ogni permesso:
   - **Calendario**: obbligatorio.
   - **Notifiche**, **Sveglia a schermo intero** e **Sveglie puntuali**: servono per le sveglie.
   - **Niente risparmio batteria**: consigliato, soprattutto su Xiaomi, Huawei, Oppo e Samsung, che altrimenti possono bloccare le sveglie.
3. In **Impostazioni ⚙️ → Sveglie** tocca **Prova la sveglia**: dopo 5 secondi deve suonare, anche a schermo bloccato.

## Collegarsi in due 💕

Nell'app tocca **💕** in alto (oppure **⚙️ → Noi due**).

**Chi invita:** scrive l'email Google dell'altra persona e tocca **💌 Invia invito**. L'app crea il calendario «Noi due 💕» su Google Calendar e lo condivide con permesso di modifica. Volendo condivide anche il calendario personale, solo in lettura. Poi tocca **📲 Manda il link** per inviarlo anche su WhatsApp.

**Chi riceve:** apre l'email di Google Calendar (o il link) e tocca **Aggiungi questo calendario**. Poi apre l'app: compare «💌 Calendario condiviso!» e basta toccare **Sì, usalo**. In alternativa: 💕 → **Cerca calendari condivisi**.

Da quel momento ogni impegno aggiunto da uno dei due, a voce o a mano, compare anche sul telefono dell'altro.

### Configurazione Google (una volta sola, solo per chi invita)

Per creare e condividere calendari dall'app, Google chiede di registrare l'app. Servono circa 10 minuti, dal computer:

1. Vai su **console.cloud.google.com** con il tuo account Google e crea un progetto (nome a piacere, es. «Calendarietto»).
2. **API e servizi → Libreria** → cerca **Google Calendar API** → **Abilita**.
3. **API e servizi → Schermata consenso OAuth** (o «Google Auth Platform»): tipo **Esterno**, nome app «Calendarietto», la tua email come contatto. Alla voce **Utenti di prova** aggiungi la tua email e quella del tuo amore.
4. **API e servizi → Credenziali → Crea credenziali → ID client OAuth**, tipo **Android**:
   - Nome pacchetto: `com.calendarietto.app`
   - Impronta certificato SHA-1: `32:36:F6:7B:57:44:D5:39:29:82:A7:6E:BE:62:EF:DA:07:C8:9A:A9`
5. Salva. Non serve copiare nulla nell'app: Google la riconosce da pacchetto e impronta.

Al primo invito Google mostrerà «App non verificata»: è normale per un'app personale. Tocca **Continua** e consenti l'accesso al calendario.

Se non vuoi configurare nulla, c'è la strada manuale: crea il calendario da calendar.google.com (dal computer), in **Impostazioni e condivisione** aggiungi l'email dell'altra persona con **Apportare modifiche agli eventi**, poi nell'app di chi riceve usa **Cerca calendari condivisi**.

## Note tecniche

- Kotlin e Jetpack Compose, minSdk 26 (Android 8), target Android 15.
- I dati passano dal Calendar Provider di Android, che è sincronizzato con Google dall'account del telefono: non serve configurare Google Cloud né OAuth.
- Il riconoscimento vocale usa quello di sistema (Google). La frase viene interpretata da `voice/ItalianEventParser.kt`, i cui test in `app/src/test` girano a ogni build.
- L'APK è firmato con la chiave `app/cozy.keystore`, inclusa nel progetto perché ogni versione si aggiorni sopra la precedente. Per questo conviene tenere il repository **privato**.
- Per modificare il progetto in Android Studio: **File → Open** sulla cartella `calendarietto`.
