# Golu Study Buddy

**Version 0.8.0** — your study buddy.

Golu is a study buddy, not just a pet. He lives on your home screen -
drawn entirely with Canvas code, no image assets - and is powered by
Google Gemini: chat with him, turn your PDFs into quizzes, notes and one-liners,
read them back in the reader, and let him build you learning web apps and games.

## What is inside

- **Home** - Golu as your companion, fully visible above the buttons. The
  Buddy button swaps the home buttons in place for the Look panel (14 body
  colours, boy / girl mode with 10 outfits each, 14 clothes colours, 5
  accessories, 6 hats and 5 glasses styles - hats and glasses can be worn
  together) - no separate screen to open and close. He breathes, blinks
  and looks around while idle, and smiles when you tap him. Main goal:
  learning without boredom.
- **Chats (AI agent)** - multiple chats with Gemini. Each chat has its own
  model (changeable any time, even mid-chat) and one or more skills (system
  prompts, combined when calling Gemini). Attach up to 10 files at once from
  your folders and the Library, with a folder-grouped picker and search.
  AI voice is always off here. Long-press a message to copy it, save it to
  the Library, delete one pair, select several pairs to delete at once, or
  clear the whole chat.
- **Talk** - one persistent voice chat with Golu. Speak or type, Golu
  replies out loud with lip-sync. Mic, voice replies and speech input can each
  be switched on or off. Attach up to 3 files from folders or the Library.
  Uses the light conversation model. The compact header leaves most of the
  screen for chatting. Long-press a message to manage it, like in Chats.
- **Reader** - tap any notes, quiz, flashcard set, one-liner set or question
  set in the Library - or tap its card in an agent chat - to read it. Notes
  show as clean text; quizzes show each question with its options and a
  "Show answer" reveal; flashcards flip when you tap them; one-liners show
  as a clean numbered list; questions show with their answers and
  explanations. The reader is voice-free on purpose: no TTS, no STT, no mic
  - speech stays in Talk mode only.
- **Library (AI's own database)** - everything the AI creates (quizzes, notes,
  flashcards, one-liners, questions, web apps) is auto-saved here and stays even if you
  delete the chat it came from. Items live in folders: organise them by hand
  (long-press to move) or tap **Manage** and the helper model proposes folders
  for everything unfiled. Search by title, filter by kind and folder. Tap an
  item to read it in the reader; tap "Open" on a web app to play
  it. Export / Import the whole Library as JSON, PDF or ZIP.
- **Web apps** - ask Golu (Web App Maker skill) to build a small learning web
  app or game: a 10-question quiz app, a Duolingo-style English game, a
  Read-Along-style reader. The app is one self-contained HTML file, saved to
  the Library; tap Open to play it. When you finish, the app sends its result
  (score, answers, time - whatever the app tracks) to Golu only if you choose
  to share it: after each result you can Share with Golu or Don't share, and
  remember the choice - or set it once in Settings > Web apps ("Ask each time"
  is the default). Your result is always saved to the Library either way. The web page runs locally with no navigation
  and gets a single bridge call (`window.Golu.sendResult`), so it can never
  reach your API key or app data.
- **Files (your own database)** - folders and sub-folders for your PDFs.
  Upload with the file picker into the folder you are in (or pick a folder
  when uploading from the top level); files are copied into the app's private
  storage so they stay put. Search finds files across all folders.
  Export / Import folders and files as JSON (list only), PDF or ZIP
  (ZIP includes the actual files).
- **Skills** - custom system prompts. Built in: **Quiz Maker** (returns a JSON
  quiz the reader can show), **Flashcard Maker** (returns a JSON set of
  front/back cards), **One Liner Maker** (returns a JSON list of crisp
  revision lines), **Questions Asker** (study questions with answers and
  explanations), **Web App Maker** (returns one self-contained HTML learning
  game or app that reports its result back to Golu - only if you choose to
  share it). Make your own skills too
  and pick several per chat.
  Export / Import skills as JSON, PDF or ZIP.
- **Memory** - facts Golu remembers about you, now one tap from Home. When
  enabled, they are added to every chat's system prompt. "Suggest from chats"
  uses the background model to propose new facts. Export / Import memories
  as JSON, PDF or ZIP.
- **Backup & Restore** - in Settings: **Backup** writes one ZIP file with
  everything (chats, library, files, skills, memory, settings) - except your
  Gemini API key, which is never exported. **Restore** validates the backup
  first, then replaces all local data with it and restarts the app; enter
  your API key again afterwards.
- **Settings** - your Gemini API key (entered in the app, stored only on your
  device, never in this repo), three model slots (agent, talk, helper)
  picked from your saved model list (presets: gemini-3.5-flash-lite,
  gemini-3.6-flash, gemini-3.8-flash), TTS & STT language (Hindi / English),
  Talk toggles, web app result sharing and a daily study reminder. API errors show
  full detail (model, HTTP status, Gemini's message) instead of a plain toast.

## Data & privacy

- Three separate on-device databases: `golu_user.db` (your folders + files),
  `golu_ai.db` (the Library), `golu_app.db` (chats, messages, skills, memory).
  Deleting a chat never deletes Library items.
- Only your messages and attached files are sent to Gemini, and only when you
  press send (web app results only when you choose to share them). The API key is stored in SharedPreferences on the device.

## Tech notes

- Native Android, Java, no AndroidX, no third-party dependencies: platform
  APIs + `org.json` + `HttpURLConnection` only. All UI is built in code.
- Gemini: `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}`
  with `systemInstruction` and `inlineData` parts for attached PDFs/images.
- Launcher icon is a vector drawable (no PNGs in the repo).

## Build the APK

Every push to `main` runs GitHub Actions, builds the APK and publishes it to the release
tagged `golu-latest`:

```
https://github.com/sp-dev-22/my-pet-golu/releases/download/golu-latest/Golu.apk
```

Local build:

```bash
gradle assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

The app is locked to portrait mode. Permissions: internet (Gemini), microphone
(voice), vibration (touch feedback), notifications (study reminder).

## License

Golu Study Buddy is licensed under the PolyForm Noncommercial License 1.0.0 — see
[LICENSE](LICENSE). Copyright 2026 Somdatt Prajapat.

Noncommercial use only. Any commercial use requires permission from the
copyright holder.
