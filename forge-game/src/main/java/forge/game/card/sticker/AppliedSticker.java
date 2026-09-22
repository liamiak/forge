package forge.game.card.sticker;

import forge.game.card.Card;

/**
 * A sticker that is on an object, with the two things placing it decided: when, and - for a name
 * sticker - where in the name its word sits (CR 123.6b).
 * <p>
 * The position is a word index, not a character offset, and is remembered as the object moves
 * between public zones. If the name later has fewer words than that, the word goes on the end
 * instead (CR 123.6c).
 */
public class AppliedSticker {
    private final Sticker sticker;
    private final long timestamp;
    private final int namePosition;

    public AppliedSticker(Sticker sticker, long timestamp) {
        this(sticker, timestamp, 0);
    }

    public AppliedSticker(Sticker sticker, long timestamp, int namePosition) {
        this.sticker = sticker;
        this.timestamp = timestamp;
        this.namePosition = namePosition;
    }

    public Sticker getSticker() {
        return sticker;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /** How many of the object's words precede this name sticker's word. */
    public int getNamePosition() {
        return namePosition;
    }

    public StickerKind getKind() {
        return sticker.getKind();
    }

    /**
     * Applies this sticker to the card it is on. Name stickers are not applied one at a time:
     * every name sticker on a card contributes to one name, so the card recomputes the whole
     * name instead - see {@link Card#recomputeStickerName}.
     */
    public void applyEffect(Card c) {
        switch (sticker.getKind()) {
            case PT -> c.addNewPT(sticker.getPower(), sticker.getToughness(), timestamp, 0);
            case NAME -> c.recomputeStickerName();
            // Ability stickers are granted once their sheet script carries a Forge ability;
            // art stickers only ever act as a marker (CR 123.9).
            default -> {
            }
        }
    }

    @Override
    public String toString() {
        return sticker.toString();
    }
}
