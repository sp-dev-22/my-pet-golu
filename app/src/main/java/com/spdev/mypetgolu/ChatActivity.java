package com.spdev.mypetgolu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One AI-agent chat. Text + file attachments go to Gemini; replies from
 * creator skills (Quiz Maker, Flashcard Maker, One Liner Maker, Questions
 * Asker, Web App Maker) are auto-saved to the Library. Web App Maker
 * replies are saved as web apps and shown as a compact card - tap it to
 * play the app. No voice here - AI voice is always OFF in agent chats.
 */
public class ChatActivity extends Activity {

    private static final int MAX_ATTACHMENTS = 10;

    private long chatId;
    private AppDb db;
    private AiDb aiDb;
    private UserDb userDb;
    private AppDb.Chat chat;

    private List<AppDb.Msg> msgs;
    private ListView listView;
    private MsgAdapter adapter;
    private TextView modelBtn;
    private TextView skillBtn;
    private TextView statusView;
    private EditText input;
    private TextView sendBtn;
    private TextView attachChip;
    private LinearLayout selectionBar;
    private TextView deleteSelectedBtn;

    // message selection (delete several pairs at once)
    private boolean selectionMode = false;
    private final java.util.Set<Long> selectedPairs = new java.util.HashSet<>();

    private final List<UserDb.UserFile> attachedFiles = new ArrayList<>();
    private final List<AiDb.Item> attachedItems = new ArrayList<>();
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new AppDb(this);
        aiDb = new AiDb(this);
        userDb = new UserDb(this);

        chatId = getIntent().getLongExtra("chat_id", -1);
        chat = db.getChat(chatId);
        if (chat == null) {
            finish();
            return;
        }

        LinearLayout root = Ui.column(this);

