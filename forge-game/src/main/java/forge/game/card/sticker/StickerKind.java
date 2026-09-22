package forge.game.card.sticker;

/**
 * The four kinds of sticker - CR 123.1.
 */
public enum StickerKind {
    /** Adds a word to the object's name - CR 123.6. */
    NAME,
    /** No effect on play beyond being a marker other effects can identify - CR 123.9. */
    ART,
    /** Grants the object the ability printed on it - CR 123.7. */
    ABILITY,
    /** Sets the object's power and toughness - CR 123.8. */
    PT;

    public static StickerKind smartValueOf(String value) {
        for (StickerKind k : values()) {
            if (k.name().equalsIgnoreCase(value)) {
                return k;
            }
        }
        return null;
    }
}
