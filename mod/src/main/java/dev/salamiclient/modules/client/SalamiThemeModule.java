package dev.salamiclient.modules.client;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;

/**
 * Salami-Modus: An = kompletter Client im Salami-Look.
 *
 * <p>Wird der Client über den Salami-Launcher gestartet, gibt dieser den
 * Schalter per {@code -Dsalami.theme=salami|default} mit – dann gilt die
 * Launcher-Einstellung (siehe {@code SalamiClient#applyLauncherTheme}).
 */
public final class SalamiThemeModule extends Module {
	public SalamiThemeModule() {
		super("salami_theme", Category.CLIENT);
	}
}
