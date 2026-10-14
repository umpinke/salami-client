package dev.salamiclient.modules.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;

/** Item Model: Position und Größe der Items in deiner Hand (Ego-Sicht) frei einstellen. */
public final class ItemModelModule extends Module {
	private static ItemModelModule instance;

	private final NumberSetting x = add(new NumberSetting("x", 0.0, -0.5, 0.5, 0.02));
	private final NumberSetting y = add(new NumberSetting("y", 0.0, -0.5, 0.5, 0.02));
	private final NumberSetting z = add(new NumberSetting("z", 0.0, -0.5, 0.5, 0.02));
	private final NumberSetting size = add(new NumberSetting("size", 1.0, 0.3, 1.5, 0.05));

	public ItemModelModule() {
		super("item_model", Category.VISUAL);
		instance = this;
	}

	/** Für {@code ItemInHandRendererMixin}: rechte/linke Hand gespiegelt, damit „x“ immer nach außen zeigt. */
	public static void transform(LivingEntity entity, ItemDisplayContext context, PoseStack poseStack) {
		if (instance == null || !instance.isEnabled() || entity != Minecraft.getInstance().player) {
			return;
		}
		float direction;
		if (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
			direction = 1.0F;
		} else if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
			direction = -1.0F;
		} else {
			return;
		}
		poseStack.translate(direction * instance.x.get().floatValue(), instance.y.get().floatValue(), instance.z.get().floatValue());
		float scale = instance.size.get().floatValue();
		poseStack.scale(scale, scale, scale);
	}
}
