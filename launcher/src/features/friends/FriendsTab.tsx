import { useEffect, useMemo, useRef, useState } from "react";
import { convertFileSrc } from "@tauri-apps/api/core";
import { open } from "@tauri-apps/plugin-dialog";
import { Icon } from "@iconify/react";
import { toast } from "react-hot-toast";
import { useTranslation } from "react-i18next";
import { cn } from "../../lib/utils";
import { usePlayerAvatar } from "../../hooks/usePlayerAvatar";
import { useMinecraftAuthStore } from "../../store/minecraft-auth-store";
import { useThemeStore } from "../../store/useThemeStore";
import { hexAlpha, readableOn, SURFACE } from "../../components/ui/design-system";
import {
  CLIP_EXTENSIONS,
  FriendsService,
  IMAGE_EXTENSIONS,
  errorText,
  type Friend,
  type FriendMedia,
  type FriendMessage,
  type FriendRequest,
} from "./friends-service";
import { useFriendsStore } from "./friends-store";
import "./i18n";

const STATUS_COLOR = { game: "#4ade80", launcher: "#fbbf24", offline: "rgba(255,255,255,0.25)" } as const;
const MAX_TEXT = 256;

const statusKey = (friend: Friend) => (friend.online ? (friend.status === "launcher" ? "launcher" : "game") : "offline");

/** Freunde-Reiter: links Liste und Anfragen, rechts das Gespräch mit dem gewählten Freund. */
export function FriendsTab() {
  const { t } = useTranslation();
  const ready = useFriendsStore((s) => s.ready);
  const available = useFriendsStore((s) => s.available);
  const enabled = useFriendsStore((s) => s.enabled);
  const error = useFriendsStore((s) => s.error);
  const init = useFriendsStore((s) => s.init);
  const setTabOpen = useFriendsStore((s) => s.setTabOpen);
  const account = useMinecraftAuthStore((s) => s.activeAccount);

  useEffect(() => {
    setTabOpen(true);
    return () => setTabOpen(false);
  }, [setTabOpen]);

  // Account gewechselt oder gerade angemeldet: Zustand neu holen und neu verbinden.
  const accountId = account?.id ?? null;
  const firstAccount = useRef(true);
  useEffect(() => {
    if (firstAccount.current) {
      firstAccount.current = false;
      void init();
      return;
    }
    void FriendsService.reconnect()
      .catch(() => undefined)
      .then(() => init());
  }, [accountId, init]);

  if (!ready) return null;
  if (!available) return <Notice icon="solar:users-group-rounded-bold" text={t("salami.friends.unavailable")} />;
  if (!enabled) return <Intro signedIn={!!account} />;
  if (!account) return <Notice icon="solar:user-cross-bold" text={t("salami.friends.sign_in")} />;

  return (
    <div className="flex h-full min-h-0">
      <aside className="flex w-[300px] shrink-0 flex-col border-r" style={{ borderColor: SURFACE.line }}>
        <ListHeader />
        <AddFriend />
        {error && <p className="px-4 pb-2 font-minecraft text-xs text-red-300">{error}</p>}
        <div className="custom-scrollbar min-h-0 flex-1 overflow-y-auto">
          <Requests />
          <FriendList />
        </div>
      </aside>
      <Conversation />
    </div>
  );
}

function Notice({ icon, text }: { icon: string; text: string }) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-3 px-6 text-center">
      <Icon icon={icon} className="h-10 w-10 text-white/20" />
      <p className="max-w-md font-minecraft text-sm text-white/60">{text}</p>
    </div>
  );
}

