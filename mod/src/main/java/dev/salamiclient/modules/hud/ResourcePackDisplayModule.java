package dev.salamiclient.modules.hud;

import dev.salamiclient.module.TextHudModule;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.server.packs.repository.Pack;

import java.util.ArrayList;
import java.util.List;

/** Resource Pack Display: Name des obersten aktiven Ressourcenpakets (ohne Vanilla/Mods). */
public final class ResourcePackDisplayModule extends TextHudModule {
	public ResourcePackDisplayModule() {
		super("resource_pack_display", 0.20, 0.41);
	}

	@Override
	protected String label() {
		return I18n.get("salami.module.resource_pack_display.label");
	}

	@Override
	protected String value() {
		List<Pack> packs = new ArrayList<>(mc().getResourcePackRepository().getSelectedPacks());
		// letzte Stelle = höchste Priorität
		for (int i = packs.size() - 1; i >= 0; i--) {
			Pack pack = packs.get(i);
			String id = pack.getId();
			// Vanilla, Fabric/Mods und fest eingebaute Pakete überspringen
			if (pack.isRequired() || pack.isFixedPosition() || id.equals("vanilla") || id.startsWith("fabric")) {
				continue;
			}
			return pack.getTitle().getString();
		}
		return null;
	}
}
