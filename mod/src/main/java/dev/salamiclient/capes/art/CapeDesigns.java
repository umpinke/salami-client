package dev.salamiclient.capes.art;

import java.util.List;

/** Alle gemalten Capes, in der Reihenfolge der Galerie. */
public final class CapeDesigns {
	private static final List<CapeDesign> ALL = List.of(
		new SalamiCape(),
		new AuroraCape(),
		new MagmaCape(),
		new GalaxyCape(),
		new NeonCape(),
		new PrismCape(),
		new SakuraCape(),
		new StormCape(),
		new DragonCape());

	private CapeDesigns() {}

	public static List<CapeDesign> all() {
		return ALL;
	}

	/** Motiv mit diesem Namen ({@code "aurora"} …) oder {@code null}. */
	public static CapeDesign byName(String name) {
		for (CapeDesign design : ALL) {
			if (design.name.equals(name)) {
				return design;
			}
		}
		return null;
	}
}
