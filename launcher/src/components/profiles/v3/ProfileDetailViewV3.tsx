"use client";

/**
 * ProfileDetailViewV3 — Redesign des Profile-Detail-Views.
 *
 * Struktur: Top-Bar (Breadcrumb + 4 Icon-Actions) · Hero (Icon + Chips + Play)
 * · Stats-Strip · Left-Rail Navigation · Main-Content (V3 Content-Tabs +
 * noch-V2 Worlds/Screenshots/Logs bis zur Migration).
 *
 * Wrapper-Toggle in `ProfileDetailViewV2Wrapper.tsx` (USE_V3) schaltet
 * zwischen altem V2 und V3 um.
 */

import { useEffect, useState, useCallback, useRef } from "react";
import type React from "react";
import { useNavigate } from "react-router-dom";
import { Icon } from "@iconify/react";
import { toast } from "react-hot-toast";
import { useTranslation } from "react-i18next";
import { preloadIcons } from "../../../lib/icon-utils";

preloadIcons([
  "solar:gamepad-bold", "solar:box-bold", "solar:folder-bold",
  "solar:widget-bold", "solar:user-bold", "solar:pen-linear",
  "solar:lock-keyhole-minimalistic-bold",
  "solar:play-bold", "solar:stop-bold", "solar:settings-bold",
  "solar:refresh-bold",
  "solar:arrow-left-linear", "solar:folder-linear", "solar:copy-linear",
  "solar:upload-linear", "solar:menu-dots-bold", "solar:refresh-circle-bold",
  "solar:clock-circle-bold", "solar:hourglass-bold", "solar:ssd-round-bold",
  "solar:copy-bold", "solar:download-bold", "solar:archive-bold",
  "solar:trash-bin-trash-bold",
]);

import type { Profile } from "../../../types/profile";
import { setDiscordState } from "../../../utils/discordRpc";
import { formatRelativeTime, formatPlaytime, formatBytes } from "../../../utils/format-relative-time";
import { parseMotdToHtml } from "../../../utils/motd-utils";
import { useThemeStore } from "../../../store/useThemeStore";
import { useProfileStore } from "../../../store/profile-store";
import { useIconCacheStore } from "../../../store/icon-cache-store";
import { useGlobalModal } from "../../../hooks/useGlobalModal";
import * as ProfileService from "../../../services/profile-service";
import UnifiedService from "../../../services/unified-service";
import type { UnifiedModpackVersionsResponse } from "../../../types/unified";
import { useProfileDuplicateStore } from "../../../store/profile-duplicate-store";
import { useProfileLaunch } from "../../../hooks/useProfileLaunch.tsx";
import { useAppDragDropStore } from "../../../store/appStore";
import { useMinecraftAuthStore } from "../../../store/minecraft-auth-store";
import { usePlayerAvatar } from "../../../hooks/usePlayerAvatar";
import { useResolvedLoaderVersion } from "../../../hooks/useResolvedLoaderVersion";

import { Button } from "../../ui/buttons/Button";
import { ProfileIconV2 } from "../ProfileIconV2";
import { ExportProfileModal } from "../ExportProfileModal";
import { ModpackVersionsModal } from "../../modals/ModpackVersionsModal";
import { ConfirmDeleteDialog } from "../../modals/ConfirmDeleteDialog";
import { SettingsContextMenu, type ContextMenuItem } from "../../ui/SettingsContextMenu";
import { Tooltip } from "../../ui/Tooltip";
import { SURFACE, KANTE_SOFT, kopfband } from "../../ui/design-system";
import { SALAMI_COLORS } from "../../../themes/salami/salami-theme";

import { ProfileLeftRailV3, type NavKey, CONTENT_NAV_KEYS, buildProfileNavGroups } from "./ProfileLeftRailV3";
import { useLook } from "../../../themes/looks/registry";
import { EditableChipV3 } from "./chips/EditableChipV3";
import { GameVersionPickerV3 } from "./chips/GameVersionPickerV3";
import { LoaderBadgeV3 } from "./chips/LoaderBadgeV3";
import { LoaderVersionPickerV3 } from "./chips/LoaderVersionPickerV3";
import { useHeroChipEditors } from "./chips/useHeroChipEditors";
import { LocalContentTabV3 } from "./tabs/LocalContentTabV3";
import { WorldsTabV3 } from "./tabs/WorldsTabV3";
import { ScreenshotsTabV3 } from "./tabs/ScreenshotsTabV3";
import { LogsTabV3 } from "./tabs/LogsTabV3";
import type { LocalContentItem } from "../../../hooks/useLocalContentManager";
import { parseErrorMessage } from "../../../utils/error-utils";
import { useContentCacheStore } from "../../../store/content-cache-store";

const mainTabFor = (k: NavKey): string =>
  CONTENT_NAV_KEYS.includes(k) ? "content" : k;

interface ProfileDetailViewV3Props {
  profile: Profile;
  onClose: () => void;
  onEdit: () => void;
}