        // top bar: title + model + skill buttons
        LinearLayout bar = Ui.topBar(this, chat.title);
        modelBtn = smallChip();
        modelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickModel();
            }
        });
        bar.addView(modelBtn, Ui.wrapWrap());
        skillBtn = smallChip();
        skillBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickSkill();
            }
        });
        bar.addView(skillBtn, Ui.wrapWrap());
        root.addView(bar, Ui.matchWrap());

        // thinking status
        statusView = new TextView(this);
        statusView.setText("Golu is thinking...");
        statusView.setTextSize(13f);
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        statusView.setTextColor(Ui.MUTED);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        statusView.setVisibility(View.GONE);
        root.addView(statusView, Ui.matchWrap());

        // messages
        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        TextView listEmpty = Ui.empty(this,
                "No messages yet\nAsk Golu anything to start this chat");
        listView.setEmptyView(listEmpty);
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(listEmpty, Ui.matchWrap());

        // attachment chip (tap to clear)
        attachChip = new TextView(this);
        attachChip.setTextSize(12f);
        attachChip.setTypeface(Typeface.DEFAULT_BOLD);
        attachChip.setTextColor(Ui.ACCENT_DARK);
        attachChip.setGravity(Gravity.CENTER);
        attachChip.setPadding(Ui.dp(this, 10), Ui.dp(this, 7), Ui.dp(this, 10), Ui.dp(this, 7));
        attachChip.setBackground(Ui.pill(this, Ui.CHIP_BG, Ui.LINE, 12));
        attachChip.setVisibility(View.GONE);
        attachChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearAttachments();
            }
        });
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setGravity(Gravity.CENTER);
        chipRow.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        chipRow.addView(attachChip, Ui.wrapWrap());
        root.addView(chipRow, Ui.matchWrap());

        // bottom input row: + attach, text, send
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setBackgroundColor(Ui.PANEL);
        inputRow.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));

        TextView attachBtn = new TextView(this);
        attachBtn.setText("+");
        attachBtn.setTextSize(22f);
        attachBtn.setTypeface(Typeface.DEFAULT_BOLD);
        attachBtn.setTextColor(Ui.ACCENT_DARK);
        attachBtn.setGravity(Gravity.CENTER);
        attachBtn.setPadding(Ui.dp(this, 10), Ui.dp(this, 2), Ui.dp(this, 10), Ui.dp(this, 2));
        attachBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAttach();
            }
        });
        inputRow.addView(attachBtn, Ui.wrapWrap());

        input = new EditText(this);
        input.setHint("Ask Golu...");
        input.setTextSize(15f);
        input.setTextColor(Ui.INK);
        input.setBackground(Ui.pill(this, Ui.CARD, Ui.LINE, 14));
        input.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        inputRow.addView(input, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        sendBtn = new TextView(this);
        sendBtn.setText("Send");
        sendBtn.setTextSize(14f);
        sendBtn.setTypeface(Typeface.DEFAULT_BOLD);
        sendBtn.setTextColor(0xFFFFFFFF);
        sendBtn.setGravity(Gravity.CENTER);
        sendBtn.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        sendBtn.setBackground(Ui.pill(this, Ui.ACCENT, 0, 14));
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSend();
            }
        });
        LinearLayout.LayoutParams sendLp = Ui.wrapWrap();
        sendLp.leftMargin = Ui.dp(this, 8);
        inputRow.addView(sendBtn, sendLp);

        root.addView(inputRow, Ui.matchWrap());

        // selection bar (delete several message pairs at once)
        selectionBar = new LinearLayout(this);
        selectionBar.setOrientation(LinearLayout.HORIZONTAL);
        selectionBar.setBackgroundColor(Ui.PANEL);
        selectionBar.setGravity(Gravity.CENTER);
        selectionBar.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        selectionBar.setVisibility(View.GONE);
        deleteSelectedBtn = Ui.button(this, "Delete (0)", Ui.RED);
        deleteSelectedBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteSelected();
            }
        });
        LinearLayout.LayoutParams selLp = Ui.wrapWrap();
        selLp.leftMargin = Ui.dp(this, 6);
        selLp.rightMargin = Ui.dp(this, 6);
        selectionBar.addView(deleteSelectedBtn, selLp);
        TextView cancelSel = Ui.button(this, "Cancel", Ui.MUTED);
        cancelSel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitSelectionMode();
            }
        });
        selectionBar.addView(cancelSel, selLp);
        root.addView(selectionBar, Ui.matchWrap());

        setContentView(root);

        adapter = new MsgAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                onMessageLongPress(msgs.get(position));
                return true;
            }
        });
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (msgs == null || position < 0 || position >= msgs.size()) return;
                if (selectionMode) {
                    togglePair(AppDb.pairKey(msgs, position));
                } else if (isWebAppMessage(msgs.get(position))) {
                    openWebAppMessage(msgs.get(position));
                }
            }
        });

        refreshHeader();
        refreshMessages();
        scrollToBottom();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppDb.Chat fresh = db.getChat(chatId);
        if (fresh == null) {
            finish();
            return;
        }
        chat = fresh;
        refreshHeader();
        refreshMessages();
    }

    // ------------------------------------------------------------------ header

    private TextView smallChip() {
        TextView tv = new TextView(this);
        tv.setTextSize(11f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Ui.ACCENT_DARK);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setMaxWidth(Ui.dp(this, 110));
        tv.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        tv.setBackground(Ui.pill(this, Ui.CHIP_BG, Ui.LINE, 12));
        LinearLayout.LayoutParams lp = Ui.wrapWrap();
        lp.leftMargin = Ui.dp(this, 4);
        tv.setLayoutParams(lp);
        return tv;
    }

    /** The chat's skills (empty = Golu Tutor). */
    private List<AppDb.Skill> currentSkills() {
        List<AppDb.Skill> out = db.getSkillsByIds(chat.skillIdList());
        if (out.isEmpty()) {
            AppDb.Skill def = db.getSkillByName(Skills.NAME_DEFAULT);
            if (def != null) out.add(def);
        }
        return out;
    }

    /** Short label for the skill chip: one name, or "N skills". */
    private String currentSkillLabel() {
        List<AppDb.Skill> skills = currentSkills();
        if (skills.size() <= 1) {
            return skills.isEmpty() ? Skills.NAME_DEFAULT : skills.get(0).name;
        }
        return skills.get(0).name + " +" + (skills.size() - 1);
    }

    private void refreshHeader() {
        modelBtn.setText(chat.model == null ? Prefs.getAgentModel(this) : chat.model);
        skillBtn.setText(currentSkillLabel());
    }

    private void pickModel() {
        final List<String> models = Prefs.getSavedModels(this);
        String[] items = models.toArray(new String[models.size()]);
        int checked = models.indexOf(chat.model);
        if (checked < 0) checked = 0;
        Ui.singleChoice(this, "Pick a model", items, checked, new Ui.ChoiceCallback() {
            @Override
            public void onChoice(int index) {
                chat.model = models.get(index);
                db.updateChatModel(chatId, chat.model);
                refreshHeader();
            }
        });
    }

    /** Multi-select: a chat can use several skills at once. */
    private void pickSkill() {
        final List<AppDb.Skill> skills = db.listSkills();
        String[] items = new String[skills.size()];
        boolean[] checked = new boolean[skills.size()];
        List<Long> mine = chat.skillIdList();
        for (int i = 0; i < skills.size(); i++) {
            items[i] = skills.get(i).name;
            checked[i] = mine.contains(skills.get(i).id);
        }
        Ui.multiChoice(this, "Pick skills", items, checked, new Ui.MultiChoiceCallback() {
            @Override
            public void onPicked(boolean[] picked) {
                List<Long> ids = new ArrayList<>();
                for (int i = 0; i < skills.size(); i++) {
                    if (picked[i]) ids.add(skills.get(i).id);
                }
                if (ids.isEmpty()) {
                    // no skill picked = plain Golu Tutor
                    AppDb.Skill def = db.getSkillByName(Skills.NAME_DEFAULT);
                    if (def != null) ids.add(def.id);
                }
                chat.skillIds = AppDb.joinSkillIds(ids);
                chat.skillId = ids.isEmpty() ? 0 : ids.get(0);
                db.updateChatSkills(chatId, ids);
                refreshHeader();
            }
        });
    }

    // ---------------------------------------------------------------- messages

    private void refreshMessages() {
        msgs = db.listMessages(chatId);
        adapter.notifyDataSetChanged();
    }

    private void scrollToBottom() {
        if (adapter.getCount() > 0) {
            listView.setSelection(adapter.getCount() - 1);
        }
    }

    private void onMessageLongPress(final AppDb.Msg msg) {
        new AlertDialog.Builder(this)
                .setTitle("Message")
                .setItems(new String[]{"Copy", "Save to Library", "Delete pair",
                                "Select pairs", "Clear whole chat"},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                if (which == 0) {
                                    ClipboardManager cm = (ClipboardManager)
                                            getSystemService(Context.CLIPBOARD_SERVICE);
                                    cm.setPrimaryClip(ClipData.newPlainText(
                                            "Golu message", msg.text));
                                    Ui.toast(ChatActivity.this, "Copied");
                                } else if (which == 1) {
                                    saveMessageToLibrary(msg);
                                } else if (which == 2) {
                                    Ui.confirm(ChatActivity.this, "Delete pair?",
                                            "This deletes this message and its reply.",
                                            new Ui.ConfirmCallback() {
                                                @Override
                                                public void onYes() {
                                                    db.deleteMessagePair(chatId, msg.id);
                                                    refreshMessages();
                                                }
                                            });
                                } else if (which == 3) {
                                    enterSelectionMode(AppDb.pairKey(msgs, msgs.indexOf(msg)));
                                } else {
                                    clearWholeChat();
                                }
                            }
                        })
                .show();
    }

    /** Long-press "Save to Library": web app replies save as clean HTML. */
    private void saveMessageToLibrary(final AppDb.Msg msg) {
        if (isWebAppMessage(msg)) {
            Ui.toast(this, "Already in the Library - tap the message to open it");
            return;
        }
        String kind = Skills.libraryKindForSkills(currentSkills());
        if (AiDb.KIND_WEBAPP.equals(kind)) {
            String html = WebAppUtil.extractHtml(msg.text);
            if (html == null) {
                Ui.toast(this, "No web app in this message");
                return;
            }
            long itemId = aiDb.addItem(WebAppUtil.extractTitle(html, "Web app"),
                    AiDb.KIND_WEBAPP, html, chat.title);
            db.updateMessageKind(msg.id, WebAppUtil.MSG_KIND_PREFIX + itemId);
            refreshMessages();
            Ui.toast(this, "Saved to Library");
            return;
        }
        if (kind == null) kind = AiDb.KIND_OTHER;
        aiDb.addItem(firstLine60(msg.text), kind, msg.text, chat.title);
        Ui.toast(this, "Saved to Library");
    }

    // ------------------------------------------------- message selection

    /** Starts multi-select delete with the long-pressed pair pre-selected. */
    private void enterSelectionMode(long pairKey) {
        selectionMode = true;
        selectedPairs.clear();
        if (pairKey >= 0) selectedPairs.add(pairKey);
        selectionBar.setVisibility(View.VISIBLE);
        updateSelectionBar();
    }

    private void exitSelectionMode() {
        selectionMode = false;
        selectedPairs.clear();
        selectionBar.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void togglePair(long pairKey) {
        if (pairKey < 0) return;
        if (selectedPairs.contains(pairKey)) selectedPairs.remove(pairKey);
        else selectedPairs.add(pairKey);
        updateSelectionBar();
    }

    private void updateSelectionBar() {
        deleteSelectedBtn.setText("Delete (" + selectedPairs.size() + ")");
        adapter.notifyDataSetChanged();
    }

    /** Deletes the selected pairs (each pair = one user message + its AI reply). */
    private void deleteSelected() {
        if (selectedPairs.isEmpty()) {
            exitSelectionMode();
            return;
        }
        final java.util.Set<Long> keys = new java.util.HashSet<>(selectedPairs);
        Ui.confirm(this, "Delete selected?",
                "Delete " + keys.size() + " selected pair" + (keys.size() == 1 ? "" : "s") + "?",
                new Ui.ConfirmCallback() {
                    @Override
                    public void onYes() {
                        db.deletePairs(chatId, keys);
                        exitSelectionMode();
                        refreshMessages();
                    }
                });
    }

    /** Deletes every message in this chat (the chat itself stays). */
    private void clearWholeChat() {
        Ui.confirm(this, "Clear whole chat?",
                "All messages in this chat will be deleted.",
                new Ui.ConfirmCallback() {
                    @Override
                    public void onYes() {
                        db.deleteAllMessages(chatId);
                        exitSelectionMode();
                        refreshMessages();
                    }
                });
    }

    // -------------------------------------------------------------- attachments

    private void onAttach() {
        AttachPicker.show(this, userDb, aiDb, MAX_ATTACHMENTS,
                attachedFiles, attachedItems, new AttachPicker.Listener() {
                    @Override
                    public void onPicked(List<UserDb.UserFile> files, List<AiDb.Item> items) {
                        attachedFiles.clear();
                        attachedFiles.addAll(files);
                        attachedItems.clear();
                        attachedItems.addAll(items);
                        updateAttachChip();
                    }
                });
    }

    private void updateAttachChip() {
        int n = attachedFiles.size() + attachedItems.size();
        if (n == 0) {
            attachChip.setVisibility(View.GONE);
        } else {
            attachChip.setText("Attached: " + n + " (tap to clear)");
            attachChip.setVisibility(View.VISIBLE);
        }
    }

    private void clearAttachments() {
        attachedFiles.clear();
        attachedItems.clear();
        updateAttachChip();
    }

    // --------------------------------------------------------------------- send

    private void setBusy(boolean b) {
        busy = b;
        sendBtn.setEnabled(!b);
        sendBtn.setBackground(Ui.pill(this, b ? Ui.MUTED : Ui.ACCENT, 0, 14));
        statusView.setVisibility(b ? View.VISIBLE : View.GONE);
    }

    private void onSend() {
        if (busy) return;
        final String text = input.getText().toString().trim();
        int attachCount = attachedFiles.size() + attachedItems.size();
        if (text.isEmpty() && attachCount == 0) {
            Ui.toast(this, "Type a message or attach a file first");
            return;
        }
        if (!Prefs.hasApiKey(this)) {
            Ui.toast(this, "Add your Gemini API key in Settings");
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }

        // history BEFORE adding the new user message (so it is not duplicated)
        final List<AppDb.Msg> history = db.lastMessages(chatId, 12);
        String userText = text;
        if (userText.isEmpty()) {
            userText = "Attached " + attachCount + " file(s) for study.";
        }
        db.addMessage(chatId, AppDb.ROLE_USER, userText, "text");
        input.setText("");
        refreshMessages();
        scrollToBottom();
        setBusy(true);

        final List<AppDb.Skill> skills = currentSkills();
        final String systemPrompt = Skills.effectivePrompt(this, db, skills);
        final boolean jsonMode = Skills.needsJsonMode(skills);
        final String model = chat.model == null ? Prefs.getAgentModel(this) : chat.model;
        final String apiKey = Prefs.getApiKey(this);
        final String sendText = text;
        final List<UserDb.UserFile> files = new ArrayList<>(attachedFiles);
        final List<AiDb.Item> items = new ArrayList<>(attachedItems);

        // base64 encoding happens off the UI thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<GeminiClient.FilePart> fileParts = new ArrayList<>();
                final List<String> skipped = new ArrayList<>();
                for (UserDb.UserFile f : files) {
                    String b64 = FileStore.base64OfFile(f.path);
                    if (b64 == null) {
                        skipped.add(f.name);
                    } else {
                        String mime = f.mime == null ? "application/octet-stream" : f.mime;
                        fileParts.add(new GeminiClient.FilePart(mime, b64));
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        for (String name : skipped) {
                            Ui.toast(ChatActivity.this,
                                    "Skipped (too big or missing): " + name);
                        }
                        JSONArray contents = GeminiClient.historyContents(history);
                        List<JSONObject> userParts = new ArrayList<>();
                        String promptText = sendText.isEmpty()
                                ? "Please look at the attached files and help me study them."
                                : sendText;
                        userParts.add(GeminiClient.textPart(promptText));
                        for (GeminiClient.FilePart fp : fileParts) {
                            userParts.add(GeminiClient.inlineDataPart(fp.mime, fp.base64));
                        }
                        for (AiDb.Item it : items) {
                            userParts.add(GeminiClient.textPart(
                                    "[From library: " + it.title + "]\n" + it.content));
                        }
                        contents.put(GeminiClient.content("user", userParts));
                        GeminiClient.generate(apiKey, model, systemPrompt, contents, jsonMode,
                                new GeminiClient.Callback() {
                                    @Override
                                    public void onOk(String reply) {
                                        long msgId = db.addMessage(chatId, AppDb.ROLE_AI, reply, "text");
                                        boolean saved = autoSave(reply, skills, msgId, sendText);
                                        clearAttachments();
                                        refreshMessages();
                                        scrollToBottom();
                                        setBusy(false);
                                        if (saved) Ui.toast(ChatActivity.this, "Saved to Library");
                                    }

                                    @Override
                                    public void onError(String message) {
                                        setBusy(false);
                                        Ui.showErrorDialog(ChatActivity.this, "Gemini error", message);
                                    }
                                });
                    }
                });
            }
        }).start();
    }

    /**
     * Auto-saves creator-skill replies to the Library. Returns true if saved
     * (the caller then toasts "Saved to Library"). Web App Maker replies are
     * saved as web apps (clean HTML only) and the message is tagged so the
     * chat shows a compact card instead of the raw HTML; when the reply holds
     * no HTML it is saved as notes instead, with its own toast.
     */
    private boolean autoSave(String reply, List<AppDb.Skill> skills, long msgId, String userText) {
        String kind = Skills.libraryKindForSkills(skills);
        if (kind == null) return false;
        if (AiDb.KIND_WEBAPP.equals(kind)) {
            String html = WebAppUtil.extractHtml(reply);
            if (html == null) {
                aiDb.addItem(firstLine60(reply), AiDb.KIND_NOTES, reply, chat.title);
                Ui.toast(this, "Golu didn't return a web app this time - saved as notes. "
                        + "Ask again for a web app.");
                return false;
            }
            String fallback = userText == null || userText.trim().isEmpty()
                    ? "Web app" : firstLine60(userText);
            long itemId = aiDb.addItem(WebAppUtil.extractTitle(html, fallback),
                    AiDb.KIND_WEBAPP, html, chat.title);
            db.updateMessageKind(msgId, WebAppUtil.MSG_KIND_PREFIX + itemId);
            return true;
        }
        if (AiDb.KIND_QUIZ.equals(kind)) {
            List<QuizUtil.Question> qs = QuizUtil.parseQuiz(reply);
            if (!qs.isEmpty()) {
                aiDb.addItem(QuizUtil.quizTitle(qs, chat.title),
                        AiDb.KIND_QUIZ, QuizUtil.toJson(qs), chat.title);
            } else {
                aiDb.addItem(firstLine60(reply), AiDb.KIND_QUIZ, reply, chat.title);
            }
            return true;
        }
        aiDb.addItem(firstLine60(reply), kind, reply, chat.title);
        return true;
    }

    private static String firstLine60(String s) {
        if (s == null) return "Untitled";
        String line = s.trim();
        int nl = line.indexOf('\n');
        if (nl >= 0) line = line.substring(0, nl).trim();
        if (line.isEmpty()) line = "Untitled";
        if (line.length() > 60) line = line.substring(0, 57) + "...";
        return line;
    }

    /** True when a message is a reply that was saved as a web app. */
    private static boolean isWebAppMessage(AppDb.Msg msg) {
        return msg != null && WebAppUtil.isWebAppMessageKind(msg.kind);
    }

    /** Opens the web app behind a chat message (tap on its card). */
    private void openWebAppMessage(final AppDb.Msg msg) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                AiDb.Item found = null;
                long itemId = WebAppUtil.webAppItemId(msg.kind);
                if (itemId > 0) {
                    AiDb.Item it = aiDb.getItem(itemId);
                    if (it != null && AiDb.KIND_WEBAPP.equals(it.kind)) found = it;
                }
                if (found == null) {
                    // id is stale (e.g. after a backup restore): match by content
                    String html = WebAppUtil.extractHtml(msg.text);
                    if (html != null) {
                        for (AiDb.Item it : aiDb.listItems()) {
                            if (AiDb.KIND_WEBAPP.equals(it.kind) && html.equals(it.content)) {
                                found = it;
                                break;
                            }
                        }
                    }
                }
                final AiDb.Item target = found;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (target != null) {
                            Intent i = new Intent(ChatActivity.this, WebAppActivity.class);
                            i.putExtra("library_item_id", target.id);
                            i.putExtra("chat_id", chatId);
                            startActivity(i);
                        } else {
                            Ui.toast(ChatActivity.this,
                                    "That web app is no longer in the Library");
                            startActivity(new Intent(ChatActivity.this, LibraryActivity.class));
                        }
                    }
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------ adapter

    private class MsgAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return msgs == null ? 0 : msgs.size();
        }

        @Override
        public Object getItem(int position) {
            return msgs.get(position);
        }

        @Override
        public long getItemId(int position) {
            return msgs.get(position).id;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return AppDb.ROLE_USER.equals(msgs.get(position).role) ? 0 : 1;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppDb.Msg msg = msgs.get(position);
            boolean mine = AppDb.ROLE_USER.equals(msg.role);
            boolean selected = selectionMode
                    && selectedPairs.contains(AppDb.pairKey(msgs, position));

            LinearLayout row = new LinearLayout(ChatActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(mine ? Gravity.RIGHT : Gravity.LEFT);
            row.setPadding(0, Ui.dp(ChatActivity.this, 3), 0, Ui.dp(ChatActivity.this, 3));

            TextView bubble = new TextView(ChatActivity.this);
            String shown = msg.text;
            if (isWebAppMessage(msg)) {
                // never dump the raw HTML in the chat: a compact card instead
                shown = "[Web app] " + WebAppUtil.extractTitle(msg.text, "Web app")
                        + " - tap to open";
            }
            bubble.setText((selected ? "\u2611 " : selectionMode ? "\u2610 " : "") + shown);
            bubble.setTextSize(15f);
            bubble.setTextColor(Ui.INK);
            bubble.setPadding(Ui.dp(ChatActivity.this, 12), Ui.dp(ChatActivity.this, 9),
                    Ui.dp(ChatActivity.this, 12), Ui.dp(ChatActivity.this, 9));
            bubble.setBackground(Ui.pill(ChatActivity.this,
                    selected ? Ui.BUBBLE_SELECTED : (mine ? Ui.BUBBLE_USER : Ui.BUBBLE_AI),
                    Ui.LINE, 14));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            row.addView(bubble, lp);
            // keep bubbles from stretching full width
            bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.85));
            return row;
        }
    }
}
