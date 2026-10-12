package dev.salamiclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Zahl mit Minimum, Maximum und Schrittweite (Slider in der ClickGUI). */
public final class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;

	public NumberSetting(String id, double defaultValue, double min, double max, double step) {
		super(id, defaultValue);
		if (min >= max || step <= 0) {
			throw new IllegalArgumentException("Invalid range for setting " + id);
		}
		this.min = min;
		this.max = max;
		this.step = step;
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}

	public double step() {
		return step;
	}

	public int intValue() {
		return (int) Math.round(get());
	}

	/** Anteil 0..1 für die Slider-Darstellung. */
	public double fraction() {
		return (get() - min) / (max - min);
	}

	public void setFraction(double fraction) {
		set(min + Math.max(0, Math.min(1, fraction)) * (max - min));
	}

	@Override
	protected Double sanitize(Double raw) {
		if (raw == null || raw.isNaN()) {
			return defaultValue();
		}
		double snapped = min + Math.round((raw - min) / step) * step;
		// Rundungsartefakte (0.30000000000000004) vermeiden
		snapped = Math.round(snapped * 1_000_000d) / 1_000_000d;
		return Math.max(min, Math.min(max, snapped));
	}

	/** Anzeige ohne überflüssige Nachkommastellen. */
	public String display() {
		double v = get();
		if (step >= 1 && v == Math.rint(v)) {
			return Integer.toString((int) v);
		}
		return String.format(java.util.Locale.ROOT, step >= 0.1 ? "%.1f" : "%.2f", v);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			set(json.getAsDouble());
		}
	}
}
