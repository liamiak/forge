package forge.game.card.sticker;

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.Lists;

import forge.game.ability.AbilityFactory;
import forge.game.card.Card;
import forge.game.card.CardState;
import forge.game.card.CardTraitChanges;
import forge.game.card.perpetual.PerpetualInterface;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
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
public class AppliedSticker implements PerpetualInterface {
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

    @Override
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
    @Override
    public void applyEffect(Card c) {
        switch (sticker.getKind()) {
            case PT -> c.addNewPT(sticker.getPower(), sticker.getToughness(), timestamp, 0);
            case NAME -> c.recomputeStickerName();
            case ABILITY -> grantAbility(c);
            // An art sticker only ever acts as a marker (CR 123.9).
            case ART -> {
            }
        }
    }

    /**
     * CR 123.7 - the object gains the ability printed on the sticker. Keywords are granted
     * directly; anything with its own script is built from an SVar on the sheet the sticker
     * came from, which is also where any SVars it refers to live.
     */
    private void grantAbility(Card c) {
        List<String> keywords = getGrantedKeywords();
        if (!keywords.isEmpty()) {
            c.addChangedCardKeywords(keywords, null, false, timestamp, null);
        }
        CardTraitChanges traits = getGrantedTraits(c);
        if (!traits.getAbilities().isEmpty() || !traits.getTriggers().isEmpty()
                || !traits.getStaticAbilities().isEmpty()) {
            c.addChangedCardTraits(traits, timestamp, 0, true);
        }
    }

    /** The keywords this sticker prints, which do not depend on what it is on. */
    public List<String> getGrantedKeywords() {
        return sticker.getKeywords() == null ? List.of()
                : Arrays.asList(sticker.getKeywords().split(","));
    }

    /**
     * What this sticker's printed ability grants the given card, built but not applied. The
     * cards that hand an object the abilities of stickers on a different object build the same
     * changes this way.
     */
    public CardTraitChanges getGrantedTraits(Card c) {
        // The sheet's state, not the sheet, so that an SVar the ability only reads when it
        // resolves is still looked up on the sheet - the same wiring a static's AddAbility uses.
        CardState sheetState = sticker.getSheet().getCurrentState();
        List<SpellAbility> abilities = Lists.newArrayList();
        List<Trigger> triggers = Lists.newArrayList();
        List<StaticAbility> statics = Lists.newArrayList();
        if (sticker.getAbilitySVar() != null) {
            for (String svar : sticker.getAbilitySVar().split(",")) {
                abilities.add(AbilityFactory.getAbility(c, svar.trim(), sheetState));
            }
        }
        if (sticker.getTriggers() != null) {
            for (String svar : sticker.getTriggers().split(",")) {
                triggers.add(TriggerHandler.parseTrigger(sheetState.getSVar(svar.trim()), c, false, sheetState));
            }
        }
        if (sticker.getStatics() != null) {
            for (String svar : sticker.getStatics().split(",")) {
                statics.add(StaticAbility.create(sheetState.getSVar(svar.trim()), c, sheetState, false));
            }
        }
        return new CardTraitChanges(abilities, triggers, null, statics, null);
    }

    @Override
    public String toString() {
        return sticker.toString();
    }
}
