package dev.salamiclient.gui;

import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.setting.Setting;
import net.minecraft.client.resources.language.I18n;

import java.util.Locale;

/** Anzeigetexte für Einstellungen – gemeinsam für ClickGUI und Mods-Menü. */
public final class SettingText {
	private SettingText() {}

	/** Modulspezifischer Name, sonst generischer ("salami.setting.keybind"), sonst die ID. */
	public static String label(Setting<?> setting) {
		String specific = setting.translationKey();
		if (I18n.exists(specific)) {
			return I18n.get(specific);
		}
		String generic = "salami.setting." + setting.id();
		return I18n.exists(generic) ? I18n.get(generic) : setting.id();
	}

	/** Anzeigename des gewählten Modus: modulspezifisch, sonst generisch, sonst Enum-Name. */
	public static String modeLabel(ModeSetting<?> mode) {
		String specific = mode.valueTranslationKey();
		if (I18n.exists(specific)) {
			return I18n.get(specific);
		}
		String generic = "salami.setting." + mode.id() + "." + mode.get().name().toLowerCase(Locale.ROOT);
		return I18n.exists(generic) ? I18n.get(generic) : mode.get().name();
	}

	/** Beschreibung einer Einstellung oder {@code null}. */
	public static String description(Setting<?> setting) {
		String key = setting.translationKey() + ".desc";
		if (I18n.exists(key)) {
			return I18n.get(key);
		}
		String generic = "salami.setting." + setting.id() + ".desc";
		if (I18n.exists(generic)) {
			return I18n.get(generic);
		}
		return setting instanceof ColorSetting ? I18n.get("salami.colorpicker.hint") : null;
	}
}
