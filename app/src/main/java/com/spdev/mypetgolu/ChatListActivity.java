package com.spdev.mypetgolu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * List of AI-agent chats. Tap a chat to open it, long-press to rename or
 * delete it, "+ New" to start a fresh chat (title, then model, then skill).
 */
public class ChatListActivity extends Activity {

    private AppDb db;
    private List<AppDb.Chat> chats;
    private ListView listView;
    private ChatAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new AppDb(this);
        Skills.seed(db);

        LinearLayout root = Ui.column(this);

        LinearLayout bar = Ui.topBar(this, "Chats");
        TextView newBtn = new TextView(this);
        newBtn.setText("+ New");
        newBtn.setTextSize(13f);
        newBtn.setTypeface(Typeface.DEFAULT_BOLD);
        newBtn.setTextColor(0xFFFFFFFF);
        newBtn.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7));
        newBtn.setBackground(Ui.pill(this, Ui.ACCENT, 0, 12));
        newBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onNewChat();
            }
        });
        bar.addView(newBtn, Ui.wrapWrap());
        root.addView(bar, Ui.matchWrap());

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView emptyView = Ui.empty(this,
                "No chats yet.\nTap + New to start chatting with Golu!");
        root.addView(emptyView, Ui.matchWrap());
        listView.setEmptyView(emptyView);

        setContentView(root);

        adapter = new ChatAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppDb.Chat chat = chats.get(position);
                Intent i = new Intent(ChatListActivity.this, ChatActivity.class);
                i.putExtra("chat_id", chat.id);
                startActivity(i);
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                onChatLongPress(chats.get(position));
                return true;
            }
        });

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        chats = db.listChats(AppDb.CHAT_AGENT);
        adapter.notifyDataSetChanged();
    }

    private void onNewChat() {
        Ui.input(this, "New chat", "Chat title, e.g. Science revision", "", new Ui.InputCallback() {
            @Override
            public void onInput(final String title) {
                if (title == null || title.isEmpty()) {
                    Ui.toast(ChatListActivity.this, "Please give the chat a title");
                    return;
                }
                pickModelThenSkill(title);
            }
        });
    }

    private void pickModelThenSkill(final String title) {
        final List<String> models = Prefs.getSavedModels(this);
        String[] items = models.toArray(new String[models.size()]);
        int checked = models.indexOf(Prefs.getAgentModel(this));
        if (checked < 0) checked = 0;
        Ui.singleChoice(this, "Pick a model", items, checked, new Ui.ChoiceCallback() {
            @Override
            public void onChoice(int index) {
                pickSkillThenCreate(title, models.get(index));
            }
        });
    }

    private void pickSkillThenCreate(final String title, final String model) {
        final List<AppDb.Skill> skills = db.listSkills();
        String[] items = new String[skills.size()];
        int checked = 0;
        for (int i = 0; i < skills.size(); i++) {
            items[i] = skills.get(i).name;
            if (Skills.NAME_DEFAULT.equals(skills.get(i).name)) checked = i;
        }
        Ui.singleChoice(this, "Pick a skill", items, checked, new Ui.ChoiceCallback() {
            @Override
            public void onChoice(int index) {
                long id = db.createChat(title, AppDb.CHAT_AGENT, model, skills.get(index).id);
                Intent i = new Intent(ChatListActivity.this, ChatActivity.class);
                i.putExtra("chat_id", id);
                startActivity(i);
            }
        });
    }

    private void onChatLongPress(final AppDb.Chat chat) {
        new AlertDialog.Builder(this)
                .setTitle(chat.title)
                .setItems(new String[]{"Rename", "Delete"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            Ui.input(ChatListActivity.this, "Rename chat", "New title",
                                    chat.title, new Ui.InputCallback() {
                                        @Override
                                        public void onInput(String text) {
                                            if (text == null || text.isEmpty()) return;
                                            db.updateChatTitle(chat.id, text);
                                            refresh();
                                        }
                                    });
                        } else {
                            Ui.confirm(ChatListActivity.this, "Delete chat?",
                                    "This deletes the chat and its messages. "
                                            + "Library items saved from it will stay.",
                                    new Ui.ConfirmCallback() {
                                        @Override
                                        public void onYes() {
                                            db.deleteChat(chat.id);
                                            refresh();
                                        }
                                    });
                        }
                    }
                })
                .show();
    }

    private static String fmt(long t) {
        return new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date(t));
    }

    private class ChatAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return chats == null ? 0 : chats.size();
        }

        @Override
        public Object getItem(int position) {
            return chats.get(position);
        }

        @Override
        public long getItemId(int position) {
            return chats.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppDb.Chat chat = chats.get(position);
            LinearLayout outer = new LinearLayout(ChatListActivity.this);
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.setPadding(0, 0, 0, Ui.dp(ChatListActivity.this, 8));

            LinearLayout card = Ui.card(ChatListActivity.this);
            TextView title = new TextView(ChatListActivity.this);
            title.setText(chat.title);
            title.setTextSize(16f);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(Ui.INK);
            card.addView(title, Ui.matchWrap());

            TextView sub = new TextView(ChatListActivity.this);
            String model = chat.model == null ? "" : chat.model;
            sub.setText(model + "  •  " + fmt(chat.updated));
            sub.setTextSize(12f);
            sub.setTextColor(Ui.MUTED);
            card.addView(sub, Ui.matchWrap());

            outer.addView(card, Ui.matchWrap());
            return outer;
        }
    }
}
