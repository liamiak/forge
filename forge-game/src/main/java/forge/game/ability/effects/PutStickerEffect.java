package forge.game.ability.effects;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import forge.game.Game;
import forge.game.ability.AbilityKey;
import forge.game.ability.SpellAbilityEffect;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CounterEnumType;
import forge.game.card.sticker.AppliedSticker;
import forge.game.card.sticker.Sticker;
import forge.game.card.sticker.StickerKind;
import forge.game.card.sticker.StickerSheet;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

import org.apache.commons.lang3.StringUtils;

/**
 * Puts a sticker on an object - CR 123.3.
 * <p>
 * Optional parameters:
 * <ul>
 *   <li>{@code Kind$ Name|Art|Ability|PT} - restrict the choice to one kind of sticker.</li>
 *   <li>{@code Optional$ True} - the player may decline.</li>
 * </ul>
 * The effect records {@code StickersPlaced} on the ability, and for a name sticker also
 * {@code StickerWord}, {@code StickerUniqueVowels} and {@code StickerLetters}, so a sub-ability
 * can read them the way a die roll's sub-abilities read its result.
 */
public class PutStickerEffect extends SpellAbilityEffect {

    private static final String VOWELS = "AEIOUY";
    /** How many stickers this resolution placed, for the cards that say "when you do". */
    private static final String PLACED = "StickersPlaced";

    @Override
    protected String getStackDescription(SpellAbility sa) {
        StringBuilder sb = new StringBuilder();
        sb.append(sa.getActivatingPlayer()).append(" puts a ");
        if (sa.hasParam("Kind")) {
            sb.append(sa.getParam("Kind").toLowerCase()).append(" ");
        }
        sb.append("sticker on ").append(StringUtils.join(getTargetCards(sa), ", "));
        return sb.toString();
    }

    /**
     * The objects to sticker. These cards say "a nonland permanent you own", not "target", so
     * the normal form is {@code Choices$}, a choice rather than a target. {@code ValidTgts$} is
     * for the few that really do target.
     */
    private static List<Card> chooseObjects(SpellAbility sa, Game game) {
        if (!sa.hasParam("Choices")) {
            return getTargetCards(sa);
        }
        Player chooser = sa.getActivatingPlayer();
        ZoneType zone = sa.hasParam("ChoiceZone")
                ? ZoneType.smartValueOf(sa.getParam("ChoiceZone")) : ZoneType.Battlefield;
        CardCollection pool = CardLists.getValidCards(game.getCardsIn(zone), sa.getParam("Choices"),
                chooser, sa.getHostCard(), sa);
        // CR 123.3b - only an object its owner owns can be stickered, so never offer the rest.
        pool = CardLists.filter(pool, c -> chooser.equals(c.getOwner()));
        if (pool.isEmpty()) {
            return new CardCollection();
        }
        String prompt = sa.hasParam("ChoiceTitle") ? sa.getParam("ChoiceTitle")
                : "Choose a permanent to put a sticker on";
        Card chosen = chooser.getController().chooseSingleEntityForEffect(pool, sa, prompt,
                sa.hasParam("Optional"), null);
        CardCollection result = new CardCollection();
        if (chosen != null) {
            result.add(chosen);
        }
        return result;
    }

    @Override
    public void resolve(SpellAbility sa) {
        final Game game = sa.getActivatingPlayer().getGame();
        final StickerKind only = sa.hasParam("Kind") ? StickerKind.smartValueOf(sa.getParam("Kind")) : null;
        final boolean optional = sa.hasParam("Optional");
        // The same ability object resolves again and again, so clear what the last resolution
        // recorded before anything can read it.
        recordNameStickerValues(sa, null);
        sa.setSVar(PLACED, "0");
        int placed = 0;

        for (final Card target : chooseObjects(sa, game)) {
            // CR 123.3b - a player can't put a sticker on an object they don't own. If an effect
            // would make them, that part of the effect does nothing.
            final Player owner = target.getOwner();
            if (owner == null || !owner.equals(sa.getActivatingPlayer())) {
                continue;
            }
            if (!game.getCardState(target, null).equalsWithGameTimestamp(target)) {
                continue;
            }

            List<Sticker> options = new ArrayList<>();
            for (Sticker s : StickerSheet.getAvailableStickers(owner)) {
                if (only == null || s.getKind() == only) {
                    options.add(s);
                }
            }
            if (options.isEmpty()) {
                continue;
            }

            Sticker chosen = owner.getController().chooseSticker(options, target, sa, optional);
            if (chosen == null) {
                continue;
            }

            // CR 123.3c - the owner pays the sticker's ticket cost to place it.
            if (chosen.getTickets() > 0) {
                owner.subtractCounter(CounterEnumType.TICKET, chosen.getTickets(), owner);
            }

            int position = 0;
            if (chosen.getKind() == StickerKind.NAME) {
                int words = StringUtils.isBlank(target.getName()) ? 0 : target.getName().split(" ").length;
                position = owner.getController().chooseStickerNamePosition(chosen, target, words);
            }
            target.addSticker(new AppliedSticker(chosen, game.getNextTimestamp(), position));

            if (chosen.getKind() == StickerKind.NAME) {
                recordNameStickerValues(sa, chosen.getWord());
            }
            sa.setSVar(PLACED, Integer.toString(++placed));

            final Map<AbilityKey, Object> runParams = AbilityKey.newMap();
            runParams.put(AbilityKey.Card, target);
            runParams.put(AbilityKey.Player, owner);
            runParams.put(AbilityKey.StickerKind, chosen.getKind());
            game.getTriggerHandler().runTrigger(TriggerType.StickerPlaced, runParams, false);
            game.getTriggerHandler().runWaitingTriggers();
        }
    }

    /** Values a card may ask about the name sticker it just placed. */
    private static void recordNameStickerValues(SpellAbility sa, String word) {
        String letters = word == null ? "" : word.replaceAll("[^A-Za-z]", "");
        int unique = 0;
        for (char v : VOWELS.toCharArray()) {
            if (letters.toUpperCase().indexOf(v) >= 0) {
                unique++;
            }
        }
        sa.setSVar("StickerWord", word == null ? "" : word);
        sa.setSVar("StickerLetters", Integer.toString(letters.length()));
        sa.setSVar("StickerUniqueVowels", Integer.toString(unique));
    }
}
