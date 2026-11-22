package dev.salamiclient.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu: „Konfigurieren“ bei Salami Client öffnet das Salami-Menü.
 *
 * <p>Fabric lädt diese Klasse nur über den Einstiegspunkt {@code modmenu} – also nur, wenn Mod Menu
 * installiert ist. Ohne Mod Menu wird sie nie angefasst (Mod Menu ist nur zum Kompilieren eingebunden).
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SalamiMenuScreen::over;
	}
}
