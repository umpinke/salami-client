package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import net.minecraft.world.phys.AABB;

/**
 * Item Physics: fallen gelassene Gegenstände schweben und drehen sich nicht mehr, sondern liegen
 * flach auf dem Boden (Blöcke stehen still auf dem Boden). Nur Optik.
 */
public final class ItemPhysicsModule extends Module {
	/** Dünner als das ist ein flaches Item-Modell (Schwert, Apfel …), kein Block. */
	private static final double FLAT_DEPTH = 0.15;

	private static ItemPhysicsModule instance;

	public ItemPhysicsModule() {
		super("item_physics", Category.VISUAL);
		instance = this;
	}

	/** Für {@code ItemEntityRendererMixin}. */
	public static boolean active() {
		return instance != null && instance.isEnabled();
	}

	public static boolean isFlat(AABB modelBox) {
		return modelBox.getZsize() < FLAT_DEPTH;
	}

	/**
	 * Höhe über dem Boden: Blöcke mit der Unterseite auf dem Boden. Flache Items werden um 90° um X
	 * gekippt – ihre Tiefe z wird dabei zu -y, also um {@code maxZ} anheben (plus ein Hauch gegen Flackern).
	 */
	public static float restingHeight(AABB modelBox) {
		return isFlat(modelBox) ? (float) (modelBox.maxZ + 0.01) : (float) -modelBox.minY;
	}
}
