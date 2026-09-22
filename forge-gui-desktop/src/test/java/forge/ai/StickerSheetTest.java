package forge.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import com.google.common.collect.Lists;

import forge.StaticData;
import forge.card.CardRules;
import forge.card.CardType;
import forge.card.GamePieceType;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.model.FModel;

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
        for (PaperCard pc : allSheets()) {
            CardRules rules = pc.getRules();
            String keyword = null;
            for (String k : rules.getMainPart().getKeywords()) {
                if (k.startsWith("StickerSheet:")) {
                    keyword = k;
                }
            }
            assertNotNull(keyword, pc.getName() + " should declare a StickerSheet keyword");

            String[] keys = keyword.substring("StickerSheet:".length()).split(",");
            assertEquals(keys.length, 10, pc.getName() + " should list ten stickers");

            int names = 0, art = 0, abilities = 0, pts = 0;
            for (String key : keys) {
                String value = null;
                for (Entry<String, String> sVar : rules.getMainPart().getVariables()) {
                    if (sVar.getKey().equalsIgnoreCase(key)) {
                        value = sVar.getValue();
                    }
                }
                assertNotNull(value, pc.getName() + " names sticker " + key + " but has no such SVar");
                if (value.startsWith("Kind$ Name")) {
                    names++;
                    assertTrue(value.contains("| Word$ "), pc.getName() + ": name sticker " + key + " has no word");
                } else if (value.startsWith("Kind$ Art")) {
                    art++;
                } else if (value.startsWith("Kind$ Ability")) {
                    abilities++;
                    assertTrue(value.contains("| Tickets$ "), pc.getName() + ": " + key + " has no ticket cost");
                } else if (value.startsWith("Kind$ PT")) {
                    pts++;
                    assertTrue(value.contains("| Power$ ") && value.contains("| Toughness$ "),
                            pc.getName() + ": P/T sticker " + key + " is missing its power or toughness");
                    assertTrue(value.contains("| Tickets$ "), pc.getName() + ": " + key + " has no ticket cost");
                }
            }
            String where = pc.getName() + ": ";
            assertEquals(names, 3, where + "name stickers");
            assertEquals(art, 3, where + "art stickers");
            assertEquals(abilities, 2, where + "ability stickers");
            assertEquals(pts, 2, where + "power/toughness stickers");
        }
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
        boolean found = false;
        for (Entry<String, String> sVar : sheet.getRules().getMainPart().getVariables()) {
            if ("Kind$ Name | Word$ Hot Dog".equals(sVar.getValue())) {
                found = true;
            }
        }
        assertTrue(found, "\"Hot Dog\" should be one name sticker, not two");
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
