package dev.salamiclient.modules.misc;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/**
 * Auto Reconnect: nach einem Verbindungsabbruch ein „Neu verbinden“-Knopf, optional mit
 * Countdown, der sich selbst wieder verbindet. Nur für Server, nicht für Einzelspieler-Welten.
 */
public final class AutoReconnectModule extends Module {
	private static AutoReconnectModule instance;
	/** Zuletzt betretener Server (bei jedem Beitritt gemerkt). */
	private static ServerData lastServer;

	private final BoolSetting auto = add(new BoolSetting("auto", true));
	private final NumberSetting delay = add(new NumberSetting("delay", 5, 2, 30, 1));

	public AutoReconnectModule() {
		super("auto_reconnect", Category.MISC);
		instance = this;
	}

	/** Bei jedem Server-Beitritt (siehe {@code SalamiClient}). */
	public static void rememberServer(ServerData server) {
		if (server != null) {
			lastServer = server;
		}
	}

	/** Für {@code DisconnectedScreenMixin}: Knopf oder {@code null}, wenn nicht passend. */
	public static Button createButton() {
		if (instance == null || !instance.isEnabled() || lastServer == null) {
			return null;
		}
		ServerData server = lastServer;
		long deadline = instance.auto.isOn() ? System.currentTimeMillis() + instance.delay.intValue() * 1000L : -1;
		return new ReconnectButton(server, deadline);
	}

	private static void reconnect(ServerData server) {
		Minecraft mc = Minecraft.getInstance();
		ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, ServerAddress.parseString(server.ip), server, false, null);
	}

	/** Knopf mit Countdown im Text; verbindet beim Klick oder wenn der Countdown abläuft. */
	private static final class ReconnectButton extends Button.Plain {
		private final ServerData server;
		private final long deadline;
		private boolean done;

		ReconnectButton(ServerData server, long deadline) {
			super(0, 0, 200, 20, Component.empty(), button -> ((ReconnectButton) button).fire(), DEFAULT_NARRATION);
			this.server = server;
			this.deadline = deadline;
			updateLabel();
		}

		private void fire() {
			if (!done) {
				done = true;
				reconnect(server);
			}
		}

		private void updateLabel() {
			if (deadline < 0) {
				setMessage(Component.translatable("salami.module.auto_reconnect.button"));
				return;
			}
			long seconds = Math.max(0, (deadline - System.currentTimeMillis() + 999) / 1000);
			setMessage(Component.literal(I18n.get("salami.module.auto_reconnect.countdown", seconds)));
		}

		@Override
		protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			updateLabel();
			if (deadline >= 0 && !done && System.currentTimeMillis() >= deadline) {
				// nicht mitten im Zeichnen den Bildschirm wechseln – im nächsten Durchlauf verbinden
				Minecraft.getInstance().execute(this::fire);
			}
			super.renderContents(graphics, mouseX, mouseY, partialTick);
		}
	}
}
