package com.spdev.mypetgolu;

/**
 * Catalogue of everything Golu can do. Index == activity id.
 * 50 activities across five categories.
 */
public final class Acts {

    public static final int IDLE = 0;

    // moods
    public static final int SMILE = 1, LAUGH = 2, CRY = 3, ANGRY = 4, SLEEP = 5, WAKEUP = 6,
            NAMASTE = 7, DANCE = 8, SING = 9, SHY = 10, SURPRISED = 11, LOVE = 12;
    // play
    public static final int BALL = 13, KICK = 14, JUMP = 15, SPIN = 16, PEEKABOO = 17,
            SWING = 18, KITE = 19, BUBBLES = 20, BALLOON = 21, BICYCLE = 22, ROPE = 23,
            TICKLE = 24, HIDE = 25, RUN = 26;
    // food
    public static final int MILK = 27, ICECREAM = 28, BANANA = 29, APPLE = 30, CANDY = 31,
            WATER = 32, LADOO = 33, TEETH = 34;
    // toys and things
    public static final int TEDDY = 35, DRUM = 36, GUITAR = 37, BOOK = 38, DRAW = 39,
            CAR = 40, PHONE = 41, PHOTO = 42;
    // fun
    public static final int SNEEZE = 43, HICCUP = 44, SCRATCH = 45, COMB = 46, BATHE = 47,
            MIRROR = 48, RAIN = 49, YOGA = 50;

    public static final int CAT_MOOD = 0, CAT_PLAY = 1, CAT_FOOD = 2, CAT_TOY = 3, CAT_FUN = 4;
    public static final String[] CAT_NAME = {"Moods", "Play", "Food", "Toys & Things", "Fun"};

    public static final String[] LABEL = {
            "Idle", "Smile", "Laugh", "Cry", "Angry", "Sleep", "Wake Up",
            "Namaste", "Dance", "Sing", "Shy", "Surprised", "Love",
            "Play Ball", "Kick Ball", "Jump", "Spin", "Peekaboo", "Swing", "Kite", "Bubbles",
            "Balloon", "Bicycle", "Skip Rope", "Tickle", "Hide", "Run",
            "Drink Milk", "Ice Cream", "Banana", "Apple", "Candy", "Water", "Ladoo", "Brush Teeth",
            "Teddy Hug", "Drum", "Guitar", "Read Book", "Draw", "Toy Car", "Phone Call", "Photo",
            "Sneeze", "Hiccup", "Scratch Head", "Comb Hair", "Bathe", "Mirror", "Rain Dance", "Yoga"
    };

    public static final int[] CAT = {
            CAT_MOOD,
            CAT_MOOD, CAT_MOOD, CAT_MOOD, CAT_MOOD, CAT_MOOD, CAT_MOOD, CAT_MOOD, CAT_MOOD,
            CAT_MOOD, CAT_MOOD, CAT_MOOD, CAT_MOOD,
            CAT_PLAY, CAT_PLAY, CAT_PLAY, CAT_PLAY, CAT_PLAY, CAT_PLAY, CAT_PLAY, CAT_PLAY,
            CAT_PLAY, CAT_PLAY, CAT_PLAY, CAT_PLAY, CAT_PLAY, CAT_PLAY,
            CAT_FOOD, CAT_FOOD, CAT_FOOD, CAT_FOOD, CAT_FOOD, CAT_FOOD, CAT_FOOD, CAT_FOOD,
            CAT_TOY, CAT_TOY, CAT_TOY, CAT_TOY, CAT_TOY, CAT_TOY, CAT_TOY, CAT_TOY,
            CAT_FUN, CAT_FUN, CAT_FUN, CAT_FUN, CAT_FUN, CAT_FUN, CAT_FUN, CAT_FUN
    };

