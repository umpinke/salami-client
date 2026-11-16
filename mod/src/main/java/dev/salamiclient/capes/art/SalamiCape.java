package dev.salamiclient.capes.art;

/**
 * Salami: das Wappen des Clients – eine Salamischeibe im Goldring vor einem langsam drehenden
 * Strahlenkranz auf weinrotem Tuch, mit Goldstaub und einem Glanz, der übers Wappen zieht.
 */
final class SalamiCape extends CapeDesign {
	private static final int[] CLOTH = {0x3a0a12, 0x24060d, 0x120308};
	private static final int[] MEAT = {0xc8474a, 0xb23a3a, 0x8f2530};
	private static final int RAYS = 14;
	private static final double CX = 0.5;
	private static final double CY = 0.66;
	private static final double R = 0.30;

	SalamiCape() {
		super("salami", 36, 83);
	}

	@Override
	protected void shade(Rgb out, int face, double x, double y, double t, double px, double[] pre) {
		double dx = x - CX;
		double dy = y - CY;
		double dist = Math.sqrt(dx * dx + dy * dy);
		out.ramp(CLOTH, dist / 0.95);
		// Stoff: feines Rautenmuster
		double weave = Math.abs(Art.fract((x + y) * 9) - 0.5) + Math.abs(Art.fract((x - y) * 9) - 0.5);
		out.mul(0.94 + 0.12 * Art.smooth(0.35, 0.6, weave));

		// Strahlenkranz: dreht sich je Schleife um genau einen Strahl weiter
		double angle = Math.atan2(dy, dx) + Art.TAU * t / RAYS;
		double ray = Math.abs(Art.fract(angle / Art.TAU * RAYS) - 0.5) * 2;
		double rayEdge = (ray - 0.5) * dist * Art.TAU / RAYS / 2;
		out.add(0xe2a544, Art.cover(rayEdge, px) * 0.22 * Math.exp(-dist / 0.55) * Art.smooth(R, R + 0.08, dist));
		out.add(0xffc45a, Math.exp(-Math.max(0, dist - R) / 0.09) * (face == WING ? 0.10 : 0.30));
		out.add(0xffe2a0, Art.star(x, y, 9, t, 0.5, 33) * 0.9);

		if (face != WING) {
			chevrons(out, x, y, t, px);
			slice(out, dx, dy, dist, t, px);
		}
		trim(out, face, x, y, px, 0xe8b85a, 0.8);
	}

	/** Zwei Goldwinkel unter dem Wappen, die nacheinander aufleuchten. */
	private static void chevrons(Rgb out, double x, double y, double t, double px) {
		for (int i = 0; i < 2; i++) {
			double tip = 1.22 + i * 0.11 - Math.abs(x - 0.5) * 0.55;
			double d = Math.abs(y - tip) * 0.87 - 0.014;
			double side = Art.smooth(0.34, 0.30, Math.abs(x - 0.5));
			double lit = 0.6 + 0.4 * Math.sin(Art.TAU * (t - i * 0.22));
			out.mix(0xe8b85a, Art.cover(d, px) * side * (0.55 + 0.45 * lit));
			out.add(0xffd98a, Art.glow(d + 0.014, 0.03) * side * lit * 0.18);
		}
	}

	private static void slice(Rgb out, double dx, double dy, double dist, double t, double px) {
		if (dist > R + 0.06) {
			return;
		}
		// Goldring mit Kordelkante
		double ring = Math.abs(dist - (R + 0.022)) - 0.012;
		double rope = 0.75 + 0.25 * Math.sin(Math.atan2(dy, dx) * 40 + dist * 90);
		out.mix(0xb8822e, Art.cover(ring, px));
		out.mix(0xffe6a8, Art.cover(ring + 0.007, px) * rope * 0.8);
		// Pelle
		out.mix(0x4a2018, Art.cover(dist - R, px));
		double meat = Art.cover(dist - R * 0.93, px);
		if (meat <= 0) {
			return;
		}
		double keepR = out.r;
		double keepG = out.g;
		double keepB = out.b;
		out.ramp(MEAT, dist / R + 0.25 * (Art.fbm(dx * 16, dy * 16, 3, 4) - 0.5));
		// Fettstücke: unregelmäßige helle Flecken auf einem verzogenen Raster
		double gx = dx * 15 + 1.2 * (Art.noise(dx * 9, dy * 9, 5) - 0.5);
		double gy = dy * 15 + 1.2 * (Art.noise(dx * 9 + 20, dy * 9, 6) - 0.5);
		int ix = (int) Math.floor(gx);
		int iy = (int) Math.floor(gy);
		for (int oy = -1; oy <= 1; oy++) {
			for (int ox = -1; ox <= 1; ox++) {
				int cx = ix + ox;
				int cy = iy + oy;
				double pick = Art.rnd(cx, cy, 7);
				if (pick > 0.72) {
					continue;
				}
				double fx = gx - cx - (0.2 + 0.6 * Art.rnd(cx, cy, 8));
				double fy = gy - cy - (0.2 + 0.6 * Art.rnd(cx, cy, 9));
				double spin = Art.rnd(cx, cy, 10) * Math.PI;
				double lx = fx * Math.cos(spin) + fy * Math.sin(spin);
				double ly = (-fx * Math.sin(spin) + fy * Math.cos(spin)) * (1.3 + pick);
				double blob = (Math.sqrt(lx * lx + ly * ly) - (0.16 + 0.26 * Art.rnd(cx, cy, 11))) / 15;
				out.mix(pick < 0.12 ? 0x2b1d1a : 0xf6e7d6, Art.cover(blob, px) * 0.95);
			}
		}
		// Wölbung: Rand dunkler, Glanzbogen oben links
		out.mul(0.78 + 0.22 * Art.smooth(R * 0.93, R * 0.55, dist));
		double hx = dx + R * 0.30;
		double hy = dy + R * 0.34;
		out.add(0xffffff, Math.exp(-(hx * hx + hy * hy) / (R * R * 0.10)) * 0.22);
		// Glanz zieht einmal je Schleife schräg übers Wappen
		double band = dx + dy * 0.5 - (-0.75 + t * 1.5);
		out.add(0xfff3d6, Math.exp(-(band * band) / (0.045 * 0.045)) * 0.38);
		out.set(Art.mix(keepR, out.r, meat), Art.mix(keepG, out.g, meat), Art.mix(keepB, out.b, meat));
	}
}
