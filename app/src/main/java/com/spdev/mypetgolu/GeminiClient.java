package com.spdev.mypetgolu;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Gemini client over HttpURLConnection. No dependencies.
 * The API key is passed in by the caller (it comes from Settings only,
 * never from code). All calls run off the UI thread and report back
 * on the main thread.
 */
public final class GeminiClient {

    public interface Callback {
        void onOk(String text);

        void onError(String message);
    }

    /** One attached file, sent as an inlineData part. */
    public static class FilePart {
        public final String mime;
        public final String base64;

        public FilePart(String mime, String base64) {
            this.mime = mime;
            this.base64 = base64;
        }
    }

    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // ------------------------------------------------------------- builders

    public static JSONObject textPart(String text) {
        try {
            JSONObject p = new JSONObject();
            p.put("text", text == null ? "" : text);
            return p;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static JSONObject inlineDataPart(String mime, String base64) {
        try {
            JSONObject data = new JSONObject();
            data.put("mimeType", mime);
            data.put("data", base64);
            JSONObject p = new JSONObject();
            p.put("inlineData", data);
            return p;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** role is "user" or "model". */
    public static JSONObject content(String role, List<JSONObject> parts) {
        try {
            JSONObject c = new JSONObject();
            c.put("role", role);
            JSONArray arr = new JSONArray();
            for (JSONObject p : parts) arr.put(p);
            c.put("parts", arr);
            return c;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** Turns stored chat history into Gemini contents (user/ai -> user/model). */
    public static JSONArray historyContents(List<AppDb.Msg> history) {
        JSONArray out = new JSONArray();
        if (history == null) return out;
        for (AppDb.Msg m : history) {
            String role = AppDb.ROLE_AI.equals(m.role) ? "model" : "user";
            List<JSONObject> parts = new ArrayList<>();
            parts.add(textPart(m.text));
            out.put(content(role, parts));
        }
        return out;
    }

    // ---------------------------------------------------------------- calls

    /** One-shot prompt, no history. */
    public static void generateSimple(final String apiKey, final String model,
                                      final String systemPrompt, final String prompt,
                                      final boolean jsonMode, final Callback cb) {
        JSONArray contents = new JSONArray();
        List<JSONObject> parts = new ArrayList<>();
        parts.add(textPart(prompt));
        contents.put(content("user", parts));
        generate(apiKey, model, systemPrompt, contents, jsonMode, cb);
    }

    /** Full call with caller-built contents (history + files). */
    public static void generate(final String apiKey, final String model,
                                final String systemPrompt, final JSONArray contents,
                                final boolean jsonMode, final Callback cb) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            postError(cb, "No API key. Add your Gemini API key in Settings first.");
            return;
        }
        if (model == null || model.trim().isEmpty()) {
            postError(cb, "No model selected. Pick a model in Settings.");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String err = null;
                for (int attempt = 0; attempt < 2; attempt++) {
                    try {
                        String text = doCall(apiKey, model, systemPrompt, contents, jsonMode);
                        postOk(cb, text);
                        return;
                    } catch (Retryable e) {
                        err = e.getMessage();
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ignored) {
                        }
                    } catch (Exception e) {
                        err = friendly(e);
                        break;
                    }
                }
                postError(cb, err == null ? "Gemini call failed." : err);
            }
        }).start();
    }

    private static class Retryable extends Exception {
        Retryable(String m) {
            super(m);
        }
    }

    private static String doCall(String apiKey, String model, String systemPrompt,
                                 JSONArray contents, boolean jsonMode) throws Exception {
        String urlStr = ENDPOINT + URLEncoder.encode(model, "UTF-8")
                + ":generateContent?key=" + URLEncoder.encode(apiKey, "UTF-8");
        JSONObject body = new JSONObject();
        body.put("contents", contents);
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JSONObject sys = new JSONObject();
            JSONArray sp = new JSONArray();
            sp.put(textPart(systemPrompt));
            sys.put("parts", sp);
            body.put("systemInstruction", sys);
        }
        if (jsonMode) {
            JSONObject cfg = new JSONObject();
            cfg.put("responseMimeType", "application/json");
            body.put("generationConfig", cfg);
        }

        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);
            conn.setDoOutput(true);
            byte[] payload = body.toString().getBytes("UTF-8");
            OutputStream os = conn.getOutputStream();
            os.write(payload);
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300
                    ? conn.getInputStream() : conn.getErrorStream();
            String raw = readAll(in);
            if (code == 429 || code == 503) {
                throw new Retryable("Gemini is busy (HTTP " + code + "). Please try again.");
            }
            if (code < 200 || code >= 300) {
                throw new Exception("Gemini error (HTTP " + code + "): " + errorMessage(raw));
            }
            return extractText(new JSONObject(raw));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Concatenates all text parts of the first candidate. */
    public static String extractText(JSONObject data) throws Exception {
        if (data.has("error")) {
            JSONObject e = data.getJSONObject("error");
            throw new Exception(e.optString("message", "Unknown Gemini error"));
        }
        JSONArray candidates = data.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) return "No response.";
        JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
        if (content == null) return "No response.";
        JSONArray parts = content.optJSONArray("parts");
        if (parts == null) return "No response.";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            String t = parts.getJSONObject(i).optString("text", "");
            if (!t.isEmpty()) {
                if (b.length() > 0) b.append('\n');
                b.append(t);
            }
        }
        return b.length() == 0 ? "No response." : b.toString();
    }

    private static String errorMessage(String raw) {
        try {
            JSONObject d = new JSONObject(raw);
            if (d.has("error")) return d.getJSONObject("error").optString("message", raw);
        } catch (Exception ignored) {
        }
        return raw == null || raw.isEmpty() ? "unknown error" : raw;
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder b = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) b.append(line).append('\n');
        r.close();
        return b.toString();
    }

    private static String friendly(Exception e) {
        String m = e.getMessage();
        if (m == null) return "Network error. Check your internet connection.";
        if (m.contains("UnknownHost") || m.contains("ConnectException")
                || m.contains("SocketTimeout") || m.contains("Unable to resolve")) {
            return "No internet connection. Check your network and try again.";
        }
        if (m.contains("API key not valid") || m.contains("API_KEY_INVALID")) {
            return "Your Gemini API key looks wrong. Check it in Settings.";
        }
        return m;
    }

    private static void postOk(final Callback cb, final String text) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                cb.onOk(text);
            }
        });
    }

    private static void postError(final Callback cb, final String msg) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                cb.onError(msg);
            }
        });
    }

    private GeminiClient() {
    }
}
