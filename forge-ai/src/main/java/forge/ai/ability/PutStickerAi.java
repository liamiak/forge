package forge.ai.ability;

import java.util.Map;

import forge.ai.AiAbilityDecision;
import forge.ai.AiPlayDecision;
import forge.ai.ComputerUtilCard;
import forge.ai.SpellAbilityAi;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.sticker.StickerSheet;
import forge.game.player.Player;
import forge.game.player.PlayerActionConfirmMode;
import forge.game.spellability.SpellAbility;

/**
 * Putting a sticker is upside whenever there is one to put, so the AI takes it. Which sticker it
 * picks is {@code PlayerControllerAi.chooseSticker}; this class only decides whether to do it and,
 * when the ability targets, what to put the sticker on.
 */
public class PutStickerAi extends SpellAbilityAi {

    /**
     * Stickers can only go on something their owner owns (CR 123.3b), so the AI puts one on its
     * own best legal permanent, preferring one that is not stickered yet.
     */
    private static boolean chooseTarget(Player ai, SpellAbility sa) {
        sa.resetTargets();
        // CR 123.3b - only its owner's objects are ever legal, so start from those; and
        // getTargetableCards has already applied canTarget.
        CardCollection options = CardLists.getTargetableCards(
                ai.getCardsIn(sa.getTargetRestrictions().getZone()), sa);
        if (options.isEmpty()) {
            return false;
        }
        CardCollection fresh = CardLists.filter(options, c -> !c.isStickered());
        Card best = ComputerUtilCard.getBestAI(fresh.isEmpty() ? options : fresh);
        if (best == null) {
            return false;
        }
        sa.getTargets().add(best);
        return true;
    }

    @Override
    protected AiAbilityDecision checkApiLogic(final Player ai, final SpellAbility sa) {
        if (!StickerSheet.hasAvailableSticker(ai)) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        if (sa.usesTargeting() && !chooseTarget(ai, sa)) {
            return new AiAbilityDecision(0, AiPlayDecision.TargetingFailed);
        }
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    @Override
    public AiAbilityDecision chkDrawback(final Player ai, final SpellAbility sa) {
        // A sticker is worth taking even when the rest of the ability was the point, so a
        // drawback check that cannot find a target still lets the parent resolve.
        if (!StickerSheet.hasAvailableSticker(ai)) {
            return new AiAbilityDecision(0, AiPlayDecision.WillPlay);
        }
        if (sa.usesTargeting()) {
            chooseTarget(ai, sa);
        }
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    @Override
    protected AiAbilityDecision doTriggerNoCost(Player ai, SpellAbility sa, boolean mandatory) {
        if (sa.usesTargeting() && !chooseTarget(ai, sa) && !mandatory) {
            return new AiAbilityDecision(0, AiPlayDecision.TargetingFailed);
        }
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    @Override
    public boolean confirmAction(Player player, SpellAbility sa, PlayerActionConfirmMode mode, String message,
            Map<String, Object> params) {
        return StickerSheet.hasAvailableSticker(player);
    }
}
