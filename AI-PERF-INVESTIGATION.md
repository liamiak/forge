# AI combat performance investigation — parked work

**Status: parked 2026-07-20**, pending upstream PR
[#10507 "AI+engine heavy board performance optimizations"](https://github.com/Card-Forge/forge/pull/10507)
(MostCromulent). Deliberately not submitted upstream, to avoid duplicating or
colliding with that effort. Nothing here has been shared with the Forge devs.

This branch is a scratch//reference branch. It is **not** PR-ready: the first
commit is temporary instrumentation that must be stripped before any submission.

## What is on this branch

| commit | contents |
|---|---|
| `dfddabf6` | **TEMPORARY** `PerfProfiler` (forge-core) + enter/exit hooks through the AI combat paths + per-turn/per-combat dumps in `PhaseHandler` + `AiPerformanceBenchmark`. Strip before any PR. |
| `8b3d4189` | The two actual fixes, plus `BlockRequirementTests`. Self-contained and PR-able once rebased. |

`ai-combat-profiling-findings.md` (repo root on this branch) holds the full
measurements and analysis, written up as if for the devs. It has **not** been
posted anywhere.

## The two fixes (both in `forge-game`, so they help human play too)

1. **`Game.getStaticAbilitySourceCards()`** — caches the
   `STATIC_ABILITIES_SOURCE_ZONES` card list against a counter bumped by every
   zone mutation. Previously every static-ability check allocated and copied the
   whole board. Note the version bump must live at the `Zone` mutation points,
   **not** in `Zone.onChanged()`, because `PlayerZone` overrides that without
   calling super.
2. **Guard reorder in `CombatUtil.mustBlockAnAttacker`** — test the cheap
   `attackerLureSatisfied` before the board-scanning `getBlockCost`; both guard
   the same `continue`, so this is behaviour-preserving. `getBlockCost` also
   allocates its `Cost` lazily now.

Measured at 40 creatures/side: `canBlock(pair)` 892 µs → 128 µs (same call
count), declare attackers 17.3 s → 9.0 s, declare blockers 15.4 s → 7.1 s.
Decisions verified unchanged (same blockers declared at every board size; new
tests cover lure / must-be-blocked / `MustBeBlockedBy`).

## Overlap check against #10507 (as of 2026-07-20)

That PR touches `AiAttackController`, `Card`, `CardFactoryUtil`, `CardState`,
`KeywordCollection`. This branch touches `Game`, `Zone`, `CombatUtil`,
`StaticAbilityCantAttackBlock`, `StaticAbilityAssignCombatDamageAsUnblocked`
(plus the AI files, instrumentation only). **No file overlap.**

Its later fixes do target `CardState.getStaticAbilities()`, which is the largest
remaining hot spot measured here — so that specific area is theirs, not ours.

## How to pick this back up

1. Check whether #10507 (or a successor) landed, and what it changed.
2. `git rebase upstream/master` this branch. Expect conflicts if they touched
   the same combat paths.
3. Re-run the benchmark and compare against the numbers above — if their work
   already covers this ground, these fixes may no longer be worth much.
   ```
   mvn -pl forge-gui-desktop -am test -Dtest=AiPerformanceBenchmark
   ```
4. If still worthwhile: drop commit `dfddabf6` (instrumentation), keep the fixes
   and `BlockRequirementTests`, and open as a small standalone PR.

## Remaining hot spots measured (after the two fixes)

1. `ComputerUtilCombat.lifeInDanger` — ~17 ms/call, repeated across
   `AiBlockController`'s three passes.
2. `canDestroyBlocker` / `canDestroyAttacker` — ~1 ms/call, from trigger
   collection rebuilds in the `predict*BonusOf*` helpers.
3. `CardState.getStaticAbilities()` — allocates a new `FCollection` and runs
   `updateStaticAbilities()` per call, ~1.4 M calls per decision. **Contested by
   #10507 — leave alone.**
