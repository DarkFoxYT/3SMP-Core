package net.dark.threecore.screentext;

import java.util.List;

public final class ScreenTextBuilder {
    private String id = "screen_text";
    private String content = "";
    private Position position = Position.TOP_CENTER;
    private double x = 0.5D;
    private double y = 0.5D;
    private int offsetX;
    private int offsetY;
    private Layer layer = Layer.HUD;
    private int priority;
    private int zIndex;
    private ScreenTextStyle style = ScreenTextStyle.builder().build();
    private ScreenTextAnimation animation = ScreenTextAnimation.none();
    private long durationMillis;
    private int refreshTicks = 10;
    private ScreenTextType type = ScreenTextType.TIMED;
    private String condition = "";

    public ScreenTextBuilder id(String id) {
        this.id = id == null || id.isBlank() ? "screen_text" : id;
        return this;
    }

    public ScreenTextBuilder content(String content) {
        this.content = content == null ? "" : content;
        return this;
    }

    public ScreenTextBuilder content(List<String> content) {
        this.content = content == null ? "" : String.join("\n", content);
        return this;
    }

    public ScreenTextBuilder position(Position position) {
        this.position = position == null ? Position.TOP_CENTER : position;
        return this;
    }

    public ScreenTextBuilder absolute(double x, double y) {
        this.position = Position.ABSOLUTE;
        this.x = x;
        this.y = y;
        return this;
    }

    public ScreenTextBuilder offset(int offsetX, int offsetY) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        return this;
    }

    public ScreenTextBuilder layer(Layer layer) {
        this.layer = layer == null ? Layer.HUD : layer;
        return this;
    }

    public ScreenTextBuilder priority(int priority) {
        this.priority = priority;
        return this;
    }

    public ScreenTextBuilder zIndex(int zIndex) {
        this.zIndex = zIndex;
        return this;
    }

    public ScreenTextBuilder style(ScreenTextStyle style) {
        this.style = style == null ? ScreenTextStyle.builder().build() : style;
        return this;
    }

    public ScreenTextBuilder animation(ScreenTextAnimation animation) {
        this.animation = animation == null ? ScreenTextAnimation.none() : animation;
        return this;
    }

    public ScreenTextBuilder duration(long durationMillis) {
        this.durationMillis = durationMillis;
        this.type = durationMillis > 0L ? ScreenTextType.TIMED : this.type;
        return this;
    }

    public ScreenTextBuilder refreshTicks(int refreshTicks) {
        this.refreshTicks = refreshTicks;
        return this;
    }

    public ScreenTextBuilder type(ScreenTextType type) {
        this.type = type == null ? ScreenTextType.TIMED : type;
        return this;
    }

    public ScreenTextBuilder condition(String condition) {
        this.condition = condition == null ? "" : condition;
        return this;
    }

    public ScreenText build() {
        return new ScreenText(this);
    }

    String id() {
        return id;
    }

    String content() {
        return content;
    }

    Position position() {
        return position;
    }

    double x() {
        return x;
    }

    double y() {
        return y;
    }

    int offsetX() {
        return offsetX;
    }

    int offsetY() {
        return offsetY;
    }

    Layer layer() {
        return layer;
    }

    int priority() {
        return priority;
    }

    int zIndex() {
        return zIndex;
    }

    ScreenTextStyle style() {
        return style;
    }

    ScreenTextAnimation animation() {
        return animation;
    }

    long durationMillis() {
        return durationMillis;
    }

    int refreshTicks() {
        return refreshTicks;
    }

    ScreenTextType type() {
        return type;
    }

    String condition() {
        return condition;
    }
}
