# Golu Study

Golu is now a study buddy, not just a pet. He still lives on your home screen -
drawn entirely with Canvas code, no image assets - but now he is powered by
Google Gemini: chat with him, turn your PDFs into quizzes, notes and one-liners,
and let him ask you questions out loud.

## What is inside

- **Home** - Golu as your companion. Tap him to make him smile, long-press to
  tickle, swipe up to jump, swipe down to sleep. The Play sheet keeps a few fun
  actions plus the Look panel (10 body colours, boy / girl mode with 6 outfits
  each, 5 accessories). Main goal: learning without boredom.
- **Chats (AI agent)** - multiple chats with Gemini. Each chat has its own
  model (changeable any time, even mid-chat) and skill (system prompt).
  Attach up to 10 files at once from your folders and the Library. AI voice is
  always off here.
- **Conversation** - one persistent voice chat with Golu. Speak or type, Golu
  replies out loud with lip-sync. Mic, voice replies and speech input can each
  be switched on or off. Uses the light conversation model.
- **Asking** - offline voice quiz mode. Pick a quiz from the Library (or make a
  new one with Quiz Maker), and Golu asks each question aloud, speaks the four
  options, then waits silently for your set time (default 30 seconds). Answer
  by voice or by tapping an option: Golu immediately tells you right or wrong,
  with the correct answer and explanation. If time runs out, he speaks the
  answer and lets you mark yourself right or wrong. No API calls while asking -
  the questions come from the saved quiz. Golu's lips move while he speaks.
- **Library (AI's own database)** - everything the AI creates (quizzes, notes,
  flashcards, one-liners, questions) is auto-saved here and stays even if you
  delete the chat it came from. Tap an item to view it; tap "Ask me" on a quiz
  to start asking mode with it.
- **Files (your own database)** - folders for your PDFs. Upload with the file
  picker; files are copied into the app's private storage so they stay put.
- **Skills** - custom system prompts. Built in: **Quiz Maker** (returns a JSON
  quiz the app can play), **One Liner Maker** (crisp revision lines),
  **Questions Asker** (practice questions with answers and explanations).
  Make your own skills too and pick one per chat.
- **Memory** - facts Golu remembers about you. When enabled, they are added to
  every chat's system prompt. "Suggest facts from chats" uses the background
  model to propose new facts.
- **Settings** - your Gemini API key (entered in the app, stored only on your
  device, never in this repo), four model slots (AI agent, asking mode,
  conversation, background work) picked from your saved model list, TTS & STT
  language (Hindi / English), quiz answer time, conversation toggles and a
  daily study reminder.

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
