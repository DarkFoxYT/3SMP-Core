package net.dark.threecore.screentext;

public final class ScreenTextAnimation {
    public enum Type {
        NONE,
        FADE_IN,
        FADE_OUT,
        PULSE,
        WAVE,
        SLIDE_LEFT,
        SLIDE_RIGHT,
        TYPEWRITER,
        GLOW
    }

    public enum Easing {
        LINEAR,
        EASE_IN,
        EASE_OUT
    }

    private final Type type;
    private final long durationMillis;
    private final long delayMillis;
    private final Easing easing;

    private ScreenTextAnimation(Builder builder) {
        this.type = builder.type;
        this.durationMillis = Math.max(0L, builder.durationMillis);
        this.delayMillis = Math.max(0L, builder.delayMillis);
        this.easing = builder.easing;
    }

    public static ScreenTextAnimation none() {
        return builder().type(Type.NONE).duration(0).build();
    }

    public static ScreenTextAnimation of(Type type, long durationMillis) {
        return builder().type(type).duration(durationMillis).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Type type() {
        return type;
    }

    public long durationMillis() {
        return durationMillis;
    }

    public long delayMillis() {
        return delayMillis;
    }

    public Easing easing() {
        return easing;
    }

    public boolean animated() {
        return type != Type.NONE && durationMillis > 0L;
    }

    public static final class Builder {
        private Type type = Type.NONE;
        private long durationMillis = 0L;
        private long delayMillis = 0L;
        private Easing easing = Easing.EASE_OUT;

        public Builder type(Type type) {
            this.type = type == null ? Type.NONE : type;
            return this;
        }

        public Builder duration(long durationMillis) {
            this.durationMillis = durationMillis;
            return this;
        }

        public Builder delay(long delayMillis) {
            this.delayMillis = delayMillis;
            return this;
        }

        public Builder easing(Easing easing) {
            this.easing = easing == null ? Easing.EASE_OUT : easing;
            return this;
        }

        public ScreenTextAnimation build() {
            return new ScreenTextAnimation(this);
        }
    }
}
