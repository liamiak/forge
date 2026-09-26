package forge.ai;

import java.util.List;
import java.util.Map;

import forge.StaticData;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.sticker.AppliedSticker;
import forge.game.card.sticker.Sticker;
import forge.game.card.sticker.StickerKind;
import forge.game.card.sticker.StickerSheet;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Putting a sticker on a card: what it changes, and what happens when the card changes zones.
 */
public class StickerApplyTest extends AITest {

    /** The stickers of one sheet, owned by the given player. */
    private List<Sticker> sheet(Player p, String sheetName) {
        PaperCard pc = StaticData.instance().getVariantCards().getCard(sheetName);
        assertNotNull(pc, "no such sticker sheet: " + sheetName);
        Card sheet = Card.fromPaperCard(pc, p);
        p.getZone(ZoneType.StickerSheets).add(sheet);
        return StickerSheet.getStickers(sheet);
    }

    private Sticker first(List<Sticker> stickers, StickerKind kind) {
        for (Sticker s : stickers) {
            if (s.getKind() == kind) {
                return s;
            }
        }
        throw new AssertionError("sheet has no " + kind + " sticker");
    }

    private Sticker nth(List<Sticker> stickers, StickerKind kind, int index) {
        int seen = 0;
        for (Sticker s : stickers) {
            if (s.getKind() == kind && seen++ == index) {
                return s;
            }
        }
        throw new AssertionError("sheet has no " + kind + " sticker at " + index);
    }

    @Test
    public void testNameStickerAtFront() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card bear = addCard("Grizzly Bears", p);
        assertEquals(bear.getName(), "Grizzly Bears");

        Sticker word = first(sheet(p, "Eldrazi Guacamole Tightrope"), StickerKind.NAME);
        bear.addSticker(new AppliedSticker(word, game.getNextTimestamp(), 0));

