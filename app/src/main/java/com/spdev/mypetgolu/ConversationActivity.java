package com.spdev.mypetgolu;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
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
 * Single persistent voice conversation with Golu ("Talk").
 * Talk by typing or by mic (STT), Golu replies with text and voice (TTS)
 * with lip-sync. Three toggles control mic, voice and STT. Up to 3 files
 * can be attached from folders or the Library.
 */
public class ConversationActivity extends Activity {

    private static final int REQ_AUDIO = 101;
    private static final int MAX_ATTACHMENTS = 3;

    private AppDb db;
    private AiDb aiDb;
    private UserDb userDb;
    private AppDb.Chat chat;
    private GoluVoice voice;

    private PetView pet;
    private TextView status;
    private ListView list;
    private MsgAdapter adapter;
    private List<AppDb.Msg> messages = new ArrayList<>();

    private TextView micChip;
    private TextView voiceChip;
    private TextView sttChip;
    private TextView micButton;
    private EditText input;
    private TextView attachChip;

    private final List<UserDb.UserFile> attachedFiles = new ArrayList<>();
    private final List<AiDb.Item> attachedItems = new ArrayList<>();

    private boolean busy = false;
    private boolean destroyed = false;

    // ------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new AppDb(this);
        aiDb = new AiDb(this);
        userDb = new UserDb(this);
        chat = db.getOrCreateSingleChat(AppDb.CHAT_CONVERSATION, "Talk",
                Prefs.getConversationModel(this));
        setContentView(buildUi());

        pet.setLook(Prefs.getPalette(this), Prefs.getGender(this),
                Prefs.getOutfit(this), Prefs.getAccessory(this));

        voice = new GoluVoice(this);
        voice.initTts(Prefs.getTtsLocale(this), null);
        voice.setTtsListener(new GoluVoice.TtsListener() {
            @Override
            public void onStart(String id) {
                pet.setSpeaking(true);
            }

            @Override
            public void onDone(String id) {
                pet.setSpeaking(false);
            }

            @Override
            public void onError(String id) {
                pet.setSpeaking(false);
            }
        });