    /** how long one round of the activity lasts, in milliseconds */
    public static final int[] DUR = {
            0,
            2600, 3000, 4000, 3200, 600000, 2600,
            3000, 7200, 7200, 3200, 2600, 3400,
            5200, 3800, 2600, 4000, 3400, 5200, 6000, 5200,
            4200, 5200, 5200, 3400, 3400, 5200,
            4600, 4800, 4600, 4600, 4200, 4200, 4600, 5200,
            4800, 6000, 6000, 5600, 6000, 5200, 5200, 3000,
            3000, 3200, 3800, 4600, 6000, 4600, 7000, 7000
    };

    /** short English / Hindi lines spoken by the text to speech engine */
    public static final String[] SAY_EN = {
            "I am here!",
            "I am smiling.", "Hahaha!", "Waaah!", "Grr, stop it!", "Good night...", "Good morning!",
            "Namaste!", "Let's dance!", "La la la...", "Hehe, shy.", "Oh! What is that?", "I love you!",
            "Catch the ball!", "Goal!", "Hoppity hop!", "Wheee!", "Peekaboo!", "Swing me higher!",
            "Look, my kite!", "So many bubbles!", "My balloon!", "Ring ring, bicycle!", "Jump jump jump!",
            "That tickles!", "Where am I?", "Running running!",
            "Mmm, milk!", "Ice cream, yum!", "Banana time!", "An apple a day!", "Sweet candy!",
            "Water, water!", "Ladoo, my favourite!", "Brushing my teeth!",
            "My teddy!", "Dum dum dum!", "Strum strum!", "Once upon a time...", "I am drawing a sun!",
            "Vroom vroom!", "Hello? Who is it?", "Cheese! Click!",
            "Achoo!", "Hic!", "Hmm, let me think.", "Combing my hair.", "Splish splash!", "Looking good!",
            "Rain rain, come again!", "Breathe in, breathe out."
    };

    public static final String[] SAY_HI = {
            "मैं यहाँ हूँ!",
            "मैं मुस्कुरा रहा हूँ।", "हाहाहा!", "उआँ!", "गर्र, बंद करो!", "शुभ रात्रि...", "गुड मॉर्निंग!",
            "नमस्ते!", "चलो नाचते हैं!", "ला ला ला...", "हीही, शरमा गया।", "अरे! वो क्या है?", "मैं तुमसे प्यार करता हूँ!",
            "गेंद पकड़ो!", "गोल!", "टप टप टप!", "व्ही!", "पीकाबू!", "झूला और ऊँचा!",
            "देखो, मेरी पतंग!", "इतने सारे बुलबुले!", "मेरा गुब्बारा!", "ट्रिंग ट्रिंग, साइकिल!", "कूद कूद कूद!",
            "गुदगुदी हो रही है!", "मैं कहाँ हूँ?", "दौड़ रहा हूँ!",
            "म्म्म, दूध!", "आइसक्रीम, यम!", "केला टाइम!", "रोज़ एक सेब!", "मीठी कैंडी!",
            "पानी, पानी!", "लड्डू, मेरा पसंदीदा!", "दाँत साफ कर रहा हूँ!",
            "मेरा टेडी!", "ढम ढम ढम!", "टन टन टन!", "एक था राजा...", "मैं सूरज बना रहा हूँ!",
            "व्रूम व्रूम!", "हैलो? कौन है?", "चीज़! क्लिक!",
            "आच्छू!", "हिच!", "हम्म, सोचने दो।", "बाल बना रहा हूँ।", "छप छप!", "बहुत अच्छा लग रहा हूँ!",
            "बारिश आए, फिर आए!", "साँस लो, छोड़ो।"
    };

    public static int[] byCategory(int cat) {
        int n = 0;
        for (int i = 1; i < CAT.length; i++) if (CAT[i] == cat) n++;
        int[] out = new int[n];
        int k = 0;
        for (int i = 1; i < CAT.length; i++) if (CAT[i] == cat) out[k++] = i;
        return out;
    }

    private Acts() {
    }
}
