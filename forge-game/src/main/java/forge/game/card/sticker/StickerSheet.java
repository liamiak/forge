package forge.game.card.sticker;

import java.util.ArrayList;
import java.util.List;

import forge.game.ability.AbilityFactory;
import forge.game.card.Card;
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
