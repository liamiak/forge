# Profiling data: where AI combat time goes on heavy boards

Sharing measurements from profiling AI decisions on large boards, in case they're
useful. These are in *different* code paths from PR #10507 (which touches
`Card`/`CardState`/`KeywordCollection`/`AiAttackController`) — the two look
complementary rather than overlapping.

## Method

Temporary instrumentation recording call counts plus inclusive/exclusive time per
section, with per-thread nesting so a caller's self-time excludes its profiled
children. Driven by a synthetic benchmark that builds boards of 5/10/20/40
creatures per side and times `declareAttackers`, `declareBlockers` and
`chooseSpellAbilityToPlay`. Decision parity was checked by asserting the AI
declares the same attackers/blockers before and after any change.

## Scaling (baseline, before any fix)

| creatures/side | declare attackers | declare blockers | main phase |
|---|---|---|---|
| 5  | 215 ms | 38 ms | 17 ms |
| 10 | 1,005 ms | 123 ms | 14 ms |
| 20 | 7,677 ms | 1,236 ms | 24 ms |
| 40 | 17,320 ms | 15,402 ms | 43 ms |

8× the board → 81× (attack) and 405× (block) slower; main-phase spell selection
grows only 2.5×. **Combat is the cost; spell selection is not.**

## Top self-time at 40 creatures/side (declare attackers)

```
CombatUtil.canBlock(pair)                10,824 calls   9,657 ms self   892 us/call
ComputerUtilCombat.canDestroyBlocker      1,151 calls   1,508 ms
StaticAbilityCantAttackBlock.cantBlockBy 13,738 calls   1,410 ms
ComputerUtilCombat.lifeInDanger              79 calls   1,254 ms  (15.9 ms/call)
```

A single "can this creature block that one?" check costing ~892 µs turned out to
be three nested redundancies:

1. **`CombatUtil.canBlock(attacker, blocker, combat)` calls
   `mustBlockAnAttacker(blocker, combat, null)`**, which loops over *every*
   attacker in combat. Its result depends only on the **blocker** — the attacker
   being tested never enters into it (per-attacker lure/must-be-blocked checks
   happen in the caller's guard chain, before that call). So it is O(attackers)
   per pair, i.e. O(attackers² × blockers) overall.

2. **Inside that loop, `getBlockCost(game, blocker, attacker)` scans every static
   ability in play** and allocates a `Cost` per call — before the cheap check
   that would have skipped the attacker anyway.

3. **`Game.getCardsIn(Iterable<ZoneType>)` allocates a fresh `CardCollection` and
   copies every card from every listed zone on each call.** Every static-ability
   helper reaches for `ZoneType.STATIC_ABILITIES_SOURCE_ZONES` through it
   (`getBlockCost`, `cantBlockBy`, `assignCombatDamageAsUnblocked`, damage
   prevention in `GameEntity`, …). So **every static-ability check copies the
   whole board.**

Related: `ComputerUtilCard.evaluateCreature` measures ~235 µs/call and is called
1,642 times in one attack decision, because it performs three of those full-board
static scans per creature (`cantBlockBy` plus two `assignCombatDamageAsUnblocked`
calls). It's uncached across ~285 call sites.

## Two changes tried, and what they gave

Both are in `forge-game` shared code, so they help human play too, and neither
alters any decision.

**Cache the static-ability source cards.** `Game` now keeps the
`STATIC_ABILITIES_SOURCE_ZONES` list against a counter bumped by every zone
mutation, so the thousands of checks between two zone changes share one list.
(Note: the version bump has to sit at the `Zone` mutation points rather than in
`Zone.onChanged()`, because `PlayerZone` overrides that without calling super.)

**Reorder two guards in `mustBlockAnAttacker`.** It tested `getBlockCost` (full
board scan) before `attackerLureSatisfied` (a few keyword tests). Both guard the
same `continue`, so swapping them is behaviour-preserving and lets the common
case — an attacker that places no requirement on this blocker — skip out before
the scan. `getBlockCost` also allocates its `Cost` lazily now.

Result at 40 creatures/side:

| | before | after |
|---|---|---|
| `canBlock(pair)` | 892 µs | **128 µs** (same call count) |
| declare attackers | 17.3 s | **9.0 s** |
| declare blockers | 15.4 s | **7.1 s** |

Verified unchanged: identical `canBlock` call counts, the same blockers declared
at every board size, and added tests covering lure / "must be blocked if able" /
`MustBeBlockedBy`, since those paths aren't exercised by boards without block
requirements.

## Remaining hot spots after those two

1. `ComputerUtilCombat.lifeInDanger` — ~17 ms/call; a full combat damage
   prediction, called repeatedly across `AiBlockController`'s three passes.
2. `canDestroyBlocker` / `canDestroyAttacker` — ~1 ms/call, from the trigger
   collection rebuilds in the `predict*BonusOf*` helpers.
3. `CardState.getStaticAbilities()` allocates a new `FCollection` **and** runs
   `updateStaticAbilities()` on every call — roughly 1.4 M calls per decision.
   This looks like the same ground PR #10507's later fixes are on, and the
   `ICardTraitChanges` / `keywordsCache` direction suggested in review there
   seems like the right shape for it.

Happy to share the instrumentation and benchmark harness, or to open the two
changes above as a small separate PR if that's useful — they don't touch any of
the files in #10507.
