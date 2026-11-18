package com.spdev.mypetgolu;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Golu - drawn completely with Canvas primitives.
 * 50 activities, eased animation, toys/props, colours and clothes.
 */
public class PetView extends View {

    public interface Listener {
        void onPetTapped();
        void onPetLongPressed();
        void onSwipeUp();
        void onSwipeDown();
        void onActivityChanged(int act);
    }

    // ---------------------------------------------------------------- palettes

    /** body, outline, belly, cheek */
    public static final int[][] PALETTE = {
            {0xFFFFC94A, 0xFFE39B00, 0xFFFFE7A8, 0xFFFF8FA8},   // golden
            {0xFFFFB0C4, 0xFFE4778F, 0xFFFFDCE4, 0xFFFF7A9A},   // pink
            {0xFF9BD2FF, 0xFF5C97D6, 0xFFD9EDFF, 0xFFFF8FA8},   // sky blue
            {0xFF9FE8C8, 0xFF4FA985, 0xFFD9FBEC, 0xFFFF8FA8},   // mint
            {0xFFC9B6FF, 0xFF8E74D8, 0xFFEAE1FF, 0xFFFF8FA8},   // lavender
            {0xFFFFC49A, 0xFFE08A50, 0xFFFFE7D3, 0xFFFF8FA8},   // peach
            {0xFFD9D9E0, 0xFF9C9CA8, 0xFFF3F3F7, 0xFFFF8FA8},   // grey
            {0xFFC98B62, 0xFF8D5A34, 0xFFEBCBAF, 0xFFFF8FA8},   // cocoa
            {0xFFFFFFFF, 0xFFBFC4CC, 0xFFF2F6FA, 0xFFFF8FA8},   // snow
            {0xFF8EE6E0, 0xFF41A8A2, 0xFFD6FBFA, 0xFFFF8FA8}    // aqua
    };

    public static final String[] PALETTE_NAME = {
            "Golden", "Pink", "Blue", "Mint", "Lavender", "Peach", "Grey", "Cocoa", "Snow", "Aqua"
    };

    /** outfit shapes */
    public static final int OUT_NONE = 0, OUT_TSHIRT = 1, OUT_KURTA = 2, OUT_HOODIE = 3,
            OUT_DRESS = 4, OUT_TOPSKIRT = 5, OUT_OVERALL = 6;

    public static final String[] OUTFIT_BOY = {
            "None", "T-Shirt", "Kurta", "Hoodie", "Dungarees", "Night Suit", "Jersey"
    };
    public static final String[] OUTFIT_GIRL = {
            "None", "Frock", "Kurta", "Hoodie", "Top & Skirt", "Night Suit", "Jersey"
    };
    /** primary, secondary colour per outfit id, boy / girl */
    private static final int[][] OUTFIT_COLOR_BOY = {
            {0, 0},
            {0xFF3D7BE0, 0xFFFFFFFF},   // tshirt blue
            {0xFFF3E0B5, 0xFFE07A1F},   // kurta cream + saffron
            {0xFFEF6C00, 0xFFFFE0B2},   // hoodie orange
            {0xFF3F51B5, 0xFFFFD54F},   // dungarees indigo
            {0xFF263238, 0xFF80CBC4},   // night suit
            {0xFF2E7D32, 0xFFFFFFFF}    // jersey
    };
    private static final int[][] OUTFIT_COLOR_GIRL = {
            {0, 0},
            {0xFFE91E63, 0xFFFFF1F5},   // frock pink
            {0xFF8E24AA, 0xFFFFE082},   // kurta purple
            {0xFFD81B60, 0xFFFFCDD2},   // hoodie
            {0xFF00ACC1, 0xFFFFF59D},   // top and skirt
            {0xFF5E35B1, 0xFFB39DDB},   // night suit
            {0xFF00897B, 0xFFFFFFFF}    // jersey
    };

    public static final String[] ACCESSORY_NAME = {"None", "Bow", "Cap", "Glasses", "Flower"};

    // ------------------------------------------------------------------- props

    private static final int P_NONE = 0, P_BALL = 1, P_KITE = 2, P_BALLOON = 3, P_TEDDY = 4,
            P_DRUM = 5, P_GUITAR = 6, P_BOOK = 7, P_CRAYON = 8, P_CAR = 9, P_PHONE = 10,
            P_CAMERA = 11, P_ICECREAM = 12, P_BANANA = 13, P_APPLE = 14, P_CANDY = 15,
            P_GLASS = 16, P_LADOO = 17, P_BRUSH = 18, P_MILK = 19, P_SWING = 20,
            P_BICYCLE = 21, P_ROPE = 22, P_COMB = 23, P_MIRROR = 24, P_UMBRELLA = 25;

    // effect flags
    private static final int FX_TEARS = 1, FX_ZZZ = 2, FX_HEARTS = 4, FX_NOTES = 8,
            FX_STEAM = 16, FX_STARS = 32, FX_BUBBLES = 64, FX_DROPS = 128, FX_RAIN = 256,
            FX_DUST = 512, FX_QUESTION = 1024, FX_SPARKLE = 2048;

    // ------------------------------------------------------------------ fields

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();

    private float density = 1f;
    private Listener listener;

    // style (persisted by the activity)
    private int palette = 0, gender = 0, outfit = 0, accessory = 0;

    // activity state
    private int act = Acts.IDLE;
    private long actStart = SystemClock.uptimeMillis();
    private long actDur = 0;

    // lip-sync: true while TTS is speaking (mouth opens and closes)
    private boolean speaking = false;

    // compact header mode (Talk / Practice): the pet fills the short header
    // instead of standing 118dp up with empty sky below him
    private boolean compact = false;

    /** Compact header mode: pet fills the view, feet near the bottom edge. */
    public void setCompact(boolean c) {
        compact = c;
        invalidate();
    }

    // geometry
    private float w, h, cx, floorY, R, bodyCy, headCy, headR;
    private float handLX, handLY, handRX, handRY;

    // colours resolved each frame
    private int cBody, cDark, cBelly, cCheek;

    // touch tracking
    private float downX, downY;
    private long downTime;
    private boolean longFired;

    public PetView(Context c) {
        super(c);
        init(c);
    }

    public PetView(Context c, AttributeSet a) {
        super(c, a);
        init(c);
    }

    public PetView(Context c, AttributeSet a, int s) {
        super(c, a, s);
        init(c);
    }

