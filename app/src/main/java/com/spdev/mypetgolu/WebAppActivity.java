package com.spdev.mypetgolu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Plays an AI-made web app from the Library in a WebView. The HTML is
 * loaded from the local database only (loadDataWithBaseURL, no base URL),
 * all navigation is blocked, and the page gets exactly ONE bridge call:
 * window.Golu.sendResult(jsonString), fired once by the app when the user
 * reaches its end trigger. The result JSON has no fixed schema - the AI
 * chooses the fields. The result is shown to the user and saved to the
 * Library as notes; whether it is ALSO sent to Gemini for feedback is
 * the user's choice (ask each time, always share, never share - see
 * Prefs.getWebAppShareMode; the default is to ask).
 *
 * SECURITY: the bridge exposes a single method and holds no Context, no
 * preferences and no API key - JavaScript in the page cannot reach
 * anything in the app except sendResult.
 */
public class WebAppActivity extends Activity {

    /** System instruction for the feedback call after a web activity. */
    private static final String FEEDBACK_SYSTEM =
            "You are Golu, a friendly study buddy. The user just finished a web "
            + "activity you built for them. Give warm feedback on their performance, "
            + "correct mistakes if the result data shows any, and suggest a next step. "
            + "Reply in the same language the user uses.";

    private static final String RESULTS_CHAT_TITLE = "Web App Results";

    private AiDb aiDb;
    private AppDb appDb;
    private long itemId;
    private long chatId = -1;
    private AiDb.Item item;
    private WebView webView;
    private TextView loadingView;
    /** One result per play-through; Replay resets this. */
    private boolean resultReceived = false;
    private boolean destroyed = false;

    /**
     * The one and only JavaScript bridge. Holds no app state at all - just
     * a weak link back to the activity so it can hand over the result.
     */
    private static class GoluBridge {
        private final WeakReference<WebAppActivity> ref;

        GoluBridge(WebAppActivity a) {
            ref = new WeakReference<>(a);
        }

