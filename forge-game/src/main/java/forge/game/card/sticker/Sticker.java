package forge.game.card.sticker;

import java.util.Map;

import forge.game.card.Card;

/**
 * One sticker on a sticker sheet.
 * <p>
 * A sticker is identified by the sheet it came from and its slot on that sheet, never by its
 * text: CR 123.3a makes two stickers distinct even when they read the same.
 */
public class Sticker {
    private final Card sheet;
    private final String slot;
    private final StickerKind kind;
    private final int tickets;
    private final String word;
    private final String text;
    private final String abilitySVar;
    private final String keywords;
    private final String triggers;
    private final int power;
    private final int toughness;

    /**
     * Reads one sticker from a sheet's SVar, in the form
     * {@code Kind$ Name | Word$ Eldrazi} or {@code Kind$ PT | Tickets$ 2 | Power$ 1 | Toughness$ 4}.
     *
     * @return the sticker, or null if the SVar does not name a valid kind.
     */
    public static Sticker parse(Card sheet, String slot, Map<String, String> params) {
        StickerKind kind = StickerKind.smartValueOf(params.get("Kind"));
        if (kind == null) {
            return null;
        }
        return new Sticker(sheet, slot, kind, params);
    }

    private Sticker(Card sheet, String slot, StickerKind kind, Map<String, String> params) {
        this.sheet = sheet;
        this.slot = slot;
        this.kind = kind;
        this.tickets = intParam(params, "Tickets", 0);
        this.word = params.get("Word");
        this.text = params.get("Text");
        this.abilitySVar = params.get("Ability");
        this.keywords = params.get("Keywords");
        this.triggers = params.get("Triggers");
        this.power = intParam(params, "Power", 0);
        this.toughness = intParam(params, "Toughness", 0);
    }

    private static int intParam(Map<String, String> params, String key, int fallback) {
        String value = params.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public Card getSheet() {
        return sheet;
    }

    public String getSlot() {
        return slot;
    }

    public StickerKind getKind() {
        return kind;
    }

    /** CR 123.3c - the {TK} a sticker costs to place. Name and art stickers cost nothing. */
    public int getTickets() {
        return tickets;
    }

    /** The word a name sticker adds. May be more than one word - CR 123.6. */
    public String getWord() {
        return word;
    }

    /** The printed text of an ability sticker, for display. */
    public String getText() {
        return text;
    }

    /** The SVar on the sheet holding an ability sticker's Forge ability, or null if unwritten. */
    public String getAbilitySVar() {
        return abilitySVar;
    }

    /** Keywords an ability sticker grants directly, comma separated, or null. */
    public String getKeywords() {
        return keywords;
    }

    /** SVars on the sheet holding triggers an ability sticker grants, comma separated, or null. */
    public String getTriggers() {
        return triggers;
    }

    public int getPower() {
        return power;
    }

    public int getToughness() {
        return toughness;
    }

    /**
     * An ability sticker whose Forge ability has not been written yet cannot be placed, since
     * placing it would grant nothing. Stickers of every other kind are always placeable.
     */
    public boolean isImplemented() {
        return kind != StickerKind.ABILITY || abilitySVar != null || keywords != null || triggers != null;
    }

    /** How this sticker reads to a player choosing one. */
    public String getDescription() {
        return switch (kind) {
            case NAME -> word;
            case ART -> "(art)";
            case ABILITY -> text != null ? text : "(ability)";
            case PT -> power + "/" + toughness;
        };
    }

    @Override
    public String toString() {
        return sheet.getName() + " " + slot + ": " + getDescription();
    }
}
