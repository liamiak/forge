package forge.game.ability.effects;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import forge.game.Game;
import forge.game.ability.AbilityKey;
import forge.game.ability.AbilityUtils;
import forge.game.ability.SpellAbilityEffect;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
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
 *   <li>{@code MaxTickets$ N} - offer only stickers costing that many tickets or fewer.</li>
 *   <li>{@code NoTicketCost$ True} - place it without paying the ticket cost.</li>
 * </ul>
 * The effect records {@code StickersPlaced} on the ability, and for a name sticker also
 * {@code StickerUniqueVowels}, so a sub-ability can read them the way a die roll's
 * sub-abilities read its result.
 */
public class PutStickerEffect extends SpellAbilityEffect {

    /** How many stickers this resolution placed, for the cards that say "when you do". */
    private static final String PLACED = "StickersPlaced";
    /** The unique vowels in the name sticker just placed, for the cards that count them. */
    private static final String VOWELS = "StickerUniqueVowels";

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
        pool = CardLists.filter(pool, CardPredicates.isOwner(chooser));
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
        sa.setSVar(VOWELS, "0");
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

            // CR 123.3c - normally only stickers the owner can pay for, but a card may waive the
            // cost, and may cap how expensive a sticker it offers.
            final boolean free = sa.hasParam("NoTicketCost");
            int affordable = free ? Integer.MAX_VALUE : owner.getCounters(CounterEnumType.TICKET);
            if (sa.hasParam("MaxTickets")) {
                affordable = Math.min(affordable,
                        AbilityUtils.calculateAmount(sa.getHostCard(), sa.getParam("MaxTickets"), sa));
            }
            List<Sticker> options = new ArrayList<>();
            for (Sticker s : StickerSheet.getAvailableStickers(owner, affordable)) {
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
            if (chosen.getTickets() > 0 && !free) {
                owner.subtractCounter(CounterEnumType.TICKET, chosen.getTickets(), owner);
            }

            int position = 0;
            if (chosen.getKind() == StickerKind.NAME) {
                position = owner.getController().chooseStickerNamePosition(chosen, target);
                sa.setSVar(VOWELS, Integer.toString(chosen.getUniqueVowelCount()));
            }
            target.addSticker(new AppliedSticker(chosen, game.getNextTimestamp(), position));
            sa.setSVar(PLACED, Integer.toString(++placed));

            final Map<AbilityKey, Object> runParams = AbilityKey.newMap();
            runParams.put(AbilityKey.Card, target);
            runParams.put(AbilityKey.Player, owner);
            runParams.put(AbilityKey.StickerKind, chosen.getKind());
            game.getTriggerHandler().runTrigger(TriggerType.StickerPlaced, runParams, false);
            game.getTriggerHandler().runWaitingTriggers();
        }
    }
}
