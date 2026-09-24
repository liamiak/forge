package forge.ai;

import java.util.List;

import forge.StaticData;
import forge.game.Game;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.card.sticker.Sticker;
import forge.game.card.sticker.StickerKind;
import forge.game.card.sticker.StickerSheet;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * The stickers a player may choose from, and the first card that places one.
 */
public class StickerCardTest extends AITest {

    private Card giveSheet(Player p, String sheetName) {
        PaperCard pc = StaticData.instance().getVariantCards().getCard(sheetName);
        assertNotNull(pc, "no such sticker sheet: " + sheetName);
        Card sheet = Card.fromPaperCard(pc, p);
        p.getZone(ZoneType.StickerSheets).add(sheet);
        return sheet;
    }

    /** CR 123.3 - the pool is what is on the revealed sheets and not already on something. */
    @Test
    public void testAvailableStickersExcludePlacedOnes() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        giveSheet(p, "Eldrazi Guacamole Tightrope");

        List<Sticker> before = StickerSheet.getAvailableStickers(p);
        // Ability stickers carry no Forge ability yet, so they are not offered.
        assertEquals(before.size(), 6, "three name and three art stickers, no ability or P/T yet");

        Card bear = addCard("Grizzly Bears", p);
        Sticker taken = before.get(0);
        bear.addSticker(new forge.game.card.sticker.AppliedSticker(taken, game.getNextTimestamp(), 0));

