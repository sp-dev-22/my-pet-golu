package com.spdev.mypetgolu;

/**
 * Activity ids and durations for PetView's pose engine.
 * The Play actions were removed in v0.4.0 (Golu is now a calm study buddy -
 * only the idle pose is used), but PetView still draws from these ids and
 * durations, so the catalogue stays.
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

    private Acts() {
    }
}
