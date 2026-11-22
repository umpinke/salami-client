package dev.salamiclient.gui;

import dev.salamiclient.SalamiClient;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Salami-Knopf im Titelbildschirm und im Pausemenü: ein Symbolknopf (wie Sprache und Barrierefreiheit)
 * rechts neben „Einzelspieler“ bzw. „Zurück zum Spiel“. Ist dort schon etwas (andere Mods), weicht er
 * nach links und zuletzt in die linke obere Ecke aus. Öffnet das Salami-Menü über dem Bildschirm.
 */
public final class MenuButtons {
	private static final Identifier ICON = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "icon/salami");
	private static final int SIZE = 20;
	private static final int GAP = 4;

	private MenuButtons() {}

	public static void register() {
		// Nach den anderen Mods: Mod Menu verschiebt die Titelbildschirm-Knöpfe ebenfalls in AFTER_INIT
		Identifier late = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "menu_buttons");
		ScreenEvents.AFTER_INIT.addPhaseOrdering(Event.DEFAULT_PHASE, late);
		ScreenEvents.AFTER_INIT.register(late, (client, screen, width, height) -> {
			if (screen instanceof TitleScreen) {
				add(screen, "menu.singleplayer");
			} else if (screen instanceof PauseScreen pause && pause.showsPauseMenu()) {
				add(screen, "menu.returnToGame");
			}
		});
	}

	private static void add(Screen screen, String anchorKey) {
		List<AbstractWidget> widgets = widgets(screen);
		AbstractWidget anchor = find(widgets, anchorKey, screen.width);
		int[][] spots = anchor == null
			? new int[][] {{GAP, GAP}}
			: new int[][] {{anchor.getRight() + GAP, anchor.getY()}, {anchor.getX() - GAP - SIZE, anchor.getY()}, {GAP, GAP}};
		for (int[] spot : spots) {
			if (fits(widgets, spot[0], spot[1], screen.width, screen.height)) {
				widgets.add(button(spot[0], spot[1]));
				return;
			}
		}
	}

	private static List<AbstractWidget> widgets(Screen screen) {
		//? if >=26.1 {
		/*return Screens.getWidgets(screen);
		*///?} else
		return Screens.getButtons(screen);
	}

	/** Knopf mit diesem Übersetzungsschlüssel, sonst der oberste breite Knopf in der Mitte. */
	private static AbstractWidget find(List<AbstractWidget> widgets, String key, int screenWidth) {
		AbstractWidget fallback = null;
		for (AbstractWidget widget : widgets) {
			if (widget.getMessage().getContents() instanceof TranslatableContents translatable && key.equals(translatable.getKey())) {
				return widget;
			}
			boolean centered = Math.abs(widget.getX() + widget.getWidth() / 2 - screenWidth / 2) <= 4 && widget.getWidth() >= 150;
			if (centered && (fallback == null || widget.getY() < fallback.getY())) {
				fallback = widget;
			}
		}
		return fallback;
	}

	/** Passt der Knopf an diese Stelle – im Bild und ohne andere Knöpfe zu verdecken? */
	private static boolean fits(List<AbstractWidget> widgets, int x, int y, int width, int height) {
		if (x < 0 || y < 0 || x + SIZE > width || y + SIZE > height) {
			return false;
		}
		for (AbstractWidget widget : widgets) {
			if (widget.visible && x < widget.getRight() && x + SIZE > widget.getX() && y < widget.getBottom() && y + SIZE > widget.getY()) {
				return false;
			}
		}
		return true;
	}

	private static AbstractWidget button(int x, int y) {
		Component label = Component.translatable("salami.menu.button");
		SpriteIconButton button = SpriteIconButton.builder(label, b -> open(), true)
			.sprite(ICON, 16, 16)
			.size(SIZE, SIZE)
			.build();
		button.setPosition(x, y);
		button.setTooltip(Tooltip.create(Component.translatable("salami.menu.button.tooltip", SalamiClient.get().clickGuiKey().getTranslatedKeyMessage())));
		return button;
	}

	private static void open() {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.setScreen(SalamiMenuScreen.over(minecraft.screen));
	}
}
