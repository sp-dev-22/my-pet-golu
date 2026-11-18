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
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
    }

    /** Queues speech after anything currently spoken. */
    public void speakQueued(String text, String utteranceId) {
        if (!ttsReady || tts == null) return;
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId);
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
        this.sttListener = listener;
        if (!isSttAvailable(ctx)) {
            postSttError("Speech recognition is not available on this device.");
            return;
        }
        if (recognizer == null) {
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
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        try {
            recognizer.startListening(i);
            listening = true;
        } catch (Exception e) {
            listening = false;
            postSttError("Could not start listening.");
        }
    }

    public void cancelListening() {
        if (recognizer != null && listening) {
            try {
                recognizer.cancel();
            } catch (Exception ignored) {
            }
        }
        listening = false;
    }

    public void destroyStt() {
        if (recognizer != null) {
            try {
                recognizer.destroy();
            } catch (Exception ignored) {
            }
            recognizer = null;
        }
        listening = false;
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
