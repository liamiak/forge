package forge.ai.blocking;

import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;

import forge.ai.PlayerControllerAi;
import forge.ai.simulation.SimulationTest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Parity tests for block requirements (lure and "must be blocked if able").
 *
 * These exist to guard the CombatUtil.canBlock / mustBlockAnAttacker fast paths:
 * the AI performance work reorders and caches parts of that logic, and the
 * board states used for benchmarking contain no block requirements, so they
 * exercise only the cheap path. These assert the resulting block assignment,
 * which is what any optimization there must leave unchanged.
 */
public class BlockRequirementTests extends SimulationTest {

    private static final String LURE_CREATURE = "Elvish Bard";        // all able to block do so
    private static final String BIG_LURE = "Breaker of Armies";       // all able to block do so
    private static final String MUST_BLOCK = "Goblin Fire Fiend";     // must be blocked if able
    private static final String VANILLA = "Grizzly Bears";

    /** Declares blockers for the AI against every creature the attacker controls. */
    private Combat attackWithAll(final Game game, final Player attacker, final Player ai) {
        final Combat combat = new Combat(attacker);
        for (final Card c : attacker.getCreaturesInPlay()) {
            combat.addAttacker(c, ai);
        }
        ((PlayerControllerAi) ai.getController()).getAi().declareBlockersFor(ai, combat);
        return combat;
    }

    private List<String> blockerNames(final Combat combat, final Card attacker) {
        final List<String> names = new ArrayList<>();
        for (final Card b : combat.getBlockers(attacker)) {
            names.add(b.getName());
        }
        return names;
    }

    @Test
    public void lureForcesEveryAbleBlockerOntoIt() {
        final Game game = initAndCreateGame();
        final Player human = game.getPlayers().get(0);
        final Player ai = game.getPlayers().get(1);

        final Card lure = addCard(LURE_CREATURE, human);
        final Card other = addCard(VANILLA, human);
        for (int i = 0; i < 3; i++) {
            addCard(VANILLA, ai).setSickness(false);
        }

        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, human);
        game.getAction().checkStateEffects(true);

        final Combat combat = attackWithAll(game, human, ai);

        // every AI creature able to block must be on the lure creature, none elsewhere
        assertEquals(combat.getBlockers(lure).size(), 3,
                "all three blockers must block the lure creature, got " + blockerNames(combat, lure));
        assertEquals(combat.getBlockers(other).size(), 0,
                "no blocker may block a non-lure attacker while the lure is unblocked");
    }

    @Test
    public void twoLuresBothGetBlockedAndNothingElseDoes() {
        final Game game = initAndCreateGame();
        final Player human = game.getPlayers().get(0);
        final Player ai = game.getPlayers().get(1);

        final Card lure1 = addCard(LURE_CREATURE, human);
        final Card lure2 = addCard(BIG_LURE, human);
        final Card other = addCard(VANILLA, human);
        for (int i = 0; i < 4; i++) {
            addCard(VANILLA, ai).setSickness(false);
        }

        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, human);
        game.getAction().checkStateEffects(true);

        final Combat combat = attackWithAll(game, human, ai);

        final int onLure1 = combat.getBlockers(lure1).size();
        final int onLure2 = combat.getBlockers(lure2).size();

        // With two lures every blocker is obligated to one of them, and none may
        // block the ordinary attacker. This is the case that makes the per-attacker
        // structure of mustBlockAnAttacker look necessary - it must survive intact.
        //
        // Note the split between the two lures is NOT fixed by the rules: a blocker
        // can only satisfy one of the two requirements either way, so any assignment
        // that puts every blocker on some lure fulfils the same number of
        // requirements (CR 509.1c). Only the totals are asserted as rules behaviour.
        assertEquals(onLure1 + onLure2, 4,
                "all four blockers must block one of the two lure creatures, got "
                        + onLure1 + " and " + onLure2);
        assertEquals(combat.getBlockers(other).size(), 0,
                "no blocker may block the ordinary attacker while lures are unsatisfied");

        // Parity lock: the specific split is an AI preference (it currently stacks
        // everything onto the larger threat). A pure memoization/reordering change
        // must not alter it - if this fails after such a change, the change altered
        // a decision rather than just its cost.
        assertEquals(onLure2, 4, "AI is expected to put all blockers on the larger lure");
    }

    @Test
    public void mustBeBlockedIfAbleGetsABlocker() {
        final Game game = initAndCreateGame();
        final Player human = game.getPlayers().get(0);
        final Player ai = game.getPlayers().get(1);

        final Card mustBlock = addCard(MUST_BLOCK, human);
        for (int i = 0; i < 3; i++) {
            addCard(VANILLA, ai).setSickness(false);
        }

        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, human);
        game.getAction().checkStateEffects(true);

        final Combat combat = attackWithAll(game, human, ai);

        assertTrue(combat.getBlockers(mustBlock).size() >= 1,
                "an attacker that must be blocked if able needs at least one blocker");
    }

    /** Baseline: with no requirements in play the AI still makes ordinary blocks. */
    @Test
    public void ordinaryBlocksStillHappen() {
        final Game game = initAndCreateGame();
        final Player human = game.getPlayers().get(0);
        final Player ai = game.getPlayers().get(1);

        ai.setLife(4, null); // low enough that the AI must block to survive

        final Card a1 = addCard(VANILLA, human);
        final Card a2 = addCard(VANILLA, human);
        addCard(VANILLA, ai).setSickness(false);
        addCard(VANILLA, ai).setSickness(false);

        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, human);
        game.getAction().checkStateEffects(true);

        final Combat combat = attackWithAll(game, human, ai);

        assertTrue(combat.getBlockers(a1).size() + combat.getBlockers(a2).size() > 0,
                "AI at 4 life facing lethal should block");
    }
}
