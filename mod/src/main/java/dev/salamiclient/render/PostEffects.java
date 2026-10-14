package dev.salamiclient.render;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.salamiclient.SalamiClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Eigene Nachbearbeitungs-Effekte (Farbsättigung, Motion Blur). Läuft nach dem Weltbild und
 * unabhängig vom Vanilla-Effekt (Creeper-/Spinnenblick beim Zuschauen), damit sich nichts
 * gegenseitig abschaltet. Ohne aktiven Effekt kostet es nichts.
 */
public final class PostEffects {
	/** Jeder Eintrag liefert die gewünschte Effekt-ID oder {@code null} (aus). */
	private static final List<Supplier<Identifier>> SOURCES = new ArrayList<>();
	private static final List<Identifier> FAILED = new ArrayList<>();

	private PostEffects() {}

	public static void register(Supplier<Identifier> source) {
		SOURCES.add(source);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, path);
	}

	/** Für {@code GameRendererTweaksMixin}: nach dem Weltbild, vor dem HUD. */
	@SuppressWarnings("deprecation") // PostChain.process – nutzt Vanilla für seine eigenen Effekte genauso
	public static void process(GraphicsResourceAllocator resources) {
		if (SOURCES.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		for (Supplier<Identifier> source : SOURCES) {
			Identifier id = source.get();
			if (id == null || FAILED.contains(id)) {
				continue;
			}
			PostChain chain = mc.getShaderManager().getPostChain(id, LevelTargetBundle.MAIN_TARGETS);
			if (chain == null) {
				// Shader fehlt oder kompiliert nicht (z. B. Ressourcenpaket) – nicht jedes Frame neu versuchen
				SalamiClient.LOG.warn("Post effect {} could not be loaded – skipping it", id);
				FAILED.add(id);
				continue;
			}
			chain.process(mc.getMainRenderTarget(), resources);
		}
	}

	/** Effekte, deren Shader nicht geladen werden konnten (für Tests und Fehlersuche). */
	public static List<Identifier> failures() {
		return List.copyOf(FAILED);
	}

	/** Nach einem Ressourcen-Neuladen dürfen fehlgeschlagene Effekte es wieder versuchen. */
	public static void resetFailures() {
		FAILED.clear();
	}
}
