package dev.salamiclient.modules.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Side Shield: beim Blocken rückt dein Schild in der Ego-Sicht zur Seite, nach unten und wird
 * kleiner – er verdeckt nicht mehr die Bildmitte. Ohne Blocken bleibt er wie in Vanilla.
 */
public final class SideShieldModule extends Module {
	private static SideShieldModule instance;

	private final NumberSetting side = add(new NumberSetting("side", 0.25, 0.0, 0.6, 0.05));
	private final NumberSetting down = add(new NumberSetting("down", 0.15, 0.0, 0.5, 0.05));
	private final NumberSetting size = add(new NumberSetting("size", 0.85, 0.5, 1.0, 0.05));
	private final BoolSetting always = add(new BoolSetting("always", false));

	public SideShieldModule() {
		super("side_shield", Category.VISUAL);
		instance = this;
	}

	/** Für {@code ItemInHandRendererMixin}: nur Schilde in deiner eigenen Hand in der Ego-Sicht. */
	public static void transform(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack poseStack) {
		if (instance == null || !instance.isEnabled() || !stack.is(Items.SHIELD) || entity != Minecraft.getInstance().player) {
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
		// Der Hand-Renderer arbeitet mit einer Kopie des Stacks – daher nach Item vergleichen
		boolean blocking = entity.isUsingItem() && entity.getUseItem().is(Items.SHIELD);
		if (!blocking && !instance.always.isOn()) {
			return;
		}
		float scale = instance.size.get().floatValue();
		poseStack.translate(direction * instance.side.get().floatValue(), -instance.down.get().floatValue(), 0.0F);
		poseStack.scale(scale, scale, scale);
	}
}
