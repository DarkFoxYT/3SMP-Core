package net.dark.threecore.screentext;

import java.util.Objects;

public final class ScreenText {
    private final String id;
    private final String content;
    private final Position position;
    private final double x;
    private final double y;
    private final int offsetX;
    private final int offsetY;
    private final Layer layer;
    private final int priority;
    private final int zIndex;
    private final ScreenTextStyle style;
    private final ScreenTextAnimation animation;
    private final long durationMillis;
    private final int refreshTicks;
    private final ScreenTextType type;
    private final String condition;

    ScreenText(ScreenTextBuilder builder) {
        this.id = Objects.requireNonNullElse(builder.id(), "screen_text");
        this.content = Objects.requireNonNullElse(builder.content(), "");
        this.position = builder.position();
        this.x = Math.max(0.0D, Math.min(1.0D, builder.x()));
        this.y = Math.max(0.0D, Math.min(1.0D, builder.y()));
        this.offsetX = builder.offsetX();
        this.offsetY = builder.offsetY();
        this.layer = builder.layer();
        this.priority = builder.priority();
        this.zIndex = builder.zIndex();
        this.style = builder.style();
        this.animation = builder.animation();
        this.durationMillis = Math.max(0L, builder.durationMillis());
        this.refreshTicks = Math.max(1, builder.refreshTicks());
        this.type = builder.type();
        this.condition = Objects.requireNonNullElse(builder.condition(), "");
    }

    public static ScreenTextBuilder builder() {
        return new ScreenTextBuilder();
    }

    public ScreenTextBuilder toBuilder() {
        return builder()
            .id(id)
            .content(content)
            .position(position)
            .absolute(x, y)
            .offset(offsetX, offsetY)
            .layer(layer)
            .priority(priority)
            .zIndex(zIndex)
            .style(style)
            .animation(animation)
            .duration(durationMillis)
            .refreshTicks(refreshTicks)
            .type(type)
            .condition(condition);
    }

    public String id() {
        return id;
    }

    public String content() {
        return content;
    }

    public Position position() {
        return position;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public int offsetX() {
        return offsetX;
    }

    public int offsetY() {
        return offsetY;
    }

    public Layer layer() {
        return layer;
    }

    public int priority() {
        return priority;
    }

    public int zIndex() {
        return zIndex;
    }

    public ScreenTextStyle style() {
        return style;
    }

    public ScreenTextAnimation animation() {
        return animation;
    }

    public long durationMillis() {
        return durationMillis;
    }

    public int refreshTicks() {
        return refreshTicks;
    }

    public ScreenTextType type() {
        return type;
    }

    public String condition() {
        return condition;
    }
}
