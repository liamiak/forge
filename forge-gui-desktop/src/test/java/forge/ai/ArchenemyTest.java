package forge.ai;

import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseHandler;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.item.IPaperCard;
import forge.model.FModel;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class ArchenemyTest extends AITest {

    private Card addSchemeToDeck(String name, Player p) {
        IPaperCard paperCard = FModel.getMagicDb().getVariantCards().getCard(name);
        Card c = Card.fromPaperCard(paperCard, p);
        c.setGameTimestamp(p.getGame().getNextTimestamp());
        p.getZone(ZoneType.SchemeDeck).add(c);
        return c;
    }

    @Test
    public void testSchemesResumeAfterAllInGoodTime() {
        Game game = initAndCreateGame();
        Player archenemy = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        for (int i = 0; i < 30; i++) {
            addCardToZone("Plains", archenemy, ZoneType.Library);
            addCardToZone("Plains", opponent, ZoneType.Library);
        }

        // Top of the scheme deck first: the extra turn scheme, then token schemes
        addSchemeToDeck("All in Good Time", archenemy);
        addSchemeToDeck("Roots of All Evil", archenemy);
        addSchemeToDeck("Roots of All Evil", archenemy);

        PhaseHandler ph = game.getPhaseHandler();
        List<Player> turnOwners = new ArrayList<>();
        int lastTurn = ph.getTurn();
        int turnTransitions = 0;
        int steps = 0;

        // Current archenemy turn is already past the MAIN1 scheme action.
        // Expected turns from here: opponent, archenemy (All in Good Time),
        // archenemy (extra turn, no schemes), opponent, archenemy (Roots of All Evil).
        while (!game.isGameOver() && turnTransitions < 6 && steps++ < 20000) {
            ph.mainLoopStep();
            if (ph.getTurn() != lastTurn) {
                lastTurn = ph.getTurn();
                turnTransitions++;
                turnOwners.add(ph.getPlayerTurn());
            }
        }

        boolean extraTurnTaken = false;
        for (int i = 1; i < turnOwners.size(); i++) {
            if (turnOwners.get(i - 1).equals(archenemy) && turnOwners.get(i).equals(archenemy)) {
                extraTurnTaken = true;
            }
        }
        assertTrue(extraTurnTaken, "All in Good Time should have granted an extra turn; turn owners: " + turnOwners);

        assertEquals(countCardsWithName(game, "Saproling Token"), 5,
                "Roots of All Evil should have been set in motion on the first archenemy turn after the extra turn");
    }
}
