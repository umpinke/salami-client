package dev.salamiclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.emotes.Emote;
import dev.salamiclient.emotes.Emotes;
import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.theme.Theme;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Emote-Rad: Taste halten, Maus in die Richtung des Emotes bewegen, loslassen – oder klicken bzw.
 * 1–8 drücken. Kurz antippen lässt das Rad offen, dann wählt ein Klick.
 */
public final class EmoteWheelScreen extends Screen {
	private static final Emote[] EMOTES = Emote.values();
	private static final int CHIP_H = 22;
	private static final int ICON = 16;

	private final @Nullable KeyMapping key;
	private int hovered = -1;

	public EmoteWheelScreen(@Nullable KeyMapping key) {
		super(Component.translatable("salami.emotes.wheel"));
		this.key = key;
	}

	public static Identifier icon(Emote emote) {
		return Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "textures/emotes/" + emote.id() + ".png");
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int radius() {
		return Math.max(50, Math.min(92, Math.min(width, height) / 2 - 30));
	}

	/** Mitte des Felds Nr. {@code i}: im Uhrzeigersinn, oben beginnend. */
	private int[] chipCenter(int i) {
		double angle = -Math.PI / 2 + i * (2 * Math.PI / EMOTES.length);
		int r = radius();
		return new int[] {width / 2 + (int) Math.round(Math.cos(angle) * r), height / 2 + (int) Math.round(Math.sin(angle) * r * 0.8)};
	}

	private int chipWidth() {
		int widest = 0;
		for (Emote emote : EMOTES) {
			widest = Math.max(widest, font.width(I18n.get(emote.translationKey())));
		}
		return widest + ICON + 14;
	}

	/** Feld unter der Maus: nach Richtung von der Mitte aus, mit totem Bereich in der Mitte. */
	private int sectorAt(double mx, double my) {
		double dx = mx - width / 2.0;
		double dy = (my - height / 2.0) / 0.8;
		if (dx * dx + dy * dy < 22 * 22) {
			return -1;
		}
		double angle = Math.atan2(dy, dx) + Math.PI / 2;
		double step = 2 * Math.PI / EMOTES.length;
		return Math.floorMod((int) Math.round(angle / step), EMOTES.length);
	}

	@Override
	//? if >=26.1 {
	/*public void extractBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	*///?} else
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0x78000000);
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	*///?} else {
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
	//?}
		Theme theme = SalamiClient.get().theme();
		hovered = sectorAt(mouseX, mouseY);
		int w = chipWidth();
		for (int i = 0; i < EMOTES.length; i++) {
			int[] c = chipCenter(i);
			int x = c[0] - w / 2;
			int y = c[1] - CHIP_H / 2;
			boolean on = i == hovered;
			GuiDraw.rounded(g, x, y, w, CHIP_H, 6, on ? theme.accent() : 0xE0121216);
			GuiDraw.roundedOutline(g, x, y, w, CHIP_H, 6, on ? 0x40FFFFFF : 0x26FFFFFF);
			g.blit(RenderPipelines.GUI_TEXTURED, icon(EMOTES[i]), x + 5, y + 3, 0f, 0f, ICON, ICON, 32, 32, 32, 32);
			int text = on ? GuiDraw.textOn(theme.accent()) : 0xFFE6E6EC;
			GuiDraw.text(g, font, I18n.get(EMOTES[i].translationKey()), x + ICON + 9, y + 7, text, false);
			GuiDraw.text(g, font, String.valueOf(i + 1), x + w - 9, y + 2, GuiDraw.fade(text, 0.5F), false);
		}
		// Mitte: gewähltes Emote oder Hinweis
		String middle = hovered >= 0 ? I18n.get(EMOTES[hovered].translationKey()) : I18n.get("salami.emotes.wheel");
		int mw = font.width(middle) + 16;
		GuiDraw.rounded(g, (width - mw) / 2, height / 2 - 9, mw, 18, 6, 0xE8121216);
		GuiDraw.centeredText(g, font, middle, width / 2, height / 2 - 4, hovered >= 0 ? theme.accent() : 0xFFB8B8C0);
		String hint = I18n.get("salami.emotes.wheel.hint");
		GuiDraw.centeredText(g, font, hint, width / 2, height - 14, 0xFF9A9AA4);
	}

	private void play(int index) {
		onClose();
		if (index >= 0 && index < EMOTES.length) {
			Emotes.play(EMOTES[index]);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int sector = sectorAt(event.x(), event.y());
			if (sector >= 0) {
				play(sector);
				return true;
			}
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			onClose();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int k = event.key();
		if (k >= InputConstants.KEY_1 && k < InputConstants.KEY_1 + EMOTES.length) {
			play(k - InputConstants.KEY_1);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		// Taste gehalten und losgelassen: gezeigtes Emote abspielen; nur angetippt: Rad bleibt offen
		if (key != null && key.matches(event) && hovered >= 0) {
			play(hovered);
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(null);
	}
}
