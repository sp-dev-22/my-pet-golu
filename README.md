# Golu Study

Golu is now a study buddy, not just a pet. He still lives on your home screen -
drawn entirely with Canvas code, no image assets - but now he is powered by
Google Gemini: chat with him, turn your PDFs into quizzes, notes and one-liners,
and let him ask you questions out loud.

## What is inside

- **Home** - Golu as your companion. Tap him to make him smile, long-press to
  tickle, swipe up to jump, swipe down to sleep. The Play button swaps the
  home buttons in place for a few fun actions plus the Look panel (10 body
  colours, boy / girl mode with 6 outfits each, 5 accessories) - no separate
  screen to open and close. Main goal: learning without boredom.
- **Chats (AI agent)** - multiple chats with Gemini. Each chat has its own
  model (changeable any time, even mid-chat) and one or more skills (system
  prompts, combined when calling Gemini). Attach up to 10 files at once from
  your folders and the Library, with a folder-grouped picker and search.
  AI voice is always off here.
- **Talk** - one persistent voice chat with Golu. Speak or type, Golu
  replies out loud with lip-sync. Mic, voice replies and speech input can each
  be switched on or off. Attach up to 3 files from folders or the Library.
  Uses the light conversation model.
- **Quiz** - offline voice quiz mode. Pick a quiz from the Library (made in
  agent chats with Quiz Maker) - nothing is created here - and Golu asks each
  question aloud, speaks the four options, then listens for your answer until
  your set time runs out (default 30 seconds; the mic keeps listening and
  restarts on silence instead of cutting off early). Answer by voice or by
  tapping an option: Golu immediately tells you right or wrong, with the
  correct answer and explanation. If time runs out, he speaks the answer and
  lets you mark yourself right or wrong. No API calls while asking - the
  questions come from the saved quiz. Golu's lips move while he speaks, and
  his voice can be switched off.
- **Library (AI's own database)** - everything the AI creates (quizzes, notes,
  flashcards, one-liners, questions) is auto-saved here and stays even if you
  delete the chat it came from. Items live in folders: organise them by hand
  (long-press to move) or tap **Manage** and the helper model proposes folders
  for everything unfiled. Search by title, filter by kind and folder. Tap an
  item to view it; tap "Practice" on a quiz to start Quiz mode with it.
- **Files (your own database)** - folders and sub-folders for your PDFs.
  Upload with the file picker into the folder you are in (or pick a folder
  when uploading from the top level); files are copied into the app's private
  storage so they stay put. Search finds files across all folders.
- **Skills** - custom system prompts. Built in: **Quiz Maker** (returns a JSON
  quiz the app can play), **One Liner Maker** (crisp revision lines),
  **Questions Asker** (practice questions with answers and explanations).
  Make your own skills too and pick several per chat.
- **Memory** - facts Golu remembers about you, now one tap from Home. When
  enabled, they are added to every chat's system prompt. "Suggest from chats"
  uses the background model to propose new facts.
- **Settings** - your Gemini API key (entered in the app, stored only on your
  device, never in this repo), four model slots (agent, quiz, talk, helper)
  picked from your saved model list (presets: gemini-3.5-flash-lite,
  gemini-3.6-flash, gemini-3.8-flash), TTS & STT language (Hindi / English),
  quiz answer time, Talk toggles and a daily study reminder. API errors show
  full detail (model, HTTP status, Gemini's message) instead of a plain toast.

## Data & privacy

- Three separate on-device databases: `golu_user.db` (your folders + files),
  `golu_ai.db` (the Library), `golu_app.db` (chats, messages, skills, memory,
  asking sessions). Deleting a chat never deletes Library items.
- Only your messages and attached files are sent to Gemini, and only when you
  press send. The API key is stored in SharedPreferences on the device.
- Asking mode works offline once a quiz is saved (voice uses the on-device
  speech engine).

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

Golu Study is licensed under the PolyForm Noncommercial License 1.0.0 — see
[LICENSE](LICENSE). Copyright 2026 Somdatt Prajapat.

Noncommercial use only. Any commercial use requires permission from the
copyright holder.
