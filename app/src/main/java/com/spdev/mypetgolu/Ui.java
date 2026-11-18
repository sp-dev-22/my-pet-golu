package com.spdev.mypetgolu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Shared programmatic-UI helpers. The whole app builds its UI in code
 * (no XML layouts), so every screen uses these helpers for one look.
 */
public final class Ui {

    // palette (matches the original warm Golu theme)
    public static final int BG = 0xFFFFF3D9;
    public static final int PANEL = 0xFFFFFBF2;
    public static final int CARD = 0xFFFFFFFF;
    public static final int INK = 0xFF4E342E;
    public static final int MUTED = 0xFF8A7360;
    public static final int ACCENT = 0xFFEF6C00;
    public static final int ACCENT_DARK = 0xFFE65100;
    public static final int GREEN = 0xFF2E7D32;
    public static final int BLUE = 0xFF1565C0;
    public static final int RED = 0xFFC62828;
    public static final int LINE = 0xFFE0C08A;
    public static final int BUBBLE_USER = 0xFFFFE0B2;
    public static final int BUBBLE_AI = 0xFFFFFFFF;
    public static final int CHIP_BG = 0xFFFFF3E0;

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable pill(Context c, int fillColor, int strokeColor, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(c, radiusDp));
        g.setColor(fillColor);
        if (strokeColor != 0) g.setStroke(dp(c, 1.5f), strokeColor);
        return g;
    }

    public static void toast(Context c, String msg) {
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    /** Vertical full-screen root with the app background colour. */
    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(BG);
        return l;
    }

    /** Top bar: back arrow + title. Back finishes the activity. */
    public static LinearLayout topBar(final Activity a, String title) {
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(PANEL);
        bar.setPadding(dp(a, 8), dp(a, 10), dp(a, 12), dp(a, 10));

        TextView back = new TextView(a);
        back.setText("\u2190");
        back.setTextSize(22f);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setTextColor(ACCENT_DARK);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(a, 8), dp(a, 2), dp(a, 8), dp(a, 2));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.finish();
            }
        });
        bar.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView t = new TextView(a);
        t.setText(title);
        t.setTextSize(18f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(INK);
        t.setPadding(dp(a, 6), 0, 0, 0);
        bar.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return bar;
    }

    /** Rounded filled button (a TextView). */
    public static TextView button(Context c, String label, int bgColor) {
        TextView b = new TextView(c);
        b.setText(label);
        b.setTextSize(14f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(0xFFFFFFFF);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 14), dp(c, 11), dp(c, 14), dp(c, 11));
        b.setBackground(pill(c, bgColor, 0, 14));
        return b;
    }

    /** Small outlined chip. */
    public static TextView chip(Context c, String label, boolean selected) {
        TextView tv = new TextView(c);
        tv.setText(label);
        tv.setTextSize(12f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(selected ? 0xFFFFFFFF : 0xFF6B4A22);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));
        tv.setBackground(pill(c, selected ? ACCENT : CHIP_BG,
                selected ? 0 : LINE, 14));
        return tv;
    }

    /** Small section heading. */
    public static TextView section(Context c, String s) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(14f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(0xFFB26A00);
        tv.setPadding(dp(c, 4), dp(c, 12), dp(c, 4), dp(c, 4));
        return tv;
    }

    /** Centered muted "nothing here yet" text. */
    public static TextView empty(Context c, String s) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(14f);
        tv.setTextColor(MUTED);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(c, 24), dp(c, 32), dp(c, 24), dp(c, 32));
        return tv;
    }

    /** Card row: white rounded box with padding, for list items. */
    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(pill(c, CARD, LINE, 14));
        l.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        return l;
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    // ---------------------------------------------------------------- dialogs

    public interface ConfirmCallback {
        void onYes();
    }

    public static void confirm(Context c, String title, String msg, final ConfirmCallback cb) {
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        cb.onYes();
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }

    public interface InputCallback {
        void onInput(String text);
    }

    /** Single-line text input dialog. */
    public static void input(Context c, String title, String hint, String initial,
                             final InputCallback cb) {
        final EditText et = new EditText(c);
        et.setHint(hint);
        et.setText(initial == null ? "" : initial);
        et.setTextColor(INK);
        et.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(et)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        cb.onInput(et.getText().toString().trim());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Password-style input dialog (used for the API key). */
    public static void passwordInput(Context c, String title, String hint, String initial,
                                     final InputCallback cb) {
        final EditText et = new EditText(c);
        et.setHint(hint);
        et.setText(initial == null ? "" : initial);
        et.setTextColor(INK);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(et)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        cb.onInput(et.getText().toString().trim());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Multi-line text input dialog (used for skill prompts and memory facts). */
    public static void longInput(Context c, String title, String hint, String initial,
                                 final InputCallback cb) {
        final EditText et = new EditText(c);
        et.setHint(hint);
        et.setText(initial == null ? "" : initial);
        et.setTextColor(INK);
        et.setMinLines(4);
        et.setGravity(Gravity.TOP);
        et.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(et)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        cb.onInput(et.getText().toString().trim());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public interface ChoiceCallback {
        void onChoice(int index);
    }

    /** Single-choice list dialog. */
    public static void singleChoice(Context c, String title, String[] items, int checked,
                                    final ChoiceCallback cb) {
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setSingleChoiceItems(items, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        cb.onChoice(which);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Simple scrolling text dialog (used to view library items). */
    public static void showText(Context c, String title, String text) {
        TextView tv = new TextView(c);
        tv.setText(text);
        tv.setTextSize(14f);
        tv.setTextColor(INK);
        tv.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        android.widget.ScrollView sv = new android.widget.ScrollView(c);
        sv.addView(tv);
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(sv)
                .setPositiveButton("Close", null)
                .show();
    }

    private Ui() {
    }
}
