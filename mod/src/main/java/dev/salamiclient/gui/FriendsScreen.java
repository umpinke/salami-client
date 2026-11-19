package dev.salamiclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.friends.Friends;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.modules.social.FriendsModule;
import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Freunde-Menü: links Freundesliste und offene Anfragen (oben ein Feld zum Hinzufügen per
 * Minecraft-Name), rechts der Chat mit dem gewählten Freund. Enter sendet, Tab wechselt
 * zwischen den Eingabefeldern, Esc schließt.
 */
public final class FriendsScreen extends Screen {
	private static final int HEADER = 24;
	private static final int PAD = 8;
	private static final int LIST_W = 150;
	private static final int ROW = 18;
	private static final int SECTION = 13;
	private static final int FIELD_H = 16;
	private static final int NOTICE_H = 12;
	private static final int BUBBLE_GAP = 4;
	private static final int ONLINE = 0xFF4ADE80;
	private static final int OFFLINE = 0xFF6B6B75;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	/** Zuletzt offene Unterhaltung – beim nächsten Öffnen wieder da. */
	private static @Nullable UUID selected;

	private final Screen parent;
	private float uiScale = 1f;
	private final Field addField = new Field(16);
	private final Field chatField = new Field(Friends.MAX_MESSAGE);
	private @Nullable Field focused;
	private double listScroll;
	/** Abstand vom unteren Ende des Chats (0 = neueste Nachricht sichtbar). */
	private double chatScroll;
	private boolean sending;
	private @Nullable UUID confirmRemove;
	private long confirmUntil;

	/** Einfaches Eingabefeld (Cursor immer am Ende). */
	private static final class Field {
		final int max;
		String text = "";

		Field(int max) {
			this.max = max;
		}
	}

	/** Klickbares Element der Liste links. */
	private record Entry(int y, int height, @Nullable String section, Friends.@Nullable Friend friend, Friends.@Nullable Request request) {}

