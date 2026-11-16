package dev.salamiclient.capes.art;

/** Eine Farbe zum Mischen (Kanäle 0..1, dürfen beim Rechnen überlaufen). Eine Instanz je Thread. */
public final class Rgb {
	public double r;
	public double g;
	public double b;

	public Rgb set(double r, double g, double b) {
		this.r = r;
		this.g = g;
		this.b = b;
		return this;
	}

	public Rgb set(int hex) {
		return set((hex >> 16 & 255) / 255.0, (hex >> 8 & 255) / 255.0, (hex & 255) / 255.0);
	}

	/** Zur Farbe {@code hex} überblenden ({@code a} = 0..1). */
	public Rgb mix(int hex, double a) {
		a = Art.clamp01(a);
		r += ((hex >> 16 & 255) / 255.0 - r) * a;
		g += ((hex >> 8 & 255) / 255.0 - g) * a;
		b += ((hex & 255) / 255.0 - b) * a;
		return this;
	}

	/** Licht dazugeben (Leuchten, Funken). */
	public Rgb add(int hex, double a) {
		r += (hex >> 16 & 255) / 255.0 * a;
		g += (hex >> 8 & 255) / 255.0 * a;
		b += (hex & 255) / 255.0 * a;
		return this;
	}

	public Rgb mul(double k) {
		r *= k;
		g *= k;
		b *= k;
		return this;
	}

	/** Farbe aus einem Verlauf: {@code v} = 0..1 läuft gleichmäßig durch {@code stops}. */
	public Rgb ramp(int[] stops, double v) {
		int hex = rampHex(stops, v);
		return set(hex);
	}

	public Rgb mixRamp(int[] stops, double v, double a) {
		return mix(rampHex(stops, v), a);
	}

	public Rgb addRamp(int[] stops, double v, double a) {
		return add(rampHex(stops, v), a);
	}

	private static int rampHex(int[] stops, double v) {
		double p = Art.clamp01(v) * (stops.length - 1);
		int i = Math.min((int) p, stops.length - 2);
		double f = p - i;
		int a = stops[i];
		int b = stops[i + 1];
		int r = (int) Math.round(Art.mix(a >> 16 & 255, b >> 16 & 255, f));
		int g = (int) Math.round(Art.mix(a >> 8 & 255, b >> 8 & 255, f));
		int bl = (int) Math.round(Art.mix(a & 255, b & 255, f));
		return r << 16 | g << 8 | bl;
	}
}
