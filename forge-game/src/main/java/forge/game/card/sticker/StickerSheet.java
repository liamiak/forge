package forge.game.card.sticker;

import java.util.ArrayList;
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

    private StickerSheet() {
    }

    /** True if this card is a sticker sheet rather than a card. */
    public static boolean isSheet(Card c) {
        return c != null && c.getType().isStickers();
    }

    /** The sheets a player currently has access to - CR 123.2c. */
    public static List<Card> getAccessibleSheets(Player p) {
        return new ArrayList<>(p.getCardsIn(ZoneType.StickerSheets));
    }

    /**
     * The stickers a player may choose from when told to put one on an object - CR 123.3.
     * <p>
     * A sticker is available when it is on one of their revealed sheets and is not currently on
     * any object they own. Note a sticker becomes available again once the object it was on
     * moves to a hidden zone, because it is then on no object at all (CR 123.5).
     */
    public static List<Sticker> getAvailableStickers(Player p) {
        Set<String> onSomething = new HashSet<>();
        for (Card c : p.getAllCards()) {
            for (AppliedSticker applied : c.getStickers()) {
                onSomething.add(identity(applied.getSticker()));
            }
        }
        List<Sticker> available = new ArrayList<>();
        for (Card sheet : getAccessibleSheets(p)) {
            for (Sticker s : getStickers(sheet)) {
                // CR 123.3c - a sticker they cannot pay the ticket cost of is not a legal choice.
                if (!onSomething.contains(identity(s)) && s.isImplemented()
                        && s.getTickets() <= p.getCounters(CounterEnumType.TICKET)) {
                    available.add(s);
                }
            }
        }
        return available;
    }

    /** CR 123.3a - a sticker is its sheet and its slot, never its text. */
    private static String identity(Sticker s) {
        return s.getSheet().getId() + "/" + s.getSlot();
    }

    /**
     * The stickers on one sheet, in printed order. Returns an empty list for anything that is
     * not a sheet, or a sheet whose script names no stickers.
     */
    public static List<Sticker> getStickers(Card sheet) {
        List<Sticker> stickers = new ArrayList<>();
        if (!isSheet(sheet)) {
            return stickers;
        }
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
