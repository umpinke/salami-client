import type React from "react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { MainLaunchButton } from "../../../components/launcher/MainLaunchButton";
import { PlayerRig } from "../../../components/launcher/PlayerRig";
import { ProfileIcon } from "../../../components/profiles/ProfileIcon";
import type { Friend } from "../../../features/friends/friends-service";
import { useFriendsStore } from "../../../features/friends/friends-store";
import { usePlayerAvatar } from "../../../hooks/usePlayerAvatar";
import { cn } from "../../../lib/utils";
import { useProfileWizardStore } from "../../../store/profile-wizard-store";
import { useThemeStore } from "../../../store/useThemeStore";
import type { Profile } from "../../../types/profile";
import type { LookPlayViewProps } from "../types";
import { WerkbankIcon } from "./icons";

/** So viele Plätze hat das Inventar „Zuletzt gespielt“. */
const SLOT_COUNT = 7;
/** So viele Freunde zeigt die Tafel höchstens. */
const FRIEND_ROWS = 5;

const LOADER_NAMES: Record<string, string> = {
  vanilla: "Vanilla",
  fabric: "Fabric",
  quilt: "Quilt",
  forge: "Forge",
  neoforge: "NeoForge",
};

/** Blockfarben für Profile ohne eigenes Bild (fest pro Profil). */
const ITEM_COLORS = ["#6b8f3a", "#b23a3a", "#3a6b8f", "#c9a23a", "#7a5a8f", "#c08a5a"];

const plainName = (name: string) => name.replace(/§./g, "").trim();

function itemColor(id: string): string {
  let sum = 0;
  for (let i = 0; i < id.length; i++) sum = (sum + id.charCodeAt(i) * (i + 1)) % 9973;
  return ITEM_COLORS[sum % ITEM_COLORS.length];
}

/** Die Pixel-Schrift ist breit: lange Profilnamen bekommen eine kleinere Stufe. */
function titleStep(name: string): string {
  const length = plainName(name).length;
  if (length <= 9) return "wb-title--xl";
  if (length <= 14) return "wb-title--lg";
  if (length <= 22) return "wb-title--md";
  return "wb-title--sm";
}

/**
 * Startseite der Pixel-Werkbank: links Profilname, Schildchen, der große Start-Knopf und das
 * Inventar „Zuletzt gespielt“; rechts die Spielfigur auf einem Salami-Block und die Freunde-Tafel.
 */
