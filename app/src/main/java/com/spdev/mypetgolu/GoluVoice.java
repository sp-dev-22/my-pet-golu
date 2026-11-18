package com.spdev.mypetgolu;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.Locale;

/**
 * One helper for Golu's voice: TextToSpeech (with utterance callbacks for
 * lip-sync) and SpeechRecognizer. Activities create it, use it and shut
 * it down in onDestroy. All callbacks arrive on the main thread.
 */
public class GoluVoice {

    public interface TtsListener {
        void onStart(String utteranceId);

        void onDone(String utteranceId);

        void onError(String utteranceId);
    }

    public interface SttListener {
        void onResult(String text);

        void onError(String message);
    }

    public interface ReadyListener {
        void onReady(boolean hindiAvailable);
    }

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean hindiAvailable = false;
    private TtsListener ttsListener;
    private ReadyListener readyListener;

    private SpeechRecognizer recognizer;
    private SttListener sttListener;
    private boolean listening = false;

    // continuous listening (Asking mode): restart the recognizer on
    // silence/timeout until the deadline, so the mic lasts the whole
    // answer time instead of stopping after a few quiet seconds
    private boolean continuous = false;
    private long listenDeadline = 0;
    private String listenLang = "";
    private final Runnable restartRunnable = new Runnable() {
        @Override
        public void run() {
            if (continuous && recognizer != null && System.currentTimeMillis() < listenDeadline) {
                beginListening(listenLang);
            } else if (continuous) {
                continuous = false;
                postSttError("TIMEOUT");
            }
        }
    };

