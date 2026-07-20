package forge.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TEMPORARY diagnostic instrumentation for investigating AI decision slowness.
 * Not intended to be merged - see the ai-perf-investigation branch.
 *
 * Records call count, inclusive time and exclusive (self) time per named key.
 * Nesting is tracked per thread, so a parent's self-time excludes the profiled
 * children it calls. Disabled by default; enable with -Dforge.perfprofile=true
 * or {@link #setEnabled(boolean)}.
 */
public final class PerfProfiler {
    private static volatile boolean enabled = Boolean.getBoolean("forge.perfprofile");

    private PerfProfiler() { }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(final boolean value) {
        enabled = value;
    }

    public static final class Stat {
        final AtomicLong calls = new AtomicLong();
        final AtomicLong inclusiveNanos = new AtomicLong();
        final AtomicLong exclusiveNanos = new AtomicLong();
    }

    private static final Map<String, Stat> STATS = new ConcurrentHashMap<>();

    private static final class Frame {
        final String key;
        final long start;
        long childNanos;

        Frame(final String key, final long start) {
            this.key = key;
            this.start = start;
        }
    }

    private static final ThreadLocal<Deque<Frame>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    /** Marks the start of a profiled section. Pair with {@link #exit(String)} in a finally block. */
    public static void enter(final String key) {
        if (!enabled) {
            return;
        }
        STACK.get().push(new Frame(key, System.nanoTime()));
    }

    public static void exit(final String key) {
        if (!enabled) {
            return;
        }
        final Deque<Frame> stack = STACK.get();
        final Frame frame = stack.peek();
        if (frame == null || !frame.key.equals(key)) {
            return; // unbalanced (e.g. profiling switched on mid-call) - ignore rather than corrupt
        }
        stack.pop();
        final long elapsed = System.nanoTime() - frame.start;
        final Stat stat = STATS.computeIfAbsent(key, k -> new Stat());
        stat.calls.incrementAndGet();
        stat.inclusiveNanos.addAndGet(elapsed);
        stat.exclusiveNanos.addAndGet(elapsed - frame.childNanos);
        final Frame parent = stack.peek();
        if (parent != null) {
            parent.childNanos += elapsed;
        }
    }

    /** Counts an event without timing it (e.g. cache hits). */
    public static void count(final String key) {
        if (!enabled) {
            return;
        }
        STATS.computeIfAbsent(key, k -> new Stat()).calls.incrementAndGet();
    }

    public static void reset() {
        STATS.clear();
    }

    /**
     * Prints the accumulated report with a context label (turn number, board size, ...)
     * and clears the counters so the next report covers only the next interval.
     */
    public static void dumpAndReset(final String context) {
        if (!enabled) {
            return;
        }
        final String report = report(context);
        STATS.clear();
        if (report != null) {
            System.out.println(report);
        }
    }

    public static String report(final String context) {
        if (STATS.isEmpty()) {
            return null;
        }
        final List<Map.Entry<String, Stat>> entries = new ArrayList<>(STATS.entrySet());
        entries.sort(Comparator.comparingLong((Map.Entry<String, Stat> e) -> e.getValue().exclusiveNanos.get()).reversed());

        final StringBuilder sb = new StringBuilder();
        sb.append("\n[AIPERF] ").append(context).append('\n');
        sb.append(String.format("%-46s %10s %12s %12s %10s%n", "section", "calls", "self ms", "total ms", "self us/call"));
        for (final Map.Entry<String, Stat> entry : entries) {
            final Stat s = entry.getValue();
            final long calls = s.calls.get();
            final long self = s.exclusiveNanos.get();
            sb.append(String.format("%-46s %10d %12.1f %12.1f %10.2f%n",
                    entry.getKey(), calls, self / 1e6, s.inclusiveNanos.get() / 1e6,
                    calls == 0 ? 0 : self / 1000.0 / calls));
        }
        return sb.toString();
    }
}
