package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/** NameTags: eigenen Namen in der Third-Person-Ansicht zeigen, Hintergrund der Namen einstellen. */
public final class NameTagsModule extends Module {
	private static NameTagsModule instance;

	private final BoolSetting showOwn = add(new BoolSetting("show_own", true));
	private final NumberSetting background = add(new NumberSetting("background", 0.25, 0.0, 1.0, 0.05));

	public NameTagsModule() {
		super("nametags", Category.VISUAL);
		instance = this;
	}

	/** Für {@code LivingEntityRendererMixin}: eigener Name nur, wenn die Kamera nicht in dir steckt. */
	public static boolean showOwn(LivingEntity entity) {
		if (instance == null || !instance.isEnabled() || !instance.showOwn.isOn()) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		return entity == mc.player
			&& Minecraft.renderNames()
			&& !mc.options.getCameraType().isFirstPerson()
			&& !entity.isInvisible();
	}

	/** Für {@code NameTagStorageMixin}. */
	public static float backgroundOpacity(float vanilla) {
		return instance != null && instance.isEnabled() ? instance.background.get().floatValue() : vanilla;
	}
}
