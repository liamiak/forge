package forge.ai;

import java.util.ArrayList;
import java.util.List;

import forge.StaticData;
import forge.deck.DeckSection;
import forge.game.Game;
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityKey;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.card.sticker.AppliedSticker;
import forge.game.card.sticker.Sticker;
import forge.game.card.sticker.StickerKind;
import forge.game.card.sticker.StickerSheet;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.ability.ApiType;
import forge.game.ability.effects.CharmEffect;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * The Unfinity cards that hand out tickets and stickers without needing anything the engine does
 * not already do. A card script's abilities are parsed when the card is built, so building each
 * one is itself the check that the script is well formed.
 */
public class StickerTicketCardTest extends AITest {

    private static final List<String> CARDS = List.of(
            "Finishing Move", "Robo-Piñata", "Command Performance", "Costume Shop",
            "Done for the Day", "Park Bleater", "Lineprancers", "Tusk and Whiskers",
            "Wolf in _____ Clothing", "Fight the _____ Fight",
            "Ambassador Blorpityblorpboop", "Roxi, Publicist to the Stars",
            "Pin Collection", "Clandestine Chameleon", "_____ _____ Rocketship", "Wicker Picker",
            "Last Voyage of the _____");

    private Card giveSheet(Player p, String sheetName) {
        PaperCard pc = StaticData.instance().getVariantCards().getCard(sheetName);
        assertNotNull(pc, "no such sticker sheet: " + sheetName);
        Card sheet = Card.fromPaperCard(pc, p);
        p.getZone(ZoneType.StickerSheets).add(sheet);
        return sheet;
    }

    private Card playAndResolve(Game game, Player p, String name) {
        Card entered = game.getAction().moveTo(ZoneType.Battlefield,
                addCardToZone(name, p, ZoneType.Hand), null, null);
        // A card's own triggers are not active until the game next checks state effects.
        game.getAction().checkStateEffects(true);
        // Twice round: a reflexive "when you do" trigger is only registered while the ability
        // that spawned it resolves, so one pass never sees it.
        for (int i = 0; i < 2; i++) {
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            while (!game.getStack().isEmpty()) {
                game.getStack().resolveStack();
            }
        }
        return entered;
    }

    /** Costume Shop is an Attraction, which Forge keeps with the other variant cards. */
    private PaperCard paper(String name) {
        PaperCard pc = StaticData.instance().getCommonCards().getCard(name);
        return pc != null ? pc : StaticData.instance().getVariantCards().getCard(name);
    }

