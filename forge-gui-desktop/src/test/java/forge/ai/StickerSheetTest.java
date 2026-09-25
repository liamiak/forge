package forge.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;

import forge.StaticData;
import forge.card.CardType;
import forge.card.GamePieceType;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.sticker.Sticker;
import forge.game.card.sticker.StickerKind;
import forge.game.card.sticker.StickerSheet;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.localinstance.skin.FSkinProp;
import forge.trackable.TrackableProperty;
import forge.model.FModel;

import org.apache.commons.lang3.StringUtils;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Unfinity sticker sheets: the data in cardsfolder and the CR 123.2a pregame choice.
 */
public class StickerSheetTest extends AITest {

    private static final int SHEET_COUNT = 48;

    private List<PaperCard> allSheets() {
        List<PaperCard> sheets = new ArrayList<>();
        for (PaperCard pc : StaticData.instance().getVariantCards().getAllCards()) {
            if (pc.getRules().getType().isStickers()) {
                sheets.add(pc);
            }
        }
        return sheets;
    }

    @Test
    public void testAllSheetsLoad() {
        List<PaperCard> sheets = allSheets();
        assertEquals(sheets.size(), SHEET_COUNT, "expected every Unfinity sticker sheet in the variant card pool");
        for (PaperCard pc : sheets) {
            assertEquals(DeckSection.matchingSection(pc), DeckSection.Stickers,
                    pc.getName() + " should sort into the sticker sheet deck section");
            assertTrue(pc.getRules().isVariant(), pc.getName() + " should be a variant card");
        }
        assertEquals(CardType.CoreType.Stickers.toGamePieceType(), GamePieceType.STICKER_SHEET,
                "a sticker sheet should be a sticker sheet game piece");
    }

    /**
     * Every sheet carries ten stickers: three name, three art, two ability, two power/toughness.
     * Verified against the printed sheets for Deep-Fried Plague Myr and Ancestral Hot Dog Minotaur.
     */
    @Test
    public void testEverySheetHasTenStickers() {
        Game game = newGame();
        Player owner = game.getPlayers().get(0);

        for (PaperCard pc : allSheets()) {
            List<Sticker> stickers = StickerSheet.getStickers(Card.fromPaperCard(pc, owner));
            assertEquals(stickers.size(), 10, pc.getName() + " should carry ten stickers");

            int names = 0, art = 0, abilities = 0, pts = 0;
            for (Sticker s : stickers) {
                switch (s.getKind()) {
                    case NAME -> {
                        names++;
                        assertTrue(StringUtils.isNotBlank(s.getWord()),
                                pc.getName() + ": name sticker " + s.getSlot() + " has no word");
                        assertEquals(s.getTickets(), 0, pc.getName() + ": name stickers are free");
                    }
                    case ART -> {
                        art++;
                        assertEquals(s.getTickets(), 0, pc.getName() + ": art stickers are free");
                    }
                    case ABILITY -> {
                        abilities++;
                        assertTrue(s.getTickets() > 0,
                                pc.getName() + ": ability sticker " + s.getSlot() + " has no ticket cost");
                        assertTrue(StringUtils.isNotBlank(s.getText()),
                                pc.getName() + ": ability sticker " + s.getSlot() + " has no text");
                    }
                    case PT -> {
                        pts++;
                        assertTrue(s.getTickets() > 0,
                                pc.getName() + ": P/T sticker " + s.getSlot() + " has no ticket cost");
                    }
                }
            }
            String where = pc.getName() + ": ";
            assertEquals(names, 3, where + "name stickers");
            assertEquals(art, 3, where + "art stickers");
            assertEquals(abilities, 2, where + "ability stickers");
            assertEquals(pts, 2, where + "power/toughness stickers");
        }
    }

    /** CR 123.3a - two stickers are never the same sticker, even reading identically. */
    @Test
    public void testStickersAreDistinctBySlot() {
        Game game = newGame();
        Card sheet = Card.fromPaperCard(allSheets().get(0), game.getPlayers().get(0));
        List<Sticker> stickers = StickerSheet.getStickers(sheet);
        Set<String> slots = new HashSet<>();
        for (Sticker s : stickers) {
            assertTrue(slots.add(s.getSlot()), "slot " + s.getSlot() + " appears twice");
        }
        // The three art stickers read identically but are three different stickers.
        assertEquals(stickers.stream().filter(s -> s.getKind() == StickerKind.ART).count(), 3);
    }

    /**
     * The deck editor shows its sticker sheet section when this pool is non-empty, so an empty
     * pool means the section silently disappears from the editor.
     */
    @Test
    public void testEditorPoolIsPopulated() {
        assertEquals(FModel.getStickerSheetPool().countAll(), SHEET_COUNT,
                "the deck editor's sticker sheet pool should hold every sheet");
    }