        /** Called by the web app exactly once, when the user finishes it. */
        @JavascriptInterface
        public void sendResult(final String json) {
            final WebAppActivity a = ref.get();
            if (a == null) return;
            // bridge calls arrive off the UI thread
            a.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    a.onResultReceived(json);
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        aiDb = new AiDb(this);
        appDb = new AppDb(this);
        itemId = getIntent().getLongExtra("library_item_id", -1);
        chatId = getIntent().getLongExtra("chat_id", -1);
        if (itemId <= 0) {
            Ui.toast(this, "No web app to open");
            finish();
            return;
        }

        // loading screen while the item is read off the UI thread
        LinearLayout loading = Ui.column(this);
        loadingView = Ui.empty(this, "Loading web app...");
        loading.addView(loadingView, Ui.matchWrap());
        setContentView(loading);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final AiDb.Item loaded = aiDb.getItem(itemId);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed) return;
                        if (loaded == null || !AiDb.KIND_WEBAPP.equals(loaded.kind)
                                || loaded.content == null
                                || loaded.content.trim().isEmpty()) {
                            new AlertDialog.Builder(WebAppActivity.this)
                                    .setTitle("Can't open this web app")
                                    .setMessage("It is missing or empty in the Library.")
                                    .setPositiveButton("OK",
                                            new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface d, int w) {
                                                    finish();
                                                }
                                            })
                                    .setCancelable(false)
                                    .show();
                            return;
                        }
                        item = loaded;
                        buildUi();
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (webView != null) {
            webView.stopLoading();
            webView.removeJavascriptInterface("Golu");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = Ui.column(this);

        LinearLayout bar = Ui.topBar(this, item.title);
        TextView replayBtn = Ui.chip(this, "Replay", false);
        replayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onReplay();
            }
        });
        bar.addView(replayBtn, Ui.wrapWrap());
        root.addView(bar, Ui.matchWrap());

        loadingView = Ui.empty(this, "Loading web app...");
        root.addView(loadingView, Ui.matchWrap());

        webView = new WebView(this);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        // local content only: no file, content or network access for the page
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setAllowFileAccessFromFileURLs(false);
        ws.setAllowUniversalAccessFromFileURLs(false);
        webView.addJavascriptInterface(new GoluBridge(this), "Golu");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true; // no navigation: the app is local-only
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true; // same, for older WebView versions
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (loadingView != null) loadingView.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description,
                                        String failingUrl) {
                if (!destroyed) Ui.toast(WebAppActivity.this,
                        "Couldn't load this web app");
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        webView.loadDataWithBaseURL(null, item.content, "text/html", "UTF-8", null);
    }

    /** Restarts the app from the beginning; a new play-through may send a new result. */
    private void onReplay() {
        resultReceived = false;
        if (loadingView != null) loadingView.setVisibility(View.VISIBLE);
        if (webView != null && item != null) {
            webView.loadDataWithBaseURL(null, item.content, "text/html", "UTF-8", null);
        }
    }

    /**
     * Handles a result from the web app. Runs on the UI thread (the bridge
     * posts here). Validates the JSON, saves a summary to the Library as
     * notes, and then - depending on the user's share choice - either asks
     * whether to send it to Golu, sends it, or keeps it local. Nothing is
     * ever forwarded to Gemini without the user's say-so, and the flow
     * never crashes on bad input.
     */
    private void onResultReceived(String json) {
        if (destroyed) return;
        if (resultReceived) {
            Ui.toast(this, "Result already sent for this play-through. "
                    + "Tap Replay to play again and send a new result.");
            return;
        }
        // validate: must be a JSON object (or array) - malformed input is
        // ignored and does NOT count as the one result
        boolean valid = false;
        try {
            new JSONObject(json);
            valid = true;
        } catch (Exception e1) {
            try {
                new JSONArray(json);
                valid = true;
            } catch (Exception ignored) {
            }
        }
        if (!valid) {
            Ui.toast(this, "That result wasn't valid - nothing was sent.");
            return;
        }
        resultReceived = true;

        final String pretty = WebAppUtil.prettyJson(json, 2000);
        // (a) keep the result: save a short summary to the Library as notes
        String when = new SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.US)
                .format(new Date());
        aiDb.addItem("Web app result: " + item.title, AiDb.KIND_NOTES,
                "Result from '" + item.title + "' (" + when + "):\n\n" + pretty,
                "Web app");

        // (b) the user's share choice decides what happens next
        int mode = Prefs.getWebAppShareMode(this);
        if (mode == Prefs.SHARE_NEVER) {
            showSavedLocally(pretty);
        } else if (mode == Prefs.SHARE_ALWAYS) {
            shareResult(json, pretty);
        } else {
            showShareChoice(json, pretty);
        }
    }

    /** Asks the user whether to share this result with Golu (with a "remember" checkbox). */
    private void showShareChoice(final String json, String pretty) {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));

        TextView intro = new TextView(this);
        intro.setText("You finished '" + item.title + "'. Share your result with Golu "
                + "so it can give you feedback? Your result is already saved to the "
                + "Library either way.");
        intro.setTextSize(14f);
        intro.setTextColor(Ui.INK);
        view.addView(intro, Ui.matchWrap());

        TextView result = new TextView(this);
        result.setText(pretty);
        result.setTextSize(13f);
        result.setTextColor(Ui.MUTED);
        result.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        ScrollView sv = new ScrollView(this);
        sv.addView(result, Ui.matchWrap());
        LinearLayout.LayoutParams svLp = Ui.matchWrap();
        svLp.height = Ui.dp(this, 160);
        view.addView(sv, svLp);

        final CheckBox remember = new CheckBox(this);
        remember.setText("Remember my choice");
        remember.setTextSize(14f);
        remember.setTextColor(Ui.INK);
        view.addView(remember, Ui.matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("Share result with Golu?")
                .setView(view)
                .setPositiveButton("Share with Golu", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (remember.isChecked()) {
                            Prefs.setWebAppShareMode(WebAppActivity.this, Prefs.SHARE_ALWAYS);
                        }
                        shareResult(json, WebAppUtil.prettyJson(json, 2000));
                    }
                })
                .setNegativeButton("Don't share", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (remember.isChecked()) {
                            Prefs.setWebAppShareMode(WebAppActivity.this, Prefs.SHARE_NEVER);
                        }
                        showSavedLocally(WebAppUtil.prettyJson(json, 2000));
                    }
                })
                .setCancelable(false)
                .show();
    }

    /** The result was kept on the device only - tell the user where it is. */
    private void showSavedLocally(String pretty) {
        TextView tv = new TextView(this);
        tv.setText("Your result was saved to the Library as notes, and was NOT "
                + "sent to Golu.\n\n" + pretty
                + "\n\nYou can change this any time in Settings > Web apps.");
        tv.setTextSize(14f);
        tv.setTextColor(Ui.INK);
        tv.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("Result saved")
                .setView(sv)
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Forwards the result to Gemini for feedback in the originating chat
     * (or the shared "Web App Results" chat). Only called after the user
     * chose to share - either just now, or by an earlier remembered choice.
     */
    private void shareResult(String json, String pretty) {
        // which chat gets the feedback? the originating chat, else the
        // shared "Web App Results" agent chat
        AppDb.Chat target = null;
        if (chatId > 0) target = appDb.getChat(chatId);
        if (target == null) target = getOrCreateResultsChat();
        final AppDb.Chat targetChat = target;
        if (targetChat == null) {
            // chat lookup/creation failed (essentially never): the result is
            // already saved as notes, so just tell the user
            Ui.toast(this, "Result saved to the Library, but no chat could be "
                    + "opened for Golu's feedback.");
            return;
        }

        if (!Prefs.hasApiKey(this)) {
            // no key: the result is saved locally; feedback needs a key
            new AlertDialog.Builder(this)
                    .setTitle("Result saved")
                    .setMessage("Your result was saved to the Library as notes.\n\n"
                            + "Add your Gemini API key in Settings to get Golu's "
                            + "feedback on it.")
                    .setPositiveButton("Settings", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            startActivity(new Intent(WebAppActivity.this,
                                    SettingsActivity.class));
                        }
                    })
                    .setNegativeButton("Close", null)
                    .show();
            return;
        }

        // show the user what was sent, then forward it to Gemini
        String sent = "Sent to Golu:\n\n" + pretty
                + "\n\nGolu's feedback will appear in the chat '"
                + targetChat.title + "'.";
        TextView tv = new TextView(this);
        tv.setText(sent);
        tv.setTextSize(14f);
        tv.setTextColor(Ui.INK);
        tv.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("Result sent!")
                .setView(sv)
                .setPositiveButton("Open chat", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Intent i = new Intent(WebAppActivity.this, ChatActivity.class);
                        i.putExtra("chat_id", targetChat.id);
                        startActivity(i);
                    }
                })
                .setNegativeButton("Close", null)
                .show();
        sendFeedback(targetChat, json);
    }

    /** Finds the shared results chat, or creates it (plain Golu Tutor skill). */
    private AppDb.Chat getOrCreateResultsChat() {
        List<AppDb.Chat> agents = appDb.listChats(AppDb.CHAT_AGENT);
        for (AppDb.Chat c : agents) {
            if (RESULTS_CHAT_TITLE.equals(c.title)) return c;
        }
        AppDb.Skill def = appDb.getSkillByName(Skills.NAME_DEFAULT);
        List<Long> ids = new ArrayList<>();
        if (def != null) ids.add(def.id);
        long id = appDb.createChat(RESULTS_CHAT_TITLE, AppDb.CHAT_AGENT,
                Prefs.getAgentModel(this), ids);
        return appDb.getChat(id);
    }

    /** Sends the result to Gemini as a user message in the target chat. */
    private void sendFeedback(final AppDb.Chat targetChat, String json) {
        final List<AppDb.Msg> history = appDb.lastMessages(targetChat.id, 12);
        String data = WebAppUtil.prettyJson(json, 8000);
        String userText = "I finished the web app '" + item.title + "'. Here is my result:\n"
                + data + "\n\nPlease give me feedback on how I did.";
        appDb.addMessage(targetChat.id, AppDb.ROLE_USER, userText, "text");
        JSONArray contents = GeminiClient.historyContents(history);
        List<JSONObject> parts = new ArrayList<>();
        parts.add(GeminiClient.textPart(userText));
        contents.put(GeminiClient.content("user", parts));
        String model = targetChat.model == null
                ? Prefs.getAgentModel(this) : targetChat.model;
        GeminiClient.generate(Prefs.getApiKey(this), model, FEEDBACK_SYSTEM, contents, false,
                new GeminiClient.Callback() {
                    @Override
                    public void onOk(String reply) {
                        appDb.addMessage(targetChat.id, AppDb.ROLE_AI, reply, "text");
                        if (!destroyed) {
                            Ui.toast(WebAppActivity.this,
                                    "Golu replied - see it in Chats");
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (destroyed) return;
                        Ui.showErrorDialog(WebAppActivity.this, "Gemini error", message);
                    }
                });
    }
}