    private void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.CENTER);
        resolveColors();
    }

    private void resolveColors() {
        int[] p = PALETTE[Math.max(0, Math.min(PALETTE.length - 1, palette))];
        cBody = p[0];
        cDark = p[1];
        cBelly = p[2];
        cCheek = p[3];
    }

    public void setListener(Listener l) {
        listener = l;
    }

    // ------------------------------------------------------------------- style

    public void setLook(int palette, int gender, int outfit, int accessory) {
        this.palette = palette;
        this.gender = gender;
        this.outfit = Math.max(0, Math.min(6, outfit));
        this.accessory = accessory;
        resolveColors();
        invalidate();
    }

    public int getPalette() {
        return palette;
    }

    public int getGender() {
        return gender;
    }

    public int getOutfit() {
        return outfit;
    }

    public int getAccessory() {
        return accessory;
    }

    public int outfitCount() {
        return gender == 0 ? OUTFIT_BOY.length : OUTFIT_GIRL.length;
    }

    public String outfitName(int i) {
        return gender == 0 ? OUTFIT_BOY[i] : OUTFIT_GIRL[i];
    }

    private int[] outfitColors() {
        int[][] table = gender == 0 ? OUTFIT_COLOR_BOY : OUTFIT_COLOR_GIRL;
        return table[Math.max(0, Math.min(table.length - 1, outfit))];
    }

    /** outfit id -> shape */
    private int outfitShape() {
        if (outfit == 0) return OUT_NONE;
        if (gender == 0) {
            switch (outfit) {
                case 1: return OUT_TSHIRT;
                case 2: return OUT_KURTA;
                case 3: return OUT_HOODIE;
                case 4: return OUT_OVERALL;
                case 5: return OUT_TSHIRT;
                default: return OUT_TSHIRT;
            }
        } else {
            switch (outfit) {
                case 1: return OUT_DRESS;
                case 2: return OUT_KURTA;
                case 3: return OUT_HOODIE;
                case 4: return OUT_TOPSKIRT;
                case 5: return OUT_TSHIRT;
                default: return OUT_TSHIRT;
            }
        }
    }

    // ---------------------------------------------------------------- activity

    public int getActivity() {
        return act;
    }

    public boolean isSleeping() {
        return act == Acts.SLEEP;
    }

    /** Lip-sync: call true when TTS starts, false when it stops. */
    public void setSpeaking(boolean s) {
        speaking = s;
        invalidate();
    }

    public boolean isSpeaking() {
        return speaking;
    }

    public void setActivity(int a) {
        act = a;
        actStart = SystemClock.uptimeMillis();
        actDur = a == Acts.IDLE ? 0 : Acts.DUR[Math.min(a, Acts.DUR.length - 1)];
        if (listener != null) listener.onActivityChanged(a);
        invalidate();
    }

    public float progress() {
        if (actDur <= 0) return 0f;
        float p = (SystemClock.uptimeMillis() - actStart) / (float) actDur;
        return Math.max(0f, Math.min(1f, p));
    }

    // ------------------------------------------------------------------ easing

    private static float clamp(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static float easeOut(float p) {
        float q = 1 - clamp(p);
        return 1 - q * q * q;
    }

    private static float easeInOut(float p) {
        p = clamp(p);
        return p < 0.5f ? 2 * p * p : 1 - 2 * (1 - p) * (1 - p);
    }

    private static float easeOutBack(float p) {
        p = clamp(p);
        float c = 1.70158f;
        float q = p - 1;
        return 1 + q * q * ((c + 1) * q + c);
    }

    /** 0..1..0 arc used for jumps */
    private static float hop(float p) {
        p = clamp(p);
        return (float) Math.sin(p * Math.PI);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        computeGeometry();
        resolveColors();

        long now = SystemClock.uptimeMillis();
        float t = now / 1000f;
        long el = now - actStart;
        float p = actDur > 0 ? clamp(el / (float) actDur) : 0f;

        if (act != Acts.IDLE && act != Acts.SLEEP && actDur > 0 && el > actDur) {
            act = Acts.IDLE;
            actStart = now;
            actDur = 0;
            p = 0f;
            if (listener != null) listener.onActivityChanged(Acts.IDLE);
        }

        Pose pose = new Pose();
        computePose(act, p, t, el / 1000f, pose);
        if (speaking) {
            pose.mouthStyle = 3;
            pose.mouthOpen = 0.3f + 0.7f * Math.abs((float) Math.sin(t * 10.0)) * (0.6f + 0.4f * Math.abs((float) Math.sin(t * 3.7)));
            pose.bob += (float) Math.sin(t * 10.0) * R * 0.006f;
        }

        drawBackground(c, t);
        drawShadow(c, pose);

        c.save();
        c.translate(cx, floorY);
        c.rotate((float) Math.toDegrees(pose.rot));
        float sc = 1f + pose.squash;
        c.scale(2f - sc, sc);
        c.translate(-cx, -floorY);
        c.translate(0, pose.bob);

        drawLegs(c, pose);
        drawBody(c);
        drawArms(c, pose.armL, pose.armR);
        drawClothes(c);
        c.save();
        c.rotate((float) Math.toDegrees(pose.headTilt), cx, headCy + headR * 0.72f);
        drawEars(c);
        drawHead(c);
        drawFace(c, t, pose);
        drawAccessory(c);
        c.restore();
        c.restore();

        drawProp(c, pose, t, p);
        drawEffects(c, pose, t, p);
        postInvalidateOnAnimation();
    }

    private void computeGeometry() {
        w = getWidth();
        h = getHeight();
        float sidePad = Math.min(w * 0.08f, 34f * density);
        float usable = Math.max(120f, w - 2f * sidePad);
        if (compact) {
            // short header: pet fills the view, feet near the bottom edge
            R = Math.min(usable * 0.30f, h * 0.26f);
            floorY = h - 10f * density;
        } else {
            R = Math.min(usable * 0.30f, h * 0.132f);
            floorY = h - 118f * density;
        }
        cx = w / 2f;
        bodyCy = floorY - R * 0.92f;
        headR = R * 0.86f;
        headCy = bodyCy - R * 0.92f;
    }

    // ------------------------------------------------------------- pose engine

    private static class Pose {
        float bob, squash, rot, headTilt;
        float armL = 0.45f, armR = 0.45f;
        float eyeOpen = 1f, blush = 0.45f, lookX = 0f, lookY = 0f;
        float pawCover = 0f, mouthOpen = 0f, legSpread = 0f;
        int eyeStyle = 0, mouthStyle = 0, prop = P_NONE, fx = 0;
    }

    private void computePose(int a, float p, float t, float elapsed, Pose o) {
        float s1 = (float) Math.sin(t * 2.4f);
        o.bob = s1 * R * 0.020f;

        // gentle "pop" when an activity starts
        if (a != Acts.IDLE && elapsed < 0.30f) {
            float k = elapsed / 0.30f;
            o.squash += (1f - easeOutBack(k)) * 0.10f;
        }

        switch (a) {
            case Acts.IDLE: {
                o.eyeStyle = 0;
                o.mouthStyle = 0;
                // look around now and then
                float look = (float) Math.sin(t * 0.55f);
                o.lookX = look * 0.55f;
                o.lookY = (float) Math.sin(t * 0.9f) * 0.25f;
                if (look > 0.94f) o.headTilt = 0.05f;
                if (look < -0.94f) o.headTilt = -0.05f;
                break;
            }
            case Acts.SMILE:
                o.eyeStyle = 1;
                o.blush = 0.85f;
                o.armL = o.armR = 0.75f;
                o.bob = (float) Math.sin(t * 4f) * R * 0.02f;
                break;
            case Acts.LAUGH: {
                float l = Math.abs((float) Math.sin(t * 9f));
                o.bob = -l * R * 0.07f;
                o.squash = l * 0.05f;
                o.eyeStyle = 1;
                o.mouthStyle = 1;
                o.blush = 1f;
                o.armL = o.armR = 1.05f;
                o.headTilt = -0.06f;
                o.fx |= FX_HEARTS;
                break;
            }
            case Acts.CRY:
                o.eyeStyle = 2;
                o.mouthStyle = 2;
                o.blush = 0.5f;
                o.headTilt = 0.13f;
                o.bob = (float) Math.sin(t * 9f) * R * 0.012f;
                o.armL = o.armR = 0.18f;
                o.fx |= FX_TEARS;
                break;
            case Acts.ANGRY:
                o.eyeStyle = 3;
                o.mouthStyle = 4;
                o.blush = 1f;
                o.headTilt = -0.10f;
                o.bob = (float) Math.sin(t * 16f) * R * 0.016f;
                o.armL = o.armR = 1.15f;
                o.fx |= FX_STEAM;
                break;
            case Acts.SLEEP:
                o.eyeStyle = 4;
                o.mouthStyle = 3;
                o.blush = 0.40f;
                o.headTilt = 0.16f;
                o.bob = (float) Math.sin(t * 1.1f) * R * 0.03f;
                o.armL = o.armR = 0.15f;
                o.fx |= FX_ZZZ;
                break;
            case Acts.WAKEUP: {
                float k = easeOut(Math.min(1f, p * 1.8f));
                o.eyeStyle = p < 0.30f ? 4 : 5;
                o.mouthStyle = p < 0.50f ? 3 : 0;
                o.bob = -hop(p * 0.9f) * R * 0.13f;
                o.armL = o.armR = 0.4f + k * 0.9f;
                o.squash = -0.03f * k;
                break;
            }
            case Acts.NAMASTE:
                o.armR = 0.55f + Math.abs((float) Math.sin(t * 6f)) * 1.0f;
                o.armL = 0.35f;
                o.eyeStyle = 1;
                o.blush = 0.9f;
                o.bob = (float) Math.sin(t * 5f) * R * 0.025f;
                break;
            case Acts.DANCE: {
                float d = (float) Math.sin(t * 6.2f);
                o.bob = d * R * 0.09f;
                o.rot = d * 0.10f;
                o.squash = Math.abs(d) * 0.06f;
                o.armL = 0.55f + Math.abs(d) * 1.15f;
                o.armR = 0.55f + Math.abs(d) * 1.15f;
                o.eyeStyle = 1;
                o.mouthStyle = 1;
                o.blush = 1f;
                o.headTilt = d * 0.12f;
                o.fx |= FX_NOTES;
                break;
            }
            case Acts.SING: {
                float s = Math.abs((float) Math.sin(t * 5.5f));
                o.mouthStyle = 3;
                o.mouthOpen = s;
                o.eyeStyle = 1;
                o.bob = s * R * 0.03f;
                o.headTilt = (float) Math.sin(t * 5.5f) * 0.08f;
                o.armL = o.armR = 0.85f;
                o.blush = 0.9f;
                o.fx |= FX_NOTES;
                break;
            }
            case Acts.SHY:
                o.eyeStyle = 6;
                o.blush = 1f;
                o.headTilt = 0.22f;
                o.lookX = -0.35f;
                o.lookY = 0.55f;
                o.armL = o.armR = 0.08f;
                o.bob = (float) Math.sin(t * 3f) * R * 0.015f;
                break;
            case Acts.SURPRISED:
                o.eyeStyle = 5;
                o.mouthStyle = 3;
                o.bob = -Math.abs((float) Math.sin(t * 3f)) * R * 0.05f;
                o.armL = o.armR = 1.35f;
                o.squash = -0.03f;
                o.fx |= FX_STARS;
                break;
            case Acts.LOVE:
                o.eyeStyle = 7;
                o.blush = 1f;
                o.armL = o.armR = 0.55f;
                o.bob = (float) Math.sin(t * 3f) * R * 0.02f;
                o.headTilt = 0.06f;
                o.fx |= FX_HEARTS;
                break;

            case Acts.BALL: {
                o.prop = P_BALL;
                o.armL = o.armR = 0.95f;
                o.lookY = -0.45f;
                o.mouthStyle = 1;
                o.bob = -Math.abs((float) Math.sin(t * 6f)) * R * 0.025f;
                break;
            }
            case Acts.KICK: {
                o.prop = P_BALL;
                float k = hop(p * 1.6f);
                o.bob = -k * R * 0.05f;
                o.armL = 0.5f;
                o.armR = 1.1f;
                o.eyeStyle = 1;
                o.mouthStyle = 1;
                o.fx |= FX_DUST;
                break;
            }
            case Acts.JUMP: {
                float j = hop(p);
                float crouch = p < 0.12f ? p / 0.12f : (p > 0.92f ? (1 - p) / 0.08f : 0f);
                o.bob = -j * R * 0.60f + crouch * R * 0.10f;
                o.squash = crouch * 0.10f - j * 0.06f;
                o.armL = o.armR = 0.6f + j * 0.8f;
                o.eyeStyle = 1;
                o.mouthStyle = 1;
                if (p > 0.9f) o.fx |= FX_DUST;
                break;
            }
            case Acts.SPIN:
                o.rot = (float) (p * Math.PI * 4);
                o.bob = -Math.abs((float) Math.sin(p * Math.PI * 2)) * R * 0.05f;
                o.armL = o.armR = 1.4f;
                o.eyeStyle = 1;
                o.mouthStyle = 1;
                o.fx |= FX_STARS;
                break;
            case Acts.PEEKABOO: {
                float k = p < 0.45f ? 1f : 1f - (p - 0.45f) / 0.2f;
                o.pawCover = Math.max(0f, Math.min(1f, k));
                o.mouthStyle = 1;
                o.bob = (float) Math.sin(t * 4f) * R * 0.02f;
                o.armL = o.armR = 1.0f;
                break;
            }
            case Acts.SWING: {
                o.prop = P_SWING;
                float s = (float) Math.sin(t * 2.6f);
                o.bob = -Math.abs(s) * R * 0.12f;
                o.rot = s * 0.06f;
                o.armL = o.armR = 1.30f;
                o.eyeStyle = 1;
                o.mouthStyle = 1;
                break;
            }
            case Acts.KITE:
                o.prop = P_KITE;
                o.armR = 1.15f;
                o.lookY = -0.5f;
                o.bob = (float) Math.sin(t * 2f) * R * 0.02f;
                o.eyeStyle = 0;
                o.mouthStyle = 0;
                o.blush = 0.8f;
                break;
            case Acts.BUBBLES: {
                float b = Math.abs((float) Math.sin(t * 2.2f));
                o.mouthStyle = 3;
                o.mouthOpen = b;
                o.eyeStyle = 1;
                o.armL = o.armR = 0.5f;
                o.fx |= FX_BUBBLES;
                o.bob = (float) Math.sin(t * 3f) * R * 0.02f;
                break;
            }
            case Acts.BALLOON:
                o.prop = P_BALLOON;
                o.armR = 1.25f;
                o.bob = -Math.abs((float) Math.sin(t * 2f)) * R * 0.03f;
                o.eyeStyle = 1;
                o.lookY = -0.45f;
                o.mouthStyle = 0;
                break;
            case Acts.BICYCLE:
                o.prop = P_BICYCLE;
                o.bob = (float) Math.sin(t * 7f) * R * 0.02f;
                o.armL = o.armR = 1.05f;
                o.eyeStyle = 1;
                o.mouthStyle = 0;
                o.fx |= FX_DUST;
                break;
            case Acts.ROPE:
                o.prop = P_ROPE;
                o.bob = -Math.abs((float) Math.sin(t * 4f)) * R * 0.09f;
                o.armL = o.armR = 1.25f;
                o.eyeStyle = 1;
                o.mouthStyle = 1;
                if (p > 0.85f) o.fx |= FX_DUST;
                break;
            case Acts.TICKLE: {
                float k = Math.abs((float) Math.sin(t * 10f));
                o.eyeStyle = 1;
                o.mouthStyle = 1;
                o.blush = 1f;
                o.bob = k * R * 0.03f;
                o.armL = o.armR = 0.9f;
                o.squash = k * 0.05f;
                o.fx |= FX_HEARTS;
                break;
            }
            case Acts.HIDE:
                o.pawCover = 1f;
                o.bob = (float) Math.sin(t * 2f) * R * 0.012f;
                o.mouthStyle = 0;
                o.fx |= FX_QUESTION;
                break;
            case Acts.RUN:
                o.bob = -Math.abs((float) Math.sin(t * 9f)) * R * 0.05f;
                o.rot = (float) Math.sin(t * 9f) * 0.05f;
                o.armL = 1.0f + (float) Math.sin(t * 9f) * 0.5f;
                o.armR = 1.0f - (float) Math.sin(t * 9f) * 0.5f;
                o.eyeStyle = 1;
                o.mouthStyle = 1;
                o.fx |= FX_DUST;
                break;

            case Acts.MILK:
                o.prop = P_MILK;
                o.headTilt = -0.20f;
                o.mouthStyle = 3;
                o.eyeStyle = 1;
                o.armL = 1.25f;
                o.armR = 0.30f;
                o.blush = 0.9f;
                break;
            case Acts.ICECREAM:
            case Acts.BANANA:
            case Acts.APPLE:
            case Acts.CANDY:
            case Acts.LADOO: {
                o.prop = a == Acts.ICECREAM ? P_ICECREAM : a == Acts.BANANA ? P_BANANA
                        : a == Acts.APPLE ? P_APPLE : a == Acts.CANDY ? P_CANDY : P_LADOO;
                o.armR = 1.15f;
                o.armL = 0.45f;
                o.mouthStyle = 3;
                o.mouthOpen = Math.abs((float) Math.sin(t * 3f));
                o.eyeStyle = 1;
                o.blush = 1f;
                o.bob = (float) Math.sin(t * 4f) * R * 0.015f;
                break;
            }
            case Acts.WATER:
                o.prop = P_GLASS;
                o.headTilt = -0.15f;
                o.mouthStyle = 3;
                o.eyeStyle = 1;
                o.armR = 1.1f;
                o.armL = 0.4f;
                break;
            case Acts.TEETH:
                o.prop = P_BRUSH;
                o.headTilt = (float) Math.sin(t * 6f) * 0.10f;
                o.mouthStyle = 1;
                o.eyeStyle = 0;
                o.armR = 1.35f;
                o.armL = 0.4f;
                o.fx |= FX_BUBBLES;
                break;

            case Acts.TEDDY:
                o.prop = P_TEDDY;
                o.armL = o.armR = 0.55f;
                o.eyeStyle = 1;
                o.blush = 1f;
                o.bob = (float) Math.sin(t * 2.5f) * R * 0.02f;
                o.headTilt = 0.08f;
                o.fx |= FX_HEARTS;
                break;
            case Acts.DRUM:
                o.prop = P_DRUM;
                o.armL = 1.0f + (float) Math.sin(t * 10f) * 0.35f;
                o.armR = 1.0f - (float) Math.sin(t * 10f) * 0.35f;
                o.eyeStyle = 1;
                o.mouthStyle = 1;
                o.bob = (float) Math.sin(t * 10f) * R * 0.02f;
                o.fx |= FX_NOTES;
                break;
            case Acts.GUITAR:
                o.prop = P_GUITAR;
                o.armL = 0.95f;
                o.armR = 1.05f;
                o.eyeStyle = 1;
                o.mouthStyle = 3;
                o.mouthOpen = Math.abs((float) Math.sin(t * 4f));
                o.headTilt = (float) Math.sin(t * 4f) * 0.07f;
                o.fx |= FX_NOTES;
                break;
            case Acts.BOOK:
                o.prop = P_BOOK;
                o.armL = o.armR = 0.72f;
                o.eyeStyle = 0;
                o.lookY = 0.45f;
                o.lookX = (float) Math.sin(t * 0.9f) * 0.3f;
                o.bob = (float) Math.sin(t * 2f) * R * 0.01f;
                break;
            case Acts.DRAW:
                o.prop = P_CRAYON;
                o.armR = 1.0f + (float) Math.sin(t * 5f) * 0.18f;
                o.armL = 0.55f;
                o.lookY = 0.5f;
                o.lookX = 0.25f;
                o.mouthStyle = 0;
                break;
            case Acts.CAR:
                o.prop = P_CAR;
                o.bob = (float) Math.sin(t * 8f) * R * 0.02f;
                o.armL = o.armR = 0.6f;
                o.eyeStyle = 1;
                o.mouthStyle = 1;
                o.lookY = 0.45f;
                break;
            case Acts.PHONE:
                o.prop = P_PHONE;
                o.armR = 1.4f;
                o.armL = 0.35f;
                o.eyeStyle = 1;
                o.mouthStyle = 3;
                o.mouthOpen = Math.abs((float) Math.sin(t * 4.5f));
                o.headTilt = 0.06f;
                break;
            case Acts.PHOTO:
                o.prop = P_CAMERA;
                o.armL = o.armR = 1.15f;
                o.eyeStyle = 5;
                o.mouthStyle = 1;
                o.blush = 1f;
                o.fx |= FX_SPARKLE;
                break;

            case Acts.SNEEZE: {
                float s = p < 0.55f ? easeInOut(p / 0.55f) : 1f - easeOut((p - 0.55f) / 0.45f);
                o.headTilt = -0.18f * s;
                o.eyeStyle = 4;
                o.mouthStyle = 1;
                o.squash = 0.06f * s;
                o.bob = -s * R * 0.04f;
                if (p > 0.5f) o.fx |= FX_DROPS;
                break;
            }
            case Acts.HICCUP: {
                float k = Math.abs((float) Math.sin(p * Math.PI * 3));
                o.bob = -k * R * 0.06f;
                o.squash = k * 0.06f;
                o.eyeStyle = 5;
                o.mouthStyle = 3;
                o.armL = o.armR = 0.4f;
                o.fx |= FX_STARS;
                break;
            }
            case Acts.SCRATCH:
                o.armR = 1.45f + (float) Math.sin(t * 8f) * 0.18f;
                o.armL = 0.3f;
                o.eyeStyle = 6;
                o.lookY = -0.4f;
                o.headTilt = -0.05f;
                o.mouthStyle = 0;
                o.fx |= FX_QUESTION;
                break;
            case Acts.COMB:
                o.prop = P_COMB;
                o.armR = 1.40f + (float) Math.sin(t * 5f) * 0.15f;
                o.armL = 0.3f;
                o.eyeStyle = 1;
                o.headTilt = (float) Math.sin(t * 3f) * 0.06f;
                o.blush = 0.8f;
                break;
            case Acts.BATHE:
                o.armL = 0.8f + (float) Math.sin(t * 6f) * 0.3f;
                o.armR = 0.8f - (float) Math.sin(t * 6f) * 0.3f;
                o.eyeStyle = 1;
                o.mouthStyle = 0;
                o.bob = (float) Math.sin(t * 4f) * R * 0.02f;
                o.fx |= FX_BUBBLES | FX_DROPS;
                break;
            case Acts.MIRROR:
                o.prop = P_MIRROR;
                o.eyeStyle = 1;
                o.blush = 1f;
                o.headTilt = (float) Math.sin(t * 2f) * 0.07f;
                o.armL = o.armR = 0.35f;
                o.fx |= FX_SPARKLE;
                break;
            case Acts.RAIN:
                o.prop = P_UMBRELLA;
                o.armR = 1.40f;
                o.armL = 0.5f;
                o.eyeStyle = 1;
                o.mouthStyle = 1;
                o.bob = -Math.abs((float) Math.sin(t * 5f)) * R * 0.03f;
                o.rot = (float) Math.sin(t * 5f) * 0.05f;
                o.fx |= FX_RAIN;
                break;
            case Acts.YOGA:
                o.eyeStyle = 4;
                o.bob = (float) Math.sin(t * 1.2f) * R * 0.02f;
                o.armL = o.armR = 0.12f;
                o.legSpread = 1f;
                o.blush = 0.6f;
                o.fx |= FX_SPARKLE;
                break;
            default:
                break;
        }
    }

    // --------------------------------------------------------------- body bits

    private void drawBackground(Canvas c, float t) {
        fill.setShader(new LinearGradient(0, 0, 0, h, 0xFFB9E4FF, 0xFFFFF4DC, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, fill);
        fill.setShader(null);

        // sun
        fc(c, w * 0.84f, h * 0.11f, 34f * density, 0x33FFD54F);
        fc(c, w * 0.84f, h * 0.11f, 22f * density, 0x66FFE082);

        // drifting clouds
        drawCloud(c, (w * 0.18f + t * 6f * density) % (w + 200f * density) - 100f * density, h * 0.16f, 1f);
        drawCloud(c, (w * 0.62f + t * 4f * density) % (w + 200f * density) - 100f * density, h * 0.26f, 0.75f);

        // rug
        fo(c, cx - R * 2.05f, floorY - R * 0.30f, cx + R * 2.05f, floorY + R * 0.34f, 0xFFFFDCA8);
        stroke.setColor(0xFFF0BF78);
        stroke.setStrokeWidth(3f * density);
        rf.set(cx - R * 2.05f, floorY - R * 0.30f, cx + R * 2.05f, floorY + R * 0.34f);
        c.drawOval(rf, stroke);
    }

    private void drawCloud(Canvas c, float x, float y, float s) {
        fill.setColor(0xCCFFFFFF);
        c.drawCircle(x, y, 26f * density * s, fill);
        c.drawCircle(x + 30f * density * s, y + 6f * density * s, 20f * density * s, fill);
        c.drawCircle(x - 30f * density * s, y + 8f * density * s, 18f * density * s, fill);
        c.drawCircle(x + 8f * density * s, y - 14f * density * s, 18f * density * s, fill);
    }

    private void drawShadow(Canvas c, Pose pose) {
        float lift = Math.max(0f, -pose.bob) / (R * 0.6f);
        float k = 1f - 0.35f * Math.min(1f, lift);
        fill.setColor(0x33201A00);
        rf.set(cx - R * 0.95f * k, floorY - R * 0.16f * k,
                cx + R * 0.95f * k, floorY + R * 0.10f * k);
        c.drawOval(rf, fill);
    }

    private void drawLegs(Canvas c, Pose pose) {
        float spread = 0.42f + pose.legSpread * 0.45f;
        fill.setColor(cDark);
        fo(c, cx - R * (spread + 0.13f), floorY - R * 0.17f, cx - R * (spread - 0.13f), floorY + R * 0.03f, cDark);
        fo(c, cx + R * (spread - 0.13f), floorY - R * 0.17f, cx + R * (spread + 0.13f), floorY + R * 0.03f, cDark);
    }

    private void drawBody(Canvas c) {
        fo(c, cx - R * 0.88f, bodyCy - R * 0.92f, cx + R * 0.88f, bodyCy + R * 0.92f, cBody);
        rf.set(cx - R * 0.88f, bodyCy - R * 0.92f, cx + R * 0.88f, bodyCy + R * 0.92f);
        stroke.setColor(cDark);
        stroke.setStrokeWidth(R * 0.035f);
        c.drawOval(rf, stroke);
        fo(c, cx - R * 0.46f, bodyCy - R * 0.30f, cx + R * 0.46f, bodyCy + R * 0.62f, cBelly);
    }

    private void drawArms(Canvas c, float armL, float armR) {
        float len = R * 0.68f;
        float sxL = cx - R * 0.74f, syL = bodyCy - R * 0.05f;
        float sxR = cx + R * 0.74f, syR = bodyCy - R * 0.05f;
        handLX = sxL - (float) Math.sin(armL) * len;
        handLY = syL + (float) Math.cos(armL) * len;
        handRX = sxR + (float) Math.sin(armR) * len;
        handRY = syR + (float) Math.cos(armR) * len;

        stroke.setColor(cBody);
        stroke.setStrokeWidth(R * 0.19f);
        c.drawLine(sxL, syL, handLX, handLY, stroke);
        c.drawLine(sxR, syR, handRX, handRY, stroke);

        sc(c, handLX, handLY, R * 0.095f, R * 0.032f, cDark);
        fc(c, handLX, handLY, R * 0.085f, cBody);
        sc(c, handRX, handRY, R * 0.095f, R * 0.032f, cDark);
        fc(c, handRX, handRY, R * 0.085f, cBody);
    }

    private void drawClothes(Canvas c) {
        int shape = outfitShape();
        if (shape == OUT_NONE) return;
        int[] col = outfitColors();
        int main = col[0], accent = col[1];

        float bt = bodyCy - R * 0.72f;      // top of garment
        float bb = bodyCy + R * 0.55f;      // bottom of garment

        switch (shape) {
            case OUT_TSHIRT: {
                fo(c, cx - R * 0.82f, bt, cx + R * 0.82f, bb, main);
                // sleeves
                fc(c, cx - R * 0.78f, bodyCy - R * 0.30f, R * 0.20f, main);
                fc(c, cx + R * 0.78f, bodyCy - R * 0.30f, R * 0.20f, main);
                // collar
                fo(c, cx - R * 0.20f, bt - R * 0.05f, cx + R * 0.20f, bt + R * 0.10f, accent);
                // stripe
                fr(c, cx - R * 0.82f, bodyCy + R * 0.05f, cx + R * 0.82f, bodyCy + R * 0.18f, R * 0.06f, accent);
                break;
            }
            case OUT_KURTA: {
                fr(c, cx - R * 0.78f, bt, cx + R * 0.78f, bodyCy + R * 0.95f, R * 0.30f, main);
                fc(c, cx - R * 0.74f, bodyCy - R * 0.28f, R * 0.19f, main);
                fc(c, cx + R * 0.74f, bodyCy - R * 0.28f, R * 0.19f, main);
                // v neck + buttons
                path.reset();
                path.moveTo(cx - R * 0.20f, bt);
                path.lineTo(cx, bt + R * 0.34f);
                path.lineTo(cx + R * 0.20f, bt);
                path.close();
                fill.setColor(accent);
                c.drawPath(path, fill);
                fc(c, cx, bodyCy + R * 0.20f, R * 0.045f, accent);
                fc(c, cx, bodyCy + R * 0.45f, R * 0.045f, accent);
                break;
            }
            case OUT_HOODIE: {
                fo(c, cx - R * 0.84f, bt - R * 0.05f, cx + R * 0.84f, bb + R * 0.10f, main);
                fc(c, cx - R * 0.80f, bodyCy - R * 0.28f, R * 0.22f, main);
                fc(c, cx + R * 0.80f, bodyCy - R * 0.28f, R * 0.22f, main);
                // hood behind neck
                fo(c, cx - R * 0.55f, bt - R * 0.28f, cx + R * 0.55f, bt + R * 0.22f, accent);
                // pocket
                fr(c, cx - R * 0.34f, bodyCy + R * 0.10f, cx + R * 0.34f, bodyCy + R * 0.36f, R * 0.10f, accent);
                // strings
                ln(c, cx - R * 0.12f, bt + R * 0.02f, cx - R * 0.12f, bt + R * 0.26f, R * 0.03f, accent);
                ln(c, cx + R * 0.12f, bt + R * 0.02f, cx + R * 0.12f, bt + R * 0.26f, R * 0.03f, accent);
                break;
            }
            case OUT_DRESS: {
                // bodice
                fo(c, cx - R * 0.66f, bt, cx + R * 0.66f, bodyCy + R * 0.16f, main);
                // flared skirt
                path.reset();
                path.moveTo(cx - R * 0.55f, bodyCy + R * 0.05f);
                path.lineTo(cx - R * 1.02f, bodyCy + R * 0.95f);
                path.quadTo(cx, bodyCy + R * 1.16f, cx + R * 1.02f, bodyCy + R * 0.95f);
                path.lineTo(cx + R * 0.55f, bodyCy + R * 0.05f);
                path.close();
                fill.setColor(main);
                c.drawPath(path, fill);
                // hem + bow
                ln(c, cx - R * 0.95f, bodyCy + R * 0.93f, cx + R * 0.95f, bodyCy + R * 0.93f, R * 0.05f, accent);
                fc(c, cx, bodyCy + R * 0.06f, R * 0.07f, accent);
                break;
            }
            case OUT_TOPSKIRT: {
                fr(c, cx - R * 0.70f, bt, cx + R * 0.70f, bodyCy + R * 0.14f, R * 0.14f, main);
                fc(c, cx - R * 0.70f, bodyCy - R * 0.26f, R * 0.18f, main);
                fc(c, cx + R * 0.70f, bodyCy - R * 0.26f, R * 0.18f, main);
                path.reset();
                path.moveTo(cx - R * 0.62f, bodyCy + R * 0.08f);
                path.lineTo(cx - R * 1.00f, bodyCy + R * 0.92f);
                path.lineTo(cx + R * 1.00f, bodyCy + R * 0.92f);
                path.lineTo(cx + R * 0.62f, bodyCy + R * 0.08f);
                path.close();
                fill.setColor(accent);
                c.drawPath(path, fill);
                break;
            }
            case OUT_OVERALL: {
                fo(c, cx - R * 0.62f, bodyCy - R * 0.25f, cx + R * 0.62f, bodyCy + R * 0.92f, main);
                // straps
                ln(c, cx - R * 0.42f, bodyCy - R * 0.22f, cx - R * 0.52f, bt, R * 0.13f, main);
                ln(c, cx + R * 0.42f, bodyCy - R * 0.22f, cx + R * 0.52f, bt, R * 0.13f, main);
                fr(c, cx - R * 0.30f, bodyCy + R * 0.18f, cx + R * 0.30f, bodyCy + R * 0.46f, R * 0.08f, accent);
                break;
            }
            default:
                break;
        }
    }

    private void drawEars(Canvas c) {
        float earR = headR * 0.30f;
        float ex = headR * 0.68f;
        float ey = -headR * 0.66f;
        fc(c, cx - ex, headCy + ey, earR, cBody);
        sc(c, cx - ex, headCy + ey, earR, R * 0.035f, cDark);
        fc(c, cx + ex, headCy + ey, earR, cBody);
        sc(c, cx + ex, headCy + ey, earR, R * 0.035f, cDark);
        fc(c, cx - ex, headCy + ey, earR * 0.45f, cCheek);
        fc(c, cx + ex, headCy + ey, earR * 0.45f, cCheek);
    }

    private void drawHead(Canvas c) {
        fc(c, cx, headCy, headR, cBody);
        sc(c, cx, headCy, headR, R * 0.035f, cDark);
        stroke.setColor(cDark);
        stroke.setStrokeWidth(R * 0.045f);
        path.reset();
        float ty = headCy - headR * 0.98f;
        path.moveTo(cx - headR * 0.16f, ty + headR * 0.06f);
        path.quadTo(cx - headR * 0.10f, ty - headR * 0.20f, cx + headR * 0.02f, ty - headR * 0.10f);
        path.quadTo(cx + headR * 0.12f, ty - headR * 0.26f, cx + headR * 0.20f, ty + headR * 0.04f);
        c.drawPath(path, stroke);
    }

    private void drawFace(Canvas c, float t, Pose pose) {
        float eyeDx = headR * 0.34f;
        float eyeY = headCy - headR * 0.06f;
        float eyeRx = headR * 0.145f;
        float eyeRy = headR * 0.185f;

        float open = pose.eyeOpen;
        if (pose.eyeStyle == 0 || pose.eyeStyle == 6) {
            // irregular blinking
            float cyc = t % 4.1f;
            float blink = 0f;
            if (cyc > 3.85f) blink = 1f - Math.abs(cyc - 3.98f) / 0.13f;
            open = Math.max(0.08f, 1f - Math.max(0f, blink));
        }

        // cheeks
        fill.setColor(cCheek);
        fill.setAlpha((int) (255 * clamp(pose.blush)));
        c.drawCircle(cx - headR * 0.58f, headCy + headR * 0.26f, headR * 0.15f, fill);
        c.drawCircle(cx + headR * 0.58f, headCy + headR * 0.26f, headR * 0.15f, fill);
        fill.setAlpha(255);

        float lx = pose.lookX * headR * 0.10f;
        float ly = pose.lookY * headR * 0.10f;

        for (int s = -1; s <= 1; s += 2) {
            float ex = cx + s * eyeDx + lx;
            float ey = eyeY + ly;
            switch (pose.eyeStyle) {
                case 1:
                    stroke.setColor(0xFF3B2A26);
                    stroke.setStrokeWidth(headR * 0.075f);
                    path.reset();
                    path.moveTo(ex - eyeRx * 1.15f, ey + eyeRy * 0.45f);
                    path.quadTo(ex, ey - eyeRy * 0.85f, ex + eyeRx * 1.15f, ey + eyeRy * 0.45f);
                    c.drawPath(path, stroke);
                    break;
                case 2:
                    stroke.setColor(0xFF3B2A26);
                    stroke.setStrokeWidth(headR * 0.070f);
                    path.reset();
                    path.moveTo(ex - eyeRx * 1.1f, ey - eyeRy * 0.35f);
                    path.quadTo(ex, ey + eyeRy * 0.95f, ex + eyeRx * 1.1f, ey - eyeRy * 0.35f);
                    c.drawPath(path, stroke);
                    break;
                case 3:
                    fill.setColor(0xFF3B2A26);
                    fo(c, ex - eyeRx * 1.05f, ey - eyeRy * 0.45f, ex + eyeRx * 1.05f, ey + eyeRy * 0.55f, 0xFF3B2A26);
                    ln(c, ex - s * eyeRx * 1.25f, ey - eyeRy * 1.35f,
                            ex + s * eyeRx * 1.15f, ey - eyeRy * 0.85f, headR * 0.085f, 0xFF3B2A26);
                    break;
                case 4:
                    ln(c, ex - eyeRx, ey, ex + eyeRx, ey, headR * 0.065f, 0xFF3B2A26);
                    break;
                case 5:
                    fo(c, ex - eyeRx * 1.25f, ey - eyeRy * 1.15f, ex + eyeRx * 1.25f, ey + eyeRy * 1.15f, 0xFFFFFFFF);
                    sc(c, ex, ey, eyeRx * 1.15f, headR * 0.035f, 0xFF3B2A26);
                    fc(c, ex, ey + eyeRy * 0.15f, eyeRx * 0.72f, 0xFF3B2A26);
                    fc(c, ex - eyeRx * 0.25f, ey - eyeRy * 0.25f, eyeRx * 0.24f, 0xFFFFFFFF);
                    break;
                case 6: {
                    // shy side look
                    fo(c, ex - eyeRx, ey - eyeRy * open, ex + eyeRx, ey + eyeRy * open, 0xFF3B2A26);
                    fc(c, ex - eyeRx * 0.1f, ey - eyeRy * 0.35f * open, eyeRx * 0.28f, 0xFFFFFFFF);
                    break;
                }
                case 7: {
                    // heart eyes
                    drawHeart(c, ex, ey, headR * 0.17f, 0xFFE91E63, 255);
                    break;
                }
                default: {
                    fo(c, ex - eyeRx, ey - eyeRy * open, ex + eyeRx, ey + eyeRy * open, 0xFF3B2A26);
                    fc(c, ex - eyeRx * 0.32f, ey - eyeRy * 0.38f * open, eyeRx * 0.30f, 0xFFFFFFFF);
                    break;
                }
            }
            // lashes for the girl look
            if (gender == 1 && (pose.eyeStyle == 0 || pose.eyeStyle == 6)) {
                ln(c, ex + s * eyeRx * 0.95f, ey - eyeRy * 0.55f * open,
                        ex + s * eyeRx * 1.45f, ey - eyeRy * 0.95f * open, headR * 0.030f, 0xFF3B2A26);
            }
        }

        float my = headCy + headR * 0.38f;
        float mw = headR * 0.28f;
        switch (pose.mouthStyle) {
            case 1: {
                float o = 0.6f + pose.mouthOpen * 0.4f;
                rf.set(cx - mw * 1.05f, my - mw * 0.45f, cx + mw * 1.05f, my + mw * 1.05f * o);
                fill.setColor(0xFF8E2A2A);
                c.drawArc(rf, 0f, 180f, true, fill);
                fo(c, cx - mw * 0.55f, my + mw * 0.55f * o, cx + mw * 0.55f, my + mw * 1.05f * o, 0xFFFF7A90);
                break;
            }
            case 2:
                stroke.setColor(0xFF3B2A26);
                stroke.setStrokeWidth(headR * 0.065f);
                path.reset();
                path.moveTo(cx - mw, my + mw * 0.42f);
                path.quadTo(cx, my - mw * 0.55f, cx + mw, my + mw * 0.42f);
                c.drawPath(path, stroke);
                break;
            case 3: {
                float o = 0.55f + pose.mouthOpen * 0.6f;
                fo(c, cx - mw * 0.42f, my - mw * 0.35f * o, cx + mw * 0.42f, my + mw * 0.55f * o, 0xFF8E2A2A);
                break;
            }
            case 4:
                stroke.setColor(0xFF3B2A26);
                stroke.setStrokeWidth(headR * 0.060f);
                path.reset();
                path.moveTo(cx - mw, my + mw * 0.10f);
                path.quadTo(cx - mw * 0.5f, my - mw * 0.35f, cx, my + mw * 0.10f);
                path.quadTo(cx + mw * 0.5f, my + mw * 0.55f, cx + mw, my + mw * 0.10f);
                c.drawPath(path, stroke);
                break;
            default:
                stroke.setColor(0xFF3B2A26);
                stroke.setStrokeWidth(headR * 0.065f);
                path.reset();
                path.moveTo(cx - mw, my - mw * 0.20f);
                path.quadTo(cx, my + mw * 0.85f, cx + mw, my - mw * 0.20f);
                c.drawPath(path, stroke);
                break;
        }

        // paws over the eyes for peekaboo / hide
        if (pose.pawCover > 0.02f) {
            float k = clamp(pose.pawCover);
            float py = eyeY + (1f - k) * headR * 0.9f;
            fc(c, cx - eyeDx, py, headR * 0.26f, cBody);
            sc(c, cx - eyeDx, py, headR * 0.26f, R * 0.032f, cDark);
            fc(c, cx + eyeDx, py, headR * 0.26f, cBody);
            sc(c, cx + eyeDx, py, headR * 0.26f, R * 0.032f, cDark);
        }
    }

    private void drawAccessory(Canvas c) {
        switch (accessory) {
            case 1: { // bow
                float bx = cx + headR * 0.62f, by = headCy - headR * 0.72f;
                path.reset();
                path.moveTo(bx, by);
                path.lineTo(bx - headR * 0.26f, by - headR * 0.16f);
                path.lineTo(bx - headR * 0.26f, by + headR * 0.16f);
                path.close();
                fill.setColor(0xFFFF4081);
                c.drawPath(path, fill);
                path.reset();
                path.moveTo(bx, by);
                path.lineTo(bx + headR * 0.26f, by - headR * 0.16f);
                path.lineTo(bx + headR * 0.26f, by + headR * 0.16f);
                path.close();
                c.drawPath(path, fill);
                fc(c, bx, by, headR * 0.07f, 0xFFFF80AB);
                break;
            }
            case 2: { // cap
                path.reset();
                path.arcTo(new RectF(cx - headR * 0.92f, headCy - headR * 1.22f,
                        cx + headR * 0.92f, headCy + headR * 0.30f), 180f, 180f, true);
                path.close();
                fill.setColor(0xFF1565C0);
                c.drawPath(path, fill);
                fr(c, cx - headR * 1.05f, headCy - headR * 0.42f, cx + headR * 0.35f,
                        headCy - headR * 0.26f, headR * 0.08f, 0xFF0D47A1);
                break;
            }
            case 3: { // glasses
                float gy = headCy - headR * 0.06f;
                float gx = headR * 0.34f;
                stroke.setColor(0xFF37474F);
                stroke.setStrokeWidth(headR * 0.045f);
                rf.set(cx - gx - headR * 0.24f, gy - headR * 0.20f, cx - gx + headR * 0.24f, gy + headR * 0.20f);
                c.drawRoundRect(rf, headR * 0.08f, headR * 0.08f, stroke);
                rf.set(cx + gx - headR * 0.24f, gy - headR * 0.20f, cx + gx + headR * 0.24f, gy + headR * 0.20f);
                c.drawRoundRect(rf, headR * 0.08f, headR * 0.08f, stroke);
                ln(c, cx - gx + headR * 0.24f, gy, cx + gx - headR * 0.24f, gy, headR * 0.04f, 0xFF37474F);
                break;
            }
            case 4: { // flower
                float fx = cx - headR * 0.62f, fy = headCy - headR * 0.72f;
                for (int i = 0; i < 5; i++) {
                    double a = i * Math.PI * 2 / 5;
                    fc(c, fx + (float) Math.cos(a) * headR * 0.13f,
                            fy + (float) Math.sin(a) * headR * 0.13f, headR * 0.10f, 0xFFFFEB3B);
                }
                fc(c, fx, fy, headR * 0.08f, 0xFFFF7043);
                break;
            }
            default:
                break;
        }
    }

    // ------------------------------------------------------------------- props

    private void drawProp(Canvas c, Pose pose, float t, float p) {
        switch (pose.prop) {
            case P_BALL: {
                float bx, by;
                if (act == Acts.KICK) {
                    bx = cx + R * 0.55f + p * R * 1.15f;
                    by = floorY - R * 0.16f - hop(p * 1.4f) * R * 0.95f;
                } else {
                    float k = (t * 1.6f) % 1f;
                    bx = cx + R * 0.95f;
                    by = floorY - R * 0.16f - hop(k) * R * 0.95f;
                }
                float br = R * 0.17f;
                float squash = by > floorY - R * 0.30f ? 1.12f : 1f;
                rf.set(bx - br * squash, by - br / squash, bx + br * squash, by + br / squash);
                fill.setColor(0xFFE53935);
                c.drawOval(rf, fill);
                fo(c, bx - br * 0.5f, by - br * 0.8f, bx + br * 0.5f, by + br * 0.2f, 0xFFFFEB3B);
                break;
            }
            case P_KITE: {
                float kx = cx + R * 1.05f + (float) Math.sin(t * 1.6f) * R * 0.16f;
                float ky = headCy - R * 1.35f + (float) Math.cos(t * 2.2f) * R * 0.10f;
                ln(c, handRX, handRY, kx, ky, R * 0.02f, 0xAA5D4037);
                path.reset();
                path.moveTo(kx, ky - R * 0.30f);
                path.lineTo(kx + R * 0.24f, ky);
                path.lineTo(kx, ky + R * 0.34f);
                path.lineTo(kx - R * 0.24f, ky);
                path.close();
                fill.setColor(0xFFFF7043);
                c.drawPath(path, fill);
                ln(c, kx, ky - R * 0.30f, kx, ky + R * 0.34f, R * 0.015f, 0xFFFFF176);
                ln(c, kx - R * 0.24f, ky, kx + R * 0.24f, ky, R * 0.015f, 0xFFFFF176);
                for (int i = 0; i < 3; i++) {
                    float ty = ky + R * 0.40f + i * R * 0.16f;
                    fc(c, kx + (float) Math.sin(t * 3 + i) * R * 0.06f, ty, R * 0.035f, 0xFF42A5F5);
                }
                break;
            }
            case P_BALLOON: {
                float bx = cx + R * 1.05f;
                float by = headCy - R * 1.30f + (float) Math.sin(t * 1.5f) * R * 0.07f;
                ln(c, handRX, handRY, bx, by + R * 0.30f, R * 0.02f, 0xAA5D4037);
                fo(c, bx - R * 0.22f, by - R * 0.30f, bx + R * 0.22f, by + R * 0.30f, 0xFFE91E63);
                fo(c, bx - R * 0.10f, by - R * 0.20f, bx - R * 0.02f, by - R * 0.10f, 0x88FFFFFF);
                break;
            }
            case P_TEDDY: {
                float tx = cx - R * 0.95f, ty = floorY - R * 0.28f;
                fc(c, tx - R * 0.16f, ty - R * 0.30f, R * 0.09f, 0xFF8D6E63);
                fc(c, tx + R * 0.16f, ty - R * 0.30f, R * 0.09f, 0xFF8D6E63);
                fc(c, tx, ty - R * 0.16f, R * 0.24f, 0xFFA1887F);
                fo(c, tx - R * 0.20f, ty - R * 0.02f, tx + R * 0.20f, ty + R * 0.34f, 0xFF8D6E63);
                fc(c, tx - R * 0.07f, ty - R * 0.18f, R * 0.030f, 0xFF3E2723);
                fc(c, tx + R * 0.07f, ty - R * 0.18f, R * 0.030f, 0xFF3E2723);
                fc(c, tx, ty - R * 0.10f, R * 0.035f, 0xFF3E2723);
                break;
            }
            case P_DRUM: {
                float dy = bodyCy + R * 0.52f;
                fr(c, cx - R * 0.40f, dy - R * 0.22f, cx + R * 0.40f, dy + R * 0.22f, R * 0.08f, 0xFFFDD835);
                fo(c, cx - R * 0.40f, dy - R * 0.30f, cx + R * 0.40f, dy - R * 0.10f, 0xFFFFF176);
                ln(c, cx - R * 0.40f, dy - R * 0.05f, cx + R * 0.40f, dy - R * 0.05f, R * 0.03f, 0xFFEF6C00);
                ln(c, handLX, handLY, cx - R * 0.18f, dy - R * 0.14f, R * 0.035f, 0xFF8D6E63);
                ln(c, handRX, handRY, cx + R * 0.18f, dy - R * 0.14f, R * 0.035f, 0xFF8D6E63);
                break;
            }
            case P_GUITAR: {
                float gx = cx - R * 0.55f, gy = bodyCy + R * 0.25f;
                fo(c, gx - R * 0.28f, gy - R * 0.22f, gx + R * 0.28f, gy + R * 0.26f, 0xFFD2691E);
                fc(c, gx, gy + R * 0.02f, R * 0.10f, 0xFF3E2723);
                ln(c, gx + R * 0.16f, gy - R * 0.10f, handRX - R * 0.05f, handRY - R * 0.20f, R * 0.08f, 0xFF8D6E63);
                for (int i = 0; i < 3; i++) {
                    ln(c, gx - R * 0.10f, gy + R * 0.02f + i * R * 0.03f,
                            handRX - R * 0.05f, handRY - R * 0.18f + i * R * 0.02f, R * 0.010f, 0xCCFFFFFF);
                }
                break;
            }
            case P_BOOK: {
                float bx = cx, by = bodyCy + R * 0.30f;
                path.reset();
                path.moveTo(bx, by - R * 0.16f);
                path.lineTo(bx - R * 0.42f, by - R * 0.24f);
                path.lineTo(bx - R * 0.42f, by + R * 0.16f);
                path.lineTo(bx, by + R * 0.26f);
                path.close();
                fill.setColor(0xFFFFFDE7);
                c.drawPath(path, fill);
                path.reset();
                path.moveTo(bx, by - R * 0.16f);
                path.lineTo(bx + R * 0.42f, by - R * 0.24f);
                path.lineTo(bx + R * 0.42f, by + R * 0.16f);
                path.lineTo(bx, by + R * 0.26f);
                path.close();
                c.drawPath(path, fill);
                for (int i = 0; i < 3; i++) {
                    ln(c, bx - R * 0.34f, by - R * 0.10f + i * R * 0.10f,
                            bx - R * 0.08f, by - R * 0.06f + i * R * 0.10f, R * 0.014f, 0xFFBDBDBD);
                    ln(c, bx + R * 0.08f, by - R * 0.06f + i * R * 0.10f,
                            bx + R * 0.34f, by - R * 0.10f + i * R * 0.10f, R * 0.014f, 0xFFBDBDBD);
                }
                break;
            }
            case P_CRAYON: {
                fr(c, cx + R * 0.35f, floorY - R * 0.05f, cx + R * 1.15f, floorY + R * 0.22f, R * 0.03f, 0xFFFFFFFF);
                for (int i = 0; i < 3; i++) {
                    ln(c, cx + R * 0.45f, floorY + R * 0.04f + i * R * 0.06f,
                            cx + R * 1.05f, floorY + R * 0.02f + i * R * 0.06f, R * 0.014f, 0xFF90CAF9);
                }
                float cxx = handRX + (float) Math.sin(t * 5f) * R * 0.10f;
                float cyy = handRY + (float) Math.cos(t * 5f) * R * 0.06f;
                fr(c, cxx - R * 0.035f, cyy - R * 0.20f, cxx + R * 0.035f, cyy + R * 0.06f, R * 0.02f, 0xFFFF5252);
                break;
            }
            case P_CAR: {
                float px = cx + R * 0.45f + (float) Math.sin(t * 2f) * R * 0.45f;
                float py = floorY - R * 0.10f;
                fr(c, px - R * 0.30f, py - R * 0.20f, px + R * 0.30f, py, R * 0.08f, 0xFF1E88E5);
                fr(c, px - R * 0.16f, py - R * 0.32f, px + R * 0.16f, py - R * 0.18f, R * 0.05f, 0xFF90CAF9);
                fc(c, px - R * 0.18f, py, R * 0.07f, 0xFF212121);
                fc(c, px + R * 0.18f, py, R * 0.07f, 0xFF212121);
                break;
            }
            case P_PHONE: {
                float px = handRX - R * 0.02f, py = handRY - R * 0.12f;
                fr(c, px - R * 0.10f, py - R * 0.26f, px + R * 0.10f, py + R * 0.16f, R * 0.05f, 0xFF263238);
                fr(c, px - R * 0.075f, py - R * 0.21f, px + R * 0.075f, py + R * 0.10f, R * 0.02f, 0xFF81D4FA);
                break;
            }
            case P_CAMERA: {
                float px = cx, py = bodyCy + R * 0.10f;
                fr(c, px - R * 0.28f, py - R * 0.18f, px + R * 0.28f, py + R * 0.18f, R * 0.06f, 0xFF37474F);
                fc(c, px, py, R * 0.11f, 0xFF90CAF9);
                fc(c, px, py, R * 0.06f, 0xFF1E88E5);
                fc(c, px + R * 0.20f, py - R * 0.12f, R * 0.03f, 0xFFFFEB3B);
                break;
            }
            case P_ICECREAM: {
                float ix = handRX, iy = handRY - R * 0.10f;
                path.reset();
                path.moveTo(ix - R * 0.10f, iy);
                path.lineTo(ix + R * 0.10f, iy);
                path.lineTo(ix, iy + R * 0.30f);
                path.close();
                fill.setColor(0xFFD2A24C);
                c.drawPath(path, fill);
                fc(c, ix, iy - R * 0.10f, R * 0.13f, 0xFFFFCC80);
                fc(c, ix, iy - R * 0.24f, R * 0.11f, 0xFFF48FB1);
                break;
            }
            case P_BANANA: {
                float bx = handRX, by = handRY;
                path.reset();
                path.moveTo(bx - R * 0.16f, by - R * 0.10f);
                path.quadTo(bx, by + R * 0.22f, bx + R * 0.18f, by - R * 0.06f);
                path.quadTo(bx, by + R * 0.10f, bx - R * 0.16f, by - R * 0.10f);
                path.close();
                fill.setColor(0xFFFFE082);
                c.drawPath(path, fill);
                break;
            }
            case P_APPLE: {
                fc(c, handRX, handRY - R * 0.05f, R * 0.14f, 0xFFE53935);
                ln(c, handRX, handRY - R * 0.18f, handRX + R * 0.03f, handRY - R * 0.26f, R * 0.025f, 0xFF6D4C41);
                fc(c, handRX + R * 0.07f, handRY - R * 0.24f, R * 0.05f, 0xFF43A047);
                break;
            }
            case P_CANDY: {
                float bx = handRX, by = handRY - R * 0.06f;
                fc(c, bx, by, R * 0.12f, 0xFFFF4081);
                path.reset();
                path.moveTo(bx - R * 0.12f, by);
                path.lineTo(bx - R * 0.26f, by - R * 0.10f);
                path.lineTo(bx - R * 0.26f, by + R * 0.10f);
                path.close();
                fill.setColor(0xFFFF80AB);
                c.drawPath(path, fill);
                path.reset();
                path.moveTo(bx + R * 0.12f, by);
                path.lineTo(bx + R * 0.26f, by - R * 0.10f);
                path.lineTo(bx + R * 0.26f, by + R * 0.10f);
                path.close();
                c.drawPath(path, fill);
                break;
            }
            case P_GLASS: {
                float gx = handRX, gy = handRY - R * 0.05f;
                fr(c, gx - R * 0.11f, gy - R * 0.18f, gx + R * 0.11f, gy + R * 0.14f, R * 0.04f, 0x88B3E5FC);
                fr(c, gx - R * 0.09f, gy - R * 0.02f, gx + R * 0.09f, gy + R * 0.12f, R * 0.03f, 0xFF4FC3F7);
                break;
            }
            case P_LADOO: {
                fc(c, handRX, handRY - R * 0.04f, R * 0.13f, 0xFFFFB300);
                fc(c, handRX - R * 0.04f, handRY - R * 0.08f, R * 0.03f, 0xFFFFE082);
                fc(c, handRX + R * 0.05f, handRY, R * 0.025f, 0xFFFFE082);
                break;
            }
            case P_BRUSH: {
                float bx = handRX - R * 0.05f, by = handRY - R * 0.10f;
                ln(c, bx, by, bx - R * 0.02f, by - R * 0.22f, R * 0.05f, 0xFF4FC3F7);
                fr(c, bx - R * 0.06f, by - R * 0.34f, bx + R * 0.06f, by - R * 0.22f, R * 0.02f, 0xFFECEFF1);
                break;
            }
            case P_MILK: {
                float k = act == Acts.MILK ? Math.min(1f, progress() * 1.1f) : 0f;
                float bx = handRX + (cx - handRX) * 0.55f * k;
                float by = handRY - R * 0.35f * k;
                c.save();
                c.rotate(-30f * k, bx, by);
                fr(c, bx - R * 0.12f, by - R * 0.30f, bx + R * 0.12f, by + R * 0.22f, R * 0.06f, 0x66FFFFFF);
                float level = -R * 0.30f + (R * 0.52f) * k;
                fr(c, bx - R * 0.10f, by + level, bx + R * 0.10f, by + R * 0.20f, R * 0.05f, 0xFFFDFDF7);
                fr(c, bx - R * 0.07f, by - R * 0.40f, bx + R * 0.07f, by - R * 0.28f, R * 0.02f, 0xFFFF7043);
                fr(c, bx - R * 0.09f, by - R * 0.06f, bx + R * 0.09f, by + R * 0.06f, R * 0.02f, 0xFF4FC3F7);
                c.restore();
                break;
            }
            case P_SWING: {
                float top = floorY - R * 2.6f;
                ln(c, cx - R * 0.55f, top, cx - R * 0.45f, floorY - R * 0.28f, R * 0.03f, 0xFF8D6E63);
                ln(c, cx + R * 0.55f, top, cx + R * 0.45f, floorY - R * 0.28f, R * 0.03f, 0xFF8D6E63);
                fr(c, cx - R * 0.55f, floorY - R * 0.30f, cx + R * 0.55f, floorY - R * 0.20f, R * 0.04f, 0xFFA1887F);
                break;
            }
            case P_BICYCLE: {
                float wy = floorY - R * 0.10f;
                float wx1 = cx - R * 0.62f, wx2 = cx + R * 0.62f;
                sc(c, wx1, wy, R * 0.24f, R * 0.045f, 0xFF37474F);
                sc(c, wx2, wy, R * 0.24f, R * 0.045f, 0xFF37474F);
                float ang = t * 6f;
                for (int i = 0; i < 4; i++) {
                    double a = ang + i * Math.PI / 4;
                    ln(c, wx1 - (float) Math.cos(a) * R * 0.22f, wy - (float) Math.sin(a) * R * 0.22f,
                            wx1 + (float) Math.cos(a) * R * 0.22f, wy + (float) Math.sin(a) * R * 0.22f,
                            R * 0.012f, 0x9937474F);
                    ln(c, wx2 - (float) Math.cos(a) * R * 0.22f, wy - (float) Math.sin(a) * R * 0.22f,
                            wx2 + (float) Math.cos(a) * R * 0.22f, wy + (float) Math.sin(a) * R * 0.22f,
                            R * 0.012f, 0x9937474F);
                }
                ln(c, wx1, wy, cx, wy - R * 0.34f, R * 0.045f, 0xFF546E7A);
                ln(c, wx2, wy, cx, wy - R * 0.34f, R * 0.045f, 0xFF546E7A);
                ln(c, wx1, wy, cx - R * 0.05f, wy, R * 0.045f, 0xFF546E7A);
                break;
            }
            case P_ROPE: {
                float s = (float) Math.sin(t * 4f);
                path.reset();
                path.moveTo(handLX, handLY);
                path.quadTo(cx + s * R * 1.1f, floorY + R * 0.05f, handRX, handRY);
                stroke.setColor(0xFF7E57C2);
                stroke.setStrokeWidth(R * 0.035f);
                c.drawPath(path, stroke);
                break;
            }
            case P_COMB: {
                float bx = handRX - R * 0.02f, by = handRY - R * 0.14f;
                fr(c, bx - R * 0.14f, by - R * 0.05f, bx + R * 0.14f, by + R * 0.05f, R * 0.03f, 0xFF00897B);
                for (int i = 0; i < 5; i++) {
                    ln(c, bx - R * 0.12f + i * R * 0.06f, by + R * 0.05f,
                            bx - R * 0.12f + i * R * 0.06f, by + R * 0.16f, R * 0.018f, 0xFF00897B);
                }
                break;
            }
            case P_MIRROR: {
                float mx = cx - R * 0.95f, my = bodyCy + R * 0.10f;
                fo(c, mx - R * 0.26f, my - R * 0.34f, mx + R * 0.26f, my + R * 0.34f, 0xFFCFD8DC);
                fo(c, mx - R * 0.20f, my - R * 0.28f, mx + R * 0.20f, my + R * 0.28f, 0xFFE3F2FD);
                ln(c, mx - R * 0.12f, my - R * 0.18f, mx + R * 0.02f, my + R * 0.06f, R * 0.02f, 0x88FFFFFF);
                break;
            }
            case P_UMBRELLA: {
                float uy = headCy - R * 1.10f;
                ln(c, handRX, handRY, cx + R * 0.30f, uy + R * 0.25f, R * 0.03f, 0xFF6D4C41);
                path.reset();
                path.arcTo(new RectF(cx - R * 1.00f, uy - R * 0.55f, cx + R * 1.00f, uy + R * 0.55f), 180f, 180f, true);
                path.close();
                fill.setColor(0xFFE53935);
                c.drawPath(path, fill);
                for (int i = -1; i <= 1; i++) {
                    ln(c, cx + i * R * 0.45f, uy + R * 0.02f, cx + i * R * 0.30f, uy + R * 0.55f, R * 0.02f, 0x88FFFFFF);
                }
                break;
            }
            default:
                break;
        }
    }

    // ----------------------------------------------------------------- effects

    private void drawEffects(Canvas c, Pose pose, float t, float p) {
        if ((pose.fx & FX_ZZZ) != 0) {
            text.setColor(0xCC3F51B5);
            for (int i = 0; i < 3; i++) {
                float k = (t * 0.45f + i * 0.33f) % 1f;
                text.setTextSize(headR * (0.20f + 0.10f * i));
                text.setAlpha((int) (200 * (1f - k)));
                c.drawText("Z", cx + headR * (0.85f + 0.35f * i + k * 0.4f),
                        headCy - headR * (0.85f + k * 1.3f), text);
            }
            text.setAlpha(255);
        }
        if ((pose.fx & FX_TEARS) != 0) {
            for (int s = -1; s <= 1; s += 2) {
                for (int i = 0; i < 2; i++) {
                    float k = (t * 1.1f + i * 0.5f) % 1f;
                    fc(c, cx + s * headR * 0.34f, headCy + headR * 0.18f + k * headR * 1.1f,
                            headR * 0.085f * (1f - 0.35f * k), 0xFF64B5F6);
                }
            }
        }
        if ((pose.fx & FX_STEAM) != 0) {
            for (int i = 0; i < 2; i++) {
                float k = (t * 0.9f + i * 0.5f) % 1f;
                fill.setColor(0x66FF5252);
                fill.setAlpha((int) (110 * (1f - k)));
                c.drawCircle(cx + (i == 0 ? -1 : 1) * headR * 0.95f,
                        headCy - headR * (0.75f + k * 0.8f), headR * 0.14f * (1f - 0.4f * k), fill);
                fill.setAlpha(255);
            }
            text.setColor(0xFFFF3D00);
            text.setTextSize(headR * 0.34f);
            c.drawText("!", cx + headR * 1.15f, headCy - headR * 0.55f, text);
        }
        if ((pose.fx & FX_HEARTS) != 0) {
            for (int i = 0; i < 3; i++) {
                float k = (t * 0.55f + i * 0.33f) % 1f;
                drawHeart(c, cx + (i - 1) * headR * 0.75f, headCy - headR * (1.05f + k * 0.9f),
                        headR * 0.16f * (1f - 0.5f * k), 0xFFFF6F91, (int) (220 * (1f - k)));
            }
        }
        if ((pose.fx & FX_NOTES) != 0) {
            text.setColor(0xFF7E57C2);
            for (int i = 0; i < 3; i++) {
                float k = (t * 0.6f + i * 0.33f) % 1f;
                text.setTextSize(headR * 0.26f);
                text.setAlpha((int) (230 * (1f - k)));
                c.drawText(i % 2 == 0 ? "\u266A" : "\u266B",
                        cx + (i - 1) * headR * 0.9f + (float) Math.sin(k * 6f) * headR * 0.12f,
                        headCy - headR * (0.9f + k * 1.1f), text);
            }
            text.setAlpha(255);
        }
        if ((pose.fx & FX_STARS) != 0) {
            for (int i = 0; i < 4; i++) {
                float k = (t * 0.8f + i * 0.25f) % 1f;
                float a = (float) (i * Math.PI / 2 + t);
                float sx = cx + (float) Math.cos(a) * headR * 1.15f;
                float sy = headCy + (float) Math.sin(a) * headR * 0.9f;
                star(c, sx, sy, headR * 0.10f * (1f - k * 0.5f), 0xFFFFD54F, (int) (230 * (1f - k)));
            }
        }
        if ((pose.fx & FX_BUBBLES) != 0) {
            for (int i = 0; i < 5; i++) {
                float k = (t * 0.55f + i * 0.2f) % 1f;
                float bx = cx + (i - 2) * headR * 0.35f + (float) Math.sin(k * 8f + i) * headR * 0.1f;
                float by = headCy + headR * 0.5f - k * headR * 1.9f;
                float br = headR * (0.09f + 0.05f * (i % 3)) * (0.6f + 0.4f * (1f - k));
                sc(c, bx, by, br, headR * 0.012f, 0xAAFFFFFF);
                fc(c, bx - br * 0.3f, by - br * 0.3f, br * 0.25f, 0xCCFFFFFF);
            }
        }
        if ((pose.fx & FX_DROPS) != 0) {
            for (int i = 0; i < 6; i++) {
                float k = (t * 1.4f + i * 0.18f) % 1f;
                float ang = -0.6f + i * 0.22f;
                float dx = cx + headR * 0.9f + (float) Math.cos(ang) * k * headR * 1.2f;
                float dy = headCy - headR * 0.3f + (float) Math.sin(ang) * k * headR * 0.9f;
                fc(c, dx, dy, headR * 0.05f * (1f - k * 0.5f), 0xCC81D4FA);
            }
        }
        if ((pose.fx & FX_RAIN) != 0) {
            for (int i = 0; i < 12; i++) {
                float k = (t * 1.6f + i * 0.13f) % 1f;
                float rx = w * ((i * 0.083f + 0.03f) % 1f);
                float ry = k * floorY;
                ln(c, rx, ry, rx - R * 0.05f, ry + R * 0.16f, R * 0.018f, 0x8856CCF2);
            }
        }
        if ((pose.fx & FX_DUST) != 0) {
            for (int i = 0; i < 5; i++) {
                float k = (t * 1.2f + i * 0.2f) % 1f;
                float dx = cx + (i - 2) * R * 0.30f * (1f + k);
                fill.setColor(0xFFE0C9A6);
                fill.setAlpha((int) (180 * (1f - k)));
                c.drawCircle(dx, floorY - R * 0.04f - k * R * 0.18f, R * 0.07f * (1f - k * 0.5f), fill);
                fill.setAlpha(255);
            }
        }
        if ((pose.fx & FX_QUESTION) != 0) {
            text.setColor(0xFF3949AB);
            text.setTextSize(headR * 0.42f);
            c.drawText("?", cx + headR * 1.05f, headCy - headR * (0.95f + (float) Math.sin(t * 2f) * 0.08f), text);
        }
        if ((pose.fx & FX_SPARKLE) != 0) {
            for (int i = 0; i < 5; i++) {
                float k = (t * 1.1f + i * 0.2f) % 1f;
                float sx = cx + (float) Math.sin(i * 2.3f) * R * 1.3f;
                float sy = headCy - R * 0.6f + (float) Math.cos(i * 1.7f) * R * 0.9f;
                star(c, sx, sy, R * 0.06f * (1f - k * 0.6f), 0xFFFFFFFF, (int) (200 * (1f - k)));
            }
        }
    }

    // ------------------------------------------------------------- primitives

    private void fc(Canvas c, float x, float y, float r, int color) {
        fill.setColor(color);
        c.drawCircle(x, y, r, fill);
    }

    private void fo(Canvas c, float l, float t, float r, float b, int color) {
        rf.set(l, t, r, b);
        fill.setColor(color);
        c.drawOval(rf, fill);
    }

    private void fr(Canvas c, float l, float t, float r, float b, float rad, int color) {
        rf.set(l, t, r, b);
        fill.setColor(color);
        c.drawRoundRect(rf, rad, rad, fill);
    }

    private void ln(Canvas c, float x1, float y1, float x2, float y2, float wd, int color) {
        stroke.setColor(color);
        stroke.setStrokeWidth(wd);
        c.drawLine(x1, y1, x2, y2, stroke);
    }

    private void sc(Canvas c, float x, float y, float r, float wd, int color) {
        stroke.setColor(color);
        stroke.setStrokeWidth(wd);
        c.drawCircle(x, y, r, stroke);
    }

    private void drawHeart(Canvas c, float x, float y, float s, int color, int alpha) {
        fill.setColor(color);
        fill.setAlpha(Math.max(0, Math.min(255, alpha)));
        path.reset();
        path.moveTo(x, y + s * 0.85f);
        path.cubicTo(x - s * 1.6f, y - s * 0.35f, x - s * 0.55f, y - s * 1.35f, x, y - s * 0.45f);
        path.cubicTo(x + s * 0.55f, y - s * 1.35f, x + s * 1.6f, y - s * 0.35f, x, y + s * 0.85f);
        c.drawPath(path, fill);
        fill.setAlpha(255);
    }

    private void star(Canvas c, float x, float y, float s, int color, int alpha) {
        fill.setColor(color);
        fill.setAlpha(Math.max(0, Math.min(255, alpha)));
        path.reset();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rr = (i % 2 == 0) ? s : s * 0.45f;
            float px = x + (float) Math.cos(a) * rr;
            float py = y + (float) Math.sin(a) * rr;
            if (i == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
        }
        path.close();
        c.drawPath(path, fill);
        fill.setAlpha(255);
    }

    // ------------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                downTime = SystemClock.uptimeMillis();
                longFired = false;
                return true;
            case MotionEvent.ACTION_UP: {
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                long dt = SystemClock.uptimeMillis() - downTime;
                if (longFired) return true;
                if (Math.abs(dy) > 70f * density && Math.abs(dy) > Math.abs(dx) && dt < 700) {
                    if (listener != null) {
                        if (dy < 0) listener.onSwipeUp();
                        else listener.onSwipeDown();
                    }
                } else if (dt < 320 && Math.abs(dx) < 40f * density && Math.abs(dy) < 40f * density) {
                    if (listener != null) listener.onPetTapped();
                } else if (dt >= 600) {
                    if (listener != null) listener.onPetLongPressed();
                }
                return true;
            }
            default:
                return true;
        }
    }
}
