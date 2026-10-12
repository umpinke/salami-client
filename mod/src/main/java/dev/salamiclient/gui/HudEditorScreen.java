package dev.salamiclient.gui;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.module.Module;
import dev.salamiclient.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD-Editor (Overlay-System): alle aktiven HUD-Elemente per Maus verschieben.
 * Die Elemente zeichnen sich weiter selbst über das HUD; der Editor legt nur
 * Rahmen darüber und speichert die Position als Anteil der Bildschirmgröße.
 */
public final class HudEditorScreen extends Screen {
	private final Screen parent;
	private HudModule dragging;
	private int dragOffsetX;
	private int dragOffsetY;

	public HudEditorScreen(Screen parent) {
		super(Component.translatable("salami.hud_editor.title"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static List<HudModule> elements() {
		List<HudModule> result = new ArrayList<>();
		for (Module module : SalamiClient.get().modules().all()) {
			if (module instanceof HudModule hud && hud.isEnabled()) {
				result.add(hud);
			}
		}
		return result;
	}

	private record Box(int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private Box box(HudModule hud) {
		float s = hud.scale.get().floatValue();
		return new Box(hud.screenX(width), hud.screenY(height), Math.max(8, Math.round(hud.width() * s)), Math.max(8, Math.round(hud.height() * s)));
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0x30000000);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		Theme theme = SalamiClient.get().theme();
		for (HudModule hud : elements()) {
			Box b = box(hud);
			boolean active = hud == dragging || b.contains(mouseX, mouseY);
			if (!hud.hasContent()) {
				graphics.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), 0x40000000);
			}
			graphics.renderOutline(b.x() - 1, b.y() - 1, b.w() + 2, b.h() + 2, active ? theme.accent() : theme.textDim());
			if (active) {
				// Name nur am Element unter der Maus – sonst überlappen die Beschriftungen
				String name = I18n.get(hud.translationKey());
				int labelY = b.y() >= 10 ? b.y() - 10 : b.y() + b.h() + 2;
				int labelX = Math.min(b.x(), width - font.width(name) - 2);
				graphics.drawString(font, name, labelX, labelY, theme.accent(), true);
			}
		}
		graphics.drawCenteredString(font, I18n.get("salami.hud_editor.hint"), width / 2, height / 2 + 24, theme.textDim());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		List<HudModule> list = elements();
		for (int i = list.size() - 1; i >= 0; i--) {
			HudModule hud = list.get(i);
			Box b = box(hud);
			if (b.contains(event.x(), event.y())) {
				dragging = hud;
				dragOffsetX = (int) event.x() - b.x();
				dragOffsetY = (int) event.y() - b.y();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging != null) {
			dragging.moveTo((int) event.x() - dragOffsetX, (int) event.y() - dragOffsetY, width, height);
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
