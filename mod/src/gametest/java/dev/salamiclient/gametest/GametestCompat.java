package dev.salamiclient.gametest;

import dev.salamiclient.SalamiClient;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/** Aufrufe der Gametest-API, die sich zwischen Minecraft-Versionen unterscheiden. */
final class GametestCompat {
	private GametestCompat() {}

	/**
	 * Nur ausgewählte Tests laufen lassen, z. B. {@code ./gradlew :26.3:runClientGameTest -PsalamiTests=Benchmark}
	 * (kommagetrennte Teile des Klassennamens; ohne Angabe laufen alle).
	 */
	static boolean selected(Object test) {
		String only = System.getProperty("salami.tests");
		if (only == null || only.isBlank()) {
			return true;
		}
		String name = test.getClass().getSimpleName().toLowerCase(Locale.ROOT);
		for (String part : only.split(",")) {
			if (!part.isBlank() && name.contains(part.trim().toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		SalamiClient.LOG.info("[Gametest] {} übersprungen (salami.tests={})", test.getClass().getSimpleName(), only);
		return false;
	}

	static void waitForChunksRender(TestSingleplayerContext world) {
		//? if >=26.2 {
		/*world.getConnection().waitForChunksRender();
		*///?} else if >=26.1 {
		/*world.getClientLevel().waitForChunksRender();
		*///?} else
		world.getClientWorld().waitForChunksRender();
	}

	/** HUD ausblenden wie mit F1 (für Screenshots). Ab 26.2 gehört das zum Hud und bleibt hier, wie es ist. */
	static void hideHud(Minecraft client, boolean hidden) {
		//? if <26.2 {
		client.options.hideGui = hidden;
		//?}
	}

	/**
	 * Ab 26.3 kommen getippte Zeichen über SDL nur an, solange die Texteingabe des Fensters eingeschaltet ist.
	 * Die Test-Eingabe ({@code typeChars}) ruft {@code charTyped} direkt auf und merkt davon nichts – darum prüfen
	 * die Tests das hier.
	 */
	//? if >=26.3 {
	/*static final boolean TEXT_INPUT_GATED = true;
	*///?} else {
	static final boolean TEXT_INPUT_GATED = false;
	//?}

	/** Ob das Fenster gerade getippte Zeichen annimmt; vor 26.3 immer {@code true}. */
	static boolean acceptsTypedText(Minecraft client) {
		//? if >=26.3 {
		/*return org.lwjgl.sdl.SDLKeyboard.SDL_TextInputActive(client.getWindow().handle());
		*///?} else {
		return true;
		//?}
	}

	/** Ob gerade ein Ladebildschirm (z. B. nach einem Sprachwechsel) über allem liegt. */
	static boolean hasOverlay(Minecraft client) {
		//? if >=26.2 {
		/*return client.gui.overlay() != null;
		*///?} else
		return client.getOverlay() != null;
	}

	/** Anzahl aller Partikel; ab 26.2 liefert Minecraft „Art Anzahl … T Summe“ statt nur der Zahl. */
	static int particleCount(Minecraft client) {
		String counts = client.particleEngine.countParticles().trim();
		return Integer.parseInt(counts.substring(counts.lastIndexOf(' ') + 1));
	}
}
