package dev.salamiclient.minigames;

import dev.salamiclient.minigames.games.ChiliAlertGame;
import dev.salamiclient.minigames.games.ColdCutsGame;
import dev.salamiclient.minigames.games.MeatMergeGame;
import dev.salamiclient.minigames.games.SalamiVsCheeseGame;
import dev.salamiclient.minigames.games.SausageChainGame;
import dev.salamiclient.minigames.games.SliceFlightGame;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Alle Minispiele in Menü-Reihenfolge. */
public final class Minigames {
	private static final List<Supplier<Minigame>> GAMES = List.of(
		SausageChainGame::new,
		ColdCutsGame::new,
		SliceFlightGame::new,
		MeatMergeGame::new,
		ChiliAlertGame::new,
		SalamiVsCheeseGame::new
	);

	private Minigames() {}

	public static int count() {
		return GAMES.size();
	}

	/** Neues Spiel Nummer {@code index} (noch nicht gestartet: {@link Minigame#restart()} aufrufen). */
	public static Minigame create(int index) {
		return GAMES.get(index).get();
	}

	/** Je ein Spiel im Vorschau-Zustand ({@link Minigame#demo()}) – für die Karten im Menü. */
	public static List<Minigame> previews() {
		List<Minigame> list = new ArrayList<>();
		for (Supplier<Minigame> factory : GAMES) {
			Minigame game = factory.get();
			game.demo();
			list.add(game);
		}
		return list;
	}

	/** Index des Spiels mit dieser ID oder -1. */
	public static int indexOf(String id) {
		for (int i = 0; i < GAMES.size(); i++) {
			if (create(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}
}