// ─── Atoms ─────────────────────────────────────────────────────────────────
const Chip: React.FC<{ icon?: string; children: React.ReactNode }> = ({ icon, children }) => (
  <span className="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg bg-black/30 border border-white/10 text-sm text-white/75 font-minecraft">
    {icon && <Icon icon={icon} className="w-4 h-4 flex-shrink-0" />}
    {children}
  </span>
);

// Salami: Kennzahl in der Fußzeile der Kopfkarte – kein eigener grauer Kasten mehr
const Stat: React.FC<{ icon: string; label: string; value: string; muted?: boolean }> = ({ icon, label, value, muted }) => (
  <div className="flex items-center gap-2.5 min-w-0 max-w-[240px]">
    <Icon icon={icon} className={`w-[18px] h-[18px] flex-shrink-0 ${muted ? "text-white/25" : "text-white/50"}`} />
    <div className="flex flex-col leading-tight min-w-0">
      <span className="text-xs text-white/45">{label}</span>
      <span className={`text-sm font-minecraft truncate ${muted ? "text-white/40" : "text-white/90"}`} title={value}>{value}</span>
    </div>
  </div>
);

const LOADER_NAMES: Record<string, string> = {
  fabric: "Fabric",
  quilt: "Quilt",
  forge: "Forge",
  neoforge: "NeoForge",
};

