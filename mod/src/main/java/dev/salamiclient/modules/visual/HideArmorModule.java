package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Hide Armor: Rüstung ausblenden – nur bei dir selbst oder bei allen Spielern (nur Optik). */
public final class HideArmorModule extends Module {
	public enum Who {
		SELF,
		PLAYERS
	}

	private static HideArmorModule instance;

	private final ModeSetting<Who> who = add(new ModeSetting<>("who", Who.SELF));

	public HideArmorModule() {
		super("hide_armor", Category.VISUAL);
		instance = this;
	}

	/** Für {@code HumanoidArmorLayerMixin}. Mobs (Zombies in Rüstung usw.) bleiben unverändert. */
	public static boolean hides(HumanoidRenderState state) {
		if (instance == null || !instance.isEnabled() || !(state instanceof AvatarRenderState avatar)) {
			return false;
		}
		if (instance.who.get() == Who.PLAYERS) {
			return true;
		}
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && avatar.id == mc.player.getId();
	}
}
