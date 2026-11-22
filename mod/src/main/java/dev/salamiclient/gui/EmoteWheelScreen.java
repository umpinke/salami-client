package dev.salamiclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.emotes.Emote;
import dev.salamiclient.emotes.EmoteCatalog;
import dev.salamiclient.emotes.EmoteIcons;
import dev.salamiclient.emotes.Emotes;
import dev.salamiclient.gui.skin.Skin;
import dev.salamiclient.gui.skin.Skins;
import dev.salamiclient.render.GuiDraw;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Emote-Rad: Taste halten, Maus in die Richtung des Emotes bewegen, loslassen – oder klicken bzw.
 * 1–8 drücken. Kurz antippen lässt das Rad offen, dann wählt ein Klick. Gibt es mehr als acht
 * Emotes, blättert das Mausrad (oder Tab) zur nächsten Seite.
 */
public final class EmoteWheelScreen extends Screen {
	private static final int SLOTS = 8;
	private static final int CHIP_H = 22;
	private static final int ICON = 16;
	/** So weit ragt ein seitliches Feld von seinem Platz auf dem Kreis zur Mitte hin. */
	private static final int ANCHOR = 36;

	/** Zuletzt gezeigte Seite – das Rad öffnet dort wieder. */
	private static int lastPage;

	private final @Nullable KeyMapping key;
	private int page = lastPage;
	private int hovered = -1;

	public EmoteWheelScreen(@Nullable KeyMapping key) {
		super(Component.translatable("salami.emotes.wheel"));
		this.key = key;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static int pages() {
		return Math.max(1, (EmoteCatalog.all().size() + SLOTS - 1) / SLOTS);
	}

	/** Emote im Feld {@code slot} der aktuellen Seite oder {@code null} (leeres Feld auf der letzten Seite). */
	private @Nullable Emote emoteAt(int slot) {
		List<Emote> all = EmoteCatalog.all();
		int index = page * SLOTS + slot;
		return slot >= 0 && index < all.size() ? all.get(index) : null;
	}

	private void turnPage(int direction) {
		page = Math.floorMod(page + direction, pages());
		lastPage = page;
	}

	private int radius() {
		return Math.max(50, Math.min(92, Math.min(width, height) / 2 - 30));
	}

	/**
	 * Linke obere Ecke des Felds Nr. {@code i} (im Uhrzeigersinn, oben beginnend) bei Feldbreite
	 * {@code w}. Felder rechts der Mitte wachsen mit langen Namen nach rechts, linke nach links –
	 * so bleibt die Mitte frei und nichts überlappt.
	 */
	private int[] chipPos(int i, int w) {
		double angle = -Math.PI / 2 + i * (2 * Math.PI / SLOTS);
		double cos = Math.cos(angle);
		int r = radius();
		int cx = width / 2 + (int) Math.round(cos * r);
		int cy = height / 2 + (int) Math.round(Math.sin(angle) * r * 0.8);
		int x;
		if (Math.abs(cos) < 0.1) {
			x = cx - w / 2;
		} else if (cos > 0) {
			x = cx - ANCHOR;
		} else {
			x = cx + ANCHOR - w;
		}
		return new int[] {Math.max(2, Math.min(width - w - 2, x)), cy - CHIP_H / 2};
	}

	private int chipWidth() {
		int widest = 0;
		for (int slot = 0; slot < SLOTS; slot++) {
			Emote emote = emoteAt(slot);
			if (emote != null) {
				widest = Math.max(widest, font.width(emote.displayName()));
			}
		}
		return widest + ICON + 24;
	}

	/** Feld unter der Maus: nach Richtung von der Mitte aus, mit totem Bereich in der Mitte; -1 = keins. */
	private int sectorAt(double mx, double my) {
		double dx = mx - width / 2.0;
		double dy = (my - height / 2.0) / 0.8;
		if (dx * dx + dy * dy < 22 * 22) {
			return -1;
		}
		double angle = Math.atan2(dy, dx) + Math.PI / 2;
		double step = 2 * Math.PI / SLOTS;
		int sector = Math.floorMod((int) Math.round(angle / step), SLOTS);
		return emoteAt(sector) == null ? -1 : sector;
	}

	@Override
	//? if >=26.1 {
	/*public void extractBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	*///?} else
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// Kein Hintergrund des Looks: das Rad liegt im laufenden Spiel, man soll sich weiter sehen
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
		Skin skin = Skins.current();
		Skin.Ink popup = skin.ink(Skin.Surface.POPUP);
		if (page >= pages()) {
			page = 0; // der Katalog ist kleiner geworden
		}
		hovered = sectorAt(mouseX, mouseY);
		int w = chipWidth();
		for (int i = 0; i < SLOTS; i++) {
			Emote emote = emoteAt(i);
			if (emote == null) {
				continue;
			}
			int[] pos = chipPos(i, w);
			int x = pos[0];
			int y = pos[1];
			boolean on = i == hovered;
			Skin.ButtonKind kind = on ? Skin.ButtonKind.PRIMARY : Skin.ButtonKind.SECONDARY;
			Skin.UiState state = on ? Skin.UiState.HOVER : Skin.UiState.NORMAL;
			skin.button(g, font, x, y, w, CHIP_H, "", kind, state);
			int text = skin.buttonInk(kind, state);
			g.blit(RenderPipelines.GUI_TEXTURED, EmoteIcons.texture(emote), x + 5, y + 2, 0f, 0f, ICON, ICON,
				EmoteIcons.SIZE, EmoteIcons.SIZE, EmoteIcons.SIZE, EmoteIcons.SIZE, text);
			GuiDraw.text(g, font, emote.displayName(), x + ICON + 9, y + 6, text, false);
			GuiDraw.text(g, font, String.valueOf(i + 1), x + w - 9, y + 2, GuiDraw.fade(text, 0.5F), false);
		}
		// Mitte: gewähltes Emote oder Hinweis, darunter die Seite
		Emote chosen = emoteAt(hovered);
		String middle = chosen != null ? chosen.displayName() : I18n.get("salami.emotes.wheel");
		int mw = font.width(middle) + 16;
		skin.surface(g, Skin.Surface.POPUP, (width - mw) / 2, height / 2 - 9, mw, 18);
		GuiDraw.text(g, font, middle, (width - font.width(middle)) / 2, height / 2 - 4, chosen != null ? popup.accent() : popup.text(), false);
		int pages = pages();
		if (pages > 1) {
			// ein Punkt pro Seite
			int dotsWidth = pages * 7 - 3;
			int dx = (width - dotsWidth) / 2;
			for (int i = 0; i < pages; i++) {
				GuiDraw.rounded(g, dx + i * 7, height / 2 + 14, 4, 4, 2, i == page ? popup.accent() : 0x90FFFFFF);
			}
		}
		SalamiWindow.hint(g, font, I18n.get(pages > 1 ? "salami.emotes.wheel.hint_pages" : "salami.emotes.wheel.hint"), width, height);
	}

	private void play(int slot) {
		Emote emote = emoteAt(slot);
		onClose();
		if (emote != null) {
			Emotes.play(emote);
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
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0 && pages() > 1) {
			turnPage(scrollY < 0 ? 1 : -1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int k = event.key();
		if (k >= InputConstants.KEY_1 && k < InputConstants.KEY_1 + SLOTS) {
			if (emoteAt(k - InputConstants.KEY_1) != null) {
				play(k - InputConstants.KEY_1);
			}
			return true;
		}
		if (k == InputConstants.KEY_TAB && pages() > 1) {
			turnPage(1);
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
