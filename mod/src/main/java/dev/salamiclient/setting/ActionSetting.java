package dev.salamiclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** Knopf in den Einstellungen (z. B. „Freunde öffnen“). Hat keinen gespeicherten Wert. */
public final class ActionSetting extends Setting<Boolean> {
	private final Runnable action;
	/** Beschriftung des Knopfs (Übersetzungsschlüssel). */
	private String buttonKey = "salami.mods.open";

	public ActionSetting(String id, Runnable action) {
		super(id, false);
		this.action = action;
	}

	/** Eigene Beschriftung des Knopfs statt „Öffnen“ (z. B. „Speichern“). */
	public ActionSetting button(String translationKey) {
		this.buttonKey = translationKey;
		return this;
	}

	public String buttonKey() {
		return buttonKey;
	}

	public void run() {
		action.run();
	}

	@Override
	public JsonElement toJson() {
		return JsonNull.INSTANCE;
	}

	@Override
	public void fromJson(JsonElement json) {
		// nichts zu laden
	}
}