	public FriendsScreen(@Nullable Screen parent) {
		super(Component.translatable("salami.module.friends"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		uiScale = GuiScaleModule.clickGuiScale();
		if (selected != null && Friends.friend(selected) == null && !Friends.friends().isEmpty()) {
			selected = null;
		}
		Friends.view(selected);
		focused = selected != null ? chatField : addField;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void removed() {
		Friends.view(null);
		Friends.clearNotice();
		super.removed();
	}

	/** Für {@link SalamiClient#isTyping}: die Tastatur gehört den Eingabefeldern. */
	public boolean isTyping() {
		return focused != null;
	}

	// ------------------------------------------------------------------ Layout

	private int uiW() {
		return Math.round(width / uiScale);
	}

	private int uiH() {
		return Math.round(height / uiScale);
	}

	private int[] frame() {
		int w = Math.min(uiW() - 16, 480);
		int h = Math.min(uiH() - 16, 300);
		int x0 = (uiW() - w) / 2;
		int y0 = (uiH() - h) / 2;
		return new int[] {x0, y0, x0 + w, y0 + h};
	}

	private int[] backButton() {
		int[] f = frame();
		return new int[] {f[0] + 4, f[1] + 5, f[0] + 4 + font.width(I18n.get("salami.mods.back")) + 12, f[1] + 19};
	}

	/** Linke Spalte unterhalb des Hinzufügen-Felds. */
	private int[] listArea() {
		int[] f = frame();
		int top = f[1] + HEADER + 4 + FIELD_H + 6;
		return new int[] {f[0] + PAD, top, f[0] + PAD + LIST_W, f[3] - PAD - NOTICE_H};
	}

	private int[] addFieldBox() {
		int[] f = frame();
		int y = f[1] + HEADER + 4;
		return new int[] {f[0] + PAD, y, f[0] + PAD + LIST_W - FIELD_H - 3, y + FIELD_H};
	}

	private int[] addButton() {
		int[] box = addFieldBox();
		return new int[] {box[2] + 3, box[1], box[2] + 3 + FIELD_H, box[3]};
	}

	/** Rechte Seite (Chat). */
	private int[] chatPanel() {
		int[] f = frame();
		return new int[] {f[0] + PAD + LIST_W + PAD, f[1] + HEADER + 4, f[2] - PAD, f[3] - PAD - NOTICE_H};
	}

	private int[] chatHeader() {
		int[] p = chatPanel();
		return new int[] {p[0], p[1], p[2], p[1] + 16};
	}

	private int[] removeButton() {
		int[] h = chatHeader();
		String label = I18n.get(isConfirming() ? "salami.friends.remove_confirm" : "salami.friends.remove");
		int w = font.width(label) + 10;
		return new int[] {h[2] - w, h[1] + 1, h[2], h[3] - 1};
	}

	private int[] messagesArea() {
		int[] p = chatPanel();
		return new int[] {p[0], p[1] + 19, p[2], p[3] - FIELD_H - 5};
	}

	private int[] chatFieldBox() {
		int[] p = chatPanel();
		int w = font.width(I18n.get("salami.friends.send")) + 12;
		return new int[] {p[0], p[3] - FIELD_H, p[2] - w - 3, p[3]};
	}

	private int[] sendButton() {
		int[] box = chatFieldBox();
		int[] p = chatPanel();
		return new int[] {box[2] + 3, box[1], p[2], box[3]};
	}

	private int[] enableButton() {
		int[] p = chatPanel();
		String label = I18n.get("salami.friends.enable");
		int w = font.width(label) + 14;
		int cx = (p[0] + p[2]) / 2;
		int y = (p[1] + p[3]) / 2 + 10;
		return new int[] {cx - w / 2, y, cx + w / 2, y + 16};
	}

	private boolean isConfirming() {
		return confirmRemove != null && confirmRemove.equals(selected) && System.currentTimeMillis() < confirmUntil;
	}

	private static boolean inside(int[] r, double x, double y) {
		return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
	}

	/** Einträge der linken Liste mit y-Positionen (inkl. Scroll). */
	private List<Entry> entries() {
		int[] a = listArea();
		int y = a[1] - (int) Math.round(listScroll);
		List<Entry> result = new ArrayList<>();
		List<Friends.Request> requests = Friends.requests();
		if (!requests.isEmpty()) {
			result.add(new Entry(y, SECTION, I18n.get("salami.friends.requests", requests.size()), null, null));
			y += SECTION;
			for (Friends.Request request : requests) {
				result.add(new Entry(y, ROW, null, null, request));
				y += ROW;
			}
			y += 4;
		}
		List<Friends.Friend> friends = Friends.friends();
		long online = friends.stream().filter(Friends.Friend::online).count();
		result.add(new Entry(y, SECTION, I18n.get("salami.friends.list", online, friends.size()), null, null));
		y += SECTION;
		for (Friends.Friend friend : friends) {
			result.add(new Entry(y, ROW, null, friend, null));
			y += ROW;
		}
		return result;
	}

	private int listHeight() {
		List<Entry> list = entries();
		if (list.isEmpty()) {
			return 0;
		}
		Entry last = list.get(list.size() - 1);
		return last.y() + last.height() - (listArea()[1] - (int) Math.round(listScroll));
	}

	/** Zwei Knöpfe einer Anfrage: annehmen (nur eingehend) und ablehnen/zurückziehen – je {x0, x1}. */
	private int[][] requestButtons() {
		int[] a = listArea();
		int deny = a[2] - 14;
		int accept = deny - 16;
		return new int[][] {{accept, accept + 14}, {deny, deny + 14}};
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	//? if >=26.1 {
	/*public void extractBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	*///?} else
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xAA000000);
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	*///?} else {
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
	//?}
		TextInput.sync(this, isTyping());
		Theme theme = SalamiClient.get().theme();
		int mx = (int) (mouseX / uiScale);
		int my = (int) (mouseY / uiScale);
		if (selected != null && Friends.friend(selected) == null && Friends.status() == Friends.Status.ONLINE && !Friends.friends().isEmpty()) {
			selected = null; // Freundschaft beendet
			Friends.view(null);
		}

		g.pose().pushMatrix();
		g.pose().scale(uiScale, uiScale);
		int[] f = frame();
		GuiDraw.roundRect(g, f[0], f[1], f[2] - f[0], f[3] - f[1], theme.outline());
		GuiDraw.roundRect(g, f[0] + 1, f[1] + 1, f[2] - f[0] - 2, f[3] - f[1] - 2, theme.panel() | 0xFF000000);
		g.fill(f[0] + 1, f[1] + 1, f[2] - 1, f[1] + HEADER - 2, theme.header());
		g.fill(f[0] + 1, f[1] + HEADER - 2, f[2] - 1, f[1] + HEADER - 1, theme.accent());
		GuiDraw.centeredText(g, font, I18n.get("salami.module.friends"), (f[0] + f[2]) / 2, f[1] + 8, theme.headerText());
		button(g, theme, backButton(), I18n.get("salami.mods.back"), mx, my, false);
		renderStatus(g, theme, f);

		renderAddField(g, theme, mx, my);
		renderList(g, theme, mx, my);
		g.fill(f[0] + PAD + LIST_W + PAD / 2, f[1] + HEADER + 4, f[0] + PAD + LIST_W + PAD / 2 + 1, f[3] - PAD - NOTICE_H, theme.outline());
		renderChat(g, theme, mx, my);

		Component notice = Friends.notice();
		if (notice != null) {
			String text = font.plainSubstrByWidth(notice.getString(), f[2] - f[0] - 2 * PAD);
			GuiDraw.text(g, font, text, f[0] + PAD, f[3] - PAD - 8, Friends.noticeIsError() ? theme.warning() : theme.textDim(), false);
		}
		g.pose().popMatrix();
	}

	private void renderStatus(GuiGraphics g, Theme theme, int[] f) {
		Friends.Status status = Friends.status();
		String label = I18n.get("salami.friends.status." + status.name().toLowerCase(java.util.Locale.ROOT));
		int color = switch (status) {
			case ONLINE -> ONLINE;
			case ERROR, NO_SERVER, NO_ACCOUNT -> theme.warning();
			default -> theme.textDim();
		};
		int x = f[2] - 8 - font.width(label);
		g.fill(x - 8, f[1] + 10, x - 4, f[1] + 14, color);
		GuiDraw.text(g, font, label, x, f[1] + 8, color, false);
	}

	private void renderAddField(GuiGraphics g, Theme theme, int mx, int my) {
		field(g, theme, addFieldBox(), addField, I18n.get("salami.friends.add_placeholder"));
		button(g, theme, addButton(), "+", mx, my, !addField.text.isBlank());
	}

	private void renderList(GuiGraphics g, Theme theme, int mx, int my) {
		int[] a = listArea();
		g.enableScissor(a[0], a[1], a[2], a[3]);
		int[][] buttons = requestButtons();
		for (Entry entry : entries()) {
			int y = entry.y();
			if (y + entry.height() < a[1] || y > a[3]) {
				continue;
			}
			boolean hovered = mx >= a[0] && mx < a[2] && my >= y && my < y + entry.height() && my >= a[1] && my < a[3];
			if (entry.section() != null) {
				GuiDraw.text(g, font, entry.section(), a[0] + 1, y + 3, theme.textDim(), false);
				continue;
			}
			if (entry.request() != null) {
				Friends.Request request = entry.request();
				if (hovered) {
					g.fill(a[0], y, a[2], y + ROW, theme.hover());
				}
				int nameRight = request.incoming() ? buttons[0][0] - 3 : buttons[1][0] - 3;
				String suffix = request.incoming() ? "" : " · " + I18n.get("salami.friends.pending");
				String name = font.plainSubstrByWidth(request.name() + suffix, nameRight - a[0] - 4);
				GuiDraw.text(g, font, name, a[0] + 4, y + 5, request.incoming() ? theme.text() : theme.textDim(), false);
				if (request.incoming()) {
					smallButton(g, theme, new int[] {buttons[0][0], y + 2, buttons[0][1], y + ROW - 2}, "✔", ONLINE, mx, my);
				}
				smallButton(g, theme, new int[] {buttons[1][0], y + 2, buttons[1][1], y + ROW - 2}, "✖", theme.warning(), mx, my);
				continue;
			}
			Friends.Friend friend = entry.friend();
			if (friend == null) {
				continue;
			}
			boolean isSelected = friend.uuid().equals(selected);
			if (isSelected) {
				g.fill(a[0], y, a[2], y + ROW, theme.enabledRow());
				g.fill(a[0], y, a[0] + 2, y + ROW, theme.accent());
			} else if (hovered) {
				g.fill(a[0], y, a[2], y + ROW, theme.hover());
			}
			g.fill(a[0] + 6, y + 7, a[0] + 10, y + 11, friend.online() ? ONLINE : OFFLINE);
			int unread = Friends.unread(friend.uuid());
			int nameRight = a[2] - 4;
			if (unread > 0) {
				String badge = unread > 99 ? "99+" : String.valueOf(unread);
				int bw = font.width(badge) + 6;
				GuiDraw.roundRect(g, a[2] - 4 - bw, y + 4, bw, 10, theme.accent());
				GuiDraw.text(g, font, badge, a[2] - 4 - bw + 3, y + 5, GuiDraw.textOn(theme.accent()), false);
				nameRight -= bw + 3;
			}
			String name = font.plainSubstrByWidth(friend.name(), nameRight - a[0] - 14);
			GuiDraw.text(g, font, name, a[0] + 14, y + 5, friend.online() ? theme.text() : theme.textDim(), false);
		}
		if (Friends.friends().isEmpty() && Friends.status() == Friends.Status.ONLINE) {
			int y = entries().get(entries().size() - 1).y() + SECTION + 2;
			List<FormattedCharSequence> lines = font.split(Component.translatable("salami.friends.empty"), LIST_W - 4);
			for (int i = 0; i < lines.size(); i++) {
				GuiDraw.text(g, font, lines.get(i), a[0] + 2, y + i * 10, theme.textDim(), false);
			}
		}
		g.disableScissor();
	}

	private void renderChat(GuiGraphics g, Theme theme, int mx, int my) {
		int[] p = chatPanel();
		Friends.Status status = Friends.status();
		Friends.Friend friend = selected != null ? Friends.friend(selected) : null;
		if (status != Friends.Status.ONLINE && friend == null) {
			String key = "salami.friends.info." + status.name().toLowerCase(java.util.Locale.ROOT);
			Component text = status == Friends.Status.ERROR && Friends.error() != null
				? Component.translatable(key, Friends.error())
				: Component.translatable(key);
			centeredLines(g, theme, text, p, (p[1] + p[3]) / 2 - 10);
			if (status == Friends.Status.OFF) {
				button(g, theme, enableButton(), I18n.get("salami.friends.enable"), mx, my, true);
			}
			return;
		}
		if (friend == null) {
			centeredLines(g, theme, Component.translatable(Friends.friends().isEmpty() ? "salami.friends.hint_add" : "salami.friends.hint_select"),
				p, (p[1] + p[3]) / 2 - 10);
			return;
		}

		// Kopfzeile: Name, Status, Entfernen
		int[] h = chatHeader();
		g.fill(h[0] + 2, h[1] + 6, h[0] + 6, h[1] + 10, friend.online() ? ONLINE : OFFLINE);
		GuiDraw.text(g, font, friend.name(), h[0] + 10, h[1] + 4, theme.headerText(), false);
		String state = I18n.get(friend.online() ? "salami.friends.online" : "salami.friends.offline");
		GuiDraw.text(g, font, state, h[0] + 14 + font.width(friend.name()), h[1] + 4, friend.online() ? ONLINE : theme.textDim(), false);
		int[] remove = removeButton();
		boolean confirming = isConfirming();
		boolean overRemove = inside(remove, mx, my);
		GuiDraw.roundRect(g, remove[0], remove[1], remove[2] - remove[0], remove[3] - remove[1], confirming || overRemove ? theme.warning() : theme.outline());
		GuiDraw.roundRect(g, remove[0] + 1, remove[1] + 1, remove[2] - remove[0] - 2, remove[3] - remove[1] - 2,
			confirming ? theme.warning() : theme.panel() | 0xFF000000);
		GuiDraw.text(g, font, I18n.get(confirming ? "salami.friends.remove_confirm" : "salami.friends.remove"), remove[0] + 5, remove[1] + 3,
			confirming ? GuiDraw.textOn(theme.warning()) : overRemove ? theme.warning() : theme.textDim(), false);
		g.fill(h[0], h[3] + 1, h[2], h[3] + 2, theme.outline());

		renderMessages(g, theme, friend);

		field(g, theme, chatFieldBox(), chatField, I18n.get("salami.friends.message_placeholder", friend.name()));
		boolean canSend = !chatField.text.isBlank() && !sending && Friends.status() == Friends.Status.ONLINE;
		button(g, theme, sendButton(), I18n.get("salami.friends.send"), mx, my, canSend);
		if (!chatField.text.isEmpty()) {
			String count = chatField.text.length() + "/" + Friends.MAX_MESSAGE;
			int[] box = chatFieldBox();
			if (chatField.text.length() > Friends.MAX_MESSAGE - 40) {
				GuiDraw.text(g, font, count, box[2] - font.width(count) - 3, box[1] - 10, theme.textDim(), false);
			}
		}
	}

	/** Eine Nachricht, umgebrochen; {@code y} wird beim Zeichnen gesetzt. */
	private record Bubble(Friends.Message message, boolean mine, List<FormattedCharSequence> lines, int width, int height) {}

	private List<Bubble> bubbles(int areaWidth) {
		List<Friends.Message> messages = Friends.conversation(selected);
		List<Bubble> result = new ArrayList<>();
		if (messages == null) {
			return result;
		}
		int maxWidth = Math.max(60, areaWidth * 3 / 4);
		UUID me = Friends.me();
		for (Friends.Message message : messages) {
			List<FormattedCharSequence> lines = font.split(Component.literal(message.text()), maxWidth - 8);
			int w = 0;
			for (FormattedCharSequence line : lines) {
				w = Math.max(w, font.width(line));
			}
			result.add(new Bubble(message, message.from().equals(me), lines, w + 8, lines.size() * 10 + 4));
		}
		return result;
	}

	private int messagesHeight(List<Bubble> list) {
		int total = 0;
		for (Bubble bubble : list) {
			total += bubble.height() + BUBBLE_GAP;
		}
		return total;
	}

	private void renderMessages(GuiGraphics g, Theme theme, Friends.Friend friend) {
		int[] m = messagesArea();
		List<Bubble> list = bubbles(m[2] - m[0]);
		if (Friends.conversation(friend.uuid()) == null) {
			GuiDraw.centeredText(g, font, I18n.get("salami.friends.loading"), (m[0] + m[2]) / 2, (m[1] + m[3]) / 2, theme.textDim());
			return;
		}
		if (list.isEmpty()) {
			centeredLines(g, theme, Component.translatable("salami.friends.no_messages", friend.name()), m, (m[1] + m[3]) / 2 - 5);
			return;
		}
		int total = messagesHeight(list);
		int visible = m[3] - m[1];
		chatScroll = Math.max(0, Math.min(chatScroll, Math.max(0, total - visible)));
		// Unten ausrichten: neueste Nachricht am unteren Rand
		int y = m[3] - total + (int) Math.round(chatScroll);
		g.enableScissor(m[0], m[1], m[2], m[3]);
		for (Bubble bubble : list) {
			int top = y;
			y += bubble.height() + BUBBLE_GAP;
			if (top + bubble.height() < m[1] || top > m[3]) {
				continue;
			}
			int x = bubble.mine() ? m[2] - bubble.width() - 2 : m[0] + 2;
			int fill = bubble.mine() ? theme.accent() : theme.header();
			int textColor = bubble.mine() ? GuiDraw.textOn(theme.accent()) : theme.text();
			GuiDraw.roundRect(g, x, top, bubble.width(), bubble.height(), fill);
			for (int i = 0; i < bubble.lines().size(); i++) {
				GuiDraw.text(g, font, bubble.lines().get(i), x + 4, top + 3 + i * 10, textColor, false);
			}
			String time = TIME.format(Instant.ofEpochMilli(bubble.message().at()).atZone(ZoneId.systemDefault()));
			int timeX = bubble.mine() ? x - font.width(time) - 4 : x + bubble.width() + 4;
			GuiDraw.text(g, font, time, timeX, top + bubble.height() - 9, theme.textDim(), false);
		}
		g.disableScissor();
	}

	private void centeredLines(GuiGraphics g, Theme theme, Component text, int[] area, int y) {
		List<FormattedCharSequence> lines = font.split(text, Math.max(40, area[2] - area[0] - 20));
		int cx = (area[0] + area[2]) / 2;
		int top = y - (lines.size() - 1) * 5;
		for (int i = 0; i < lines.size(); i++) {
			GuiDraw.text(g, font, lines.get(i), cx - font.width(lines.get(i)) / 2, top + i * 10, theme.textDim(), false);
		}
	}

	private void field(GuiGraphics g, Theme theme, int[] r, Field field, String placeholder) {
		boolean isFocused = focused == field;
		GuiDraw.roundRect(g, r[0], r[1], r[2] - r[0], r[3] - r[1], isFocused ? theme.accent() : theme.outline());
		GuiDraw.roundRect(g, r[0] + 1, r[1] + 1, r[2] - r[0] - 2, r[3] - r[1] - 2, 0xFF0C0C10);
		int inner = r[2] - r[0] - 8;
		if (field.text.isEmpty()) {
			GuiDraw.text(g, font, font.plainSubstrByWidth(placeholder, inner), r[0] + 4, r[1] + 4, theme.textDim(), false);
		} else {
			// Ende sichtbar halten: von hinten abschneiden
			String shown = font.plainSubstrByWidth(field.text, inner, true);
			GuiDraw.text(g, font, shown, r[0] + 4, r[1] + 4, theme.text(), false);
		}
		if (isFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = r[0] + 4 + Math.min(font.width(field.text), inner);
			g.fill(cx, r[1] + 3, cx + 1, r[3] - 3, theme.text());
		}
	}

	private void button(GuiGraphics g, Theme theme, int[] r, String label, int mx, int my, boolean primary) {
		boolean hovered = inside(r, mx, my);
		int w = r[2] - r[0];
		int h = r[3] - r[1];
		GuiDraw.roundRect(g, r[0], r[1], w, h, primary || hovered ? theme.accent() : theme.outline());
		GuiDraw.roundRect(g, r[0] + 1, r[1] + 1, w - 2, h - 2, primary ? theme.accent() : hovered ? theme.header() : theme.panel() | 0xFF000000);
		if (primary && hovered) {
			GuiDraw.roundRect(g, r[0] + 1, r[1] + 1, w - 2, h - 2, 0x30FFFFFF);
		}
		GuiDraw.text(g, font, label, r[0] + (w - font.width(label)) / 2, r[1] + (h - 8) / 2, primary ? GuiDraw.textOn(theme.accent()) : theme.headerText(), false);
	}

	private void smallButton(GuiGraphics g, Theme theme, int[] r, String label, int color, int mx, int my) {
		boolean hovered = inside(r, mx, my);
		GuiDraw.roundRect(g, r[0], r[1], r[2] - r[0], r[3] - r[1], hovered ? color : theme.outline());
		GuiDraw.roundRect(g, r[0] + 1, r[1] + 1, r[2] - r[0] - 2, r[3] - r[1] - 2, hovered ? color : theme.panel() | 0xFF000000);
		GuiDraw.text(g, font, label, r[0] + (r[2] - r[0] - font.width(label)) / 2 + 1, r[1] + 3, hovered ? GuiDraw.textOn(color) : color, false);
	}

	// ------------------------------------------------------------------ Eingabe

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x() / uiScale;
		double y = event.y() / uiScale;
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		if (inside(backButton(), x, y)) {
			onClose();
			return true;
		}
		if (inside(addFieldBox(), x, y)) {
			focused = addField;
			return true;
		}
		if (inside(addButton(), x, y)) {
			submitAdd();
			return true;
		}
		if (clickList(x, y)) {
			return true;
		}
		Friends.Friend friend = selected != null ? Friends.friend(selected) : null;
		if (friend == null) {
			if (Friends.status() == Friends.Status.OFF && inside(enableButton(), x, y)) {
				FriendsModule module = FriendsModule.instance();
				if (module != null) {
					SalamiClient.get().modules().setEnabled(module, true);
				}
			}
			return true;
		}
		if (inside(removeButton(), x, y)) {
			if (isConfirming()) {
				confirmRemove = null;
				Friends.remove(friend.uuid());
			} else {
				confirmRemove = friend.uuid();
				confirmUntil = System.currentTimeMillis() + 4_000L;
			}
			return true;
		}
		if (inside(chatFieldBox(), x, y)) {
			focused = chatField;
			return true;
		}
		if (inside(sendButton(), x, y)) {
			submitMessage();
			return true;
		}
		return true;
	}

	private boolean clickList(double x, double y) {
		int[] a = listArea();
		if (!inside(a, x, y)) {
			return false;
		}
		int[][] buttons = requestButtons();
		for (Entry entry : entries()) {
			if (y < entry.y() || y >= entry.y() + entry.height()) {
				continue;
			}
			if (entry.request() != null) {
				Friends.Request request = entry.request();
				if (request.incoming() && x >= buttons[0][0] && x < buttons[0][1]) {
					Friends.accept(request.uuid());
				} else if (x >= buttons[1][0] && x < buttons[1][1]) {
					Friends.deny(request.uuid());
				}
			} else if (entry.friend() != null) {
				select(entry.friend().uuid());
			}
			return true;
		}
		return true;
	}

	private void select(UUID friend) {
		if (!friend.equals(selected)) {
			selected = friend;
			chatScroll = 0;
			chatField.text = "";
			confirmRemove = null;
		}
		Friends.view(friend);
		focused = chatField;
	}

	private void submitAdd() {
		String name = addField.text.trim();
		if (!name.isEmpty()) {
			Friends.addFriend(name);
			addField.text = "";
		}
	}

	private void submitMessage() {
		Friends.Friend friend = selected != null ? Friends.friend(selected) : null;
		if (friend == null || sending || chatField.text.isBlank()) {
			return;
		}
		String text = chatField.text;
		sending = true;
		Friends.send(friend.uuid(), text, sent -> {
			sending = false;
			if (sent && chatField.text.equals(text)) {
				chatField.text = "";
				chatScroll = 0;
			}
		});
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		double x = mouseX / uiScale;
		double y = mouseY / uiScale;
		if (inside(listArea(), x, y)) {
			int[] a = listArea();
			listScroll = Math.max(0, Math.min(listScroll - scrollY * ROW * 2, Math.max(0, listHeight() - (a[3] - a[1]))));
		} else {
			chatScroll += scrollY * 20; // in render() begrenzt
			chatScroll = Math.max(0, chatScroll);
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE) {
			return super.keyPressed(event);
		}
		if (event.key() == InputConstants.KEY_TAB) {
			focused = focused == addField && selected != null ? chatField : addField;
			return true;
		}
		if (focused == null) {
			return super.keyPressed(event);
		}
		switch (event.key()) {
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
				if (focused == addField) {
					submitAdd();
				} else {
					submitMessage();
				}
			}
			case InputConstants.KEY_BACKSPACE -> {
				if (!focused.text.isEmpty()) {
					if (event.hasControlDown()) {
						// Strg + Rück: letztes Wort
						String trimmed = focused.text.stripTrailing();
						int space = trimmed.lastIndexOf(' ');
						focused.text = space < 0 ? "" : trimmed.substring(0, space + 1);
					} else {
						focused.text = focused.text.substring(0, focused.text.offsetByCodePoints(focused.text.length(), -1));
					}
				}
			}
			case InputConstants.KEY_V -> {
				if (event.hasControlDown()) {
					paste(minecraft.keyboardHandler.getClipboard());
				}
			}
			default -> {
				// Zeichen kommen über charTyped
			}
		}
		return true;
	}

	private void paste(String clip) {
		StringBuilder clean = new StringBuilder();
		clip.codePoints()
			.filter(c -> c >= 32 && c != 127 && c != 0xA7)
			.forEach(clean::appendCodePoint);
		append(clean.toString());
	}

	private void append(String text) {
		if (focused == null) {
			return;
		}
		String next = focused.text + text;
		if (focused == addField) {
			// Minecraft-Namen: nur Buchstaben, Ziffern, _
			next = next.replaceAll("[^A-Za-z0-9_]", "");
		}
		if (next.length() > focused.max) {
			next = next.substring(0, focused.max);
		}
		focused.text = next;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (focused != null) {
			if (event.isAllowedChatCharacter()) {
				append(event.codepointAsString());
			}
			return true;
		}
		return super.charTyped(event);
	}
}
