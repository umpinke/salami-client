package dev.salamiclient.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

/** Aufrufe der Gametest-API, die sich zwischen Minecraft-Versionen unterscheiden. */
final class GametestCompat {
	private GametestCompat() {}

	static void waitForChunksRender(TestSingleplayerContext world) {
		//? if >=26.2 {
		/*world.getConnection().waitForChunksRender();
		*///?} else if >=26.1 {
		/*world.getClientLevel().waitForChunksRender();
		*///?} else
		world.getClientWorld().waitForChunksRender();
	}

	/** Anzahl aller Partikel; ab 26.2 liefert Minecraft „Art Anzahl … T Summe“ statt nur der Zahl. */
	static int particleCount(Minecraft client) {
		String counts = client.particleEngine.countParticles().trim();
		return Integer.parseInt(counts.substring(counts.lastIndexOf(' ') + 1));
	}
}
