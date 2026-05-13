package net.dark.threecore.screentext;

public enum Position {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    CENTER,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT,
    ABSOLUTE;

    public boolean top() {
        return this == TOP_LEFT || this == TOP_CENTER || this == TOP_RIGHT;
    }

    public boolean bottom() {
        return this == BOTTOM_LEFT || this == BOTTOM_CENTER || this == BOTTOM_RIGHT;
    }
}
