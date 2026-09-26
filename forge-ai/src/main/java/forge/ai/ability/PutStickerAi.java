package forge.ai.ability;

import java.util.List;
import java.util.Map;

import forge.ai.AiAbilityDecision;
import forge.ai.AiPlayDecision;
import forge.ai.ComputerUtilCard;
import forge.ai.SpellAbilityAi;
import forge.game.ability.effects.PutStickerEffect;
import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.card.sticker.Sticker;
import forge.game.card.sticker.StickerSheet;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerActionConfirmMode;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * Putting a sticker is upside whenever there is one worth putting, so the AI takes it. This class
 * decides whether to do it, what to put the sticker on, and - for
 * {@code PlayerControllerAi.chooseSticker}, which delegates here - which sticker to put there.
 * Both choices score the same (sticker, object) pairs, because which object is the best one to
 * sticker depends on what the sticker on offer would do to it.
 */
public class PutStickerAi extends SpellAbilityAi {

    /** What a point of power and of toughness is worth to {@link ComputerUtilCard#evaluateCreature}. */
    private static final int POWER_VALUE = 15;
    private static final int TOUGHNESS_VALUE = 10;
    /** An ability sticker is worth about what that evaluator pays for a middling keyword. */
    private static final int ABILITY_VALUE = 30;
    /** Most of what a sticker can print does nothing on something that is not a creature. */
    private static final int NONCREATURE_VALUE = 4;
    /** A name or art sticker changes nothing by itself, but a few cards count them. */
    private static final int MARKER_VALUE = 5;
    /** Tickets buy stickers and nothing else, so keeping one back is worth very little. */
    private static final int TICKET_VALUE = 2;

    /**
     * What putting this sticker on this object would gain, in the units
     * {@link ComputerUtilCard#evaluateCreature} counts in, less what its tickets cost.
     */
    private static int score(Sticker sticker, Card target, boolean free) {
        int value = switch (sticker.getKind()) {
            // CR 123.8 - a power and toughness sticker sets them rather than adding to them, so a
            // small one on a big creature is a downgrade, and on something with no power or
            // toughness to set it does nothing at all.
            case PT -> !target.isCreature() ? 0
                    : (sticker.getPower() - target.getCurrentPower()) * POWER_VALUE
                            + (sticker.getToughness() - target.getCurrentToughness()) * TOUGHNESS_VALUE;
            case ABILITY -> target.isCreature() ? ABILITY_VALUE : NONCREATURE_VALUE;
            // The cards that pay out for a name sticker pay out per unique vowel, and one printed
            // with a blank has somewhere a word is meant to go.
            case NAME -> MARKER_VALUE + sticker.getUniqueVowelCount()
                    + (target.stickerWouldFillBlank() ? MARKER_VALUE : 0);
            case ART -> MARKER_VALUE;
        };
        return free ? value : value - sticker.getTickets() * TICKET_VALUE;
    }

    /**
     * The sticker to put on the given object, or nothing when none of them is worth what it would
     * cost and the card allows that.
     */
    public static Sticker chooseSticker(List<Sticker> options, Card target, SpellAbility sa, boolean isOptional) {
        final boolean free = sa != null && sa.hasParam("NoTicketCost");
        Sticker best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Sticker s : options) {
            int value = score(s, target, free);
            if (value > bestScore) {
                bestScore = value;
                best = s;
            }
        }
        return isOptional && bestScore <= 0 ? null : best;
    }

    /** The object the best sticker on offer would do the most for. */
    private static Card bestTarget(Player ai, SpellAbility sa, Iterable<Card> pool) {
        final List<Sticker> options = PutStickerEffect.availableStickers(sa, ai);
        final boolean free = sa.hasParam("NoTicketCost");
        Card best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Card c : pool) {
            int value = Integer.MIN_VALUE;
            for (Sticker s : options) {
                value = Math.max(value, score(s, c, free));
            }
            if (best == null || value > bestScore || (value == bestScore && preferred(c, best))) {
                bestScore = value;
                best = c;
            }
        }
        return best;
    }

    /** Between two objects the sticker would do the same for, the one worth keeping it on. */
    private static boolean preferred(Card candidate, Card best) {
        if (candidate.isCreature() != best.isCreature()) {
            return candidate.isCreature();
        }
        if (candidate.isStickered() != best.isStickered()) {
            return !candidate.isStickered();
        }
        if (!candidate.isCreature()) {
            return candidate.getCMC() > best.getCMC();
        }
        return ComputerUtilCard.evaluateCreature(candidate) > ComputerUtilCard.evaluateCreature(best);
    }

    /** Whether the ability's own Choices$ pool holds anything the AI could sticker. */
    private static boolean hasSomethingToSticker(Player ai, SpellAbility sa) {
        if (!sa.hasParam("Choices")) {
            return true;
        }
        ZoneType zone = sa.hasParam("ChoiceZone")
                ? ZoneType.smartValueOf(sa.getParam("ChoiceZone")) : ZoneType.Battlefield;
        // CR 123.3b - and it has to be one the AI owns.
        return !CardLists.filter(CardLists.getValidCards(ai.getGame().getCardsIn(zone),
                sa.getParam("Choices"), ai, sa.getHostCard(), sa),
                CardPredicates.isOwner(ai)).isEmpty();
    }

    /**
     * These cards choose rather than target - "a nonland permanent you own", not "target nonland
     * permanent you own" - so the choice arrives here rather than through target selection.
     */
    @Override
    protected Card chooseSingleCard(Player ai, SpellAbility sa, Iterable<Card> options, boolean isOptional,
            Player targetedPlayer, Map<String, Object> params) {
        return bestTarget(ai, sa, options);
    }

    /**
     * A sticker is permanent upside that nothing can take away, so there is never a reason to buy
     * one while the mana could still be doing something else. An ability that costs mana therefore
     * waits until the AI's second main phase, the same way the ticket abilities on Prize Wall,
     * Blorbian Buddy and Ticket Turbotubes wait with {@code AILogic$ AtOppEOT}.
     */
    @Override
    protected boolean checkPhaseRestrictions(final Player ai, final SpellAbility sa, final PhaseHandler ph) {
        return sa.getPayCosts() == null || !sa.getPayCosts().hasManaCost()
                || !ph.isPlayerTurn(ai) || !ph.getPhase().isBefore(PhaseType.MAIN2);
    }

    @Override
    protected AiAbilityDecision checkApiLogic(final Player ai, final SpellAbility sa) {
        if (!StickerSheet.hasAvailableSticker(ai)) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        // An ability that chooses rather than targets can still have nothing to choose from -
        // Park Bleater only offers creatures that entered this turn.
        if (!hasSomethingToSticker(ai, sa)) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    @Override
    protected AiAbilityDecision doTriggerNoCost(Player ai, SpellAbility sa, boolean mandatory) {
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    @Override
    public boolean confirmAction(Player player, SpellAbility sa, PlayerActionConfirmMode mode, String message,
            Map<String, Object> params) {
        return StickerSheet.hasAvailableSticker(player);
    }
}
