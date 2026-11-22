package dev.salamiclient.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.gui.MinigamesScreen;
import dev.salamiclient.gui.clickgui.ClickGuiScreen;
import dev.salamiclient.minigames.GameKey;
import dev.salamiclient.minigames.Highscores;
import dev.salamiclient.minigames.Minigame;
import dev.salamiclient.minigames.Minigames;
import dev.salamiclient.minigames.games.ChiliAlertGame;
import dev.salamiclient.minigames.games.ColdCutsGame;
import dev.salamiclient.minigames.games.MeatMergeGame;
import dev.salamiclient.minigames.games.SalamiVsCheeseGame;
import dev.salamiclient.minigames.games.SausageChainGame;
import dev.salamiclient.minigames.games.SliceFlightGame;
import dev.salamiclient.modules.client.GuiScaleModule;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.resources.language.I18n;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Minispiele: Spiellogik (Zusammenschieben, Reihen, erster Klick sicher, CPU-Zug …), Bestenlisten-Datei,
 * der Knopf in der ClickGUI, Tastatur (Esc/Pause/Menü) und Screenshots von Menü, Bestenliste,
 * jedem Spiel mitten im Spiel, Pause und Spielende mit neuem Rekord.
 */
public final class SalamiMinigamesGameTest implements FabricClientGameTest {
	private static final LocalDate DATE = LocalDate.of(2026, 11, 15);

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!GametestCompat.selected(this)) {
			return;
		}
		try {
			testHighscoreFile();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		testLogic();
		// das Testfenster hat oft keinen Fokus – sonst pausiert jedes Spiel sofort
		context.runOnClient(c -> MinigamesScreen.setPauseOnFocusLoss(false));
		seedHighscores(context);
		testClickGuiButton(context);
		MinigamesScreen screen = context.computeOnClient(c -> {
			MinigamesScreen s = new MinigamesScreen(null);
			c.setScreen(s);
			return s;
		});
		testScreenshots(context, screen);
		testGameOver(context, screen);
		testKeyboard(context, screen);
		context.runOnClient(c -> MinigamesScreen.setPauseOnFocusLoss(true));
		context.setScreen(() -> null);
	}

	// ------------------------------------------------------------------ Bestenliste

	private static void testHighscoreFile() throws IOException {
		Path dir = Files.createTempDirectory("salami-minigames");
		Path file = dir.resolve("minigames.json");
		Highscores scores = new Highscores(file);
		scores.load();
		check(scores.best("test") == 0, "empty highscores");
		check(scores.submit("test", "A", 100, DATE) == 0, "first entry is rank 0");
		check(scores.submit("test", "B", 300, DATE) == 0, "better entry goes first");
		check(scores.submit("test", "C", 200, DATE) == 1, "middle entry");
		check(scores.submit("test", "D", 0, DATE) == -1, "zero score must not be listed");
		scores.submit("test", "E", 50, DATE);
		scores.submit("test", "F", 40, DATE);
		check(scores.submit("test", "G", 30, DATE) == -1, "sixth-best must not be listed");
		check(scores.submit("test", "H", 100, DATE) == 3, "equal score goes after the older one");
		List<Highscores.Entry> top = scores.top("test");
		check(top.size() == Highscores.MAX_ENTRIES, "top 5 only: " + top);
		check(top.get(0).score() == 300 && top.get(2).name().equals("A") && top.get(3).name().equals("H") && top.get(4).score() == 50,
			"order: " + top);
		scores.setMuted(true);

		Highscores reloaded = new Highscores(file);
		reloaded.load();
		check(reloaded.top("test").equals(top), "reloaded: " + reloaded.top("test"));
		check(reloaded.muted(), "mute switch not saved");
		check(reloaded.top("test").get(0).date().equals("2026-11-15"), "date not saved");

		Files.writeString(file, "{\"games\": {\"test\": [{\"score\": \"x\"}, 5, {\"name\": \"Z\", \"score\": 7}]}, \"muted\": 3}", StandardCharsets.UTF_8);
		reloaded.load();
		check(reloaded.best("test") == 7 && reloaded.top("test").size() == 1 && !reloaded.muted(), "broken entries: " + reloaded.top("test"));
		Files.writeString(file, "{kaputt", StandardCharsets.UTF_8);
		reloaded.load();
		check(reloaded.best("test") == 0, "broken file must give empty highscores");
		Files.deleteIfExists(file);
		Files.deleteIfExists(dir);
	}

	// ------------------------------------------------------------------ Spiellogik

	private static void testLogic() {
		// Verwursten: gleiche verschmelzen, aber nur einmal pro Zug
		MeatMergeGame merge = new MeatMergeGame();
		merge.restart(1);
		merge.setBoard(new int[][] {{2, 2, 2, 2}, {4, 0, 4, 8}, {0, 0, 0, 0}, {2, 0, 0, 2}});
		check(merge.move(-1, 0), "merge: nothing moved");
		check(merge.valueAt(0, 0) == 4 && merge.valueAt(1, 0) == 4, "merge: 2 2 2 2 -> 4 4");
		check(merge.valueAt(0, 1) == 8 && merge.valueAt(1, 1) == 8, "merge: 4 _ 4 8 -> 8 8 (no double merge)");
		check(merge.valueAt(0, 3) == 4, "merge: 2 _ _ 2 -> 4");
		check(merge.score() == 20, "merge score " + merge.score());
		merge.setBoard(new int[][] {{2, 4, 2, 4}, {4, 2, 4, 2}, {2, 4, 2, 4}, {4, 2, 4, 2}});
		check(!merge.move(-1, 0) && !merge.move(0, 1), "merge: locked board moved");

		// Aufschnitt: vier Reihen auf einmal
		ColdCutsGame cuts = new ColdCutsGame();
		cuts.restart(1);
		for (int y = ColdCutsGame.VISIBLE_ROWS - 4; y < ColdCutsGame.VISIBLE_ROWS; y++) {
			cuts.fillRow(y, ColdCutsGame.COLS - 1);
		}
		cuts.forcePiece(0);
		tap(cuts, GameKey.UP);
		for (int i = 0; i < 4; i++) {
			tap(cuts, GameKey.RIGHT);
		}
		tap(cuts, GameKey.ACTION);
		for (int i = 0; i < 20; i++) {
			cuts.step();
		}
		check(cuts.lines() == 4, "cold cuts: lines " + cuts.lines());
		check(!cuts.cellFilled(0, ColdCutsGame.VISIBLE_ROWS - 1), "cold cuts: bottom row not cleared");
		check(cuts.score() >= 800, "cold cuts: score " + cuts.score());
		check(!cuts.isOver(), "cold cuts: game over after a clear");

		// Chili-Alarm: erster Klick (und seine Nachbarn) sicher, Sieg nach allen sicheren Feldern
		ChiliAlertGame chili = new ChiliAlertGame();
		chili.restart(5);
		chili.primary(3 + 3 * ChiliAlertGame.COLS);
		check(chili.isOpen(3, 3) && !chili.isOver(), "chili: first click not safe");
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				check(!chili.isChili(3 + dx, 3 + dy), "chili: chili next to the first click");
			}
		}
		chili.revealAllSafe();
		check(chili.isWon() && chili.isOver() && chili.score() > 1000, "chili: no win after all safe tiles, score " + chili.score());

		// Wurst gegen Käse: CPU gewinnt, wenn sie kann, und blockt sonst
		SalamiVsCheeseGame c4 = new SalamiVsCheeseGame();
		c4.restart(2);
		int[][] board = c4.board();
		int bottom = SalamiVsCheeseGame.ROWS - 1;
		board[bottom][0] = board[bottom][1] = board[bottom][2] = SalamiVsCheeseGame.CPU;
		board[bottom][5] = board[bottom - 1][5] = SalamiVsCheeseGame.PLAYER;
		check(c4.chooseMove() == 3, "cheese does not take the win");
		for (int[] row : board) {
			java.util.Arrays.fill(row, 0);
		}
		board[bottom][0] = board[bottom][1] = board[bottom][2] = SalamiVsCheeseGame.PLAYER;
		board[bottom][6] = board[bottom - 1][6] = SalamiVsCheeseGame.CPU;
		check(c4.chooseMove() == 3, "cheese does not block");

		// Wurstkette: fährt nach dem Start los und stößt irgendwann an die Wand
		SausageChainGame snake = new SausageChainGame();
		snake.restart(3);
		for (int i = 0; i < 30; i++) {
			snake.step();
		}
		check(snake.score() == 0 && !snake.isOver(), "snake moved before a key press");
		tap(snake, GameKey.RIGHT);
		for (int i = 0; i < 2000 && !snake.isOver(); i++) {
			snake.step();
		}
		check(snake.isOver(), "snake never hit the wall");

		// Scheibenflug: Flattern hebt die Scheibe, ohne Flattern fällt sie auf den Boden
		SliceFlightGame flight = new SliceFlightGame();
		flight.restart(3);
		check(flight.isWaiting(), "flight: not waiting at start");
		tap(flight, GameKey.ACTION);
		check(!flight.isWaiting(), "flight: still waiting after flap");
		float before = flight.playerY();
		flight.step();
		check(flight.playerY() < before, "flight: flap did not lift");
		check(!flight.drainSounds().isEmpty(), "flight: no flap sound");
		for (int i = 0; i < 1000 && !flight.isOver(); i++) {
			flight.step();
		}
		check(flight.isOver(), "flight: never crashed");

		// jede Vorschau und jedes frische Spiel lässt sich zeichnen und schrittweise spielen
		for (int i = 0; i < Minigames.count(); i++) {
			Minigame game = Minigames.create(i);
			game.restart(i);
			for (int step = 0; step < 600; step++) {
				game.step();
				if (step % 7 == 0) {
					tap(game, GameKey.values()[step % GameKey.values().length]);
				}
			}
			check(game.width() > 0 && game.height() > 0 && !game.controls().isEmpty(), game.id() + ": no size or controls");
		}
	}

	private static void tap(Minigame game, GameKey key) {
		game.press(key);
		game.release(key);
	}

	// ------------------------------------------------------------------ Bildschirm

	private static void seedHighscores(ClientGameTestContext context) {
		context.runOnClient(c -> {
			Highscores scores = MinigamesScreen.highscores();
			scores.setMuted(false);
			scores.submit("sausage_chain", "Steve", 23, DATE.minusDays(3));
			scores.submit("sausage_chain", "Alex", 41, DATE.minusDays(1));
			scores.submit("sausage_chain", "Salami_Sepp", 17, DATE);
			scores.submit("cold_cuts", "Alex", 50, DATE.minusDays(2));
			scores.submit("meat_merge", "Steve", 5120, DATE.minusDays(5));
			scores.submit("slice_flight", "Alex", 12, DATE.minusDays(4));
			scores.submit("salami_vs_cheese", "Steve", 236, DATE.minusDays(6));
		});
	}

	private static void testClickGuiButton(ClientGameTestContext context) {
		context.setScreen(() -> new ClickGuiScreen(SalamiClient.get().modules(), SalamiClient.get().config()));
		context.waitTicks(2);
		double[] pos = context.computeOnClient(c -> {
			float ui = GuiScaleModule.clickGuiScale();
			int mods = c.font.width(I18n.get("salami.clickgui.mods")) + 16;
			int configs = c.font.width(I18n.get("salami.clickgui.configs")) + 12;
			int cosmetics = c.font.width(I18n.get("salami.clickgui.cosmetics")) + 12;
			int games = c.font.width(I18n.get("salami.clickgui.minigames")) + 12;
			double x = 6 + mods + 4 + configs + 4 + cosmetics + 4 + games / 2.0;
			double toWindow = c.getWindow().getGuiScale() * ui;
			return new double[] {x * toWindow, (ClickGuiScreen.barTop() + ClickGuiScreen.BAR_H / 2.0) * toWindow};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTick();
		context.takeScreenshot("salami-minigames-00-clickgui-button");
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
		check(context.computeOnClient(c -> c.screen instanceof MinigamesScreen), "ClickGUI: minigames button did not open the menu");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTick();
		check(context.computeOnClient(c -> c.screen instanceof ClickGuiScreen), "Esc in the minigames menu did not return to the ClickGUI");
		context.getInput().setCursorPos(0, 0);
		context.setScreen(() -> null);
	}

	private static void testScreenshots(ClientGameTestContext context, MinigamesScreen screen) {
		context.waitTicks(3);
		context.takeScreenshot("salami-minigames-01-menu");
		context.runOnClient(c -> screen.showLeaderboard(0));
		context.waitTicks(2);
		context.takeScreenshot("salami-minigames-02-leaderboard");

		// jedes Spiel mitten im Spiel (Vorschau-Stand, Zeit angehalten)
		for (int i = 0; i < Minigames.count(); i++) {
			int index = i;
			String id = context.computeOnClient(c -> {
				screen.startDemo(index);
				screen.setFrozen(true);
				return screen.game().id();
			});
			context.waitTicks(2);
			context.takeScreenshot("salami-minigames-1" + i + "-" + id);
		}
		context.runOnClient(c -> screen.setFrozen(false));

		// echtes Spiel in Echtzeit: die Kette fährt los
		context.runOnClient(c -> {
			screen.startGame(0);
			screen.game().press(GameKey.DOWN);
			screen.game().release(GameKey.DOWN);
		});
		context.waitTicks(16);
		check(context.computeOnClient(c -> screen.isPlaying() && SalamiClient.isTyping(screen)), "live game stopped or keys not captured");
		context.takeScreenshot("salami-minigames-20-live");

		// Pause-Fenster
		context.runOnClient(c -> {
			screen.startDemo(1);
			screen.pause();
		});
		context.waitTicks(2);
		check(context.computeOnClient(c -> screen.isPaused()), "pause did not pause");
		context.takeScreenshot("salami-minigames-21-pause");
	}

	/** Aufschnitt bis oben stapeln: Spielende, Eintrag in der Bestenliste, „Neuer Rekord!“. */
	private static void testGameOver(ClientGameTestContext context, MinigamesScreen screen) {
		context.runOnClient(c -> screen.startGame(1));
		boolean over = false;
		for (int i = 0; i < 120 && !over; i++) {
			over = context.computeOnClient(c -> {
				Minigame game = screen.game();
				if (game.isOver()) {
					return true;
				}
				game.press(GameKey.ACTION);
				game.release(GameKey.ACTION);
				return false;
			});
			context.waitTick();
		}
		check(over, "cold cuts: stacking to the top did not end the game");
		context.waitTicks(30);
		int score = context.computeOnClient(c -> screen.game().score());
		check(context.computeOnClient(c -> MinigamesScreen.highscores().best("cold_cuts")) == score,
			"game over: score " + score + " not in the highscores");
		context.takeScreenshot("salami-minigames-22-game-over-record");
	}

	private static void testKeyboard(ClientGameTestContext context, MinigamesScreen screen) {
		context.runOnClient(c -> screen.showMenu());
		context.waitTick();
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTick();
		check(context.computeOnClient(c -> screen.isPlaying()), "Enter in the menu did not start a game");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTick();
		check(context.computeOnClient(c -> screen.isPaused()), "Esc did not pause");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTick();
		check(context.computeOnClient(c -> screen.isPlaying()), "Esc in the pause menu did not continue");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTick();
		// Pause-Fenster: Weiter, Neustart, Zum Menü
		context.getInput().pressKey(InputConstants.KEY_DOWN);
		context.getInput().pressKey(InputConstants.KEY_DOWN);
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTick();
		check(context.computeOnClient(c -> screen.game() == null && c.screen == screen), "pause menu: 'back to menu' did not work");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTick();
		// ohne Welt zeigt Minecraft statt „kein Bildschirm“ das Titelmenü
		check(context.computeOnClient(c -> !(c.screen instanceof MinigamesScreen)), "Esc in the menu did not close the screen");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