    public GoluVoice(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    // ------------------------------------------------------------------- TTS

    public void initTts(final Locale locale, ReadyListener ready) {
        this.readyListener = ready;
        tts = new TextToSpeech(ctx, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.SUCCESS) {
                    ttsReady = false;
                    postReady(false);
                    return;
                }
                ttsReady = true;
                int res = tts.setLanguage(locale);
                hindiAvailable = res != TextToSpeech.LANG_MISSING_DATA
                        && res != TextToSpeech.LANG_NOT_SUPPORTED;
                if (!hindiAvailable) tts.setLanguage(Locale.US);
                tts.setSpeechRate(0.95f);
                tts.setPitch(1.15f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(final String id) {
                        postTtsStart(id);
                    }

                    @Override
                    public void onDone(final String id) {
                        postTtsDone(id);
                    }

                    @Override
                    public void onError(final String id) {
                        postTtsError(id);
                    }
                });
                postReady(hindiAvailable);
            }
        });
    }

    public void setTtsListener(TtsListener l) {
        this.ttsListener = l;
    }

    public boolean isTtsReady() {
        return ttsReady;
    }

    public void setTtsLanguage(Locale locale) {
        if (ttsReady && tts != null) {
            int res = tts.setLanguage(locale);
            hindiAvailable = res != TextToSpeech.LANG_MISSING_DATA
                    && res != TextToSpeech.LANG_NOT_SUPPORTED;
            if (!hindiAvailable) tts.setLanguage(Locale.US);
        }
    }

    /** Speaks, replacing anything currently spoken. */
    public void speak(String text, String utteranceId) {
        if (!ttsReady || tts == null) return;
        String clean = cleanForSpeech(text);
        if (clean.isEmpty()) {
            // nothing speakable: still report completion so callers that
            // wait for onDone (e.g. Practice) move on instead of hanging
            postTtsDone(utteranceId);
            return;
        }
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
    }

    /** Queues speech after anything currently spoken. */
    public void speakQueued(String text, String utteranceId) {
        if (!ttsReady || tts == null) return;
        String clean = cleanForSpeech(text);
        if (clean.isEmpty()) {
            postTtsDone(utteranceId);
            return;
        }
        tts.speak(clean, TextToSpeech.QUEUE_ADD, null, utteranceId);
    }

    /**
     * Strips markdown / formatting artefacts from text before it is spoken,
     * so the TTS engine reads clean natural sentences instead of saying
     * "star star" or "hash" for **bold** or ## headers. Displayed text is
     * never touched - only the spoken copy goes through here.
     */
    public static String cleanForSpeech(String text) {
        if (text == null) return "";
        String s = text;
        // markdown links [text](url) -> text
        s = s.replaceAll("\\[([^\\]]+)\\]\\([^\\)]+\\)", "$1");
        // ## headers -> plain text
        s = s.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "");
        // **bold**, __bold__, *italic*, _italic_, `code` -> inner text
        s = s.replaceAll("\\*\\*(.*?)\\*\\*", "$1");
        s = s.replaceAll("__(.*?)__", "$1");
        s = s.replaceAll("\\*(.*?)\\*", "$1");
        s = s.replaceAll("_(.*?)_", "$1");
        s = s.replaceAll("`{1,3}(.*?)`{1,3}", "$1");
        // any stray formatting characters left over
        s = s.replace("*", "").replace("#", "").replace("`", "").replace("_", "");
        // "%" is spoken as "percent" ("50%" -> "50 percent")
        s = s.replace("%", " percent ");
        // bullet markers at line starts
        s = s.replaceAll("(?m)^\\s*[-\\u2022]\\s+", "");
        // collapse whitespace and line breaks into single spaces
        s = s.replaceAll("\\s+", " ");
        return s.trim();
    }

    public void stopTts() {
        if (tts != null) tts.stop();
    }

    public void shutdownTts() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ttsReady = false;
    }

    private void postReady(final boolean hindi) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (readyListener != null) readyListener.onReady(hindi);
            }
        });
    }

    private void postTtsStart(final String id) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (ttsListener != null) ttsListener.onStart(id);
            }
        });
    }

    private void postTtsDone(final String id) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (ttsListener != null) ttsListener.onDone(id);
            }
        });
    }

    private void postTtsError(final String id) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (ttsListener != null) ttsListener.onError(id);
            }
        });
    }

    // ------------------------------------------------------------------- STT

    public static boolean isSttAvailable(Context c) {
        return SpeechRecognizer.isRecognitionAvailable(c);
    }

    public boolean isListening() {
        return listening;
    }

    public void startListening(String languageTag, SttListener listener) {
        // reset first: a stale recognizer from an earlier pass can still
        // deliver late errors that would fight the new session
        cancelListening();
        this.sttListener = listener;
        if (!isSttAvailable(ctx)) {
            postSttError("Speech recognition is not available on this device.");
            return;
        }
        ensureRecognizer();
        beginListening(languageTag);
    }

    /**
     * Keeps listening for up to durationMs: whenever the recognizer stops
     * on silence or a timeout with no result, it restarts automatically
     * until the time is up (then onError("TIMEOUT")). A real result still
     * ends listening immediately. Use for Asking mode, where mic time must
     * equal the user's set answer time.
     */
    public void startListeningUntil(String languageTag, long durationMs, SttListener listener) {
        // reset first: a stale recognizer from an earlier question can still
        // deliver a late ERROR_CLIENT after its cancel() - if that lands
        // after the next question starts, its restart loop calls
        // startListening while the new session is already listening, the
        // recognizer goes BUSY and the mic never recovers (the "mic timeout
        // returns after Q1" bug). A fresh recognizer per pass kills it.
        cancelListening();
        continuous = true;
        listenDeadline = System.currentTimeMillis() + Math.max(1000, durationMs);
        listenLang = languageTag;
        this.sttListener = listener;
        if (!isSttAvailable(ctx)) {
            continuous = false;
            postSttError("Speech recognition is not available on this device.");
            return;
        }
        ensureRecognizer();
        beginListening(languageTag);
    }

    /** Creates the recognizer (once) and wires its listener. */
    private void ensureRecognizer() {
        if (recognizer != null) return;
        recognizer = SpeechRecognizer.createSpeechRecognizer(ctx);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                listening = true;
            }

            @Override
            public void onBeginningOfSpeech() {
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                listening = false;
            }

            @Override
            public void onError(int error) {
                listening = false;
                if (continuous && isRestartable(error)) {
                    // no speech yet: restart while time remains
                    if (System.currentTimeMillis() < listenDeadline) {
                        main.postDelayed(restartRunnable, 250);
                    } else {
                        continuous = false;
                        postSttError("TIMEOUT");
                    }
                    return;
                }
                if (error == SpeechRecognizer.ERROR_NO_MATCH
                        || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    postSttError("NOMATCH");
                } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    postSttError("Microphone permission needed.");
                } else if (error == SpeechRecognizer.ERROR_CLIENT
                        || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    postSttError("BUSY");
                } else {
                    postSttError("Mic error, please try again.");
                }
            }

            @Override
            public void onResults(Bundle results) {
                listening = false;
                continuous = false;
                main.removeCallbacks(restartRunnable);
                ArrayList<String> list = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (list == null || list.isEmpty()) {
                    postSttError("NOMATCH");
                    return;
                }
                postSttResult(list.get(0));
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });
    }

    /** Errors worth an automatic restart while continuous listening. */
    private static boolean isRestartable(int error) {
        return error == SpeechRecognizer.ERROR_NO_MATCH
                || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                || error == SpeechRecognizer.ERROR_CLIENT
                || error == SpeechRecognizer.ERROR_NETWORK;
    }

    /** Starts one listen pass with long silence windows (fewer early cutoffs). */
    private void beginListening(String languageTag) {
        if (recognizer == null) return;
        if (listening) {
            // already listening (e.g. a restart racing a fresh session):
            // a second startListening would go BUSY and kill the live pass
            return;
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        // wait longer before deciding the user has stopped speaking
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500);
        try {
            recognizer.startListening(i);
            listening = true;
        } catch (Exception e) {
            listening = false;
            if (continuous && System.currentTimeMillis() < listenDeadline) {
                main.postDelayed(restartRunnable, 400);
            } else {
                continuous = false;
                postSttError("Could not start listening.");
            }
        }
    }

    /**
     * Stops listening and destroys the recognizer. Destroying (not just
     * cancel()) is deliberate: a cancelled recognizer can still deliver a
     * late onError, and that stale callback would restart or break the
     * NEXT question's listening pass. The next startListening* call creates
     * a fresh recognizer, so every pass starts from a clean state.
     */
    public void cancelListening() {
        continuous = false;
        main.removeCallbacks(restartRunnable);
        if (recognizer != null) {
            try {
                recognizer.cancel();
            } catch (Exception ignored) {
            }
            try {
                recognizer.destroy();
            } catch (Exception ignored) {
            }
            recognizer = null;
        }
        listening = false;
    }

    public void destroyStt() {
        cancelListening();
    }

    private void postSttResult(final String text) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (sttListener != null) sttListener.onResult(text);
            }
        });
    }

    private void postSttError(final String msg) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (sttListener != null) sttListener.onError(msg);
            }
        });
    }

    /** Shuts down both TTS and STT. Call from onDestroy. */
    public void shutdownAll() {
        destroyStt();
        shutdownTts();
    }
}
