package net.dark.threecore.screentext;

public final class Animation {
    public static final ScreenTextAnimation NONE = ScreenTextAnimation.none();
    public static final ScreenTextAnimation FADE_IN = ScreenTextAnimation.of(ScreenTextAnimation.Type.FADE_IN, 400);
    public static final ScreenTextAnimation FADE_OUT = ScreenTextAnimation.of(ScreenTextAnimation.Type.FADE_OUT, 400);
    public static final ScreenTextAnimation PULSE = ScreenTextAnimation.of(ScreenTextAnimation.Type.PULSE, 1200);
    public static final ScreenTextAnimation WAVE = ScreenTextAnimation.of(ScreenTextAnimation.Type.WAVE, 1200);
    public static final ScreenTextAnimation SLIDE_LEFT = ScreenTextAnimation.of(ScreenTextAnimation.Type.SLIDE_LEFT, 500);
    public static final ScreenTextAnimation SLIDE_RIGHT = ScreenTextAnimation.of(ScreenTextAnimation.Type.SLIDE_RIGHT, 500);
    public static final ScreenTextAnimation TYPEWRITER = ScreenTextAnimation.of(ScreenTextAnimation.Type.TYPEWRITER, 1400);
    public static final ScreenTextAnimation GLOW = ScreenTextAnimation.of(ScreenTextAnimation.Type.GLOW, 1200);

    private Animation() {
    }
}