        assertEquals(bear.getName(), "Eldrazi Grizzly Bears");
        assertTrue(bear.isStickered(), "CR 123.4 - the bear is now a stickered object");
    }

    /**
     * CR 123.6a - a blank line is not a word, so a sticker put on a card printed with one fills
     * it rather than being placed among the words, and blanks nobody stickered stay put.
     */
    @Test
    public void testNameStickerFillsABlank() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        List<Sticker> stickers = sheet(p, "Eldrazi Guacamole Tightrope");

        Card ship = addCard("_____ _____ Rocketship", p);
        ship.addSticker(new AppliedSticker(nth(stickers, StickerKind.NAME, 0), game.getNextTimestamp(), 0));
        assertEquals(ship.getName(), "Eldrazi _____ Rocketship",
                "the first blank is filled, the second is still waiting");

        ship.addSticker(new AppliedSticker(nth(stickers, StickerKind.NAME, 1), game.getNextTimestamp(), 0));
        assertEquals(ship.getName(), "Eldrazi Guacamole Rocketship", "and then there are none left");

        // A third word has no blank to fill, so it is placed among the words as usual.
        ship.addSticker(new AppliedSticker(nth(stickers, StickerKind.NAME, 2), game.getNextTimestamp(), 3));
        assertEquals(ship.getName(), "Eldrazi Guacamole Rocketship Tightrope");
    }

    /** A blank inside a word is still a blank - CR 123.6a does not say it stands alone. */
    @Test
    public void testNameStickerFillsABlankInsideAWord() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card saurus = addCard("_____-o-saurus", p);
        saurus.addSticker(new AppliedSticker(
                first(sheet(p, "Eldrazi Guacamole Tightrope"), StickerKind.NAME),
                game.getNextTimestamp(), 0));
        assertEquals(saurus.getName(), "Eldrazi-o-saurus");
    }

    /** CR 123.6b - the word can go after any number of the words already in the name. */
    @Test
    public void testNameStickerInTheMiddleAndAtTheEnd() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        List<Sticker> stickers = sheet(p, "Eldrazi Guacamole Tightrope");

        Card middle = addCard("Grizzly Bears", p);
        middle.addSticker(new AppliedSticker(first(stickers, StickerKind.NAME), game.getNextTimestamp(), 1));
        assertEquals(middle.getName(), "Grizzly Eldrazi Bears");

        Card end = addCard("Grizzly Bears", p);
        end.addSticker(new AppliedSticker(nth(stickers, StickerKind.NAME, 1), game.getNextTimestamp(), 2));
        assertEquals(end.getName(), "Grizzly Bears Guacamole");
    }

    /** CR 123.6b - a later name sticker builds on the name the earlier one produced. */
    @Test
    public void testTwoNameStickersCompose() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        List<Sticker> stickers = sheet(p, "Eldrazi Guacamole Tightrope");
        Card bear = addCard("Grizzly Bears", p);

        bear.addSticker(new AppliedSticker(nth(stickers, StickerKind.NAME, 0), game.getNextTimestamp(), 0));
        assertEquals(bear.getName(), "Eldrazi Grizzly Bears");
        bear.addSticker(new AppliedSticker(nth(stickers, StickerKind.NAME, 1), game.getNextTimestamp(), 3));
        assertEquals(bear.getName(), "Eldrazi Grizzly Bears Guacamole");
    }

    /** CR 123.6c - if the name is now shorter than the remembered position, the word goes last. */
    @Test
    public void testNamePositionClampsToShorterName() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card mox = addCard("Black Lotus", p);
        Sticker word = first(sheet(p, "Eldrazi Guacamole Tightrope"), StickerKind.NAME);
        // Placed as though nine words preceded it; "Black Lotus" has two.
        mox.addSticker(new AppliedSticker(word, game.getNextTimestamp(), 9));
        assertEquals(mox.getName(), "Black Lotus Eldrazi");
    }

    /** CR 123.8 - a power and toughness sticker sets them. */
    @Test
    public void testPowerToughnessSticker() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card bear = addCard("Grizzly Bears", p);
        assertEquals(bear.getNetPower(), 2);
        assertEquals(bear.getNetToughness(), 2);

        Sticker pt = first(sheet(p, "Eldrazi Guacamole Tightrope"), StickerKind.PT);
        bear.addSticker(new AppliedSticker(pt, game.getNextTimestamp()));

        assertEquals(bear.getNetPower(), pt.getPower());
        assertEquals(bear.getNetToughness(), pt.getToughness());
    }

    /** CR 123.9 - an art sticker changes nothing but makes its object stickered. */
    @Test
    public void testArtStickerOnlyMarks() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card bear = addCard("Grizzly Bears", p);
        assertFalse(bear.isStickered());

        bear.addSticker(new AppliedSticker(first(sheet(p, "Eldrazi Guacamole Tightrope"), StickerKind.ART),
                game.getNextTimestamp()));

        assertTrue(bear.isStickered());
        assertEquals(bear.getName(), "Grizzly Bears");
        assertEquals(bear.getNetPower(), 2);
    }

    /**
     * CR 123.5 - both halves of the zone rule, on the same board so that neither assertion can
     * pass merely because nothing was ever stickered.
     */
    @Test
    public void testStickersSurvivePublicZonesAndNotHiddenOnes() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        List<Sticker> stickers = sheet(p, "Eldrazi Guacamole Tightrope");

        Card dies = addCard("Grizzly Bears", p);
        dies.addSticker(new AppliedSticker(nth(stickers, StickerKind.NAME, 0), game.getNextTimestamp(), 0));
        Card bounced = addCard("Grizzly Bears", p);
        bounced.addSticker(new AppliedSticker(nth(stickers, StickerKind.NAME, 1), game.getNextTimestamp(), 0));

        assertEquals(dies.getName(), "Eldrazi Grizzly Bears");
        assertEquals(bounced.getName(), "Guacamole Grizzly Bears");

        Card inGraveyard = game.getAction().moveTo(ZoneType.Graveyard, dies, null, null);
        Card inHand = game.getAction().moveTo(ZoneType.Hand, bounced, null, null);

        assertTrue(inGraveyard.isStickered(), "a public zone keeps the sticker");
        assertEquals(inGraveyard.getName(), "Eldrazi Grizzly Bears");

        assertFalse(inHand.isStickered(), "a hidden zone does not keep the sticker");
        assertEquals(inHand.getName(), "Grizzly Bears");
    }

    /**
     * An art sticker changes nothing else a player can see (CR 123.9), so the details pane has
     * to say it is there - otherwise two identical creatures cannot be told apart. The sheet
     * says which of its stickers have been taken, for the same reason.
     */
    @Test
    public void testStickersShowInTheCardDetails() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        List<Sticker> stickers = sheet(p, "Eldrazi Guacamole Tightrope");
        Card bear = addCard("Grizzly Bears", p);
        assertFalse(bear.getView().getText().contains("Stickers:"), "nothing on it yet");

        Sticker art = first(stickers, StickerKind.ART);
        bear.addSticker(new AppliedSticker(art, game.getNextTimestamp()));
        assertTrue(bear.getView().getText().contains(art.getDescription()),
                "the details should name the art sticker that is on it");

        Card sheetCard = p.getZone(ZoneType.StickerSheets).get(0);
        String sheetText = sheetCard.getView().getText();
        assertTrue(sheetText.contains("used"), "the sheet should mark the sticker as taken");
        assertTrue(sheetText.contains(first(stickers, StickerKind.NAME).getWord()),
                "and still list the ones that are not");
    }

    /** CR 123.7 - an ability sticker grants the object the ability printed on it. */
    @Test
    public void testAbilityStickerGrantsItsKeyword() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card bear = addCard("Grizzly Bears", p);
        assertFalse(bear.hasKeyword("Flying"));

        // Ancestral Hot Dog Minotaur's second ability sticker is plain Flying.
        Sticker flying = null;
        for (Sticker s : sheet(p, "Ancestral Hot Dog Minotaur")) {
            if (s.getKind() == StickerKind.ABILITY && "Flying".equals(s.getKeywords())) {
                flying = s;
            }
        }
        assertNotNull(flying, "that sheet should carry a Flying ability sticker");
        assertTrue(flying.isImplemented(), "a keyword sticker is placeable");

        bear.addSticker(new AppliedSticker(flying, game.getNextTimestamp()));
        assertTrue(bear.hasKeyword("Flying"), "the bear should have gained flying");

        // CR 123.5 - and keeps it moving to another public zone.
        Card inGraveyard = game.getAction().moveTo(ZoneType.Graveyard, bear, null, null);
        assertTrue(inGraveyard.isStickered());
        assertTrue(inGraveyard.hasKeyword("Flying"), "the granted ability survives a public zone");
    }

    /** An ability sticker with no Forge ability yet is not offered as a choice. */
    @Test
    public void testUnwrittenAbilityStickerIsNotOffered() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card sheet = Card.fromPaperCard(
                StaticData.instance().getVariantCards().getCard("Eldrazi Guacamole Tightrope"), p);
        Sticker unwritten = Sticker.parse(sheet, "STKX",
                Map.of("Kind", "Ability", "Tickets", "2", "Text", "Do something not written yet."));
        assertFalse(unwritten.isImplemented(), "an ability sticker with no script grants nothing");

        sheet(p, "Eldrazi Guacamole Tightrope");
        for (Sticker s : StickerSheet.getAvailableStickers(p)) {
            assertTrue(s.isImplemented(), s + " should not be offered");
        }
    }
}
