package net.dark.threecore.screentext;

public final class ScreenTextStyle {
    private final String gradient;
    private final String color;
    private final boolean bold;
    private final boolean italic;
    private final boolean underline;
    private final int letterSpacing;
    private final int lineSpacing;
    private final TextAlignment alignment;
    private final int maxWidth;
    private final double opacity;
    private final double scale;
    private final Shadow shadow;
    private final boolean rainbow;

    private ScreenTextStyle(Builder builder) {
        this.gradient = builder.gradient;
        this.color = builder.color;
        this.bold = builder.bold;
        this.italic = builder.italic;
        this.underline = builder.underline;
        this.letterSpacing = Math.max(0, builder.letterSpacing);
        this.lineSpacing = Math.max(0, builder.lineSpacing);
        this.alignment = builder.alignment;
        this.maxWidth = Math.max(0, builder.maxWidth);
        this.opacity = Math.max(0.0D, Math.min(1.0D, builder.opacity));
        this.scale = Math.max(0.1D, builder.scale);
        this.shadow = builder.shadow;
        this.rainbow = builder.rainbow;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return builder()
            .gradient(gradient)
            .color(color)
            .bold(bold)
            .italic(italic)
            .underline(underline)
            .letterSpacing(letterSpacing)
            .lineSpacing(lineSpacing)
            .alignment(alignment)
            .maxWidth(maxWidth)
            .opacity(opacity)
            .scale(scale)
            .shadow(shadow)
            .rainbow(rainbow);
    }

    public String gradient() {
        return gradient;
    }

    public String color() {
        return color;
    }

    public boolean bold() {
        return bold;
    }

    public boolean italic() {
        return italic;
    }

    public boolean underline() {
        return underline;
    }

    public int letterSpacing() {
        return letterSpacing;
    }

    public int lineSpacing() {
        return lineSpacing;
    }

    public TextAlignment alignment() {
        return alignment;
    }

    public int maxWidth() {
        return maxWidth;
    }

    public double opacity() {
        return opacity;
    }

    public double scale() {
        return scale;
    }

    public Shadow shadow() {
        return shadow;
    }

    public boolean rainbow() {
        return rainbow;
    }

    public record Shadow(boolean enabled, String color, double alpha, int offsetX, int offsetY, int blur) {
        public static Shadow disabled() {
            return new Shadow(false, "#020617", 0.0D, 1, 1, 0);
        }

        public static Shadow royalDefault() {
            return new Shadow(true, "#020617", 0.8D, 1, 1, 0);
        }
    }

    public static final class Builder {
        private String gradient = "";
        private String color = "";
        private boolean bold;
        private boolean italic;
        private boolean underline;
        private int letterSpacing;
        private int lineSpacing;
        private TextAlignment alignment = TextAlignment.CENTER;
        private int maxWidth;
        private double opacity = 1.0D;
        private double scale = 1.0D;
        private Shadow shadow = Shadow.royalDefault();
        private boolean rainbow;

        public Builder gradient(String gradient) {
            this.gradient = gradient == null ? "" : gradient;
            return this;
        }

        public Builder color(String color) {
            this.color = color == null ? "" : color;
            return this;
        }

        public Builder bold(boolean bold) {
            this.bold = bold;
            return this;
        }

        public Builder italic(boolean italic) {
            this.italic = italic;
            return this;
        }

        public Builder underline(boolean underline) {
            this.underline = underline;
            return this;
        }

        public Builder letterSpacing(int letterSpacing) {
            this.letterSpacing = letterSpacing;
            return this;
        }

        public Builder lineSpacing(int lineSpacing) {
            this.lineSpacing = lineSpacing;
            return this;
        }

        public Builder alignment(TextAlignment alignment) {
            this.alignment = alignment == null ? TextAlignment.CENTER : alignment;
            return this;
        }

        public Builder maxWidth(int maxWidth) {
            this.maxWidth = maxWidth;
            return this;
        }

        public Builder opacity(double opacity) {
            this.opacity = opacity;
            return this;
        }

        public Builder scale(double scale) {
            this.scale = scale;
            return this;
        }

        public Builder shadow(boolean enabled) {
            this.shadow = enabled ? Shadow.royalDefault() : Shadow.disabled();
            return this;
        }

        public Builder shadow(String color, double alpha, int offsetX, int offsetY, int blur) {
            this.shadow = new Shadow(true, color == null || color.isBlank() ? "#020617" : color, alpha, offsetX, offsetY, blur);
            return this;
        }

        public Builder shadow(Shadow shadow) {
            this.shadow = shadow == null ? Shadow.disabled() : shadow;
            return this;
        }

        public Builder rainbow(boolean rainbow) {
            this.rainbow = rainbow;
            return this;
        }

        public ScreenTextStyle build() {
            return new ScreenTextStyle(this);
        }
    }
}
