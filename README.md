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

## Calendario condiviso: come impostarlo

1. Uno dei due crea su Google Calendar (dal computer, calendar.google.com) un nuovo calendario, per esempio «Noi due 💕».
2. In **Impostazioni e condivisione** di quel calendario aggiunge l'email dell'altra persona con il permesso **Apportare modifiche agli eventi**.
3. L'altra persona accetta l'invito dall'email.
4. Sul telefono, nell'app **Google Calendar**, il calendario deve risultare sincronizzato: Impostazioni → nome del calendario → **Sincronizza** attivo.
5. In Calendarietto, **Impostazioni → Calendari**, scegliete entrambi «Noi due 💕» come **⭐ Per la voce**. Da quel momento quello che uno aggiunge a voce compare anche sul telefono dell'altro, di solito entro pochi minuti. Il pulsante 🔄 forza la sincronizzazione.

## Note tecniche

- Kotlin e Jetpack Compose, minSdk 26 (Android 8), target Android 15.
- I dati passano dal Calendar Provider di Android, che è sincronizzato con Google dall'account del telefono: non serve configurare Google Cloud né OAuth.
- Il riconoscimento vocale usa quello di sistema (Google). La frase viene interpretata da `voice/ItalianEventParser.kt`, i cui test in `app/src/test` girano a ogni build.
- L'APK è firmato con la chiave `app/cozy.keystore`, inclusa nel progetto perché ogni versione si aggiorni sopra la precedente. Per questo conviene tenere il repository **privato**.
- Per modificare il progetto in Android Studio: **File → Open** sulla cartella `calendarietto`.