    @Test
    public void testEveryCardBuilds() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        for (String name : CARDS) {
            PaperCard pc = paper(name);
            assertNotNull(pc, name + " is not in the card pool");
            Card c = Card.fromPaperCard(pc, p);
            assertTrue(c.getSpellAbilities().size() + c.getTriggers().size()
                    + c.getKeywords().size() > 0, name + " built with nothing on it");
        }
    }

    /** Costume Shop is an Attraction, so it belongs to the attraction deck, not the main one. */
    @Test
    public void testCostumeShopIsAnAttraction() {
        PaperCard pc = paper("Costume Shop");
        assertNotNull(pc);
        assertEquals(DeckSection.matchingSection(pc), DeckSection.Attractions);
        assertTrue(pc.getRules().getType().isAttraction());
    }

    /** Lineprancers asks for a power and toughness sticker specifically, not any sticker. */
    @Test
    public void testLineprancersPlacesAPowerToughnessSticker() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        giveSheet(p, "Eldrazi Guacamole Tightrope");

        Card lineprancers = playAndResolve(game, p, "Lineprancers");
        assertTrue(lineprancers.isStickered(), "the only creature it owns is itself");
        Sticker placed = lineprancers.getStickers().get(0).getSticker();
        assertEquals(placed.getKind(), StickerKind.PT, "Kind$ PT should restrict the choice");
        assertEquals(p.getCounters(CounterEnumType.TICKET), 2 - placed.getTickets(),
                "two tickets gained, minus what the sticker cost");
    }

    /** Park Bleater pays out for other creatures, not for itself. */
    @Test
    public void testParkBleaterTicketsOnlyForOtherCreatures() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        playAndResolve(game, p, "Park Bleater");
        assertEquals(p.getCounters(CounterEnumType.TICKET), 0, "it does not pay for itself");

        playAndResolve(game, p, "Grizzly Bears");
        assertEquals(p.getCounters(CounterEnumType.TICKET), 1, "another creature you own pays {TK}");
    }

    /** Tusk and Whiskers counts ability stickers only, and rewards the creature they went on. */
    @Test
    public void testTuskAndWhiskersCountersTheStickeredCreature() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        addCard("Tusk and Whiskers", p);
        Card bear = addCard("Grizzly Bears", p);
        game.getAction().checkStateEffects(true);

        placeSticker(game, p, bear, StickerKind.ART);
        assertEquals(bear.getCounters(CounterEnumType.P1P1), 0, "an art sticker is not an ability");

        placeSticker(game, p, bear, StickerKind.ABILITY);
        assertEquals(bear.getCounters(CounterEnumType.P1P1), 1, "an ability sticker is");
    }

    /**
     * Robo-Pinata's dies trigger is modal. The trigger itself is not exercised here: this
     * harness does not fire dies triggers - a plain Forge card with the same shape behaves the
     * same way - so what is checked is that both modes were read off the script.
     */
    @Test
    public void testRoboPinataDiesTriggerIsModal() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card pinata = Card.fromPaperCard(paper("Robo-Piñata"), p);

        Trigger dies = null;
        for (Trigger t : pinata.getTriggers()) {
            if (t.getMode() == TriggerType.ChangesZone) {
                dies = t;
            }
        }
        assertNotNull(dies, "Robo-Pinata should have a dies trigger");
        assertEquals(dies.getParam("Destination"), "Graveyard");

        SpellAbility charm = dies.ensureAbility();
        assertEquals(charm.getApi(), ApiType.Charm);
        assertEquals(CharmEffect.makePossibleOptions(charm).size(), 2, "both modes should be there");
    }

    /**
     * Wolf in _____ Clothing's reflexive trigger reads the sticker it just placed: X is the
     * unique vowels in that word, and the targets are chosen after the sticker exists.
     */
    @Test
    public void testWolfInBlankClothingShrinksUpToX() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Player opp = game.getPlayers().get(1);
        giveSheet(p, "Eldrazi Guacamole Tightrope");
        List<Card> victims = addCards("Grizzly Bears", 4, opp);

        Card wolf = playAndResolve(game, p, "Wolf in _____ Clothing");
        assertTrue(wolf.isStickered(), "it should have taken the free name sticker");
        assertEquals(wolf.getStickers().get(0).getKind(), StickerKind.NAME);

        String word = wolf.getStickers().get(0).getSticker().getWord().toUpperCase();
        int vowels = 0;
        for (char v : "AEIOUY".toCharArray()) {
            if (word.indexOf(v) >= 0) {
                vowels++;
            }
        }
        long shrunk = victims.stream().filter(c -> c.getNetToughness() < 2).count();
        assertTrue(shrunk > 0, "the reflexive trigger should have shrunk something");
        assertTrue(shrunk <= vowels,
                "at most one creature per unique vowel in \"" + word + "\", but " + shrunk + " were shrunk");
    }

    /** Fight the _____ Fight's pump counts only the long name stickers on the Aura itself. */
    @Test
    public void testFightTheBlankFightCountsLongStickers() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card bear = addCard("Grizzly Bears", p);
        Card aura = addCard("Fight the _____ Fight", p);
        aura.attachToEntity(bear, null);
        game.getAction().checkStateEffects(true);
        assertEquals(bear.getNetToughness(), 2, "no stickers, no bonus");

        Card sheet = giveSheet(p, "Eldrazi Guacamole Tightrope");
        List<Sticker> names = StickerSheet.getStickers(sheet).stream()
                .filter(x -> x.getKind() == StickerKind.NAME).toList();
        Sticker seven = names.stream().filter(x -> x.getWord().length() == 7).findFirst().orElseThrow();
        Sticker nine = names.stream().filter(x -> x.getWord().length() == 9).findFirst().orElseThrow();

        aura.addSticker(new AppliedSticker(seven, game.getNextTimestamp()));
        game.getAction().checkStateEffects(true);
        assertEquals(bear.getNetToughness(), 2, "seven letters is under the bar");

        aura.addSticker(new AppliedSticker(nine, game.getNextTimestamp()));
        game.getAction().checkStateEffects(true);
        assertEquals(bear.getNetToughness(), 4, "nine letters is +0/+2");
    }

    /** Roxi counts art stickers on the battlefield and in the graveyard, and nothing else. */
    @Test
    public void testRoxiCountsArtStickersInTwoZones() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card sheet = giveSheet(p, "Eldrazi Guacamole Tightrope");
        Card roxi = addCard("Roxi, Publicist to the Stars", p);
        game.getAction().checkStateEffects(true);
        assertEquals(roxi.getNetPower(), 0, "no art stickers anywhere");

        List<Sticker> art = StickerSheet.getStickers(sheet).stream()
                .filter(x -> x.getKind() == StickerKind.ART).toList();
        Card bear = addCard("Grizzly Bears", p);
        bear.addSticker(new AppliedSticker(art.get(0), game.getNextTimestamp()));
        game.getAction().checkStateEffects(true);
        assertEquals(roxi.getNetPower(), 1, "a stickered permanent you control");

        Card corpse = addCardToZone("Grizzly Bears", p, ZoneType.Graveyard);
        corpse.addSticker(new AppliedSticker(art.get(1), game.getNextTimestamp()));
        game.getAction().checkStateEffects(true);
        assertEquals(roxi.getNetPower(), 2, "plus a stickered card in your graveyard");

        // A name sticker is not an art sticker.
        Card other = addCard("Grizzly Bears", p);
        other.addSticker(new AppliedSticker(StickerSheet.getStickers(sheet).stream()
                .filter(x -> x.getKind() == StickerKind.NAME).findFirst().orElseThrow(),
                game.getNextTimestamp()));
        game.getAction().checkStateEffects(true);
        assertEquals(roxi.getNetPower(), 2, "only art stickers count");
    }

    /**
     * Ambassador Blorpityblorpboop adds up the power and toughness stickers you have out. The
     * numbers are read through the card's own trigger, since "you control" is resolved against
     * the ability's controller.
     */
    @Test
    public void testAmbassadorTotalsStickerPowerAndToughness() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Player opp = game.getPlayers().get(1);
        Card sheet = giveSheet(p, "Eldrazi Guacamole Tightrope");
        List<Sticker> pts = StickerSheet.getStickers(sheet).stream()
                .filter(x -> x.getKind() == StickerKind.PT).toList();

        Card mine = addCard("Grizzly Bears", p);
        mine.addSticker(new AppliedSticker(pts.get(0), game.getNextTimestamp()));
        Card theirs = addCard("Grizzly Bears", opp);
        theirs.addSticker(new AppliedSticker(pts.get(1), game.getNextTimestamp()));
        Card ambassador = addCard("Ambassador Blorpityblorpboop", p);
        game.getAction().checkStateEffects(true);

        SpellAbility animate = null;
        for (Trigger t : ambassador.getTriggers()) {
            if (t.getOverridingAbility() != null && t.getOverridingAbility().getApi() == ApiType.Animate) {
                animate = t.getOverridingAbility();
            }
        }
        assertNotNull(animate, "the begin of combat trigger should animate");
        animate.setActivatingPlayer(p);

        assertEquals(AbilityUtils.calculateAmount(ambassador, "X", animate), pts.get(0).getPower(),
                "only the sticker on a permanent you control");
        assertEquals(AbilityUtils.calculateAmount(ambassador, "Y", animate), pts.get(0).getToughness());

        // And the opponent's sticker is counted for them, not ignored altogether.
        assertEquals(AbilityUtils.calculateAmount(ambassador, "Count$Valid Permanent$StickerPower", animate),
                pts.get(0).getPower() + pts.get(1).getPower());
    }

    /**
     * Pin Collection and Clandestine Chameleon both hand an object the abilities printed on
     * ability stickers sitting on a different object.
     */
    @Test
    public void testStickerAbilitiesCarryToAnotherObject() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card sheet = giveSheet(p, "Ancestral Hot Dog Minotaur");
        Sticker flying = StickerSheet.getStickers(sheet).stream()
                .filter(x -> "Flying".equals(x.getKeywords())).findFirst().orElseThrow();

        Card pins = addCard("Pin Collection", p);
        Card bear = addCard("Grizzly Bears", p);
        pins.attachToEntity(bear, null);
        game.getAction().checkStateEffects(true);
        assertEquals(bear.getNetPower(), 3, "equipped creature gets +1/+1");
        assertFalse(bear.hasKeyword("Flying"), "no sticker on the Equipment yet");

        pins.addSticker(new AppliedSticker(flying, game.getNextTimestamp()));
        game.getAction().checkStateEffects(true);
        assertTrue(bear.hasKeyword("Flying"), "the sticker is on the Equipment, the creature has it");

        // The Chameleon reads stickers on everything else you own, including the graveyard.
        Card chameleon = addCard("Clandestine Chameleon", p);
        game.getAction().checkStateEffects(true);
        assertTrue(chameleon.hasKeyword("Flying"), "an ability sticker on another permanent you own");

        Card corpse = addCardToZone("Grizzly Bears", p, ZoneType.Graveyard);
        Sticker second = StickerSheet.getStickers(sheet).stream()
                .filter(x -> x.getKind() == StickerKind.ABILITY && !x.equals(flying))
                .findFirst().orElseThrow();
        corpse.addSticker(new AppliedSticker(second, game.getNextTimestamp()));
        game.getAction().checkStateEffects(true);
        assertTrue(chameleon.getKeywords().size() > 1
                        || chameleon.getSpellAbilities().size() > 0
                        || chameleon.getTriggers().size() > 0,
                "and one in your graveyard");
    }

    /**
     * Pin Collection places a sticker it does not pay for, and only one it could afford at X.
     * The card's own X comes from how it was cast, so the two parameters are exercised here
     * rather than through the card.
     */
    @Test
    public void testStickerCanBeCappedAndFree() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        giveSheet(p, "Eldrazi Guacamole Tightrope");
        assertEquals(p.getCounters(CounterEnumType.TICKET), 0, "and no tickets to spend");

        Card bear = addCard("Grizzly Bears", p);
        SpellAbility put = AbilityFactory.getAbility(
                "DB$ PutSticker | Kind$ Ability | Defined$ Self | MaxTickets$ 2 | NoTicketCost$ True", bear);
        put.setActivatingPlayer(p);
        AbilityUtils.resolve(put);

        assertTrue(bear.isStickered(), "a sticker it cannot pay for is still placed");
        Sticker placed = bear.getStickers().get(0).getSticker();
        assertEquals(placed.getKind(), StickerKind.ABILITY);
        assertTrue(placed.getTickets() <= 2, "MaxTickets should have kept the 5-ticket one out");
        assertEquals(p.getCounters(CounterEnumType.TICKET), 0, "and nothing was paid");
    }

    /**
     * The Rocketship counts the name stickers on it that begin with the letter its controller
     * chose - not the letters they contain, which is what the existing NameLetter count does.
     */
    @Test
    public void testRocketshipCountsFirstLetters() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card sheet = giveSheet(p, "Eldrazi Guacamole Tightrope");
        Card ship = addCard("_____ _____ Rocketship", p);
        game.getAction().checkStateEffects(true);

        // Eldrazi, Guacamole, Tightrope: one starts with G, and two contain one.
        for (Sticker word : StickerSheet.getStickers(sheet)) {
            if (word.getKind() == StickerKind.NAME) {
                ship.addSticker(new AppliedSticker(word, game.getNextTimestamp()));
            }
        }
        assertEquals(ship.getStickers().size(), 3);

        ship.setChosenType("G");
        assertEquals(AbilityUtils.calculateAmount(ship, "Count$CardStickers.NameStartsWith.ChosenType", null), 1,
                "only Guacamole begins with G");
        assertEquals(AbilityUtils.calculateAmount(ship, "Count$CardStickers.NameLetter.G", null), 2,
                "but two of the three words contain one");

        ship.setChosenType("T");
        assertEquals(AbilityUtils.calculateAmount(ship, "Count$CardStickers.NameStartsWith.ChosenType", null), 1,
                "Tightrope begins with T");
        ship.setChosenType("Q");
        assertEquals(AbilityUtils.calculateAmount(ship, "Count$CardStickers.NameStartsWith.ChosenType", null), 0);
    }

    /**
     * Sticker kicker is a kicker variant, so Wicker Picker grants the kicker cost itself and
     * pays out on the trigger. The keyword has to reach the spell while it is still in hand.
     */
    @Test
    public void testWickerPickerGrantsTheKickerCost() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card inHand = addCardToZone("Grizzly Bears", p, ZoneType.Hand);
        Card land = addCardToZone("Mountain", p, ZoneType.Hand);
        game.getAction().checkStateEffects(true);
        assertFalse(inHand.hasKeyword("Kicker:1"), "nothing grants it yet");

        addCard("Wicker Picker", p);
        game.getAction().checkStateEffects(true);
        assertTrue(inHand.hasKeyword("Kicker:1"), "a creature spell you own gains the cost in hand");
        assertFalse(land.hasKeyword("Kicker:1"), "a land does not");
    }

    /**
     * Last Voyage becomes an Aura in the middle of its own trigger, reanimates a creature and
     * attaches itself to it, and its pump counts only the short name stickers it carries.
     */
    @Test
    public void testLastVoyageBecomesAnAura() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card sheet = giveSheet(p, "Eldrazi Guacamole Tightrope");
        Card corpse = addCardToZone("Grizzly Bears", p, ZoneType.Graveyard);

        Card voyage = playAndResolve(game, p, "Last Voyage of the _____");
        assertTrue(voyage.getType().hasSubtype("Aura"), "it should have become an Aura");

        Card returned = game.getCardsIn(ZoneType.Battlefield).stream()
                .filter(c -> c.getName().equals("Grizzly Bears")).findFirst().orElse(null);
        assertNotNull(returned, "the creature should have come back");
        assertEquals(voyage.getEnchantingCard(), returned, "and the Aura should be on it");
        assertFalse(game.getCardsIn(ZoneType.Graveyard).contains(corpse), "it left the graveyard");

        // Eldrazi is seven letters, so it is worth +2/+0; Guacamole, at nine, is not.
        int before = returned.getNetPower();
        Sticker nine = StickerSheet.getStickers(sheet).stream()
                .filter(x -> x.getKind() == StickerKind.NAME && x.getWord().length() == 9)
                .findFirst().orElseThrow();
        voyage.addSticker(new AppliedSticker(nine, game.getNextTimestamp()));
        game.getAction().checkStateEffects(true);
        assertEquals(returned.getNetPower(), before, "a nine-letter word is too long to count");

        Sticker seven = StickerSheet.getStickers(sheet).stream()
                .filter(x -> x.getKind() == StickerKind.NAME && x.getWord().length() == 7)
                .findFirst().orElseThrow();
        voyage.addSticker(new AppliedSticker(seven, game.getNextTimestamp()));
        game.getAction().checkStateEffects(true);
        assertEquals(returned.getNetPower(), before + 2, "seven letters is +2/+0");
    }

    /**
     * Park Bleater only offers creatures that entered this turn, so on an empty turn the AI has
     * nothing to sticker and should not activate it at all.
     */
    @Test
    public void testAiWaitsUntilItHasSomethingToSticker() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        giveSheet(p, "Eldrazi Guacamole Tightrope");
        Card bleater = addCard("Park Bleater", p);
        game.getAction().checkStateEffects(true);
        // Everything addCard puts down counts as having entered on turn one, so move off it.
        playUntilNextTurn(game);
        // An ability that costs mana is only worth it once the mana has nothing else to do, so
        // ask in the phase where the AI would actually use it - this test is about the pool.
        // Not moveToMain2: devModeSet resets the turn counter to 1 unless it is given one, which
        // would make everything addCard put down count as having entered this turn again.
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p, game.getPhaseHandler().getTurn());
        game.getAction().checkStateEffects(true);

        SpellAbility put = bleater.getSpellAbilities().stream()
                .filter(a -> a.getApi() == ApiType.PutSticker).findFirst().orElseThrow();
        assertFalse(SpellApiToAi.Converter.get(put).canPlayWithSubs(p, put).willingToPlay(),
                "nothing entered this turn, so there is nothing to put a sticker on");

        playAndResolve(game, p, "Grizzly Bears");
        assertTrue(SpellApiToAi.Converter.get(put).canPlayWithSubs(p, put).willingToPlay(),
                "a creature that entered this turn is something to put a sticker on");
    }

    /**
     * The Rocketship's letter is worth whatever it matches, so the AI should pick the one its
     * name stickers actually begin with rather than the first of the alphabet.
     */
    @Test
    public void testAiChoosesALetterThatCounts() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card sheet = giveSheet(p, "Eldrazi Guacamole Tightrope");
        Card ship = addCard("_____ _____ Rocketship", p);
        for (Sticker word : StickerSheet.getStickers(sheet)) {
            if (word.getKind() == StickerKind.NAME && word.getWord().startsWith("G")) {
                ship.addSticker(new AppliedSticker(word, game.getNextTimestamp()));
            }
        }
        game.getAction().checkStateEffects(true);

        List<String> letters = new ArrayList<>();
        for (char c = 'A'; c <= 'Z'; c++) {
            letters.add(String.valueOf(c));
        }
        SpellAbility choose = AbilityFactory.getAbility(
                "DB$ ChooseType | Type$ Letter | ValidTypes$ A,B,C | Defined$ You", ship);
        choose.setActivatingPlayer(p);
        assertEquals(ComputerUtil.chooseSomeType(p, "Letter", choose, letters), "G",
                "Guacamole is the only name sticker on it");
    }

    /**
     * Done for the Day's modes are optional, and CharmAi takes an optional mode only if the
     * mode's own AI wants it. Nothing in Forge knew what a ticket was worth, so the AI declined
     * both and the trigger resolved for nothing.
     */
    @Test
    public void testAiTakesTheTicketItIsOffered() {
        Game game = initAndCreateGame();
        Player p = game.getPhaseHandler().getPlayerTurn();
        giveSheet(p, "Eldrazi Guacamole Tightrope");
        Card dftd = addCard("Done for the Day", p);
        addCard("Park Bleater", p);
        game.getAction().checkStateEffects(true);

        SpellAbility charm = dftd.getTriggers().iterator().next().ensureAbility();
        charm.setActivatingPlayer(p);
        boolean wantsSomething = false;
        for (AbilitySub mode : CharmEffect.makePossibleOptions(charm)) {
            mode.setActivatingPlayer(p);
            wantsSomething |= SpellApiToAi.Converter.get(mode).canPlayWithSubs(p, mode).willingToPlay();
        }
        assertTrue(wantsSomething, "the AI should want at least one mode of a free payout");
    }

    /** Puts a sticker of the given kind on a card the way PutStickerEffect does. */
    private void placeSticker(Game game, Player p, Card on, StickerKind kind) {
        Card sheet = giveSheet(p, "Eldrazi Guacamole Tightrope");
        Sticker s = StickerSheet.getStickers(sheet).stream()
                .filter(x -> x.getKind() == kind).findFirst().orElseThrow();
        on.addSticker(new AppliedSticker(s, game.getNextTimestamp()));
        java.util.Map<AbilityKey, Object> runParams = AbilityKey.newMap();
        runParams.put(AbilityKey.Card, on);
        runParams.put(AbilityKey.Player, p);
        runParams.put(AbilityKey.StickerKind, kind);
        game.getTriggerHandler().runTrigger(TriggerType.StickerPlaced, runParams, false);
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        while (!game.getStack().isEmpty()) {
            game.getStack().resolveStack();
        }
    }
}
