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
 * creator skills (Quiz Maker, One Liner Maker, Questions Asker) are
 * auto-saved to the Library. No voice here - AI voice is always OFF in
 * agent chats.
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
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

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
        input.setHint("Ask Golu anything...");
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

    private AppDb.Skill currentSkill() {
        if (chat.skillId == 0) return db.getSkillByName(Skills.NAME_DEFAULT);
        AppDb.Skill s = db.getSkill(chat.skillId);
        return s == null ? db.getSkillByName(Skills.NAME_DEFAULT) : s;
    }

    private String currentSkillName() {
        AppDb.Skill s = currentSkill();
        return s == null ? Skills.NAME_DEFAULT : s.name;
    }

    private void refreshHeader() {
        modelBtn.setText(chat.model == null ? Prefs.getAgentModel(this) : chat.model);
        skillBtn.setText(currentSkillName());
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

    private void pickSkill() {
        final List<AppDb.Skill> skills = db.listSkills();
        String[] items = new String[skills.size()];
        int checked = 0;
        for (int i = 0; i < skills.size(); i++) {
            items[i] = skills.get(i).name;
            if (skills.get(i).id == chat.skillId) checked = i;
        }
        Ui.singleChoice(this, "Pick a skill", items, checked, new Ui.ChoiceCallback() {
            @Override
            public void onChoice(int index) {
                chat.skillId = skills.get(index).id;
                db.updateChatSkill(chatId, chat.skillId);
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
                .setItems(new String[]{"Copy", "Save to Library", "Delete pair"},
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
                                    String kind = Skills.libraryKindForSkill(currentSkillName());
                                    if (kind == null) kind = AiDb.KIND_OTHER;
                                    aiDb.addItem(firstLine60(msg.text), kind, msg.text, chat.title);
                                    Ui.toast(ChatActivity.this, "Saved to Library");
                                } else {
                                    Ui.confirm(ChatActivity.this, "Delete pair?",
                                            "This deletes this message and its reply.",
                                            new Ui.ConfirmCallback() {
                                                @Override
                                                public void onYes() {
                                                    db.deleteMessagePair(chatId, msg.id);
                                                    refreshMessages();
                                                }
                                            });
                                }
                            }
                        })
                .show();
    }

    // -------------------------------------------------------------- attachments

    private void onAttach() {
        final List<UserDb.UserFile> allFiles = userDb.listAllFiles();
        final List<AiDb.Item> allItems = aiDb.listItems();
        int total = allFiles.size() + allItems.size();
        if (total == 0) {
            Ui.toast(this, "Nothing to attach yet - upload a file or save something to the Library first");
            return;
        }
        final String[] labels = new String[total];
        final boolean[] checked = new boolean[total];
        for (int i = 0; i < allFiles.size(); i++) {
            UserDb.Folder folder = userDb.getFolder(allFiles.get(i).folderId);
            String folderName = folder == null ? "Files" : folder.name;
            labels[i] = folderName + " / " + allFiles.get(i).name;
            checked[i] = isAttachedFile(allFiles.get(i).id);
        }
        for (int j = 0; j < allItems.size(); j++) {
            labels[allFiles.size() + j] = "Library: " + allItems.get(j).title;
            checked[allFiles.size() + j] = isAttachedItem(allItems.get(j).id);
        }
        new AlertDialog.Builder(this)
                .setTitle("Attach files (max " + MAX_ATTACHMENTS + ")")
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        checked[which] = isChecked;
                        if (isChecked) {
                            int count = 0;
                            for (boolean b : checked) if (b) count++;
                            if (count > MAX_ATTACHMENTS) {
                                checked[which] = false;
                                ((AlertDialog) dialog).getListView().setItemChecked(which, false);
                                Ui.toast(ChatActivity.this,
                                        "Max " + MAX_ATTACHMENTS + " attachments at a time");
                            }
                        }
                    }
                })
                .setPositiveButton("Attach", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        attachedFiles.clear();
                        attachedItems.clear();
                        for (int i = 0; i < allFiles.size(); i++) {
                            if (checked[i]) attachedFiles.add(allFiles.get(i));
                        }
                        for (int j = 0; j < allItems.size(); j++) {
                            if (checked[allFiles.size() + j]) attachedItems.add(allItems.get(j));
                        }
                        updateAttachChip();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean isAttachedFile(long id) {
        for (UserDb.UserFile f : attachedFiles) {
            if (f.id == id) return true;
        }
        return false;
    }

    private boolean isAttachedItem(long id) {
        for (AiDb.Item it : attachedItems) {
            if (it.id == id) return true;
        }
        return false;
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

        final AppDb.Skill skill = currentSkill();
        final String skillName = skill == null ? Skills.NAME_DEFAULT : skill.name;
        final String systemPrompt = Skills.effectivePrompt(this, db, skill);
        final boolean jsonMode = Skills.NAME_QUIZ_MAKER.equals(skillName);
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
                                        db.addMessage(chatId, AppDb.ROLE_AI, reply, "text");
                                        boolean saved = autoSave(reply, skillName);
                                        clearAttachments();
                                        refreshMessages();
                                        scrollToBottom();
                                        setBusy(false);
                                        if (saved) Ui.toast(ChatActivity.this, "Saved to Library");
                                    }

                                    @Override
                                    public void onError(String message) {
                                        setBusy(false);
                                        Ui.toast(ChatActivity.this, message);
                                    }
                                });
                    }
                });
            }
        }).start();
    }

    /** Auto-saves creator-skill replies to the Library. Returns true if saved. */
    private boolean autoSave(String reply, String skillName) {
        String kind = Skills.libraryKindForSkill(skillName);
        if (kind == null) return false;
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

            LinearLayout row = new LinearLayout(ChatActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(mine ? Gravity.RIGHT : Gravity.LEFT);
            row.setPadding(0, Ui.dp(ChatActivity.this, 3), 0, Ui.dp(ChatActivity.this, 3));

            TextView bubble = new TextView(ChatActivity.this);
            bubble.setText(msg.text);
            bubble.setTextSize(15f);
            bubble.setTextColor(Ui.INK);
            bubble.setPadding(Ui.dp(ChatActivity.this, 12), Ui.dp(ChatActivity.this, 9),
                    Ui.dp(ChatActivity.this, 12), Ui.dp(ChatActivity.this, 9));
            bubble.setBackground(Ui.pill(ChatActivity.this,
                    mine ? Ui.BUBBLE_USER : Ui.BUBBLE_AI, Ui.LINE, 14));

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
