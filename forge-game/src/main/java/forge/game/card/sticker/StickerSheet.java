package forge.game.card.sticker;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import forge.game.ability.AbilityFactory;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.game.keyword.KeywordInterface;

/**
 * Reads the stickers off a sticker sheet card.
 * <p>
 * A sheet script names its stickers in a {@code StickerSheet} keyword and defines each one in an
 * SVar of that name, the way a dungeon names its rooms:
 * <pre>
 * K:StickerSheet:STK1,STK2,...
 * SVar:STK1:Kind$ Name | Word$ Eldrazi
 * </pre>
 */
public final class StickerSheet {
    private static final String KEYWORD = "StickerSheet:";
    /** The zones a sticker survives in - CR 123.5 drops it anywhere hidden. */
    private static final EnumSet<ZoneType> PUBLIC_ZONES = EnumSet.of(ZoneType.Battlefield,
            ZoneType.Graveyard, ZoneType.Exile, ZoneType.Command, ZoneType.Stack);

    private StickerSheet() {
    }

    /** True if this card is a sticker sheet rather than a card. */
    public static boolean isSheet(Card c) {
        return c != null && c.getType().isStickers();
    }

    /**
     * The stickers a player may choose from when told to put one on an object - CR 123.3.
     * <p>
     * A sticker is available when it is on one of their revealed sheets and is not currently on
     * any object they own. Note a sticker becomes available again once the object it was on
     * moves to a hidden zone, because it is then on no object at all (CR 123.5).
     */
    public static List<Sticker> getAvailableStickers(Player p) {
        return getAvailableStickers(p, p.getCounters(CounterEnumType.TICKET));
    }

    /**
     * The stickers a player may choose from when the tickets they have to spend are not the
     * tickets in front of them - Pin Collection places one without paying for it.
     */
    public static List<Sticker> getAvailableStickers(Player p, int tickets) {
        List<Sticker> available = new ArrayList<>();
        collectAvailable(p, tickets, available);
        return available;
    }

    /**
     * Whether the player has any sticker to choose at all. The AI asks this every time it
     * evaluates an ability that would place one, so it stops at the first answer.
     */
    public static boolean hasAvailableSticker(Player p) {
        return collectAvailable(p, p.getCounters(CounterEnumType.TICKET), null);
    }

    /** @return whether any sticker was available; fills {@code out} when it is given one. */
    private static boolean collectAvailable(Player p, int tickets, List<Sticker> out) {
        Set<String> onSomething = new HashSet<>();
        // A sticker only stays on an object in a public zone (CR 123.5), so nowhere else can
        // hold one of theirs.
        for (Card c : p.getCardsIn(PUBLIC_ZONES)) {
            for (AppliedSticker applied : c.getStickers()) {
                onSomething.add(identity(applied.getSticker()));
            }
        }
        boolean any = false;
        // CR 123.2c - only the sheets revealed before the game are ever accessible.
        for (Card sheet : p.getCardsIn(ZoneType.StickerSheets)) {
            for (Sticker s : getStickers(sheet)) {
                // CR 123.3c - a sticker they cannot pay the ticket cost of is not a legal choice.
                if (onSomething.contains(identity(s)) || !s.isImplemented() || s.getTickets() > tickets) {
                    continue;
                }
                if (out == null) {
                    return true;
                }
                out.add(s);
                any = true;
            }
        }
        return any;
    }

    /**
     * How a card's stickers read in its details: what is on it, or - for a sheet - which of its
     * stickers are still there to take. Empty for a card with nothing to say.
     */
    public static String describe(Card c) {
        if (isSheet(c)) {
            List<Sticker> stickers = getStickers(c);
            if (stickers.isEmpty()) {
                return "";
            }
            List<Sticker> free = c.getOwner() == null ? List.of() : getAvailableStickers(c.getOwner(),
                    Integer.MAX_VALUE);
            StringBuilder sb = new StringBuilder("Stickers on this sheet:");
            for (Sticker s : stickers) {
                boolean taken = free.stream().noneMatch(f -> identity(f).equals(identity(s)));
                sb.append("\r\n  ").append(s.getDescription());
                if (s.getTickets() > 0) {
                    sb.append(" (").append("{TK}".repeat(s.getTickets())).append(")");
                }
                if (taken) {
                    sb.append(" - used");
                }
            }
            return sb.toString();
        }
        if (!c.isStickered()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Stickers:");
        for (AppliedSticker applied : c.getStickers()) {
            sb.append("\r\n  ").append(applied.getSticker().getDescription())
                    .append(" (").append(applied.getKind().name().toLowerCase()).append(")");
        }
        return sb.toString();
    }

    /** CR 123.3a - a sticker is its sheet and its slot, never its text. */
    private static String identity(Sticker s) {
        return s.getSheet().getId() + "/" + s.getSlot();
    }

    /**
     * The stickers on one sheet, in printed order. Returns an empty list for anything that is
     * not a sheet, or a sheet whose script names no stickers.
     * <p>
     * A sheet's script never changes once the card is built, so the read is done once and kept
     * on the card - the AI asks for this list every time it weighs an ability that would place
     * a sticker.
     */
    public static List<Sticker> getStickers(Card sheet) {
        if (!isSheet(sheet)) {
            return List.of();
        }
        List<Sticker> cached = sheet.getSheetStickers();
        if (cached == null) {
            cached = readStickers(sheet);
            sheet.setSheetStickers(cached);
        }
        return cached;
    }

    private static List<Sticker> readStickers(Card sheet) {
        List<Sticker> stickers = new ArrayList<>();
        for (KeywordInterface kw : sheet.getKeywords()) {
            String original = kw.getOriginal();
            if (original == null || !original.startsWith(KEYWORD)) {
                continue;
            }
            for (String slot : original.substring(KEYWORD.length()).split(",")) {
                slot = slot.trim();
                String sVar = sheet.getSVar(slot);
                if (sVar == null || sVar.isEmpty()) {
                    continue;
                }
                Sticker sticker = Sticker.parse(sheet, slot, AbilityFactory.getMapParams(sVar));
                if (sticker != null) {
                    stickers.add(sticker);
                }
            }
        }
        return stickers;
    }
}
