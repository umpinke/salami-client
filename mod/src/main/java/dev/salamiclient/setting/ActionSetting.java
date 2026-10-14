package dev.salamiclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** Knopf in den Einstellungen (z. B. „Wegpunkte verwalten“). Hat keinen gespeicherten Wert. */
public final class ActionSetting extends Setting<Boolean> {
	private final Runnable action;

	public ActionSetting(String id, Runnable action) {
		super(id, false);
		this.action = action;
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