        List<Sticker> after = StickerSheet.getAvailableStickers(p);
        assertEquals(after.size(), 5, "the placed sticker is no longer available");
        assertFalse(after.stream().anyMatch(s -> s.getSlot().equals(taken.getSlot())
                && s.getSheet().equals(taken.getSheet())));
    }

    /** CR 123.3c - a sticker whose ticket cost the player cannot pay is not a legal choice. */
    @Test
    public void testTicketCostGatesAvailability() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card sheet = giveSheet(p, "Eldrazi Guacamole Tightrope");

        int cheapestPT = StickerSheet.getStickers(sheet).stream()
                .filter(s -> s.getKind() == StickerKind.PT)
                .mapToInt(Sticker::getTickets).min().orElseThrow();
        assertTrue(cheapestPT > 0);

        assertFalse(StickerSheet.getAvailableStickers(p).stream().anyMatch(s -> s.getKind() == StickerKind.PT),
                "with no tickets, no P/T sticker is affordable");

        p.setCounters(CounterEnumType.TICKET, cheapestPT, p, false);
        assertTrue(StickerSheet.getAvailableStickers(p).stream().anyMatch(s -> s.getKind() == StickerKind.PT),
                "with tickets, a P/T sticker becomes a legal choice");
    }

    /** CR 123.5 - a sticker on a card that goes to a hidden zone is on nothing, so it is free again. */
    @Test
    public void testStickerReturnsToThePoolFromAHiddenZone() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        giveSheet(p, "Eldrazi Guacamole Tightrope");

        Card bear = addCard("Grizzly Bears", p);
        Sticker taken = StickerSheet.getAvailableStickers(p).get(0);
        bear.addSticker(new forge.game.card.sticker.AppliedSticker(taken, game.getNextTimestamp(), 0));
        assertEquals(StickerSheet.getAvailableStickers(p).size(), 5);

        game.getAction().moveTo(ZoneType.Hand, bear, null, null);
        assertEquals(StickerSheet.getAvailableStickers(p).size(), 6,
                "the sticker is on no object now, so it can be chosen again");
    }

    /**
     * _____ Goblin, end to end: the AI plays it, takes a name sticker and the mana its vowels
     * are worth. Forge's first card whose printed name contains blanks.
     */
    @Test
    public void testBlankGoblinTakesANameSticker() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        giveSheet(p, "Eldrazi Guacamole Tightrope");

        Card goblin = addCard("_____ Goblin", p);
        assertEquals(goblin.getName(), "_____ Goblin", "the blanks should survive the card pipeline");

        // The trigger fires on entering the battlefield, so replay the move through GameAction.
        Card entered = game.getAction().moveTo(ZoneType.Battlefield,
                game.getAction().moveTo(ZoneType.Hand, goblin, null, null), null, null);
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        while (!game.getStack().isEmpty()) {
            game.getStack().resolveStack();
        }

        assertTrue(entered.isStickered(), "the goblin should have taken a name sticker");
        assertEquals(entered.getStickers().size(), 1);
        assertEquals(entered.getStickers().get(0).getKind(), StickerKind.NAME);

        String word = entered.getStickers().get(0).getSticker().getWord();
        assertTrue(entered.getName().contains(word),
                "the sticker's word should be in the name: " + entered.getName());
        assertTrue(entered.getName().contains("Goblin"));
    }

    /** Each of these reads the vowel count of the name sticker it just placed. */
    @Test
    public void testVowelCountingCards() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        giveSheet(p, "Eldrazi Guacamole Tightrope");

        // Eldrazi/Guacamole/Tightrope have 3, 4 and 3 unique vowels; the AI takes the richest.
        int startLife = p.getLife();
        Card bird = playAndResolve(game, p, "_____ Bird Gets the Worm");
        int vowels = uniqueVowels(bird.getStickers().get(0).getSticker().getWord());
        assertEquals(p.getLife() - startLife, vowels, "life gained should be the sticker's unique vowels");

        // Wizards of the _____ digs as deep as the sticker's vowels, and DigAi declines any dig
        // before main 2 that names no DestinationZone - so ask for it in main 2.
        for (int i = 0; i < 10; i++) {
            addCardToZone("Mountain", p, ZoneType.Library);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        playAndResolve(game, p, "Wizards of the _____");

        Card saurus = playAndResolve(game, p, "_____-o-saurus");
        assertEquals(saurus.getCounters(CounterEnumType.P1P1),
                uniqueVowels(saurus.getStickers().get(0).getSticker().getWord()),
                "+1/+1 counters should be the sticker's unique vowels");
    }

    private static int uniqueVowels(String word) {
        int n = 0;
        for (char v : "AEIOUY".toCharArray()) {
            if (word.toUpperCase().indexOf(v) >= 0) {
                n++;
            }
        }
        return n;
    }

    /** Puts a card onto the battlefield through GameAction so its enters trigger fires. */
    private Card playAndResolve(Game game, Player p, String name) {
        Card c = addCardToZone(name, p, ZoneType.Hand);
        Card entered = game.getAction().moveTo(ZoneType.Battlefield, c, null, null);
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        while (!game.getStack().isEmpty()) {
            game.getStack().resolveStack();
        }
        assertTrue(entered.isStickered(), name + " should have taken a name sticker");
        return entered;
    }

    /**
     * Goblin Airbrusher and Wee Champion both read "whenever you place a sticker ... if it's an
     * art sticker, instead". Both branches are exercised on the same board, so neither can pass
     * because no trigger fired at all.
     */
    @Test
    public void testStickerPlacedTriggerTellsArtApart() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        giveSheet(p, "Eldrazi Guacamole Tightrope");
        addCard("Goblin Airbrusher", p);
        Card champion = addCard("Wee Champion", p);
        Card bear = addCard("Grizzly Bears", p);
        // addCard puts the card straight into the zone, so its triggers are not registered as
        // active until the game next checks state effects.
        game.getAction().checkStateEffects(true);

        Sticker name = firstOfKind(p, StickerKind.NAME);
        placeAndResolve(game, bear, name, p);
        assertEquals(countCardsWithName(game, "Treasure Token"), 1, "a name sticker makes one Treasure");
        assertEquals(champion.getCounters(CounterEnumType.P1P1), 0, "a name sticker is not a counter");
        assertEquals(champion.getNetPower(), 2, "a name sticker is +1/+1 until end of turn");

        Sticker art = firstOfKind(p, StickerKind.ART);
        placeAndResolve(game, bear, art, p);
        assertEquals(countCardsWithName(game, "Treasure Token"), 3, "an art sticker makes two more Treasures");
        assertEquals(champion.getCounters(CounterEnumType.P1P1), 1, "an art sticker is a counter");
    }

    private Sticker firstOfKind(Player p, StickerKind kind) {
        for (Sticker s : StickerSheet.getAvailableStickers(p)) {
            if (s.getKind() == kind) {
                return s;
            }
        }
        throw new AssertionError("no available " + kind + " sticker");
    }

    /** Places a sticker the way the effect does, and lets the triggers it fires resolve. */
    private void placeAndResolve(Game game, Card target, Sticker sticker, Player p) {
        target.addSticker(new forge.game.card.sticker.AppliedSticker(sticker, game.getNextTimestamp(), 0));
        java.util.Map<forge.game.ability.AbilityKey, Object> runParams =
                forge.game.ability.AbilityKey.newMap();
        runParams.put(forge.game.ability.AbilityKey.Card, target);
        runParams.put(forge.game.ability.AbilityKey.Player, p);
        runParams.put(forge.game.ability.AbilityKey.StickerKind, sticker.getKind());
        game.getTriggerHandler().runTrigger(forge.game.trigger.TriggerType.StickerPlaced, runParams, false);
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        while (!game.getStack().isEmpty()) {
            game.getStack().resolveStack();
        }
    }

    /** Count$CardStickers.Name, and the withStickerKind property both targeting uses. */
    @Test
    public void testStickerCountAndProperty() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        giveSheet(p, "Eldrazi Guacamole Tightrope");
        Card trespasser = addCard("_____ _____ _____ Trespasser", p);
        game.getAction().checkStateEffects(true);

        assertEquals(trespasser.getNetPower(), 2, "no stickers yet");
        for (Sticker s : StickerSheet.getAvailableStickers(p)) {
            if (s.getKind() == StickerKind.NAME) {
                trespasser.addSticker(new forge.game.card.sticker.AppliedSticker(
                        s, game.getNextTimestamp(), 0));
                break;
            }
        }
        // The pump reads Count$CardStickers.Name, so it should now be worth +1/+0.
        SpellAbility pump = null;
        for (SpellAbility sa : trespasser.getSpellAbilities()) {
            if (sa.getApi() == forge.game.ability.ApiType.Pump) {
                pump = sa;
            }
        }
        assertNotNull(pump, "the pump ability should be on the card");
        assertEquals(AbilityUtils.calculateAmount(trespasser, "X", pump), 1,
                "one name sticker is worth one");

        // Sword-Swallowing Seraph can only target a creature that has a name sticker.
        Card seraph = addCard("Sword-Swallowing Seraph", p);
        Card plain = addCard("Grizzly Bears", p);
        game.getAction().checkStateEffects(true);
        assertTrue(trespasser.isValid("Creature.stickeredWith Name", p, seraph, null),
                "the stickered creature matches stickeredWith Name");
        assertFalse(plain.isValid("Creature.stickeredWith Name", p, seraph, null),
                "an unstickered creature does not");
    }

    /** Every sticker card implemented so far loads and keeps its rules text. */
    @Test
    public void testAllStickerCardsLoad() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        String[] names = {
            "_____ Goblin", "_____ Bird Gets the Worm", "_____-o-saurus", "Wizards of the _____",
            "Goblin Airbrusher", "Wee Champion", "_____ _____ _____ Trespasser",
            "Sword-Swallowing Seraph", "Baaallerina", "A Good Day to Pie",
            "Make a _____ Splash", "_____ Balls of Fire",
            "Aerialephant", "Carnival Carnivore", "Chicken Troupe", "Glitterflitter",
            "Minotaur de Force", "Stiltstrider", "Ticketomaton",
            "Big Winner", "Croakid Amphibonaut", "Grabby Tabby", "Sanguine Sipper", "Scared Stiff",
        };
        for (String name : names) {
            Card c = addCardToZone(name, p, ZoneType.Hand);
            assertEquals(c.getName(), name, name + " should keep its printed name");
            assertTrue(!c.getTriggers().isEmpty() || !c.getSpellAbilities().isEmpty(),
                    name + " should have a trigger or an ability");
        }
    }

    /** Count$CardStickers.NameLetter counts one letter across the name stickers on a card. */
    @Test
    public void testNameLetterCount() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        giveSheet(p, "Eldrazi Guacamole Tightrope");
        Card fire = addCard("_____ Balls of Fire", p);
        game.getAction().checkStateEffects(true);

        assertEquals(AbilityUtils.calculateAmount(fire, "X", fire.getTriggers().get(1)), 0,
                "no stickers, no o's");

        // "Guacamole" carries one o; "Tightrope" carries one too.
        for (Sticker s : StickerSheet.getAvailableStickers(p)) {
            if (s.getKind() == StickerKind.NAME && s.getWord().toLowerCase().indexOf('o') >= 0) {
                fire.addSticker(new forge.game.card.sticker.AppliedSticker(
                        s, game.getNextTimestamp(), 0));
                int expected = s.getWord().toLowerCase().replaceAll("[^o]", "").length();
                assertEquals(AbilityUtils.calculateAmount(fire, "X", fire.getTriggers().get(1)), expected,
                        "o's in " + s.getWord());
                return;
            }
        }
        throw new AssertionError("the sheet should have a name sticker containing an o");
    }

    /** "has X as long as you control a stickered permanent" turns on and off with the sticker. */
    @Test
    public void testStickeredPermanentStatic() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        giveSheet(p, "Eldrazi Guacamole Tightrope");
        Card sipper = addCard("Sanguine Sipper", p);
        Card bear = addCard("Grizzly Bears", p);
        game.getAction().checkStateEffects(true);
        assertFalse(sipper.hasKeyword("Lifelink"), "nothing is stickered yet");

        bear.addSticker(new forge.game.card.sticker.AppliedSticker(
                firstOfKind(p, StickerKind.ART), game.getNextTimestamp(), 0));
        game.getAction().checkStateEffects(true);
        assertTrue(sipper.hasKeyword("Lifelink"), "a stickered permanent grants lifelink");

        // Off the battlefield to a hidden zone, the sticker goes with it (CR 123.5).
        game.getAction().moveTo(ZoneType.Hand, bear, null, null);
        game.getAction().checkStateEffects(true);
        assertFalse(sipper.hasKeyword("Lifelink"), "no stickered permanent, no lifelink");
    }

    /**
     * "You get {TK}{TK}, then you may put a sticker on a nonland permanent you own" - the tickets
     * arrive and are then available to spend on the sticker, which is the point of the card.
     */
    @Test
    public void testTicketThenStickerCard() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        giveSheet(p, "Eldrazi Guacamole Tightrope");
        assertEquals(p.getCounters(CounterEnumType.TICKET), 0);

        Card c = addCardToZone("Stiltstrider", p, ZoneType.Hand);
        Card entered = game.getAction().moveTo(ZoneType.Battlefield, c, null, null);
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        while (!game.getStack().isEmpty()) {
            game.getStack().resolveStack();
        }

        assertTrue(entered.isStickered(), "Stiltstrider should have stickered something it owns");
        int spent = entered.getStickers().get(0).getSticker().getTickets();
        assertEquals(p.getCounters(CounterEnumType.TICKET), 2 - spent,
                "two tickets gained, minus whatever the sticker cost");
        assertTrue(spent > 0, "with two tickets in hand the AI should take a sticker worth paying for");
    }
}