/** Erster Besuch: erklären, was Freunde sehen und was gespeichert wird – erst dann einschalten. */
function Intro({ signedIn }: { signedIn: boolean }) {
  const { t } = useTranslation();
  const accent = useThemeStore((s) => s.accentColor.value);
  const setEnabled = useFriendsStore((s) => s.setEnabled);
  const [busy, setBusy] = useState(false);

  const enable = async () => {
    setBusy(true);
    try {
      await setEnabled(true);
    } catch (error) {
      toast.error(errorText(error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex h-full items-center justify-center px-8">
      <div className="max-w-xl border-l-2 pl-6" style={{ borderColor: accent }}>
        <h1 className="font-minecraft text-5xl lowercase leading-none text-white">{t("salami.friends.intro.title")}</h1>
        <p className="mt-4 font-minecraft text-sm leading-relaxed text-white/70">{t("salami.friends.intro.text")}</p>
        <ul className="mt-5 space-y-2.5">
          {["privacy_online", "privacy_storage", "privacy_off"].map((key) => (
            <li key={key} className="flex items-start gap-2.5 font-minecraft text-xs leading-relaxed text-white/55">
              <Icon icon="solar:shield-check-bold" className="mt-0.5 h-4 w-4 shrink-0" style={{ color: accent }} />
              {t(`salami.friends.intro.${key}`)}
            </li>
          ))}
        </ul>
        {signedIn ? (
          <button
            type="button"
            onClick={enable}
            disabled={busy}
            className="mt-7 flex h-11 items-center gap-2 px-5 font-minecraft text-lg lowercase transition-[filter] duration-150 hover:brightness-110 disabled:opacity-50"
            style={{ backgroundColor: accent, color: readableOn(accent) }}
          >
            <Icon icon={busy ? "svg-spinners:ring-resize" : "solar:users-group-rounded-bold"} className="h-5 w-5" />
            {t("salami.friends.enable")}
          </button>
        ) : (
          <p className="mt-7 font-minecraft text-sm text-amber-200/90">{t("salami.friends.sign_in")}</p>
        )}
      </div>
    </div>
  );
}

function ListHeader() {
  const { t } = useTranslation();
  const accent = useThemeStore((s) => s.accentColor.value);
  const connected = useFriendsStore((s) => s.connected);
  const setEnabled = useFriendsStore((s) => s.setEnabled);

  const disable = async () => {
    if (!window.confirm(t("salami.friends.disable_confirm"))) return;
    try {
      await setEnabled(false);
    } catch (error) {
      toast.error(errorText(error));
    }
  };

  return (
    <div className="flex items-end justify-between border-b px-4 pt-4" style={{ borderColor: SURFACE.line }}>
      <span className="relative pb-2.5 font-minecraft text-2xl lowercase leading-none text-white">
        {t("salami.friends.title")}
        <span className="absolute inset-x-0 -bottom-px h-[3px]" style={{ backgroundColor: accent }} />
      </span>
      <div className="mb-2 flex items-center gap-3">
        <span className="flex items-center gap-1.5 font-minecraft text-xs lowercase text-white/45">
          <span
            className={cn("h-2 w-2", !connected && "animate-pulse")}
            style={{ borderRadius: 999, backgroundColor: connected ? STATUS_COLOR.game : STATUS_COLOR.launcher }}
          />
          {t(connected ? "salami.friends.connected" : "salami.friends.connecting")}
        </span>
        <button
          type="button"
          onClick={disable}
          title={t("salami.friends.disable")}
          className="flex h-7 w-7 items-center justify-center text-white/35 transition-colors hover:bg-white/[0.06] hover:text-white"
        >
          <Icon icon="solar:power-bold" className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}

function AddFriend() {
  const { t } = useTranslation();
  const accent = useThemeStore((s) => s.accentColor.value);
  const request = useFriendsStore((s) => s.request);
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const valid = /^[A-Za-z0-9_]{1,16}$/.test(name.trim());

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!valid || busy) return;
    setBusy(true);
    try {
      const wanted = name.trim();
      const status = await request(wanted);
      toast.success(t(status === "friends" ? "salami.friends.add.friends" : "salami.friends.add.requested", { name: wanted }));
      setName("");
    } catch (error) {
      toast.error(errorText(error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="flex items-center gap-2 px-4 py-3">
      <label
        className="flex h-9 min-w-0 flex-1 items-center gap-2 border border-white/[0.08] bg-black/25 px-3 transition-colors focus-within:border-white/25"
        style={{ borderRadius: "var(--border-radius)" }}
      >
        <Icon icon="solar:user-plus-bold" className="h-4 w-4 shrink-0 text-white/40" />
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={16}
          spellCheck={false}
          placeholder={t("salami.friends.add.placeholder")}
          className="min-w-0 flex-1 bg-transparent font-minecraft text-sm text-white outline-none placeholder:text-white/30"
        />
      </label>
      <button
        type="submit"
        disabled={!valid || busy}
        className="flex h-9 shrink-0 items-center px-3 font-minecraft text-sm lowercase transition-[filter,opacity] duration-150 hover:brightness-110 disabled:opacity-40"
        style={{ backgroundColor: accent, color: readableOn(accent) }}
      >
        {busy ? <Icon icon="svg-spinners:ring-resize" className="h-4 w-4" /> : t("salami.friends.add.button")}
      </button>
    </form>
  );
}

function Avatar({ uuid, size = 36 }: { uuid: string; size?: number }) {
  const url = usePlayerAvatar({ uuid, size: 64, overlay: true });
  return (
    <span
      className="flex shrink-0 items-center justify-center overflow-hidden"
      style={{ width: size, height: size, backgroundColor: SURFACE.raised, borderRadius: "calc(var(--border-radius) * 0.8)" }}
    >
      {url ? (
        <img src={url} alt="" className="h-full w-full" style={{ imageRendering: "pixelated" }} draggable={false} />
      ) : (
        <Icon icon="solar:user-bold" className="h-1/2 w-1/2 text-white/30" />
      )}
    </span>
  );
}

function SectionLabel({ children }: { children: React.ReactNode }) {
  return <p className="px-4 pb-1 pt-3 font-minecraft text-[11px] lowercase text-white/35">{children}</p>;
}

function Requests() {
  const { t } = useTranslation();
  const incoming = useFriendsStore((s) => s.incoming);
  const outgoing = useFriendsStore((s) => s.outgoing);
  const respond = useFriendsStore((s) => s.respond);
  const [busy, setBusy] = useState<string | null>(null);

  const act = async (action: "accept" | "deny", uuid: string) => {
    setBusy(uuid);
    try {
      await respond(action, uuid);
    } catch (error) {
      toast.error(errorText(error));
    } finally {
      setBusy(null);
    }
  };

  const row = (request: FriendRequest, mine: boolean) => (
    <div key={request.uuid} className="flex items-center gap-3 border-b px-4 py-2.5" style={{ borderColor: SURFACE.line }}>
      <Avatar uuid={request.uuid} size={30} />
      <span className="min-w-0 flex-1 truncate font-minecraft text-sm text-white">{request.name}</span>
      {!mine && (
        <RowButton
          icon="solar:check-circle-bold"
          title={t("salami.friends.requests.accept")}
          onClick={() => act("accept", request.uuid)}
          disabled={busy === request.uuid}
          color={STATUS_COLOR.game}
        />
      )}
      <RowButton
        icon="solar:close-circle-bold"
        title={t(mine ? "salami.friends.requests.cancel" : "salami.friends.requests.deny")}
        onClick={() => act("deny", request.uuid)}
        disabled={busy === request.uuid}
      />
    </div>
  );

  if (incoming.length === 0 && outgoing.length === 0) return null;
  return (
    <>
      {incoming.length > 0 && (
        <>
          <SectionLabel>{t("salami.friends.requests.incoming")}</SectionLabel>
          {incoming.map((r) => row(r, false))}
        </>
      )}
      {outgoing.length > 0 && (
        <>
          <SectionLabel>{t("salami.friends.requests.outgoing")}</SectionLabel>
          {outgoing.map((r) => row(r, true))}
        </>
      )}
    </>
  );
}

function RowButton({
  icon,
  title,
  onClick,
  disabled,
  color,
}: {
  icon: string;
  title: string;
  onClick: () => void;
  disabled?: boolean;
  color?: string;
}) {
  return (
    <button
      type="button"
      title={title}
      aria-label={title}
      onClick={onClick}
      disabled={disabled}
      className="flex h-8 w-8 shrink-0 items-center justify-center text-white/45 transition-colors hover:bg-white/[0.06] hover:text-white disabled:opacity-40"
      style={color ? { color } : undefined}
    >
      <Icon icon={icon} className="h-5 w-5" />
    </button>
  );
}

function FriendList() {
  const { t } = useTranslation();
  const accent = useThemeStore((s) => s.accentColor.value);
  const friends = useFriendsStore((s) => s.friends);
  const unread = useFriendsStore((s) => s.unread);
  const activeUuid = useFriendsStore((s) => s.activeUuid);
  const select = useFriendsStore((s) => s.select);
  const loading = useFriendsStore((s) => s.loading);

  if (friends.length === 0) {
    return loading ? null : <p className="px-4 py-6 font-minecraft text-xs leading-relaxed text-white/40">{t("salami.friends.list.empty")}</p>;
  }
  return (
    <>
      {friends.map((friend) => {
        const status = statusKey(friend);
        const active = friend.uuid === activeUuid;
        const count = unread[friend.uuid] ?? 0;
        return (
          <button
            key={friend.uuid}
            type="button"
            onClick={() => void select(friend.uuid).catch((error) => toast.error(errorText(error)))}
            className="flex w-full items-center gap-3 border-b px-4 py-2.5 text-left transition-colors duration-150 hover:bg-white/[0.04]"
            style={{
              borderRadius: 0,
              borderColor: SURFACE.line,
              backgroundColor: active ? hexAlpha(accent, 0.1) : undefined,
              boxShadow: active ? `inset 3px 0 0 ${accent}` : undefined,
            }}
          >
            <span className={cn("relative", !friend.online && "opacity-50")}>
              <Avatar uuid={friend.uuid} />
              <span
                className="absolute -bottom-0.5 -right-0.5 h-3 w-3 border-2"
                style={{ borderRadius: 999, borderColor: "#151213", backgroundColor: STATUS_COLOR[status] }}
              />
            </span>
            <span className="min-w-0 flex-1">
              <span className={cn("block truncate font-minecraft text-base leading-tight", friend.online ? "text-white" : "text-white/55")}>
                {friend.name}
              </span>
              <span className="block font-minecraft text-[11px] lowercase" style={{ color: friend.online ? STATUS_COLOR[status] : "rgba(255,255,255,0.35)" }}>
                {t(`salami.friends.status.${status}`)}
              </span>
            </span>
            {count > 0 && (
              <span
                className="flex h-5 min-w-5 items-center justify-center px-1.5 font-minecraft text-xs leading-none"
                style={{ borderRadius: 999, backgroundColor: accent, color: readableOn(accent) }}
              >
                {count}
              </span>
            )}
          </button>
        );
      })}
    </>
  );
}

function Conversation() {
  const { t, i18n } = useTranslation();
  const accent = useThemeStore((s) => s.accentColor.value);
  const me = useFriendsStore((s) => s.me);
  const activeUuid = useFriendsStore((s) => s.activeUuid);
  const friend = useFriendsStore((s) => s.friends.find((f) => f.uuid === s.activeUuid) ?? null);
  const messages = useFriendsStore((s) => (s.activeUuid ? s.conversations[s.activeUuid] : undefined));
  const sendText = useFriendsStore((s) => s.sendText);
  const sendFile = useFriendsStore((s) => s.sendFile);
  const respond = useFriendsStore((s) => s.respond);
  const [text, setText] = useState("");
  const [sending, setSending] = useState(false);
  const [uploading, setUploading] = useState(0);
  const endRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end" });
  }, [messages?.length, activeUuid]);
  useEffect(() => {
    setText("");
    inputRef.current?.focus();
  }, [activeUuid]);

  const days = useMemo(() => groupByDay(messages ?? [], i18n.language, t), [messages, i18n.language, t]);

  if (!friend || !me) {
    return (
      <div className="flex min-w-0 flex-1 flex-col items-center justify-center gap-2 px-6 text-center">
        <Icon icon="solar:chat-round-dots-bold" className="mb-1 h-10 w-10 text-white/15" />
        <p className="font-minecraft text-2xl lowercase leading-none text-white/70">{t("salami.friends.chat.pick")}</p>
        <p className="font-minecraft text-xs text-white/40">{t("salami.friends.chat.pick_hint")}</p>
      </div>
    );
  }

  const status = statusKey(friend);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!text.trim() || sending) return;
    setSending(true);
    try {
      await sendText(text);
      setText("");
    } catch (error) {
      toast.error(errorText(error));
    } finally {
      setSending(false);
      inputRef.current?.focus();
    }
  };

  const attach = async () => {
    const picked = await open({
      multiple: true,
      filters: [{ name: t("salami.friends.chat.attach_filter"), extensions: [...IMAGE_EXTENSIONS, ...CLIP_EXTENSIONS] }],
    });
    const paths = Array.isArray(picked) ? picked : picked ? [picked] : [];
    for (const path of paths.slice(0, 5)) {
      setUploading((n) => n + 1);
      try {
        await sendFile(path);
      } catch (error) {
        toast.error(errorText(error));
      } finally {
        setUploading((n) => n - 1);
      }
    }
  };

  const remove = async () => {
    if (!window.confirm(t("salami.friends.chat.remove_confirm", { name: friend.name }))) return;
    try {
      await respond("remove", friend.uuid);
    } catch (error) {
      toast.error(errorText(error));
    }
  };

  return (
    <section className="flex min-w-0 flex-1 flex-col">
      <header className="flex items-center gap-3 border-b px-5 py-3" style={{ borderColor: SURFACE.line }}>
        <Avatar uuid={friend.uuid} size={40} />
        <div className="min-w-0 flex-1">
          <p className="truncate font-minecraft text-2xl leading-none text-white">{friend.name}</p>
          <p className="mt-1 flex items-center gap-1.5 font-minecraft text-xs lowercase" style={{ color: STATUS_COLOR[status] }}>
            <span className="h-2 w-2" style={{ borderRadius: 999, backgroundColor: STATUS_COLOR[status] }} />
            {t(`salami.friends.status.${status}`)}
          </p>
        </div>
        <RowButton icon="solar:user-minus-bold" title={t("salami.friends.chat.remove")} onClick={remove} />
      </header>

      <div className="custom-scrollbar min-h-0 flex-1 overflow-y-auto px-5 py-4">
        {messages && messages.length === 0 && (
          <p className="py-10 text-center font-minecraft text-sm text-white/40">{t("salami.friends.chat.empty")}</p>
        )}
        {days.map((day) => (
          <div key={day.label}>
            <p className="my-3 flex items-center gap-3 font-minecraft text-[11px] lowercase text-white/30">
              <span className="h-px flex-1" style={{ backgroundColor: SURFACE.line }} />
              {day.label}
              <span className="h-px flex-1" style={{ backgroundColor: SURFACE.line }} />
            </p>
            {day.messages.map((message) => (
              <Bubble key={message.id} message={message} mine={message.from === me.uuid} accent={accent} language={i18n.language} />
            ))}
          </div>
        ))}
        <div ref={endRef} />
      </div>

      <form onSubmit={submit} className="flex items-center gap-2 border-t px-5 py-3" style={{ borderColor: SURFACE.line }}>
        <button
          type="button"
          onClick={() => void attach()}
          title={`${t("salami.friends.chat.attach")} – ${t("salami.friends.chat.limits")}`}
          aria-label={t("salami.friends.chat.attach")}
          disabled={uploading > 0}
          className="flex h-10 w-10 shrink-0 items-center justify-center text-white/55 transition-colors hover:bg-white/[0.06] hover:text-white disabled:opacity-50"
        >
          <Icon icon={uploading > 0 ? "svg-spinners:ring-resize" : "solar:gallery-add-bold"} className="h-5 w-5" />
        </button>
        <label
          className="flex h-10 min-w-0 flex-1 items-center gap-2 border border-white/[0.08] bg-black/25 px-3 transition-colors focus-within:border-white/25"
          style={{ borderRadius: "var(--border-radius)" }}
        >
          <input
            ref={inputRef}
            value={text}
            onChange={(e) => setText(e.target.value)}
            maxLength={MAX_TEXT}
            placeholder={uploading > 0 ? t("salami.friends.chat.uploading") : t("salami.friends.chat.placeholder", { name: friend.name })}
            className="min-w-0 flex-1 bg-transparent font-minecraft text-sm text-white outline-none placeholder:text-white/30"
          />
          {text.length > MAX_TEXT - 40 && (
            <span className="shrink-0 font-minecraft text-[11px] tabular-nums text-white/40">{MAX_TEXT - text.length}</span>
          )}
        </label>
        <button
          type="submit"
          disabled={!text.trim() || sending}
          className="flex h-10 shrink-0 items-center gap-2 px-4 font-minecraft text-sm lowercase transition-[filter,opacity] duration-150 hover:brightness-110 disabled:opacity-40"
          style={{ backgroundColor: accent, color: readableOn(accent) }}
        >
          <Icon icon="solar:plain-bold" className="h-4 w-4" />
          {t("salami.friends.chat.send")}
        </button>
      </form>
    </section>
  );
}

function groupByDay(messages: FriendMessage[], language: string, t: (key: string) => string) {
  const today = new Date().toDateString();
  const yesterday = new Date(Date.now() - 86_400_000).toDateString();
  const format = new Intl.DateTimeFormat(language || undefined, { day: "numeric", month: "long", year: "numeric" });
  const days: { label: string; messages: FriendMessage[] }[] = [];
  for (const message of messages) {
    const date = new Date(message.at);
    const key = date.toDateString();
    const label = key === today ? t("salami.friends.today") : key === yesterday ? t("salami.friends.yesterday") : format.format(date);
    const last = days[days.length - 1];
    if (last?.label === label) last.messages.push(message);
    else days.push({ label, messages: [message] });
  }
  return days;
}

function Bubble({ message, mine, accent, language }: { message: FriendMessage; mine: boolean; accent: string; language: string }) {
  const time = new Intl.DateTimeFormat(language || undefined, { hour: "2-digit", minute: "2-digit" }).format(new Date(message.at));
  return (
    <div className={cn("mb-1.5 flex", mine ? "justify-end" : "justify-start")}>
      <div
        className="max-w-[70%] px-3 py-2"
        style={{
          borderRadius: "var(--border-radius)",
          backgroundColor: mine ? hexAlpha(accent, 0.22) : SURFACE.hover,
          boxShadow: mine ? `inset -3px 0 0 ${accent}` : undefined,
        }}
      >
        {message.media ? (
          <Media media={message.media} />
        ) : (
          <p className="whitespace-pre-wrap break-words font-minecraft text-sm leading-snug text-white/90">{message.text}</p>
        )}
        <p className={cn("mt-1 font-minecraft text-[10px] tabular-nums text-white/35", mine && "text-right")}>{time}</p>
      </div>
    </div>
  );
}

/** Bild oder Clip einer Nachricht: wird beim ersten Anzeigen in den Launcher-Ordner geladen. */
function Media({ media }: { media: FriendMedia }) {
  const { t } = useTranslation();
  const [src, setSrc] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  const [large, setLarge] = useState(false);

  useEffect(() => {
    if (media.expired) return;
    let cancelled = false;
    FriendsService.media(media.id, media.mime)
      .then((path) => {
        if (!cancelled) setSrc(convertFileSrc(path));
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });
    return () => {
      cancelled = true;
    };
  }, [media.id, media.mime, media.expired]);

  if (media.expired || failed) {
    return (
      <p className="flex items-center gap-2 font-minecraft text-xs text-white/45">
        <Icon icon={media.kind === "clip" ? "solar:videocamera-record-bold" : "solar:gallery-bold"} className="h-4 w-4" />
        {media.name} – {t(media.expired ? "salami.friends.media.expired" : "salami.friends.media.failed")}
      </p>
    );
  }
  if (!src) {
    return (
      <p className="flex h-24 w-56 items-center justify-center gap-2 font-minecraft text-xs text-white/45">
        <Icon icon="svg-spinners:ring-resize" className="h-4 w-4" />
        {t("salami.friends.media.loading")}
      </p>
    );
  }
  return (
    <div>
      {media.kind === "clip" ? (
        <video src={src} controls preload="metadata" className="max-h-[320px] max-w-full bg-black" style={{ borderRadius: "calc(var(--border-radius) * 0.8)" }} />
      ) : (
        <img
          src={src}
          alt={media.name}
          onClick={() => setLarge((v) => !v)}
          className={cn("max-w-full cursor-zoom-in object-contain", large ? "max-h-[70vh] cursor-zoom-out" : "max-h-[260px]")}
          style={{ borderRadius: "calc(var(--border-radius) * 0.8)" }}
          draggable={false}
        />
      )}
      <p className="mt-1 truncate font-minecraft text-[11px] text-white/45">{media.name}</p>
    </div>
  );
}