        refreshTranscript();
        refreshToggles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (voice != null) voice.setTtsLanguage(Prefs.getTtsLocale(this));
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (voice != null) voice.shutdownAll();
        super.onDestroy();
    }

    // -------------------------------------------------------------------- UI

    private View buildUi() {
        LinearLayout root = Ui.column(this);
        root.addView(Ui.topBar(this, "Talk"));

        pet = new PetView(this);
        root.addView(pet, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 200)));

        status = new TextView(this);
        status.setText("Talk to Golu - tap the mic or type below");
        status.setTextSize(13f);
        status.setTextColor(Ui.MUTED);
        status.setGravity(Gravity.CENTER);
        status.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        root.addView(status, Ui.matchWrap());

        adapter = new MsgAdapter();
        list = new ListView(this);
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= messages.size()) return true;
                showMessageMenu(messages.get(position));
                return true;
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // toggles row
        LinearLayout toggles = new LinearLayout(this);
        toggles.setOrientation(LinearLayout.HORIZONTAL);
        toggles.setGravity(Gravity.CENTER);
        toggles.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        micChip = Ui.chip(this, "Mic ON", true);
        micChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setConvMicOn(ConversationActivity.this,
                        !Prefs.isConvMicOn(ConversationActivity.this));
                if (!Prefs.isConvMicOn(ConversationActivity.this) && voice != null) {
                    voice.cancelListening();
                }
                refreshToggles();
            }
        });
        voiceChip = Ui.chip(this, "Voice ON", true);
        voiceChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setConvTtsOn(ConversationActivity.this,
                        !Prefs.isConvTtsOn(ConversationActivity.this));
                if (!Prefs.isConvTtsOn(ConversationActivity.this) && voice != null) {
                    voice.stopTts();
                    pet.setSpeaking(false);
                }
                refreshToggles();
            }
        });
        sttChip = Ui.chip(this, "STT ON", true);
        sttChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setConvSttOn(ConversationActivity.this,
                        !Prefs.isConvSttOn(ConversationActivity.this));
                if (!Prefs.isConvSttOn(ConversationActivity.this) && voice != null) {
                    voice.cancelListening();
                }
                refreshToggles();
            }
        });
        toggles.addView(micChip, chipLp());
        toggles.addView(voiceChip, chipLp());
        toggles.addView(sttChip, chipLp());
        root.addView(toggles, Ui.matchWrap());

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

        // input row
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Ui.PANEL);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));

        TextView attachBtn = new TextView(this);
        attachBtn.setText("+");
        attachBtn.setTextSize(22f);
        attachBtn.setTypeface(Typeface.DEFAULT_BOLD);
        attachBtn.setTextColor(Ui.ACCENT_DARK);
        attachBtn.setGravity(Gravity.CENTER);
        attachBtn.setPadding(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8), Ui.dp(this, 2));
        attachBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAttach();
            }
        });
        bar.addView(attachBtn, Ui.wrapWrap());

        micButton = Ui.button(this, "Mic", Ui.ACCENT);
        micButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onMicClicked();
            }
        });
        bar.addView(micButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(14f);
        input.setHint("Type to Golu...");
        input.setHintTextColor(Ui.MUTED);
        input.setTextColor(Ui.INK);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setBackground(Ui.pill(this, Ui.CARD, Ui.LINE, 14));
        input.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                sendText(input.getText().toString());
                return true;
            }
        });
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        inputLp.leftMargin = Ui.dp(this, 8);
        inputLp.rightMargin = Ui.dp(this, 8);
        bar.addView(input, inputLp);

        TextView send = Ui.button(this, "Send", Ui.GREEN);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendText(input.getText().toString());
            }
        });
        bar.addView(send, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(bar, Ui.matchWrap());
        return root;
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(this, 4);
        lp.rightMargin = Ui.dp(this, 4);
        return lp;
    }

    private void refreshToggles() {
        setChip(micChip, "Mic", Prefs.isConvMicOn(this));
        setChip(voiceChip, "Voice", Prefs.isConvTtsOn(this));
        setChip(sttChip, "STT", Prefs.isConvSttOn(this));
    }

    private void setChip(TextView chip, String name, boolean on) {
        chip.setText(name + (on ? " ON" : " OFF"));
        chip.setTextColor(on ? 0xFFFFFFFF : 0xFF6B4A22);
        chip.setBackground(Ui.pill(this, on ? Ui.ACCENT : Ui.CHIP_BG,
                on ? 0 : Ui.LINE, 14));
    }

    // ------------------------------------------------------------- transcript

    private void refreshTranscript() {
        messages = db.listMessages(chat.id);
        adapter.notifyDataSetChanged();
        if (!messages.isEmpty()) list.setSelection(messages.size() - 1);
    }

    private void showMessageMenu(final AppDb.Msg m) {
        final String[] items = {"Copy", "Delete pair"};
        new AlertDialog.Builder(this)
                .setTitle("Message")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            ClipboardManager cm = (ClipboardManager)
                                    getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.setPrimaryClip(ClipData.newPlainText("Golu message", m.text));
                                Ui.toast(ConversationActivity.this, "Copied");
                            }
                        } else {
                            db.deleteMessagePair(chat.id, m.id);
                            refreshTranscript();
                        }
                    }
                })
                .show();
    }

    private class MsgAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return messages.size();
        }

        @Override
        public Object getItem(int position) {
            return messages.get(position);
        }

        @Override
        public long getItemId(int position) {
            return messages.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppDb.Msg m = messages.get(position);
            boolean mine = AppDb.ROLE_USER.equals(m.role);

            LinearLayout row = new LinearLayout(ConversationActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, Ui.dp(ConversationActivity.this, 3),
                    0, Ui.dp(ConversationActivity.this, 3));

            TextView bubble = new TextView(ConversationActivity.this);
            bubble.setText(m.text);
            bubble.setTextSize(14f);
            bubble.setTextColor(Ui.INK);
            bubble.setPadding(Ui.dp(ConversationActivity.this, 12),
                    Ui.dp(ConversationActivity.this, 9),
                    Ui.dp(ConversationActivity.this, 12),
                    Ui.dp(ConversationActivity.this, 9));
            bubble.setBackground(Ui.pill(ConversationActivity.this,
                    mine ? Ui.BUBBLE_USER : Ui.BUBBLE_AI, Ui.LINE, 14));
            LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bubbleLp.weight = 0;

            View spacer = new View(ConversationActivity.this);
            LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

            if (mine) {
                row.addView(spacer, spacerLp);
                row.addView(bubble, bubbleLp);
            } else {
                row.addView(bubble, bubbleLp);
                row.addView(spacer, spacerLp);
            }
            return row;
        }
    }

    // ------------------------------------------------------------------- send

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

    private void sendText(String raw) {
        if (raw == null) return;
        final String text = raw.trim();
        int attachCount = attachedFiles.size() + attachedItems.size();
        if (text.isEmpty() && attachCount == 0) return;
        if (busy) {
            Ui.toast(this, "Wait - Golu is still replying");
            return;
        }
        if (!Prefs.hasApiKey(this)) {
            Ui.toast(this, "Add your Gemini API key in Settings first");
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }

        // history first, then append the current message
        final List<AppDb.Msg> history = db.lastMessages(chat.id, 10);
        String userText = text;
        if (userText.isEmpty()) {
            userText = "Attached " + attachCount + " file(s).";
        }
        db.addMessage(chat.id, AppDb.ROLE_USER, userText, "text");
        input.setText("");
        refreshTranscript();

        busy = true;
        status.setText("Golu is thinking...");

        final List<UserDb.UserFile> files = new ArrayList<>(attachedFiles);
        final List<AiDb.Item> items = new ArrayList<>(attachedItems);
        final String sendText = text;

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
                            Ui.toast(ConversationActivity.this,
                                    "Skipped (too big or missing): " + name);
                        }
                        JSONArray contents = GeminiClient.historyContents(history);
                        List<JSONObject> parts = new ArrayList<>();
                        String promptText = sendText.isEmpty()
                                ? "Please look at the attached files."
                                : sendText;
                        parts.add(GeminiClient.textPart(promptText));
                        for (GeminiClient.FilePart fp : fileParts) {
                            parts.add(GeminiClient.inlineDataPart(fp.mime, fp.base64));
                        }
                        for (AiDb.Item it : items) {
                            parts.add(GeminiClient.textPart(
                                    "[From library: " + it.title + "]\n" + it.content));
                        }
                        contents.put(GeminiClient.content("user", parts));

                        AppDb.Skill skill = db.getSkillByName(Skills.NAME_DEFAULT);
                        String system = Skills.effectivePrompt(ConversationActivity.this, db, skill);
                        GeminiClient.generate(Prefs.getApiKey(ConversationActivity.this),
                                Prefs.getConversationModel(ConversationActivity.this),
                                system, contents, false, new GeminiClient.Callback() {
                                    @Override
                                    public void onOk(String reply) {
                                        busy = false;
                                        if (destroyed) return;
                                        db.addMessage(chat.id, AppDb.ROLE_AI, reply, "text");
                                        clearAttachments();
                                        refreshTranscript();
                                        status.setText("Talk to Golu - tap the mic or type below");
                                        if (Prefs.isConvTtsOn(ConversationActivity.this)
                                                && voice != null) {
                                            voice.speak(reply, "conv");
                                        }
                                    }

                                    @Override
                                    public void onError(String message) {
                                        busy = false;
                                        if (destroyed) return;
                                        status.setText("Talk to Golu - tap the mic or type below");
                                        Ui.showErrorDialog(ConversationActivity.this,
                                                "Gemini error", message);
                                    }
                                });
                    }
                });
            }
        }).start();
    }

    // -------------------------------------------------------------------- mic

    private void onMicClicked() {
        if (!Prefs.isConvMicOn(this)) {
            Ui.toast(this, "Mic is OFF - tap the Mic chip to turn it on");
            return;
        }
        if (!Prefs.isConvSttOn(this)) {
            Ui.toast(this, "STT is OFF - tap the STT chip to turn it on");
            return;
        }
        if (voice == null) return;
        if (voice.isListening()) {
            voice.cancelListening();
            status.setText("Talk to Golu - tap the mic or type below");
            micButton.setText("Mic");
            return;
        }
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        startStt();
    }

    private void startStt() {
        if (!GoluVoice.isSttAvailable(this)) {
            Ui.toast(this, "Speech recognition is not available on this device");
            return;
        }
        status.setText("Listening... speak now");
        micButton.setText("...");
        voice.startListening(Prefs.getSttTag(this), new GoluVoice.SttListener() {
            @Override
            public void onResult(String text) {
                micButton.setText("Mic");
                status.setText("Talk to Golu - tap the mic or type below");
                sendText(text);
            }

            @Override
            public void onError(String message) {
                micButton.setText("Mic");
                status.setText("Talk to Golu - tap the mic or type below");
                if ("NOMATCH".equals(message)) {
                    Ui.toast(ConversationActivity.this, "Didn't catch that");
                } else if (!"BUSY".equals(message)) {
                    Ui.toast(ConversationActivity.this, message);
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startStt();
            } else {
                Ui.toast(this, "Mic denied - you can still type");
            }
        }
    }
}
