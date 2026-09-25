package forge.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import forge.StaticData;
import forge.game.Game;
import forge.game.ability.AbilityKey;
import forge.game.ability.ApiType;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.sticker.AppliedSticker;
import forge.game.card.sticker.Sticker;
import forge.game.card.sticker.StickerKind;
import forge.game.card.sticker.StickerSheet;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
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
 * CR 123.7 - what the ninety-six ability stickers grant. Every one is built here, because a
 * sticker's ability is only parsed when it is placed: a sheet with a broken script loads fine
 * and fails in the middle of a game.
 */
public class StickerAbilityTest extends AITest {

    private List<PaperCard> allSheets() {
        List<PaperCard> sheets = new ArrayList<>();
        for (PaperCard pc : StaticData.instance().getVariantCards().getAllCards()) {
            if (pc.getRules().getType().isStickers()) {
                sheets.add(pc);
            }
        }
        return sheets;
    }

    /** Placing any ability sticker grants the object something, and nothing throws doing it. */
    @Test
    public void testEveryAbilityStickerGrantsSomething() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        int placed = 0;

        for (PaperCard pc : allSheets()) {
            Card sheet = Card.fromPaperCard(pc, p);
            for (Sticker s : StickerSheet.getStickers(sheet)) {
                if (s.getKind() != StickerKind.ABILITY) {
                    continue;
                }
                assertTrue(s.isImplemented(), s + " has no ability to grant");

                Card bear = addCard("Grizzly Bears", p);
                int before = traits(bear);
                bear.addSticker(new AppliedSticker(s, game.getNextTimestamp()));
                assertTrue(traits(bear) > before, s + " granted nothing");
                placed++;
            }
        }
        assertTrue(placed == 96, "expected ninety-six ability stickers, placed " + placed);
    }

    /** One named sticker off one sheet, with the sheet revealed to its owner. */
    private Sticker sticker(Player p, String sheetName, String slot) {
        Card sheet = Card.fromPaperCard(StaticData.instance().getVariantCards().getCard(sheetName), p);
        p.getZone(ZoneType.StickerSheets).add(sheet);
        for (Sticker s : StickerSheet.getStickers(sheet)) {
            if (s.getSlot().equals(slot)) {
                return s;
            }
        }
        throw new AssertionError(sheetName + " has no sticker " + slot);
    }

    /** A granted static ability is conditional, and the condition is checked where it is now. */
    @Test
    public void testThresholdStickerWaitsForSevenCards() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card bear = addCard("Grizzly Bears", p);
        bear.addSticker(new AppliedSticker(sticker(p, "Trained Blessed Mind", "STK8"),
                game.getNextTimestamp()));
        game.getAction().checkStateEffects(true);

        assertEquals(bear.getNetPower(), 2, "under threshold, the sticker does nothing");
        assertFalse(bear.hasKeyword("Trample"));

        for (int i = 0; i < 7; i++) {
            addCardToZone("Grizzly Bears", p, ZoneType.Graveyard);
        }
        game.getAction().checkStateEffects(true);

        assertEquals(bear.getNetPower(), 6, "threshold - the sticker gives +4/+0");
        assertEquals(bear.getNetToughness(), 2);
        assertTrue(bear.hasKeyword("Trample"), "threshold - and trample");
    }

    /** Hellbent reads the hand, so it turns off again when the hand is not empty. */
    @Test
    public void testHellbentStickerReadsTheHand() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card bear = addCard("Grizzly Bears", p);
        bear.addSticker(new AppliedSticker(sticker(p, "Vampire Champion Fury", "STK7"),
                game.getNextTimestamp()));
        game.getAction().checkStateEffects(true);
        assertEquals(bear.getNetPower(), 5, "hellbent with an empty hand - +3/+3");

        addCardToZone("Grizzly Bears", p, ZoneType.Hand);
        game.getAction().checkStateEffects(true);
        assertEquals(bear.getNetPower(), 2, "a card in hand turns hellbent off");
    }

    /** Metalcraft counts artifacts, and the protection it grants takes a card property. */
    @Test
    public void testMetalcraftStickerNeedsThreeArtifacts() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card bear = addCard("Grizzly Bears", p);
        bear.addSticker(new AppliedSticker(sticker(p, "Wild Ogre Bupkis", "STK8"),
                game.getNextTimestamp()));
        game.getAction().checkStateEffects(true);
        assertFalse(bear.hasKeyword(Keyword.PROTECTION), "no artifacts, no metalcraft");

        addCards("Sol Ring", 3, p);
        game.getAction().checkStateEffects(true);
        assertTrue(bear.hasKeyword(Keyword.PROTECTION), "metalcraft - protection from noncreature permanents");
    }

    /**
     * Misunderstood Trapeze Elf's sticker pumps by the generic mana in the spell that was cast,
     * which is the one thing on any sheet Forge could not already count.
     */
    @Test
    public void testGenericManaStickerReadsTheSpellCast() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card bear = addCard("Grizzly Bears", p);
        bear.addSticker(new AppliedSticker(sticker(p, "Misunderstood Trapeze Elf", "STK7"),
                game.getNextTimestamp()));
        game.getAction().checkStateEffects(true);

        // Hill Giant costs {3}{R}: three generic, and a red pip that must not be counted.
        SpellAbility cast = addCardToZone("Hill Giant", p, ZoneType.Hand).getFirstSpellAbility();
        cast.setActivatingPlayer(p);
        Map<AbilityKey, Object> runParams = AbilityKey.newMap();
        runParams.put(AbilityKey.SpellAbility, cast);
        runParams.put(AbilityKey.Activator, p);
        game.getTriggerHandler().runTrigger(TriggerType.SpellCast, runParams, false);
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        while (!game.getStack().isEmpty()) {
            game.getStack().resolveStack();
        }

        assertEquals(bear.getNetPower(), 5, "+3/+3 for the three generic mana, not +4/+4");
        assertEquals(bear.getNetToughness(), 5);
    }

    /**
     * CR 123.5 - a sticker is kept into the graveyard, so a sticker whose ability only does
     * anything there has to be rebuilt on the card that arrives, not the one that left.
     */
    @Test
    public void testGraveyardStickerWorksWhereItLands() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card bear = addCard("Grizzly Bears", p);
        bear.addSticker(new AppliedSticker(sticker(p, "Eldrazi Guacamole Tightrope", "STK8"),
                game.getNextTimestamp()));
        game.getAction().checkStateEffects(true);
        assertTrue(bear.mayPlay(p).isEmpty(), "on the battlefield the sticker offers nothing");

        Card dead = game.getAction().moveTo(ZoneType.Graveyard, bear, null, null);
        game.getAction().checkStateEffects(true);
        assertTrue(dead.isStickered(), "the sticker follows the card into the graveyard");
        assertFalse(dead.mayPlay(p).isEmpty(), "from the graveyard it may be cast for 2 life");
    }

    /** The sticker that counts a creature's types, and the creatures its protection stops. */
    @Test
    public void testCountsTheStickersNeed() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        // Grizzly Bears is a Creature Bear: two types in all.
        Card bear = addCard("Grizzly Bears", p);
        assertEquals(AbilityUtils.calculateAmount(bear, "Count$CardTypeCount", null), 2);
        // Elvish Archers is an Elf Archer, so it has the two creature types the sticker asks for.
        Card archers = addCard("Elvish Archers", p);
        assertTrue(archers.isValid("Creature.numCreatureTypesGE2", p, bear, null));
        assertFalse(bear.isValid("Creature.numCreatureTypesGE2", p, bear, null));
    }

    /**
     * Unhinged Beast Hunt's sticker taps opponents' creatures with the same power or toughness
     * as the stickered creature, so the numbers it compares against live on the sheet and are
     * read inside a card filter on the card the sticker is on.
     */
    @Test
    public void testStickerFilterReadsTheSheetsNumbers() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Player opp = game.getPlayers().get(1);
        Card bear = addCard("Grizzly Bears", p);
        bear.addSticker(new AppliedSticker(sticker(p, "Unhinged Beast Hunt", "STK8"),
                game.getNextTimestamp()));
        game.getAction().checkStateEffects(true);

        SpellAbility tapAll = null;
        for (Trigger t : bear.getTriggers()) {
            if (t.getOverridingAbility() != null && t.getOverridingAbility().getApi() == ApiType.TapAll) {
                tapAll = t.getOverridingAbility();
            }
        }
        assertNotNull(tapAll, "the sticker should have granted an attack trigger that taps");

        Card match = addCard("Grizzly Bears", opp);
        Card bigger = addCard("Hill Giant", opp);
        assertTrue(match.isValid("Creature.OppCtrl+powerEQSTK8P", p, bear, tapAll),
                "a 2/2 has the bear's power");
        assertFalse(bigger.isValid("Creature.OppCtrl+powerEQSTK8P", p, bear, tapAll),
                "a 3/3 does not");
        assertTrue(match.isValid("Creature.OppCtrl+toughnessEQSTK8T", p, bear, tapAll));
    }

    private int traits(Card c) {
        return c.getSpellAbilities().size() + c.getTriggers().size()
                + c.getStaticAbilities().size() + c.getKeywords().size();
    }
}
