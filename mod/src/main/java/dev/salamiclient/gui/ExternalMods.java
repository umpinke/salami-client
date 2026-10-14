package dev.salamiclient.gui;

import dev.salamiclient.SalamiClient;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Mitgelieferte Mods aus dem Salami-Pack, die im Mods-Menü eine eigene Karte bekommen. Ihre
 * Einstellungen werden über die Mod-Menu-Schnittstelle geöffnet – per Reflection, damit
 * salami-core auch ohne Mod Menu oder ohne diese Mods läuft.
 */
public final class ExternalMods {
	/** {@code key} → Übersetzungen {@code salami.external.<key>} und {@code .desc}. */
	public record Entry(String modId, String key) {
		public String nameKey() {
			return "salami.external." + key;
		}

		public String descriptionKey() {
			return nameKey() + ".desc";
		}
	}

	private static final List<Entry> ENTRIES = List.of(
		new Entry("waveycapes", "wavey_capes"),
		new Entry("skinlayers3d", "skin_layers"),
		new Entry("tiers", "tiers"),
		new Entry("appleskin", "appleskin"),
		new Entry("shulkerboxtooltip", "shulker_tooltip"));

	private static List<Entry> installed;

	private ExternalMods() {}

	public static List<Entry> installed() {
		if (installed == null) {
			installed = ENTRIES.stream().filter(e -> FabricLoader.getInstance().isModLoaded(e.modId())).toList();
		}
		return installed;
	}

	/** Einstellungsbildschirm der Mod oder {@code null}, wenn sie keinen anbietet. */
	public static Screen configScreen(Entry entry, Screen parent) {
		try {
			Class<?> apiType = Class.forName("com.terraformersmc.modmenu.api.ModMenuApi");
			Class<?> factoryType = Class.forName("com.terraformersmc.modmenu.api.ConfigScreenFactory");
			Method getFactory = apiType.getMethod("getModConfigScreenFactory");
			Method create = factoryType.getMethod("create", Screen.class);
			for (EntrypointContainer<Object> container : FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class)) {
				if (!container.getProvider().getMetadata().getId().equals(entry.modId())) {
					continue;
				}
				Object api = container.getEntrypoint();
				if (!apiType.isInstance(api)) {
					continue;
				}
				Object factory = getFactory.invoke(api);
				if (factory != null && create.invoke(factory, parent) instanceof Screen screen) {
					return screen;
				}
			}
		} catch (ClassNotFoundException e) {
			// Mod Menu fehlt – dann gibt es auch keine Einstellungsseite
		} catch (Exception | LinkageError e) {
			// Fehler im fremden Mod (z. B. fehlende Klasse) – nicht das Spiel abstürzen lassen
			SalamiClient.LOG.warn("Could not open the settings of {}: {}", entry.modId(), e.toString());
		}
		return null;
	}
}
