package forge.game.card.sticker;

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.Lists;

import forge.game.ability.AbilityFactory;
import forge.game.card.Card;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerHandler;

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
            case ABILITY -> grantAbility(c);
            // An art sticker only ever acts as a marker (CR 123.9).
            default -> {
            }
        }
    }

    /**
     * CR 123.7 - the object gains the ability printed on the sticker. Keywords are granted
     * directly; anything with its own script is built from an SVar on the sheet the sticker
     * came from, which is also where any SVars it refers to live.
     */
    private void grantAbility(Card c) {
        Card sheet = sticker.getSheet();
        if (sticker.getKeywords() != null) {
            c.addChangedCardKeywords(Arrays.asList(sticker.getKeywords().split(",")), null, false,
                    timestamp, null);
        }
        List<SpellAbility> abilities = Lists.newArrayList();
        List<Trigger> triggers = Lists.newArrayList();
        if (sticker.getAbilitySVar() != null) {
            for (String svar : sticker.getAbilitySVar().split(",")) {
                abilities.add(AbilityFactory.getAbility(sheet.getSVar(svar.trim()), c, sheet));
            }
        }
        if (sticker.getTriggers() != null) {
            for (String svar : sticker.getTriggers().split(",")) {
                triggers.add(TriggerHandler.parseTrigger(sheet.getSVar(svar.trim()), c, false, sheet));
            }
        }
        if (!abilities.isEmpty() || !triggers.isEmpty()) {
            c.addChangedCardTraits(abilities, triggers, null, null, null, timestamp, 0);
        }
    }

    @Override
    public String toString() {
        return sticker.toString();
    }
}
