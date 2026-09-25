package forge.ai;

import java.util.List;

import forge.StaticData;
import forge.deck.DeckSection;
import forge.game.Game;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.card.sticker.AppliedSticker;
import forge.game.card.sticker.Sticker;
import forge.game.card.sticker.StickerKind;
import forge.game.card.sticker.StickerSheet;
import forge.game.player.Player;
import forge.game.ability.ApiType;
import forge.game.ability.effects.CharmEffect;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
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
            "Done for the Day", "Park Bleater", "Lineprancers", "Tusk and Whiskers");

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
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        while (!game.getStack().isEmpty()) {
            game.getStack().resolveStack();
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
