package dev.salamiclient.modules.visual;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.render.WorldLines;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.theme.Theme;
import dev.salamiclient.setting.BoolSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.renderer.state.BlockOutlineRenderState;

/** Block Outline: eigener Umriss (Farbe, Linienstärke) für den anvisierten Block statt des schwarzen Vanilla-Rahmens. */
public final class BlockOutlineModule extends Module {
	private final ColorSetting color = add(new ColorSetting("color", 0xFFB23A3A));
	private final BoolSetting themeColor = add(new BoolSetting("theme_color", true));
	private final NumberSetting width = add(new NumberSetting("width", 2.5, 1, 6, 0.5));

	public BlockOutlineModule() {
		super("block_outline", Category.VISUAL);
	}

	/**
	 * Aus {@code WorldRenderEvents.BEFORE_BLOCK_OUTLINE}.
	 *
	 * @return {@code true}, wenn der Vanilla-Umriss trotzdem gezeichnet werden soll
	 */
	public boolean render(WorldRenderContext context, BlockOutlineRenderState state) {
		if (!isEnabled()) {
			return true;
		}
		int argb = themeColor.isOn() && SalamiClient.get().theme() == Theme.SALAMI ? 0xFFF3E3D3 : color.argb();
		WorldLines.shape(context.matrices(), context.consumers(), WorldLines.VISIBLE, state.pos(), state.shape(), argb, width.get().floatValue());
		return false;
	}
}
