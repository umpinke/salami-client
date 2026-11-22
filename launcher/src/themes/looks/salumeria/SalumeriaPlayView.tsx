import type React from "react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { MainLaunchButton } from "../../../components/launcher/MainLaunchButton";
import { PlayerRig } from "../../../components/launcher/PlayerRig";
import { useProfileWizardStore } from "../../../store/profile-wizard-store";
import type { Profile } from "../../../types/profile";
import type { LookPlayViewProps } from "../types";

const RECENT_COUNT = 4;
const LOADER_NAMES: Record<string, string> = {
  vanilla: "Vanilla",
  fabric: "Fabric",
  quilt: "Quilt",
  forge: "Forge",
  neoforge: "NeoForge",
};

const loaderNameOf = (profile: Profile) => LOADER_NAMES[profile.loader] ?? profile.loader;

/** Gespeicherte Loader-Versionen enden auf " (stable)" – das gehört nicht aufs Etikett. */
const cleanLoaderVersion = (version: string | null) => (version ?? "").replace(/\s*\(.*\)\s*$/, "").trim();

/** Tageszeit für die Begrüßung. */
function daytime(): "morning" | "day" | "evening" {
  const hour = new Date().getHours();
  if (hour >= 5 && hour < 11) return "morning";
  if (hour >= 11 && hour < 18) return "day";
  return "evening";
}

/**
 * Zwei Zeilen: die erste in Creme, die zweite rot und kursiv. Bei zwei Wörtern je eins; bei mehr
 * wird so geteilt, dass die längere Zeile möglichst kurz bleibt.
 */
function splitName(name: string): [string, string] {
  const words = name.trim().split(/\s+/);
  if (words.length < 2) return [words[0] ?? "", ""];
  let best: [string, string] = [words[0], words.slice(1).join(" ")];
  for (let i = 2; i < words.length; i++) {
    const candidate: [string, string] = [words.slice(0, i).join(" "), words.slice(i).join(" ")];
    if (Math.max(candidate[0].length, candidate[1].length) < Math.max(best[0].length, best[1].length)) best = candidate;
  }
  return best;
}

/** Schriftgröße des Profilnamens nach der längsten Zeile, damit auch lange Namen in die Spalte passen. */
function nameSize(first: string, rest: string): "xl" | "lg" | "md" | "sm" {
  const longest = Math.max(first.length, rest.length);
  if (longest <= 8) return "xl";
  if (longest <= 11) return "lg";
  if (longest <= 15) return "md";
  return "sm";
}

/**
 * Startseite der Salumeria: links Begrüßung, Profilname in großer Serife, Angaben in einer
 * kursiven Zeile und der rote Start-Knopf; darunter die zuletzt gespielten Profile als
 * nummerierte Etiketten. Rechts steht die Spielfigur in einem Bogenrahmen mit Namensschild.
 */
export function SalumeriaPlayView({ profiles, selectedProfile, versions, onVersionChange, playerName }: LookPlayViewProps) {
  const { t } = useTranslation();
  const openProfileWizard = useProfileWizardStore((s) => s.openModal);
  const time = useMemo(daytime, []);

  const recent = useMemo(
    () =>
      [...profiles]
        .sort((a, b) => (b.last_played ?? "").localeCompare(a.last_played ?? ""))
        .slice(0, RECENT_COUNT),
    [profiles],
  );

  const title = selectedProfile?.name ?? t("salami.play.no_profile");
  const [first, rest] = splitName(title);

  const modCount = selectedProfile ? selectedProfile.mod_count ?? selectedProfile.mods?.length ?? 0 : 0;
  const loaderVersion = selectedProfile ? cleanLoaderVersion(selectedProfile.loader_version) : "";

  return (
    <div className="sal-play">
      <div className="sal-play__left">
        <section className="sal-play__hero">
          <p className="sal-eyebrow">
            {playerName
              ? t(`salami.look.salumeria.greeting_${time}`, { name: playerName })
              : t(`salami.look.salumeria.greeting_${time}_anon`)}
          </p>
          <h1 className="sal-play__name" data-size={nameSize(first, rest)}>
            {first}
            {rest && (
              <>
                {" "}
                <em>{rest}</em>
              </>
            )}
          </h1>

          {selectedProfile && (
            <p className="sal-play__spec">
              <b>Minecraft {selectedProfile.game_version}</b>
              <span aria-hidden="true">·</span>
              {selectedProfile.loader === "vanilla"
                ? t("salami.look.salumeria.spec_vanilla")
                : t("salami.look.salumeria.spec_loader", {
                    loader: [loaderNameOf(selectedProfile), loaderVersion].filter(Boolean).join(" "),
                  })}
              {modCount > 0 && (
                <>
                  <span aria-hidden="true">·</span>
                  {t("salami.look.salumeria.spec_mods", { count: modCount })}
                </>
              )}
            </p>
          )}

          {versions.length === 0 && (
            // ohne Profil kein Start-Knopf – dann wenigstens den Weg zum ersten Profil zeigen
            <div className="sal-play__empty">
              <p>{t("salami.play.no_profile_hint")}</p>
              <button type="button" onClick={() => openProfileWizard(null)} className="look-btn look-btn--primary sal-play__create">
                {t("salami.play.create_profile")}
              </button>
            </div>
          )}

          {versions.length > 0 && (
            <div
              className="sal-play__action"
              style={{ "--sal-switch-label": JSON.stringify(t("salami.look.salumeria.switch_profile")) } as React.CSSProperties}
            >
              <MainLaunchButton
                defaultVersion={selectedProfile?.id ?? versions[0]?.id}
                onVersionChange={onVersionChange}
                versions={versions}
                maxWidth="none"
                mainButtonHeight="h-[72px]"
              />
            </div>
          )}
        </section>

        {recent.length > 1 && (
          <section className="sal-recent">
            <h2 className="sal-recent__title">{t("salami.look.salumeria.recent")}</h2>
            <div className="sal-recent__row">
              {recent.map((profile, index) => {
                const active = profile.id === selectedProfile?.id;
                return (
                  <button
                    key={profile.id}
                    type="button"
                    onClick={() => onVersionChange(profile.id)}
                    aria-pressed={active}
                    className={active ? "sal-ticket sal-ticket--on" : "sal-ticket"}
                  >
                    <span className="sal-ticket__no">
                      {t("salami.look.salumeria.recent_no", { number: String(index + 1).padStart(2, "0") })}
                    </span>
                    <span className="sal-ticket__name">{profile.name}</span>
                    <span className="sal-ticket__spec">
                      {profile.game_version} · {loaderNameOf(profile)}
                    </span>
                  </button>
                );
              })}
            </div>
          </section>
        )}
      </div>

      <section className="sal-play__portrait">
        <div className="sal-portrait">
          <div className="sal-cameo">
            <div className="sal-cameo__rig">
              <PlayerRig playerName={playerName} showNametag={false} />
            </div>
          </div>
          <div className="sal-plaque">
            <span className="sal-plaque__name">{playerName || t("salami.look.salumeria.plaque_guest")}</span>
            <span className="sal-plaque__line">
              {playerName ? t("salami.look.salumeria.plaque_line") : t("salami.look.salumeria.plaque_line_guest")}
            </span>
          </div>
        </div>
      </section>
    </div>
  );
}
