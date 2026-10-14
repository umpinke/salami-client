package dev.salamiclient.waypoints;

import net.minecraft.world.phys.Vec3;

/** Ein Wegpunkt. Veränderbar, damit der Wegpunkt-Bildschirm direkt bearbeiten kann. */
public final class Waypoint {
	public static final int MAX_NAME = 32;

	public String name;
	public int x;
	public int y;
	public int z;
	/** Dimension, z. B. {@code minecraft:overworld}. */
	public String dimension;
	public int color;
	public boolean visible = true;

	public Waypoint(String name, int x, int y, int z, String dimension, int color) {
		this.name = clean(name);
		this.x = x;
		this.y = y;
		this.z = z;
		this.dimension = dimension;
		this.color = color;
	}

	public Vec3 center() {
		return new Vec3(x + 0.5, y + 0.5, z + 0.5);
	}

	/** Steuerzeichen und Formatierungscodes raus, Länge begrenzen. */
	public static String clean(String raw) {
		if (raw == null) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		raw.codePoints().filter(c -> c >= 0x20 && c != 0x7F && c != 0xA7).forEach(out::appendCodePoint);
		String text = out.toString().trim();
		return text.length() > MAX_NAME ? text.substring(0, MAX_NAME) : text;
	}
}