// ─── Main ──────────────────────────────────────────────────────────────────
export function ProfileDetailViewV3({
  profile,
  onClose,
  onEdit,
}: ProfileDetailViewV3Props) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const look = useLook();
  const [currentProfile, setCurrentProfile] = useState<Profile>(profile);
  const [activeNavItem, setActiveNavItem] = useState<NavKey>("mods");

  // Inline-edit chip editors — owns fetchers + save + lock state
  const chipEditors = useHeroChipEditors(currentProfile, setCurrentProfile);

  // Group-chip draft input (persisted-on-Save via chipEditors.saveGroup)
  const [groupDraft, setGroupDraft] = useState<string>(profile.group ?? "");

  useEffect(() => { setDiscordState("Editing a Profile"); }, []);

  // Context menu state
  const [isContextMenuOpen, setIsContextMenuOpen] = useState(false);
  const contextMenuId = `profile-detail-v3-${profile.id}`;
  const [contextMenuPosition, setContextMenuPosition] = useState({ x: 0, y: 0 });
  const moreButtonRef = useRef<HTMLButtonElement>(null);

  // Delete modal state
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  const [modpackVersions, setModpackVersions] = useState<UnifiedModpackVersionsResponse | null>(() => {
    const source = profile.modpack_info?.source;
    if (!source) return null;
    return useContentCacheStore.getState().getModpackVersions(JSON.stringify(source)) ?? null;
  });

  const { showModal, hideModal } = useGlobalModal();
  const { openContextMenuId, setOpenContextMenuId } = useThemeStore();
  const accentColor = useThemeStore((s) => s.accentColor);
  const { fetchProfiles } = useProfileStore();
  const { setActiveMainTab: setDragDropMainTab } = useAppDragDropStore();
  const { openModal: openDuplicateModal } = useProfileDuplicateStore();

  const accounts = useMinecraftAuthStore((state) => state.accounts);
  const preferredAccount = currentProfile.preferred_account_id
    ? accounts.find(acc => acc.id === currentProfile.preferred_account_id)
    : null;
  const preferredAccountAvatarUrl = usePlayerAvatar({
    uuid: preferredAccount?.id,
    overlay: true,
  });

  const { isLaunching, statusMessage, handleLaunch, handleQuickPlayLaunch } = useProfileLaunch({
    profileId: profile.id,
    onLaunchSuccess: () => {},
    onLaunchError: (error) => console.error("[V3] Profile launch error:", error),
  });

  // ── Handlers ──────────────────────────────────────────────────────────────
  // Salami: the project name ("Sodium") instead of the file name once Modrinth/CurseForge
  // answered; local files keep their file name.
  const modrinthTitles = useIconCacheStore((s) => s.modrinthTitles);
  const curseforgeTitles = useIconCacheStore((s) => s.curseforgeTitles);
  const getGenericDisplayFileName = useCallback(
    (item: LocalContentItem) =>
      (item.modrinth_info?.project_id && modrinthTitles[item.modrinth_info.project_id]) ||
      (item.curseforge_info?.project_id && curseforgeTitles[item.curseforge_info.project_id]) ||
      item.filename,
    [modrinthTitles, curseforgeTitles],
  );

  // Stub — tabs handle their own refresh; mirrors V2's handleRefresh noop.
  const handleRefresh = useCallback(() => {}, []);

  const handleLaunchRequest = useCallback(async (params: {
    profileId: string;
    quickPlaySingleplayer?: string;
    quickPlayMultiplayer?: string;
  }) => {
    if (params.quickPlaySingleplayer) {
      toast.success(t('profiles.toast.launching_world', { name: params.quickPlaySingleplayer }));
      handleQuickPlayLaunch(params.quickPlaySingleplayer, undefined);
    } else if (params.quickPlayMultiplayer) {
      toast.success(t('profiles.toast.joining_server', { name: params.quickPlayMultiplayer }));
      handleQuickPlayLaunch(undefined, params.quickPlayMultiplayer);
    } else {
      handleQuickPlayLaunch(undefined, undefined);
    }
  }, [handleQuickPlayLaunch, t]);

  const handleDeleteProfile = useCallback(() => {
    if (currentProfile.is_standard_version) {
      toast.error(t('profiles.cannotDeleteStandard'));
      return;
    }
    setIsDeleteModalOpen(true);
  }, [currentProfile, t]);

  const handleConfirmDelete = useCallback(async () => {
    setIsDeleting(true);
    try {
      const deletePromise = useProfileStore.getState().deleteProfile(currentProfile.id);
      await toast.promise(deletePromise, {
        loading: t('profiles.deletingProfile', { name: currentProfile.name }),
        success: () => {
          fetchProfiles();
          navigate("/profiles");
          setIsDeleteModalOpen(false);
          return t('profiles.deleteSuccess', { name: currentProfile.name });
        },
        error: (err) =>
          t('profiles.deleteError', { error: parseErrorMessage(err) }),
      });
    } catch (error) {
      console.error("[V3] Delete failed:", error);
    } finally {
      setIsDeleting(false);
    }
  }, [currentProfile, fetchProfiles, navigate, t]);

  const handleCancelDelete = useCallback(() => {
    setIsDeleteModalOpen(false);
  }, []);

  const handleOpenExportModal = useCallback(() => {
    showModal(`export-profile-${currentProfile.id}`, (
      <ExportProfileModal
        profile={currentProfile}
        isOpen={true}
        onClose={() => hideModal(`export-profile-${currentProfile.id}`)}
      />
    ));
  }, [currentProfile, showModal, hideModal]);

  const handleOpenFolder = useCallback(async () => {
    const openPromise = ProfileService.openProfileFolder(currentProfile.id);
    toast.promise(openPromise, {
      loading: t('profiles.openingFolder', { name: currentProfile.name }),
      success: t('profiles.openFolderSuccess', { name: currentProfile.name }),
      error: (err) => {
        const message = parseErrorMessage(err);
        return t('profiles.openFolderError', { error: message });
      },
    });
  }, [currentProfile, t]);

  const handleDuplicateProfile = useCallback(() => {
    openDuplicateModal(currentProfile);
  }, [currentProfile, openDuplicateModal]);

  const handleOpenModpackVersionsModal = useCallback(() => {
    showModal(`modpack-versions-${currentProfile.id}`, (
      <ModpackVersionsModal
        isOpen={true}
        onClose={() => hideModal(`modpack-versions-${currentProfile.id}`)}
        versions={modpackVersions}
        modpackName={currentProfile.name}
        profileId={currentProfile.id}
        onSwitchComplete={async () => {
          try {
            await fetchProfiles();
            const updatedProfiles = useProfileStore.getState().profiles;
            const updatedProfile = updatedProfiles.find(p => p.id === currentProfile.id);
            if (updatedProfile) setCurrentProfile(updatedProfile);
          } catch (err) {
            console.error("[V3] Failed to refresh profile data after modpack switch:", err);
          }
        }}
      />
    ));
  }, [currentProfile, showModal, hideModal, modpackVersions, fetchProfiles]);

  // ── Context menu items ────────────────────────────────────────────────────
  const contextMenuItems: ContextMenuItem[] = [
    {
      id: "edit",
      label: t('profiles.editProfile'),
      icon: "solar:settings-bold",
      onClick: () => onEdit(),
    },
    {
      id: "duplicate",
      label: t('profiles.duplicate'),
      icon: "solar:copy-bold",
      onClick: () => handleDuplicateProfile(),
    },
    {
      id: "export",
      label: t('profiles.export'),
      icon: "solar:download-bold",
      onClick: () => handleOpenExportModal(),
    },
    ...(currentProfile.modpack_info && modpackVersions ? [{
      id: "modpack-versions",
      label: t('profiles.modpackVersions'),
      icon: "solar:archive-bold",
      onClick: () => handleOpenModpackVersionsModal(),
    }] : []),
    {
      id: "open-folder",
      label: t('profiles.openFolder'),
      icon: "solar:folder-bold",
      onClick: () => handleOpenFolder(),
    },
    {
      id: "delete",
      label: t('profiles.delete'),
      icon: "solar:trash-bin-trash-bold",
      destructive: true,
      separator: true,
      onClick: () => handleDeleteProfile(),
    },
  ];

  const toggleContextMenu = useCallback((event?: React.MouseEvent<HTMLButtonElement>) => {
    event?.preventDefault();
    event?.stopPropagation();

    if (openContextMenuId && openContextMenuId !== contextMenuId) {
      setOpenContextMenuId(null);
    }

    const newState = !isContextMenuOpen;
    setIsContextMenuOpen(newState);
    setOpenContextMenuId(newState ? contextMenuId : null);

    if (!isContextMenuOpen && event?.currentTarget) {
      const buttonRect = event.currentTarget.getBoundingClientRect();
      const containerRect = event.currentTarget.closest('.relative')?.getBoundingClientRect();
      if (containerRect) {
        setContextMenuPosition({
          x: buttonRect.right - containerRect.left - 200,
          y: buttonRect.bottom - containerRect.top + 4,
        });
      }
    }
  }, [openContextMenuId, contextMenuId, isContextMenuOpen, setOpenContextMenuId]);

  // ── Effects ───────────────────────────────────────────────────────────────
  useEffect(() => {
    setCurrentProfile(profile);
    setGroupDraft(profile.group ?? "");
  }, [profile]);

  useEffect(() => {
    setDragDropMainTab(mainTabFor(activeNavItem));
    return () => { setDragDropMainTab(null); };
  }, [activeNavItem, setDragDropMainTab]);

  // Modpack-Versions beim Mount laden bzw. wenn sich die Modpack-Quelle aendert.
  // Deps sind serialisiert statt Objekt-Identitaet, damit der Effect nicht bei
  // jedem `setCurrentProfile(profile)` aus dem Store erneut feuert.
  const modpackSource = currentProfile.modpack_info?.source;
  const modpackSourceKey = modpackSource ? JSON.stringify(modpackSource) : null;
  useEffect(() => {
    if (!modpackSource) {
      setModpackVersions(null);
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const versions = await UnifiedService.getModpackVersions(modpackSource);
        if (modpackSourceKey) useContentCacheStore.getState().setModpackVersions(modpackSourceKey, versions);
        if (!cancelled) setModpackVersions(versions);
      } catch (err) {
        console.error("[V3] Failed to refresh modpack versions:", err);
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modpackSourceKey]);

  useEffect(() => {
    if (openContextMenuId && openContextMenuId !== contextMenuId && isContextMenuOpen) {
      setIsContextMenuOpen(false);
    }
  }, [openContextMenuId, contextMenuId, isContextMenuOpen]);

  // Disk usage: fetch once per profile, also refetch when a session ends
  // (Playtime-Wert hat sich dann geändert, plausibel dass sich auch Dateien änderten).
  const [diskSize, setDiskSize] = useState<number | null>(
    () => useContentCacheStore.getState().getDiskSize(profile.id) ?? null,
  );
  useEffect(() => {
    let cancelled = false;
    ProfileService.getProfileDiskSize(currentProfile.id)
      .then((size) => {
        useContentCacheStore.getState().setDiskSize(currentProfile.id, size);
        if (!cancelled) setDiskSize(size);
      })
      .catch((err) => {
        console.warn("[V3] Failed to fetch disk size:", err);
      });
    return () => { cancelled = true; };
  }, [currentProfile.id, currentProfile.playtime_seconds]);

  const resolvedLoaderVersion = useResolvedLoaderVersion(currentProfile);

  // ── Render ────────────────────────────────────────────────────────────────
  const hasModpack = !!currentProfile.modpack_info;
  const isVanillaLoader = !currentProfile.loader || currentProfile.loader === "vanilla";
  const modpackLabel = modpackVersions?.installed_version
    ? `${modpackVersions.installed_version.name ?? t("profiles.v3.stats.modpack")}`
    : hasModpack ? t("profiles.v3.stats.modpack") : t("profiles.v3.time.placeholder");
  const modpackVersionNumber = modpackVersions?.installed_version?.version_number ?? null;

  const chipsNode = (
    <>
              <EditableChipV3
                withSaveCancel={false}
                width="w-64"
                disabled={chipEditors.isLocked}
                disabledReason={chipEditors.lockReason}
                onOpen={() => { void chipEditors.loadMinecraftVersions(); }}
                renderEditor={({ close }) => (
                  <GameVersionPickerV3
                    currentVersion={currentProfile.game_version}
                    versions={chipEditors.mcVersions}
                    isLoading={chipEditors.mcLoading}
                    onSelect={(v) => {
                      void chipEditors.saveGameVersion(v);
                      close();
                    }}
                  />
                )}
              >
                Minecraft {currentProfile.game_version}
              </EditableChipV3>
              {!isVanillaLoader && currentProfile.loader && (() => {
                // Resolve priority (matches Rust `ModloaderFactory::resolve_loader_version`):
                //   1. settings.use_overwrite_loader_version + overwrite_loader_version
                //   2. selected_norisk_pack policy
                //   3. profile.loader_version (stored)
                //   4. auto-fetch latest from loader API
                // `resolvedLoaderVersion` already encapsulates this, so it's the
                // only source of truth for display + picker-highlight. Stored
                // is just a last-resort fallback if resolve hasn't answered yet
                // (network failure, vanilla check races, …).
                const resolved = resolvedLoaderVersion?.version ?? null;
                const stored = currentProfile.loader_version ?? null;
                const effectiveVersion = resolved || stored;
                // `reason: "not_resolved"` with no effective version means the
                // loader's API has no releases for this MC (e.g. Quilt for
                // 26.x today) — surface it explicitly instead of the useless
                // "latest" placeholder.
                const notSupported =
                  resolvedLoaderVersion?.reason === "not_resolved" && !effectiveVersion;
                const stripped = effectiveVersion?.replace(/\s*\(stable\)\s*$/i, "") ?? null;
                const displayVersion = notSupported
                  ? t("profiles.v3.chips.loaderVersion.notSupported")
                  : stripped || t("profiles.v3.chips.loaderVersion.latest");
                return (
                  <EditableChipV3
                    tone="red"
                    withSaveCancel={false}
                    width="w-48"
                    disabled={chipEditors.isLocked}
                    disabledReason={chipEditors.lockReason}
                    onOpen={() => { void chipEditors.loadLoaderVersions(currentProfile.loader!, currentProfile.game_version); }}
                    renderEditor={({ close }) => (
                      <LoaderVersionPickerV3
                        loader={currentProfile.loader!}
                        currentVersion={effectiveVersion}
                        resolvedSource={resolvedLoaderVersion?.reason}
                        versions={chipEditors.loaderVersions}
                        isLoading={chipEditors.loaderLoading}
                        onSelect={(v) => {
                          void chipEditors.saveLoaderVersion(v);
                          close();
                        }}
                      />
                    )}
                  >
                    {LOADER_NAMES[currentProfile.loader] ?? currentProfile.loader}{" "}
                    <span className={notSupported ? "italic opacity-70" : undefined}>
                      {displayVersion}
                    </span>
                  </EditableChipV3>
                );
              })()}
              <EditableChipV3
                icon={currentProfile.group ? "solar:folder-bold" : "solar:add-circle-linear"}
                tone={currentProfile.group ? "fat" : "ghost"}
                disabled={chipEditors.isLocked}
                disabledReason={chipEditors.lockReason}
                onSave={() => { void chipEditors.saveGroup(groupDraft); }}
                onCancel={() => setGroupDraft(currentProfile.group ?? "")}
                renderEditor={({ commit }) => (
                  <div className="px-2 pt-2 pb-1">
                    <input
                      autoFocus
                      type="text"
                      value={groupDraft}
                      onChange={(e) => setGroupDraft(e.target.value)}
                      onKeyDown={(e) => { if (e.key === "Enter") commit(); }}
                      placeholder={t("profiles.v3.chips.group.placeholder")}
                      className="w-full h-8 px-2 rounded outline-none text-sm text-white placeholder:text-white/30"
                      style={{ backgroundColor: SURFACE.hover, border: `1px solid ${SURFACE.lineStrong}` }}
                    />
                  </div>
                )}
              >
                {currentProfile.group || t("profiles.v3.chips.group.add")}
              </EditableChipV3>
              {hasModpack && (() => {
                const versionsReady = !!modpackVersions;
                const tooltip = !versionsReady
                  ? t("profiles.v3.chips.modpack.loading")
                  : modpackVersionNumber ? `${modpackLabel} · ${modpackVersionNumber}` : modpackLabel;
                return (
                  <Tooltip content={tooltip}>
                    <button
                      type="button"
                      disabled={!versionsReady}
                      onClick={() => versionsReady && handleOpenModpackVersionsModal()}
                      className={`group inline-flex items-center gap-2 py-1.5 pl-2 pr-2.5 text-sm font-minecraft leading-none transition-[filter,transform] max-w-[280px] ${
                        !versionsReady
                          ? "opacity-60 cursor-wait"
                          : "hover:brightness-105 active:translate-y-px cursor-pointer"
                      }`}
                      style={{
                        background: SALAMI_COLORS.fat,
                        color: SALAMI_COLORS.pepper,
                        borderRadius: "4px 10px 10px 4px",
                        boxShadow: "0 2px 0 rgba(0,0,0,0.35)",
                      }}
                    >
                      <span className="h-2 w-2 flex-shrink-0" style={{ borderRadius: 999, background: SALAMI_COLORS.casing }} />
                      <Icon icon="solar:widget-bold" className="w-3.5 h-3.5 flex-shrink-0 opacity-70" />
                      <span className="truncate">{modpackLabel}</span>
                      {modpackVersionNumber && <span className="opacity-55 flex-shrink-0">· {modpackVersionNumber}</span>}
                      {versionsReady && (
                        <Icon
                          icon="solar:pen-linear"
                          className="w-3.5 h-3.5 flex-shrink-0 opacity-45 group-hover:opacity-90 transition-opacity"
                        />
                      )}
                    </button>
                  </Tooltip>
                );
              })()}
              {preferredAccount && (
                <Tooltip content={t('profiles.launchWith', { account: preferredAccount.username })}>
                  <span className="inline-flex items-center gap-1.5 px-1 text-sm text-white/60">
                    {preferredAccountAvatarUrl ? (
                      <img
                        src={preferredAccountAvatarUrl}
                        alt={preferredAccount.username}
                        className="w-4 h-4 rounded-sm flex-shrink-0"
                        style={{ imageRendering: 'pixelated' }}
                      />
                    ) : (
                      <Icon icon="solar:user-bold" className="w-4 h-4 flex-shrink-0" />
                    )}
                    {preferredAccount.username}
                  </span>
                </Tooltip>
              )}
    </>
  );

  const iconNode = (
            <div className="relative">
              <div
                className="look-profile-icon h-24 w-24 overflow-hidden border-[3px]"
                style={{ borderColor: SALAMI_COLORS.fat, backgroundColor: SURFACE.solid, borderRadius: "calc(var(--border-radius) * 0.8)" }}
              >
                <ProfileIconV2 profile={currentProfile} size="lg" className="h-full w-full" />
              </div>
              <LoaderBadgeV3
                loader={currentProfile.loader}
                disabled={chipEditors.isLocked}
                disabledReason={chipEditors.lockReason}
                onChange={(loader) => { void chipEditors.saveLoader(loader); }}
              />
            </div>
  );

  const moreMenuNode = (
        <div className="relative">
          <Tooltip content={t('profiles.moreOptions')} position="top" delay={0}>
            <button
              ref={moreButtonRef}
              onClick={toggleContextMenu}
              className="p-2 rounded hover:bg-white/5 text-white/50 hover:text-white transition-colors"
            >
              <Icon icon="solar:menu-dots-bold" className="w-4 h-4" />
            </button>
          </Tooltip>

          <SettingsContextMenu
            profile={currentProfile}
            isOpen={isContextMenuOpen}
            position={contextMenuPosition}
            items={contextMenuItems}
            onClose={() => {
              setIsContextMenuOpen(false);
              setOpenContextMenuId(null);
            }}
            triggerButtonRef={moreButtonRef}
          />
        </div>
  );

  const contentNode = (
    <>
          {activeNavItem === "mods" && (
            <LocalContentTabV3<LocalContentItem>
              profile={currentProfile}
              contentType="Mod"
              getDisplayFileName={getGenericDisplayFileName}
              itemTypeName={t('profiles.content.mod')}
              itemTypeNamePlural={t('profiles.content.mods')}
              addContentButtonText={t('profiles.content.addMods')}
              emptyStateIconOverride="solar:bolt-bold-duotone"
              onRefreshRequired={handleRefresh}
            />
          )}

          {activeNavItem === "resourcepacks" && (
            <LocalContentTabV3<LocalContentItem>
              profile={currentProfile}
              contentType="ResourcePack"
              getDisplayFileName={getGenericDisplayFileName}
              itemTypeName={t('profiles.content.resourcePack')}
              itemTypeNamePlural={t('profiles.content.resourcePacks')}
              addContentButtonText={t('profiles.content.addResourcePacks')}
              emptyStateIconOverride="solar:gallery-bold-duotone"
              onRefreshRequired={handleRefresh}
            />
          )}

          {activeNavItem === "shaderpacks" && (
            <LocalContentTabV3<LocalContentItem>
              profile={currentProfile}
              contentType="ShaderPack"
              getDisplayFileName={getGenericDisplayFileName}
              itemTypeName={t('profiles.content.shaderPack')}
              itemTypeNamePlural={t('profiles.content.shaderPacks')}
              addContentButtonText={t('profiles.content.addShaderPacks')}
              emptyStateIconOverride="solar:sun-bold-duotone"
              onRefreshRequired={handleRefresh}
            />
          )}

          {activeNavItem === "datapacks" && (
            <LocalContentTabV3<LocalContentItem>
              profile={currentProfile}
              contentType="DataPack"
              getDisplayFileName={getGenericDisplayFileName}
              itemTypeName={t('profiles.content.dataPack')}
              itemTypeNamePlural={t('profiles.content.dataPacks')}
              addContentButtonText={t('profiles.content.addDataPacks')}
              emptyStateIconOverride="solar:database-bold-duotone"
              onRefreshRequired={handleRefresh}
            />
          )}

          {activeNavItem === "nrc" && (
            <LocalContentTabV3<LocalContentItem>
              profile={currentProfile}
              contentType="NoRiskMod"
              getDisplayFileName={getGenericDisplayFileName}
              itemTypeName={t('profiles.content.noriskMod')}
              itemTypeNamePlural={t('profiles.content.noriskMods')}
              addContentButtonText={t('profiles.content.addNoriskMods')}
              emptyStateIconOverride="solar:shield-check-bold-duotone"
              onRefreshRequired={async () => {
                try {
                  await fetchProfiles();
                  setCurrentProfile(prev => ({ ...prev }));
                } catch (err) {
                  console.error("[V3] Failed to refresh profile data:", err);
                }
                handleRefresh();
              }}
              /* Kein Add-CTA: NoRisk-Mods kommen ueber den Pack-Selector, nicht via Browse. */
            />
          )}

          {activeNavItem === "worlds" && (
            <WorldsTabV3
              profile={currentProfile}
              isActive={true}
              onRefresh={handleRefresh}
              onLaunchRequest={handleLaunchRequest}
            />
          )}

          {activeNavItem === "screenshots" && (
            <ScreenshotsTabV3 profile={currentProfile} isActive={true} />
          )}

          {activeNavItem === "logs" && (
            <LogsTabV3 profile={currentProfile} isActive={true} onRefresh={handleRefresh} />
          )}
    </>
  );

  const deleteDialog = (
      <ConfirmDeleteDialog
        isOpen={isDeleteModalOpen}
        itemName={currentProfile.name}
        onClose={handleCancelDelete}
        onConfirm={handleConfirmDelete}
        isDeleting={isDeleting}
        title={t('profiles.deleteProfileTitle')}
        message={
          <p className="text-white/80 font-minecraft">
            {t('profiles.deleteConfirmMessage', { name: currentProfile.name })}
          </p>
        }
      />
  );

  if (look.ProfileDetail) {
    const LookProfileDetail = look.ProfileDetail;
    return (
      <>
        <LookProfileDetail
          profile={currentProfile}
          nameHtml={parseMotdToHtml(currentProfile.name || currentProfile.id)}
          backLabel={t('profiles.back')}
          onBack={onClose}
          icon={iconNode}
          chips={chipsNode}
          launch={{ isLaunching, statusMessage: statusMessage ?? null, onLaunch: handleLaunch }}
          onEdit={onEdit}
          stats={{
            lastPlayed: formatRelativeTime(currentProfile.last_played),
            playtime: formatPlaytime(currentProfile.playtime_seconds),
            hasPlaytime: !!currentProfile.playtime_seconds,
            disk: diskSize == null ? null : formatBytes(diskSize),
          }}
          actions={{
            onSyncPacks: () => navigate("/sync-packs", { state: { fromProfileId: currentProfile.id } }),
            onOpenFolder: handleOpenFolder,
            onDuplicate: handleDuplicateProfile,
            onExport: handleOpenExportModal,
          }}
          moreMenu={moreMenuNode}
          nav={{
            items: buildProfileNavGroups(currentProfile).flatMap((group, index) =>
              group.items.map((item) => ({
                key: item.key,
                label: t(item.labelKey),
                count: item.count,
                group: index === 0 ? ("content" as const) : ("world" as const),
              })),
            ),
            active: activeNavItem,
            onChange: setActiveNavItem,
          }}
          content={contentNode}
        />
        {deleteDialog}
      </>
    );
  }

  return (
    <div className="h-full flex flex-col overflow-hidden relative">
      {/* ── Top Bar ─────────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between px-5 h-11 flex-shrink-0">
        <button
          onClick={onClose}
          className="flex items-center gap-2 text-white/55 hover:text-white transition-colors min-w-0"
        >
          <Icon icon="solar:arrow-left-linear" className="w-4 h-4 flex-shrink-0" />
          <span className="text-sm font-minecraft">{t('profiles.back')}</span>
          <span className="text-white/25">/</span>
          <span
            className="text-sm font-minecraft text-white/85 normal-case max-w-[240px] truncate"
            dangerouslySetInnerHTML={{ __html: parseMotdToHtml(currentProfile.name || currentProfile.id) }}
          />
        </button>

        <div className="flex items-center gap-1 relative">
          <button
            onClick={() => navigate("/sync-packs", { state: { fromProfileId: currentProfile.id } })}
            className="flex items-center gap-1.5 h-8 px-3 rounded text-white/70 hover:text-white transition-colors"
            style={{ backgroundColor: SURFACE.base, border: `1px solid ${SURFACE.line}`, boxShadow: KANTE_SOFT }}
          >
            <Icon icon="solar:refresh-circle-bold" className="w-4 h-4" />
            <span className="text-sm font-minecraft">{t('syncPacks.title')}</span>
          </button>
          <div className="w-px h-5 bg-white/10 mx-1" />
          <Tooltip content={t('profiles.openFolder')} position="top" delay={0}>
            <button
              onClick={handleOpenFolder}
              className="p-2 rounded hover:bg-white/5 text-white/50 hover:text-white transition-colors"
            >
              <Icon icon="solar:folder-linear" className="w-4 h-4" />
            </button>
          </Tooltip>
          <Tooltip content={t('profiles.duplicate')} position="top" delay={0}>
            <button
              onClick={handleDuplicateProfile}
              className="p-2 rounded hover:bg-white/5 text-white/50 hover:text-white transition-colors"
            >
              <Icon icon="solar:copy-linear" className="w-4 h-4" />
            </button>
          </Tooltip>
          <Tooltip content={t('profiles.export')} position="top" delay={0}>
            <button
              onClick={handleOpenExportModal}
              className="p-2 rounded hover:bg-white/5 text-white/50 hover:text-white transition-colors"
            >
              <Icon icon="solar:upload-linear" className="w-4 h-4" />
            </button>
          </Tooltip>
          <div className="w-px h-5 bg-white/10 mx-1" />
          {moreMenuNode}
        </div>
      </div>

      {/* ── Hero ────────────────────────────────────────────────────────── */}
      {/* Salami: Kopfkarte wie die Profil-Kachel – Kopfband im Salami-Anschnitt, cremegerahmtes
          Profilbild ragt hinein, Version/Loader/Gruppe als Etiketten, Kennzahlen in der Fußzeile */}
      <div className="px-5 pb-4 flex-shrink-0">
        <div
          className="relative overflow-hidden border"
          style={{ backgroundColor: SURFACE.base, borderColor: SURFACE.line, borderRadius: "var(--border-radius)" }}
        >
          <div className="salami-fat-spots h-12" style={kopfband(accentColor.value)} />

          {/* Icon + loader overlay */}
          <div className="absolute left-5 top-4 z-10">
            {iconNode}
          </div>

        <div className="flex items-start gap-5 pl-[144px] pr-5 pt-3 pb-4">
          {/* Identity + chips */}
          <div className="flex-1 min-w-0">
            <h1
              className="font-minecraft text-3xl leading-tight text-white normal-case truncate"
              dangerouslySetInnerHTML={{ __html: parseMotdToHtml(currentProfile.name || currentProfile.id) }}
            />
            <div className="flex flex-wrap items-center gap-2 mt-2">
              {chipsNode}
            </div>
          </div>

          {/* Primary CTA cluster */}
          <div className="flex items-center gap-2 flex-shrink-0">
            <Button
              onClick={handleLaunch}
              variant={isLaunching ? "destructive" : "3d"}
              size="lg"
              heightClassName="h-14"
              widthClassName="w-[200px]"
              icon={<Icon icon={isLaunching ? "solar:stop-bold" : "solar:play-bold"} width="24" height="24" />}
            >
              {isLaunching ? t('profiles.stop') : t('profiles.play')}
            </Button>
            <Tooltip content={t('profiles.editProfile')}>
              <button
                onClick={onEdit}
                className="h-14 w-12 border text-white/65 hover:text-white flex items-center justify-center transition-[background-color,color,transform] active:translate-y-px"
                style={{ backgroundColor: SURFACE.hover, borderColor: SURFACE.line, borderRadius: "var(--border-radius)", boxShadow: KANTE_SOFT }}
                onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = SURFACE.raised; }}
                onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = SURFACE.hover; }}
              >
                <Icon icon="solar:settings-bold" className="w-5 h-5" />
              </button>
            </Tooltip>
          </div>
        </div>

        {/* Kennzahlen-Fußzeile — waehrend Launch durch die Statuszeile ersetzt; feste
            Mindesthoehe, damit die Karte beim Umschalten nicht springt. */}
        <div
          className="flex items-center gap-x-8 gap-y-2 flex-wrap min-h-[48px] px-5 py-2 border-t"
          style={{ borderColor: SURFACE.line }}
        >
          {isLaunching && statusMessage ? (
            <div className="flex-1 flex items-center gap-3 min-w-[160px] animate-in fade-in duration-200">
              <Icon icon="svg-spinners:ring-resize" className="w-[18px] h-[18px] flex-shrink-0" style={{ color: accentColor.light ?? accentColor.value }} />
              <div className="flex flex-col leading-tight min-w-0">
                <span className="text-xs" style={{ color: accentColor.light ?? accentColor.value }}>
                  {t("profiles.card.starting")}
                </span>
                <span className="text-sm text-white/90 font-minecraft truncate" title={statusMessage}>
                  {statusMessage}
                </span>
              </div>
            </div>
          ) : (
            <>
              <Stat
                icon="solar:clock-circle-bold"
                label={t('profiles.sort.lastPlayed')}
                value={formatRelativeTime(currentProfile.last_played)}
              />
              <Stat
                icon="solar:hourglass-bold"
                label={t("profiles.v3.stats.playtime")}
                value={formatPlaytime(currentProfile.playtime_seconds)}
                muted={!currentProfile.playtime_seconds}
              />
              <Stat
                icon="solar:ssd-round-bold"
                label={t("profiles.v3.stats.disk")}
                value={diskSize == null ? "…" : formatBytes(diskSize)}
                muted={diskSize == null || diskSize === 0}
              />
            </>
          )}
        </div>
        </div>
      </div>

      {/* ── Body: Main + Right-Rail ─────────────────────────────────────── */}
      <div className={`flex-1 min-h-0 flex border-t ${activeNavItem === "logs" ? "" : "overflow-hidden"}`} style={{ borderColor: SURFACE.line }}>
        <main className="flex-1 min-w-0 flex flex-col min-h-0">
          {contentNode}
        </main>

        <ProfileLeftRailV3
          profile={currentProfile}
          activeNavItem={activeNavItem}
          onNavChange={setActiveNavItem}
        />
      </div>

      {/* ── Modals ──────────────────────────────────────────────────────── */}
      {deleteDialog}

    </div>
  );
}