    /** A name sticker may carry more than one word - CR 123.6 - as "Hot Dog" does on sheet 23. */
    @Test
    public void testMultiWordNameSticker() {
        PaperCard sheet = null;
        for (PaperCard pc : allSheets()) {
            if ("Ancestral Hot Dog Minotaur".equals(pc.getName())) {
                sheet = pc;
            }
        }
        assertNotNull(sheet, "Ancestral Hot Dog Minotaur should be in the sticker sheet pool");
        Game game = newGame();
        List<String> words = new ArrayList<>();
        for (Sticker s : StickerSheet.getStickers(Card.fromPaperCard(sheet, game.getPlayers().get(0)))) {
            if (s.getKind() == StickerKind.NAME) {
                words.add(s.getWord());
            }
        }
        assertEquals(words, List.of("Ancestral", "Hot Dog", "Minotaur"),
                "\"Hot Dog\" should be one name sticker, not two");
    }

    /** CR 123.2a - three of the player's sheets are chosen at random and revealed. */
    @Test
    public void testThreeSheetsAreRevealed() {
        List<PaperCard> pool = allSheets().subList(0, 10);
        Player p = playerWithSheets(pool);
        assertEquals(p.getZone(ZoneType.StickerSheets).size(), 3,
                "three of the ten registered sheets should be revealed");
        for (Card c : p.getZone(ZoneType.StickerSheets)) {
            assertTrue(pool.stream().anyMatch(pc -> pc.getName().equals(c.getName())),
                    c.getName() + " was revealed but is not one of the registered sheets");
        }
    }

    /** Fewer sheets than the CR 123.2a choice - limited play, CR 123.2b - reveals what there is. */
    @Test
    public void testFewerSheetsThanChosen() {
        Player p = playerWithSheets(allSheets().subList(0, 2));
        assertEquals(p.getZone(ZoneType.StickerSheets).size(), 2);
    }

    /** A deck with no sticker sheets reveals none, and does not fall over. */
    @Test
    public void testNoSheets() {
        Player p = playerWithSheets(new ArrayList<>());
        assertEquals(p.getZone(ZoneType.StickerSheets).size(), 0);
    }

    /**
     * Both deck editors build their sticker tab from the same three things: the section being
     * offered for the game type, an icon for its tab, and a tracked zone for the in-game view.
     * The mobile editor has no tests of its own, so these are what guard it.
     */
    @Test
    public void testStickerSectionIsWiredForBothEditors() {
        for (GameType type : new GameType[]{GameType.Constructed, GameType.Commander, GameType.Draft}) {
            assertTrue(type.getSupplimentalDeckSections().contains(DeckSection.Stickers),
                    type + " should offer a sticker sheet section");
        }
        assertEquals(FSkinProp.iconFromDeckSection(DeckSection.Stickers, false), FSkinProp.IMG_ZONE_STICKER,
                "the tab needs an icon on both platforms");
        assertEquals(ZoneType.StickerSheets.getTrackableProperty(), TrackableProperty.StickerSheets,
                "an untracked zone shows up empty in the match view");
        assertTrue(ZoneType.PART_OF_COMMAND_ZONE.contains(ZoneType.StickerSheets));
    }

    /** An empty two-player game, just to own the cards under test. */
    private Game newGame() {
        List<RegisteredPlayer> players = Lists.newArrayList(
                new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("p1", null)),
                new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("p2", null)));
        GameRules rules = new GameRules(GameType.Constructed);
        return new Game(players, rules, new Match(rules, players, "StickerSheetTest"));
    }

    /**
     * Builds a game whose first player registered the given sheets, and runs the pregame
     * variant setup that {@link Match} would normally run.
     */
    private Player playerWithSheets(List<PaperCard> sheets) {
        Deck deck = new Deck();
        for (PaperCard pc : sheets) {
            deck.getOrCreate(DeckSection.Stickers).add(pc);
        }
        List<RegisteredPlayer> players = Lists.newArrayList();
        RegisteredPlayer rp = new RegisteredPlayer(deck).setPlayer(new LobbyPlayerAi("p1", null));
        players.add(rp);
        players.add(new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("p2", null)));
        GameRules rules = new GameRules(GameType.Constructed);
        Match match = new Match(rules, players, "StickerSheetTest");
        Game game = new Game(players, rules, match);

        Player p = game.getPlayers().get(0);
        rp.restoreDeck();
        p.initVariantsZones(rp);
        return p;
    }
}
