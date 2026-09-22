package forge.ai.ability;

import java.util.Map;

import forge.ai.AiAbilityDecision;
import forge.ai.AiPlayDecision;
import forge.ai.SpellAbilityAi;
import forge.game.card.sticker.StickerSheet;
import forge.game.player.Player;
import forge.game.player.PlayerActionConfirmMode;
import forge.game.spellability.SpellAbility;

/**
 * Putting a sticker is upside whenever there is one to put, so the AI takes it. Which sticker it
 * picks is {@code PlayerControllerAi.chooseSticker}.
 */
public class PutStickerAi extends SpellAbilityAi {

    private static AiAbilityDecision decide(Player ai) {
        if (StickerSheet.getAvailableStickers(ai).isEmpty()) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    @Override
    protected AiAbilityDecision checkApiLogic(final Player ai, final SpellAbility sa) {
        return decide(ai);
    }

    @Override
    public AiAbilityDecision chkDrawback(final Player ai, final SpellAbility sa) {
        return decide(ai);
    }

    @Override
    public boolean confirmAction(Player player, SpellAbility sa, PlayerActionConfirmMode mode, String message,
            Map<String, Object> params) {
        return !StickerSheet.getAvailableStickers(player).isEmpty();
    }
}
