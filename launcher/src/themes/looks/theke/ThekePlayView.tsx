import { type CSSProperties, type ReactNode, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { MainLaunchButton } from "../../../components/launcher/MainLaunchButton";
import { PlayerRig } from "../../../components/launcher/PlayerRig";
import { readableOn } from "../../../components/ui/design-system";
import { useFriendsStore } from "../../../features/friends/friends-store";
import { useResolvedLoaderVersion } from "../../../hooks/useResolvedLoaderVersion";
import { cn } from "../../../lib/utils";
import { listClips } from "../../../services/clip-service";
import { useClipsStore } from "../../../store/clips-store";
import { useProfileWizardStore } from "../../../store/profile-wizard-store";
import { useThemeStore } from "../../../store/useThemeStore";
import type { Profile } from "../../../types/profile";
import { formatRelativeTime } from "../../../utils/format-relative-time";
import { SalamiSlice } from "../../salami/SalamiSlice";
import { DeliLabel } from "../../salami/SalamiStage";
import type { LookPlayViewProps } from "../types";

const RECENT_COUNT = 4;
const OFFER_COUNT = 3;
const BUBBLE_COUNT = 3;
const LOADER_NAMES: Record<string, string> = {
  vanilla: "Vanilla",
  fabric: "Fabric",
  quilt: "Quilt",
  forge: "Forge",
  neoforge: "NeoForge",
};

const loaderName = (profile: Profile) => LOADER_NAMES[profile.loader] ?? profile.loader;

/** Schriftgröße des Profilnamens nach Länge – lange Namen dürfen umbrechen, aber nicht das Papier sprengen. */
function titleSize(name: string): "s" | "m" | "l" | "xl" {
  if (name.length <= 10) return "s";
  if (name.length <= 16) return "m";
  if (name.length <= 24) return "l";
  return "xl";
}

/** „<b>2 Freunde</b> sind online“ → Text mit fett gesetztem Teil (ohne HTML einzusetzen). */
function withBold(text: string): ReactNode[] {
  return text.split(/<b>(.*?)<\/b>/).map((part, index) => (index % 2 === 1 ? <b key={index}>{part}</b> : part));
}

interface Offer {
  key: string;
  text: string;
  to: string;
}

/** Name des neuesten Clips – nur wenn Clips eingeschaltet sind und es einen gibt. */
function useLatestClipName(): string | null {
  const { t, i18n } = useTranslation();
  const enabled = useClipsStore((state) => state.enabled);
  const [clip, setClip] = useState<{ name: string; createdAt: number } | null>(null);

  useEffect(() => {
    if (!enabled) {
      setClip(null);
      return;
    }
    let cancelled = false;
    listClips()
      .then((clips) => {
        if (cancelled) return;
        const latest = clips.reduce<(typeof clips)[number] | null>(
          (best, entry) => (!best || entry.createdAt > best.createdAt ? entry : best),
          null,
        );
        setClip(latest ? { name: latest.name, createdAt: latest.createdAt } : null);
      })
      .catch(() => {
        if (!cancelled) setClip(null);
      });
    return () => {
      cancelled = true;
    };
  }, [enabled]);

  if (!clip) return null;
  // automatisch benannte Clips heißen „<Unix-Zeit>_clip“ – wie in der Clip-Galerie als Datum zeigen
  const auto = /^(\d{9,11})_clip$/.exec(clip.name);
  if (!auto) return clip.name;
  const when = new Date((Number(auto[1]) || clip.createdAt) * 1000).toLocaleString(i18n.language, {
    weekday: "short",
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
  return t("clips.gallery.auto_title", { when });
}

/** „Heute im Angebot“: nur, was es wirklich gibt (Freunde, Clips, Profile) – nichts Erfundenes. */
function useOffers(profileCount: number): Offer[] {
  const { t } = useTranslation();
  const friendsActive = useFriendsStore((state) => state.ready && state.enabled && state.signedIn);
  const online = useFriendsStore((state) => state.friends.filter((friend) => friend.online).length);
  const requests = useFriendsStore((state) => state.incoming.length);
  const unread = useFriendsStore((state) => Object.values(state.unread).reduce((sum, count) => sum + count, 0));
  const clipName = useLatestClipName();

  const offers: Offer[] = [];
  if (friendsActive && unread > 0) {
    offers.push({ key: "unread", text: t("theke.play.offer.unread", { count: unread }), to: "/friends" });
  }
  if (friendsActive && requests > 0) {
    offers.push({ key: "requests", text: t("theke.play.offer.requests", { count: requests }), to: "/friends" });
  }
  if (friendsActive && online > 0) {
    offers.push({ key: "friends", text: t("theke.play.offer.friends", { count: online }), to: "/friends" });
  }
  if (clipName) {
    offers.push({ key: "clip", text: t("theke.play.offer.clip", { name: clipName }), to: "/clips" });
  }
  if (profileCount > 1) {
    offers.push({ key: "profiles", text: t("theke.play.offer.profiles", { count: profileCount }), to: "/profiles" });
  }
  return offers.slice(0, OFFER_COUNT);
}

/**
 * Startseite der Feinkost-Theke: das gewählte Profil auf einem angeklebten Bogen Butterbrotpapier
 * (Begrüßung, Name, Preisschilder, Stempel als Start-Knopf, „Heute im Angebot“), daneben die
 * Spielfigur auf dem Schneidebrett und unten die Theke mit den zuletzt gespielten Profilen als
 * Papier-Etiketten. Gestartet wird über `MainLaunchButton` – der Stempel ist nur dessen Aussehen.
 */
export function ThekePlayView({ profiles, selectedProfile, versions, onVersionChange, playerName }: LookPlayViewProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const openProfileWizard = useProfileWizardStore((state) => state.openModal);
  const accent = useThemeStore((state) => state.accentColor.value);
  const resolvedLoader = useResolvedLoaderVersion(selectedProfile);
  const offers = useOffers(profiles.length);

  const recent = useMemo(
    () =>
      [...profiles]
        .sort((a, b) => (b.last_played ?? "").localeCompare(a.last_played ?? ""))
        .slice(0, RECENT_COUNT),
    [profiles],
  );

  const title = selectedProfile?.name ?? t("salami.play.no_profile");
  const modCount = selectedProfile ? selectedProfile.mod_count ?? selectedProfile.mods?.length ?? 0 : 0;
  // Wie auf der Profilseite: die aufgelöste Version zählt, die gespeicherte ist nur Ersatz, solange
  // die Antwort fehlt. Gespeicherte Fabric-/Quilt-Versionen enden auf „ (stable)“ – das gehört
  // nicht aufs Preisschild.
  const loaderVersion =
    selectedProfile && selectedProfile.loader !== "vanilla"
      ? (resolvedLoader?.version ?? selectedProfile.loader_version)?.replace(/\s*\(.*\)$/, "") || null
      : null;
  const bubble = playerName
    ? t(`theke.play.bubble_${(new Date().getDate() % BUBBLE_COUNT) + 1}`)
    : t("theke.play.bubble_anon");

  return (
    <div className="theke-play" style={{ "--theke-on-accent": readableOn(accent) } as CSSProperties}>
      {/* Theke mit Glasaufsatz – reine Kulisse */}
      <div className="theke-counter" aria-hidden="true">
        <div className="theke-glass" />
      </div>

      {/* Spielfigur auf dem Schneidebrett */}
      <div className="theke-stage">
        <div className="theke-board" aria-hidden="true">
          <span className="theke-board-handle" />
        </div>
        <div className="theke-slices" aria-hidden="true">
          <SalamiSlice size={52} />
          <SalamiSlice size={52} />
          <SalamiSlice size={52} />
        </div>
        <div className="theke-rig">
          <PlayerRig playerName={playerName} showNametag={false} />
        </div>
        <p className="theke-bubble">{bubble}</p>
      </div>

      {/* Bogen Butterbrotpapier mit dem gewählten Profil */}
      <section className="theke-paper">
        <span className="theke-tape" aria-hidden="true" />
        <p className="theke-hello">
          {playerName ? t("theke.play.greeting", { name: playerName }) : t("theke.play.greeting_anon")}
        </p>
        <h1 className="theke-title" data-size={titleSize(title)} title={title}>
          {title}
        </h1>

        {selectedProfile && (
          <div className="theke-tags">
            <DeliLabel>Minecraft {selectedProfile.game_version}</DeliLabel>
            <DeliLabel tone="red">
              {loaderVersion ? `${loaderName(selectedProfile)} ${loaderVersion}` : loaderName(selectedProfile)}
            </DeliLabel>
            {modCount > 0 && <DeliLabel>{t("theke.play.mods", { count: modCount })}</DeliLabel>}
          </div>
        )}

        <div className="theke-order">
          {versions.length > 0 ? (
            <div className="theke-stamp">
              <MainLaunchButton
                defaultVersion={selectedProfile?.id ?? versions[0]?.id}
                onVersionChange={onVersionChange}
                versions={versions}
                selectedVersionLabel={t("theke.play.stamp_sub")}
                maxWidth="none"
              />
            </div>
          ) : (
            // ohne Profil kein Start-Knopf – der Stempel führt dann zum ersten Profil
            <button type="button" className="theke-stamp-create" onClick={() => openProfileWizard(null)}>
              <span>{t("salami.play.create_profile")}</span>
            </button>
          )}

          {versions.length === 0 ? (
            <p className="theke-offers">{t("salami.play.no_profile_hint")}</p>
          ) : (
            offers.length > 0 && (
              <div className="theke-offers">
                <p className="theke-offers-title">{t("theke.play.offer.title")}</p>
                <ul>
                  {offers.map((offer) => (
                    <li key={offer.key}>
                      <button type="button" onClick={() => navigate(offer.to)}>
                        {withBold(offer.text)}
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            )
          )}
        </div>
      </section>

      {/* zuletzt gespielt: Papier-Etiketten in der Theke */}
      {recent.length > 1 && (
        <div className="theke-shelf">
          <h2 className="theke-shelf-title">{t("theke.play.recent")}</h2>
          <div className="theke-shelf-row">
            {recent.map((profile) => {
              const active = profile.id === selectedProfile?.id;
              return (
                <button
                  key={profile.id}
                  type="button"
                  onClick={() => onVersionChange(profile.id)}
                  aria-pressed={active}
                  className={cn("theke-recent", active && "is-active")}
                >
                  <b>{profile.name}</b>
                  <span>
                    {[profile.game_version, loaderName(profile), profile.last_played ? formatRelativeTime(profile.last_played) : null]
                      .filter(Boolean)
                      .join(" · ")}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
