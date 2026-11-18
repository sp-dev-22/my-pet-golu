package com.spdev.mypetgolu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

/**
 * Skills: the system prompts behind agent chats. Built-in skills can only
 * be viewed; custom skills can be created, edited and deleted.
 */
public class SkillsActivity extends Activity {

    private AppDb db;
    private List<AppDb.Skill> skills;
    private ListView listView;
    private SkillAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new AppDb(this);
        Skills.seed(db);

        LinearLayout root = Ui.column(this);

        LinearLayout bar = Ui.topBar(this, "Skills");
        TextView newBtn = new TextView(this);
        newBtn.setText("+ New skill");
        newBtn.setTextSize(13f);
        newBtn.setTypeface(Typeface.DEFAULT_BOLD);
        newBtn.setTextColor(0xFFFFFFFF);
        newBtn.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7));
        newBtn.setBackground(Ui.pill(this, Ui.ACCENT, 0, 12));
        newBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onNewSkill();
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

        TextView emptyView = Ui.empty(this, "No skills yet.\nTap + New skill to create one.");
        root.addView(emptyView, Ui.matchWrap());
        listView.setEmptyView(emptyView);

        setContentView(root);

        adapter = new SkillAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onSkillTap(skills.get(position));
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
        skills = db.listSkills();
        adapter.notifyDataSetChanged();
    }

    private void onNewSkill() {
        Ui.input(this, "New skill", "Skill name, e.g. Maths Helper", "", new Ui.InputCallback() {
            @Override
            public void onInput(final String name) {
                if (name == null || name.isEmpty()) {
                    Ui.toast(SkillsActivity.this, "Please give the skill a name");
                    return;
                }
                Ui.longInput(SkillsActivity.this, "Skill prompt",
                        "Write what Golu should do with this skill...",
                        "", new Ui.InputCallback() {
                            @Override
                            public void onInput(String prompt) {
                                if (prompt == null || prompt.isEmpty()) {
                                    Ui.toast(SkillsActivity.this, "Please write a prompt");
                                    return;
                                }
                                db.addSkill(name, prompt, false);
                                refresh();
                            }
                        });
            }
        });
    }

    private void onSkillTap(final AppDb.Skill skill) {
        if (skill.builtin) {
            Ui.showText(this, skill.name, skill.prompt);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(skill.name)
                .setItems(new String[]{"View", "Edit", "Delete"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            Ui.showText(SkillsActivity.this, skill.name, skill.prompt);
                        } else if (which == 1) {
                            Ui.input(SkillsActivity.this, "Edit skill name", "Skill name",
                                    skill.name, new Ui.InputCallback() {
                                        @Override
                                        public void onInput(final String name) {
                                            if (name == null || name.isEmpty()) return;
                                            Ui.longInput(SkillsActivity.this, "Edit prompt",
                                                    "Skill prompt", skill.prompt,
                                                    new Ui.InputCallback() {
                                                        @Override
                                                        public void onInput(String prompt) {
                                                            if (prompt == null || prompt.isEmpty()) {
                                                                return;
                                                            }
                                                            db.updateSkill(skill.id, name, prompt);
                                                            refresh();
                                                        }
                                                    });
                                        }
                                    });
                        } else {
                            Ui.confirm(SkillsActivity.this, "Delete skill?",
                                    "Delete \"" + skill.name + "\"? Chats using it will fall back to Golu Tutor.",
                                    new Ui.ConfirmCallback() {
                                        @Override
                                        public void onYes() {
                                            db.deleteSkill(skill.id);
                                            refresh();
                                        }
                                    });
                        }
                    }
                })
                .show();
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        String line = s.trim();
        int nl = line.indexOf('\n');
        if (nl >= 0) line = line.substring(0, nl).trim();
        if (line.length() > 80) line = line.substring(0, 77) + "...";
        return line;
    }

    private class SkillAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return skills == null ? 0 : skills.size();
        }

        @Override
        public Object getItem(int position) {
            return skills.get(position);
        }

        @Override
        public long getItemId(int position) {
            return skills.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppDb.Skill skill = skills.get(position);
            LinearLayout outer = new LinearLayout(SkillsActivity.this);
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.setPadding(0, 0, 0, Ui.dp(SkillsActivity.this, 8));

            LinearLayout card = Ui.card(SkillsActivity.this);
            TextView name = new TextView(SkillsActivity.this);
            name.setText(skill.name + (skill.builtin ? "  (Built-in)" : ""));
            name.setTextSize(15f);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setTextColor(Ui.INK);
            card.addView(name, Ui.matchWrap());

            TextView sub = new TextView(SkillsActivity.this);
            sub.setText(firstLine(skill.prompt));
            sub.setTextSize(12f);
            sub.setTextColor(Ui.MUTED);
            card.addView(sub, Ui.matchWrap());

            outer.addView(card, Ui.matchWrap());
            return outer;
        }
    }
}