export function WerkbankPlayView({ profiles, selectedProfile, versions, onVersionChange, playerName }: LookPlayViewProps) {
  const { t } = useTranslation();
  const openProfileWizard = useProfileWizardStore((s) => s.openModal);

  const recent = useMemo(
    () =>
      [...profiles]
        .sort((a, b) => (b.last_played ?? "").localeCompare(a.last_played ?? ""))
        .slice(0, SLOT_COUNT),
    [profiles],
  );

  const title = selectedProfile?.name ?? t("salami.play.no_profile");
  const loaderName = selectedProfile ? LOADER_NAMES[selectedProfile.loader] ?? selectedProfile.loader : "";
  // gespeicherte Fabric-/Quilt-Versionen enden auf " (stable)" – das gehört nicht aufs Schildchen
  const loaderVersion =
    selectedProfile && selectedProfile.loader !== "vanilla"
      ? (selectedProfile.loader_version ?? "").replace(/\s*\(.*\)\s*$/, "")
      : "";
  const modCount = selectedProfile ? selectedProfile.mod_count ?? selectedProfile.mods?.length ?? 0 : 0;

  return (
    <div className="wb-play">
      <section className="wb-play__main">
        <div className="wb-play__head">
          <p className="wb-eyebrow">{t("salami.werkbank.ready")}</p>
          <h1 className={cn("wb-title", titleStep(title))}>{title}</h1>
          {selectedProfile && (
            <div className="wb-chips">
              <span className="look-label look-label--ghost wb-chip">Minecraft {selectedProfile.game_version}</span>
              <span className="look-label look-label--red wb-chip">
                {loaderName}
                {loaderVersion ? ` ${loaderVersion}` : ""}
              </span>
              {modCount > 0 && (
                <span className="look-label look-label--ghost wb-chip">{t("salami.werkbank.mods", { count: modCount })}</span>
              )}
            </div>
          )}
        </div>

        {versions.length === 0 ? (
          // ohne Profil kein Start-Knopf – dann wenigstens den Weg zum ersten Profil zeigen
          <div className="wb-noprofile">
            <p className="wb-noprofile__hint">{t("salami.play.no_profile_hint")}</p>
            <button
              type="button"
              onClick={() => openProfileWizard(null)}
              className="look-btn look-btn--primary wb-create"
            >
              <span className="wb-create__plus" aria-hidden="true">
                +
              </span>
              {t("salami.play.create_profile")}
            </button>
          </div>
        ) : (
          <MainLaunchButton
            className="wb-launch"
            defaultVersion={selectedProfile?.id ?? versions[0]?.id}
            onVersionChange={onVersionChange}
            versions={versions}
            mainButtonWidth="w-full"
            maxWidth="420px"
            mainButtonHeight="h-[84px]"
          />
        )}

        {recent.length > 0 && (
          <div className="wb-panel wb-inv">
            <h3 className="wb-panel__title">{t("salami.play.recent")}</h3>
            <div className="wb-inv__grid">
              {Array.from({ length: SLOT_COUNT }, (_, index) => {
                const profile = recent[index];
                if (!profile) return <span key={`empty-${index}`} className="wb-slot wb-inv__slot" aria-hidden="true" />;
                const active = profile.id === selectedProfile?.id;
                const loader = LOADER_NAMES[profile.loader] ?? profile.loader;
                return (
                  <button
                    key={profile.id}
                    type="button"
                    onClick={() => onVersionChange(profile.id)}
                    aria-pressed={active}
                    aria-label={`${plainName(profile.name)} – Minecraft ${profile.game_version}`}
                    className={cn("wb-slot wb-inv__slot", active && "wb-inv__slot--on")}
                  >
                    <ProfileItem profile={profile} />
                    <span className="wb-tip" aria-hidden="true">
                      <span className="wb-tip__name">{plainName(profile.name)}</span>
                      <span className="wb-tip__meta">
                        Minecraft {profile.game_version} · {loader}
                      </span>
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
        )}
      </section>

      <section className="wb-play__side">
        <div className="wb-stage">
          <div className="wb-stage__figure">
            {playerName && <div className="wb-nametag">{playerName}</div>}
            {/* der Salami-Block, auf dem die Figur steht */}
            <div className="wb-block" aria-hidden="true">
              <div className="wb-block__top" />
              <div className="wb-block__front" />
            </div>
            <div className="wb-rig">
              <PlayerRig playerName={playerName} showNametag={false} />
            </div>
          </div>
        </div>
        <FriendsBoard />
      </section>
    </div>
  );
}

/** Ein Profil als Gegenstand im Slot: das eigene Bild oder ein farbiger Block mit Anfangsbuchstaben. */
function ProfileItem({ profile }: { profile: Profile }) {
  const accent = useThemeStore((s) => s.accentColor.value);

  if (profile.banner) {
    return (
      <span className="wb-item wb-item--image">
        <ProfileIcon
          profileId={profile.id}
          banner={profile.banner}
          accentColor={accent}
          onSuccessfulUpdate={() => {}}
          isEditable={false}
          variant="bare"
          className="h-full w-full"
          placeholderIcon="ph:package-duotone"
          iconClassName="h-5 w-5"
        />
      </span>
    );
  }

  return (
    <span className="wb-item wb-item--block" style={{ "--wb-item": itemColor(profile.id) } as React.CSSProperties}>
      {plainName(profile.name).charAt(0).toUpperCase() || "?"}
    </span>
  );
}

/** „Freunde online“: wer da ist, zuerst – ein Klick führt zum Freunde-Reiter. */
function FriendsBoard() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const usable = useFriendsStore((s) => s.ready && s.available && s.enabled && s.signedIn);
  const friends = useFriendsStore((s) => s.friends);

  if (!usable || friends.length === 0) return null;

  // die Liste ist schon sortiert: online zuerst, dann nach Namen
  const online = friends.filter((f) => f.online).length;
  const shown = friends.slice(0, FRIEND_ROWS);
  const more = friends.length - shown.length;

  return (
    <aside className="wb-panel wb-friends">
      <h3 className="wb-panel__title">
        {t("salami.werkbank.friends.title")}
        <span className="wb-friends__count">{online}</span>
      </h3>
      <button
        type="button"
        className="wb-friends__list"
        onClick={() => navigate("/friends")}
        aria-label={t("salami.werkbank.friends.open")}
      >
        {shown.map((friend) => (
          <FriendRow key={friend.uuid} friend={friend} />
        ))}
        {more > 0 && <span className="wb-friends__more">{t("salami.werkbank.friends.more", { count: more })}</span>}
      </button>
    </aside>
  );
}

function FriendRow({ friend }: { friend: Friend }) {
  const { t } = useTranslation();
  const avatar = usePlayerAvatar({ uuid: friend.uuid, size: 64, overlay: true });
  // fehlt der Status (ältere Server), gilt „online“ als im Spiel
  const status = friend.online ? (friend.status === "launcher" ? "launcher" : "game") : "offline";

  return (
    <span className={cn("wb-fr", !friend.online && "wb-fr--off")}>
      <span className="wb-fr__head">
        {avatar ? <img src={avatar} alt="" draggable={false} /> : <WerkbankIcon name="profiles" className="wb-fr__nohead" />}
      </span>
      <span className="wb-fr__name">{friend.name}</span>
      <span className={cn("wb-fr__dot", `wb-fr__dot--${status}`)} title={t(`salami.friends.status.${status}`)} />
    </span>
  );
}
