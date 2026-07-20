package forge.ai;

import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.util.PerfProfiler;

/**
 * TEMPORARY benchmark for the AI performance investigation (ai-perf-investigation branch).
 *
 * Measures how AI decision time scales with board size, and records the decisions
 * themselves so an optimization can be checked for decision-parity rather than just speed.
 *
 * Run with:
 *   mvn -pl forge-gui-desktop -am test -Dtest=AiPerformanceBenchmark
 */
public class AiPerformanceBenchmark extends AITest {

    private static final int[] BOARD_SIZES = {5, 10, 20, 40};
    private static final int REPEATS = 3;

    /** Cards used to build the board. Mixed so blocking decisions aren't trivially symmetric. */
    private static final String[] ATTACKER_POOL = {
            "Grizzly Bears", "Hill Giant", "Serra Angel", "Craw Wurm", "Air Elemental"
    };
    private static final String[] BLOCKER_POOL = {
            "Grizzly Bears", "Wall of Stone", "Hill Giant", "Ankle Biter", "Serra Angel"
    };

    private static final class Result {
        int size;
        long attackNanos;
        long blockNanos;
        long spellNanos;
        int attackersDeclared;
        int blockersDeclared;
    }

    @Test(timeOut = 1800000)
    public void benchmarkAiDecisionScaling() {
        final List<Result> results = new ArrayList<>();
        for (final int size : BOARD_SIZES) {
            results.add(measure(size));
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("\n[AIBENCH] AI decision time vs board size (median of ").append(REPEATS).append(" runs)\n");
        sb.append(String.format("%-14s %14s %14s %14s %12s %12s%n",
                "creatures/side", "attack ms", "block ms", "main-phase ms", "attackers", "blockers"));
        for (final Result r : results) {
            sb.append(String.format("%-14d %14.1f %14.1f %14.1f %12d %12d%n",
                    r.size, r.attackNanos / 1e6, r.blockNanos / 1e6, r.spellNanos / 1e6,
                    r.attackersDeclared, r.blockersDeclared));
        }
        sb.append("\nScaling factors (relative to smallest board):\n");
        final Result base = results.get(0);
        for (final Result r : results) {
            sb.append(String.format("  %2dx%-2d cards: attack %5.1fx  block %5.1fx  main %5.1fx%n",
                    r.size, r.size,
                    ratio(r.attackNanos, base.attackNanos),
                    ratio(r.blockNanos, base.blockNanos),
                    ratio(r.spellNanos, base.spellNanos)));
        }
        System.out.println(sb);
    }

    private static double ratio(final long value, final long base) {
        return base == 0 ? 0 : (double) value / base;
    }

    private Result measure(final int size) {
        final Result result = new Result();
        result.size = size;
        final long[] attack = new long[REPEATS];
        final long[] block = new long[REPEATS];
        final long[] spell = new long[REPEATS];

        for (int run = 0; run < REPEATS; run++) {
            // --- attack decision ---
            Game game = buildBoard(size);
            Player ai = game.getPlayers().get(1);
            long t0 = System.nanoTime();
            Combat attackCombat = ((PlayerControllerAi) ai.getController()).getAi().getPredictedCombat();
            attack[run] = System.nanoTime() - t0;
            result.attackersDeclared = attackCombat.getAttackers().size();

            // --- block decision: AI defends against a full attack ---
            game = buildBoard(size);
            ai = game.getPlayers().get(1);
            Player human = game.getPlayers().get(0);
            Combat blockCombat = new Combat(human);
            for (final Card c : human.getCreaturesInPlay()) {
                blockCombat.addAttacker(c, ai);
            }
            t0 = System.nanoTime();
            ((PlayerControllerAi) ai.getController()).getAi().declareBlockersFor(ai, blockCombat);
            block[run] = System.nanoTime() - t0;
            int blockers = 0;
            for (final Card c : human.getCreaturesInPlay()) {
                blockers += blockCombat.getBlockers(c).size();
            }
            result.blockersDeclared = blockers;

            // --- main phase decision (spell selection over a full hand) ---
            game = buildBoard(size);
            ai = game.getPlayers().get(1);
            t0 = System.nanoTime();
            ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
            spell[run] = System.nanoTime() - t0;
        }

        result.attackNanos = median(attack);
        result.blockNanos = median(block);
        result.spellNanos = median(spell);
        return result;
    }

    private static long median(final long[] values) {
        final long[] copy = values.clone();
        java.util.Arrays.sort(copy);
        return copy[copy.length / 2];
    }

    /**
     * Builds a game with {@code size} creatures per side, lands to cast things with,
     * and a hand of castable spells so the main-phase decision has real work to do.
     */
    private Game buildBoard(final int size) {
        final Game game = initAndCreateGame();
        final Player human = game.getPlayers().get(0);
        final Player ai = game.getPlayers().get(1);

        for (int i = 0; i < size; i++) {
            final Card attacker = addCard(ATTACKER_POOL[i % ATTACKER_POOL.length], human);
            attacker.setSickness(false);
            final Card blocker = addCard(BLOCKER_POOL[i % BLOCKER_POOL.length], ai);
            blocker.setSickness(false);
        }

        // mana and a hand to consider, so main-phase evaluation isn't trivially empty
        for (int i = 0; i < 12; i++) {
            addCard("Forest", ai);
            addCard("Mountain", ai);
        }
        for (int i = 0; i < 7; i++) {
            addCardToZone(ATTACKER_POOL[i % ATTACKER_POOL.length], ai, ZoneType.Hand);
        }

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);
        return game;
    }

    /**
     * Runs one large board with the profiler on and prints the per-section breakdown,
     * showing which AI internals dominate rather than just the totals above.
     */
    @Test(timeOut = 1800000)
    public void profileLargeBoardBreakdown() {
        final boolean wasEnabled = PerfProfiler.isEnabled();
        PerfProfiler.setEnabled(true);
        PerfProfiler.reset();
        try {
            final Game game = buildBoard(40);
            final Player ai = game.getPlayers().get(1);
            final Player human = game.getPlayers().get(0);

            ((PlayerControllerAi) ai.getController()).getAi().getPredictedCombat();
            PerfProfiler.dumpAndReset("declareAttackers, 40 creatures/side");

            final Combat combat = new Combat(human);
            for (final Card c : human.getCreaturesInPlay()) {
                combat.addAttacker(c, ai);
            }
            ((PlayerControllerAi) ai.getController()).getAi().declareBlockersFor(ai, combat);
            PerfProfiler.dumpAndReset("declareBlockers, 40 creatures/side");

            ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
            PerfProfiler.dumpAndReset("chooseSpellAbilityToPlay, 40 creatures/side");
        } finally {
            PerfProfiler.setEnabled(wasEnabled);
        }
    }
}
