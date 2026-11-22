"use client";

import React, { useEffect, useState } from "react";
import { Icon } from "@iconify/react";
import { toast } from "react-hot-toast";
import { useTranslation } from "react-i18next";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import type { UnifiedProjectDetails, UnifiedVersion, UnifiedModSearchResult } from "../../types/unified";
import { ModPlatform } from "../../types/unified";
import type { AccentColor } from "../../store/useThemeStore";
import type { Profile } from "../../types/profile";
import { ProgressToast } from "../ui/ProgressToast";
import { kopfband, KANTE_SOFT } from "../ui/design-system";
import { DeliLabel } from "../../themes/salami/SalamiStage";
import { ICON_WELL, isLoaderTag, KanteButton, PLAIN_CARD, projectTypeKey, QuietTag, tagLabel } from "../modrinth/v2/FeinkostParts";
import { openExternalUrl } from "../../services/tauri-service";
import { useProfileStore } from "../../store/profile-store";
import { useGlobalModalStore } from "../../hooks/useGlobalModal";
import { ModrinthQuickInstallProfilesModal } from "../modrinth/v2/ModrinthQuickInstallProfilesModal";
import UnifiedService from "../../services/unified-service";
import { ModrinthService } from "../../services/modrinth-service";
import { CurseForgeService } from "../../services/curseforge-service";
import { installContentToProfile, installContentToTarget, shouldReportInstallError } from "../../services/content-service";
import { errorText } from "../../utils/error-text";
import { ContentType, type ContentInstallTarget, type InstallContentPayload } from "../../types/content";
import { EventType, type EventPayload } from "../../types/events";
import { useNavigate } from "react-router-dom";
import { findBestVersionFor } from '../../features/mod-downloader/version-selection';

interface ModDetailHeaderProps {
  project: UnifiedProjectDetails;
  accentColor: AccentColor;
  showVersions: boolean;
  onToggleVersions: () => void;
  targetProfile?: Profile;
  installTarget?: ContentInstallTarget;
}

function formatNumber(num: number): string {
  if (num >= 1000000) {
    return `${(num / 1000000).toFixed(1)}M`;
  }
  if (num >= 1000) {
    return `${(num / 1000).toFixed(1)}K`;
  }
  return num.toLocaleString();
}

function getProjectTypeIcon(type: string): string {
  switch (type) {
    case "mod": return "pixel:bolt-solid";
    case "modpack": return "pixel:folder-open-solid";
    case "resourcepack": return "pixel:image-solid";
    case "shader": return "pixel:sun-solid";
    case "datapack": return "pixel:archive-solid";
    default: return "pixel:archive-solid";
  }
}

// Helper to map project type to ContentType
function mapProjectTypeToContentType(projectType: string): ContentType | null {
  switch (projectType.toLowerCase()) {
    case 'mod':
      return ContentType.Mod;
    case 'resourcepack':
      return ContentType.ResourcePack;
    case 'shader':
      return ContentType.ShaderPack;
    case 'datapack':
      return ContentType.DataPack;
    case 'modpack':
      return null; // Modpacks are handled differently
    default:
      return null;
  }
}

// Salami: gemeinsame, strikte Versionswahl (features/mod-downloader/version-selection.ts)
function findBestVersionForProfile(profile: Profile, versions: UnifiedVersion[]): UnifiedVersion | null {
  return findBestVersionFor(profile, versions);
}

export function ModDetailHeader({ project, accentColor, showVersions, onToggleVersions, targetProfile, installTarget }: ModDetailHeaderProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { profiles, fetchProfiles } = useProfileStore();
  const showModal = useGlobalModalStore(state => state.openModal);
  const hideModal = useGlobalModalStore(state => state.closeModal);
  const [isInstalling, setIsInstalling] = useState(false);
  const [installingProfiles, setInstallingProfiles] = useState<Record<string, boolean>>({});
  const [installStatus, setInstallStatus] = useState<Record<string, boolean>>({});
  const [installModalVersions, setInstallModalVersions] = useState<UnifiedVersion[] | null>(null);

  // Check if this is a modpack
  const isModpack = project.project_type.toLowerCase() === 'modpack';

  const handleOpenProjectPage = async () => {
    try {
      await openExternalUrl(project.project_url);
    } catch (error) {
      console.error("Failed to open URL:", error);
      toast.error(t('common.open_link_failed'));
    }
  };

  // Convert UnifiedProjectDetails to format expected by modal
  const projectAsSearchResult: UnifiedModSearchResult = {
    project_id: project.id,
    source: project.source,
    title: project.title,
    slug: project.slug,
    description: project.description,
    author: project.author,
    categories: project.categories,
    display_categories: project.categories,
    downloads: project.downloads,
    follows: project.followers,
    icon_url: project.icon_url,
    project_url: project.project_url,
    project_type: project.project_type,
    gallery: project.gallery.map(g => g.url),
  };

  // Handle modpack installation (creates a new profile)
  const handleModpackInstall = async () => {
    const eventId = crypto.randomUUID();
    const toastId = `install-${eventId}`;
    let progressUnlisten: UnlistenFn | null = null;

    setIsInstalling(true);
    toast.loading(t('mod_detail.fetching_versions', { title: project.title }), { id: toastId });

    try {
      const response = await UnifiedService.getModVersions({
        source: project.source,
        project_id: project.id,
      });
      const allVersions = response.versions;

      if (!allVersions || allVersions.length === 0) {
        throw new Error(t('mod_detail.no_versions_found'));
      }

      // Sort all versions by date published, newest first
      const sortedVersions = allVersions.sort((a, b) =>
        new Date(b.date_published).getTime() - new Date(a.date_published).getTime()
      );

      // Try to find the latest 'release' version
      let latestVersion = sortedVersions.find(v => v.release_type === 'release');
      if (!latestVersion) {
        latestVersion = sortedVersions[0];
      }

      if (!latestVersion?.files?.length) {
        throw new Error(t('mod_detail.no_files_in_version'));
      }

      const primaryFile = latestVersion.files.find(f => f.primary) || latestVersion.files[0];
      if (!primaryFile) {
        throw new Error(t('mod_detail.no_primary_file'));
      }

      const fileName = primaryFile.filename || project.title || "modpack";

      // Set up event listener for progress updates
      progressUnlisten = await listen<EventPayload>("state_event", (progressEvent) => {
        const progressPayload = progressEvent.payload;
        if (progressPayload.event_type !== EventType.TaskProgress) return;
        if (progressPayload.event_id !== eventId) return;

        const progress = (progressPayload.progress ?? 0) * 100; // Convert 0-1 to 0-100

        // Update toast with progress
        toast.custom(
          () => <ProgressToast message={t('content.install.installing_file', { name: fileName })} progress={progress} />,
          { id: toastId, duration: Infinity }
        );
      });

      // Show initial progress toast
      toast.custom(
        () => <ProgressToast message={t('content.install.installing_file', { name: fileName })} progress={0} />,
        { id: toastId, duration: Infinity }
      );

      let newProfileId: string;

      if (project.source === ModPlatform.CurseForge) {
        const projectId = parseInt(project.id);
        const fileId = parseInt(latestVersion.id);

        if (isNaN(projectId) || isNaN(fileId)) {
          throw new Error(t('mod_detail.invalid_curseforge_ids'));
        }

        newProfileId = await CurseForgeService.downloadAndInstallCurseForgeModpack(
          projectId,
          fileId,
          primaryFile.filename,
          primaryFile.url,
          project.icon_url || undefined,
          primaryFile.size,
          eventId
        );
      } else {
        newProfileId = await ModrinthService.downloadAndInstallModpack(
          project.id,
          latestVersion.id,
          primaryFile.filename,
          primaryFile.url,
          project.icon_url || undefined,
          primaryFile.size,
          eventId
        );
      }

      // Clean up listener before showing success
      if (progressUnlisten) {
        progressUnlisten();
        progressUnlisten = null;
      }

      toast.success(t('mod_detail.installed_as_profile', { title: project.title }), { id: toastId, duration: 3000 });

      // Refresh profiles and navigate
      await fetchProfiles();
      navigate(`/profilesv2/${newProfileId}`);

    } catch (error: any) {
      console.error("Modpack installation failed:", error);
      toast.error(t('mod_detail.modpack_install_failed', { error: error.message || error }), { id: toastId });
    } finally {
      // Clean up listener
      if (progressUnlisten) {
        progressUnlisten();
      }
      setIsInstalling(false);
    }
  };

  const installVersionToProfile = async (profile: Profile, bestVersion: UnifiedVersion) => {
    setInstallingProfiles(prev => ({ ...prev, [profile.id]: true }));
    try {
      const primaryFile = bestVersion.files.find(f => f.primary) || bestVersion.files[0];
      if (!primaryFile) {
        toast.error(t('mod_detail.no_download_file'));
        return;
      }

      const contentType = mapProjectTypeToContentType(project.project_type);
      if (!contentType) {
        toast.error(t('mod_detail.cannot_install_type', { type: project.project_type }));
        return;
      }

      const payload: InstallContentPayload = {
        profile_id: profile.id,
        project_id: project.id,
        version_id: bestVersion.id,
        file_name: primaryFile.filename,
        download_url: primaryFile.url,
        file_hash_sha1: primaryFile.hashes?.sha1,
        file_fingerprint: primaryFile.fingerprint,
        content_name: project.title,
        version_number: bestVersion.version_number,
        content_type: contentType,
        loaders: bestVersion.loaders,
        game_versions: bestVersion.game_versions,
        source: project.source,
      };

      await installContentToTarget(payload, installTarget);
      toast.success(t('mod_detail.installed_to_profile', { title: project.title, version: bestVersion.version_number, profile: installTarget?.type === 'syncPack' ? installTarget.packName : profile.name }));
      setInstallStatus(prev => ({ ...prev, [profile.id]: true }));
    } catch (error) {
      console.error("Installation failed:", error);
      if (shouldReportInstallError(error)) toast.error(t('mod_detail.install_failed', { error: errorText(error) }));
    } finally {
      setInstallingProfiles(prev => ({ ...prev, [profile.id]: false }));
    }
  };

  const handleInstallClick = async () => {
    if (isModpack) {
      handleModpackInstall();
      return;
    }

    setIsInstalling(true);

    try {
      const response = await UnifiedService.getModVersions({
        source: project.source,
        project_id: project.id,
      });

      if (response.versions.length === 0) {
        toast.error(t('mod_detail.no_versions_available'));
        return;
      }

      if (targetProfile) {
        const bestVersion = findBestVersionForProfile(targetProfile, response.versions);
        if (!bestVersion) {
          toast.error(t('mod_detail.no_compatible_version', { profile: targetProfile.name }));
          return;
        }
        await installVersionToProfile(targetProfile, bestVersion);
        return;
      }

      setInstallModalVersions(response.versions);
    } catch (error) {
      console.error("Failed to fetch versions:", error);
      toast.error(t('mod_detail.load_versions_failed'));
    } finally {
      setIsInstalling(false);
    }
  };

  useEffect(() => {
    if (!installModalVersions) return;

    const versions = installModalVersions;
    const modalId = `install-${project.id}`;

    const handleProfileSelect = async (_: any, profile: Profile) => {
      const bestVersion = findBestVersionForProfile(profile, versions);
      if (!bestVersion) {
        toast.error(t('mod_detail.no_compatible_version', { profile: profile.name }));
        return;
      }

      setInstallingProfiles(prev => ({ ...prev, [profile.id]: true }));

      try {
        const primaryFile = bestVersion.files.find(f => f.primary) || bestVersion.files[0];
        if (!primaryFile) {
          toast.error(t('mod_detail.no_download_file'));
          return;
        }

        const contentType = mapProjectTypeToContentType(project.project_type);
        if (!contentType) {
          toast.error(t('mod_detail.cannot_install_type', { type: project.project_type }));
          return;
        }

        const payload: InstallContentPayload = {
          profile_id: profile.id,
          project_id: project.id,
          version_id: bestVersion.id,
          file_name: primaryFile.filename,
          download_url: primaryFile.url,
          file_hash_sha1: primaryFile.hashes?.sha1,
          file_fingerprint: primaryFile.fingerprint,
          content_name: project.title,
          version_number: bestVersion.version_number,
          content_type: contentType,
          loaders: bestVersion.loaders,
          game_versions: bestVersion.game_versions,
          source: project.source,
        };

        await installContentToProfile(payload);
        toast.success(t('mod_detail.installed_to_profile', { title: project.title, version: bestVersion.version_number, profile: profile.name }));
        setInstallStatus(prev => ({ ...prev, [profile.id]: true }));
      } catch (error) {
        console.error("Installation failed:", error);
        if (shouldReportInstallError(error)) toast.error(t('mod_detail.install_failed', { error: errorText(error) }));
      } finally {
        setInstallingProfiles(prev => ({ ...prev, [profile.id]: false }));
      }
    };

    showModal(
      modalId,
      <ModrinthQuickInstallProfilesModal
        project={projectAsSearchResult as any}
        profiles={profiles}
        onProfileSelect={handleProfileSelect}
        onClose={() => {
          hideModal(modalId);
          setInstallModalVersions(null);
          setIsInstalling(false);
          setInstallingProfiles({});
          setInstallStatus({});
        }}
        installingProfiles={installingProfiles}
        installStatus={installStatus}
      />,
      1200
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [installModalVersions, installingProfiles, installStatus, profiles, project.id]);

  const typeKey = projectTypeKey(project.project_type);
  const categoryTags = project.categories.filter((c) => !isLoaderTag(c));
  const loaderTags = project.categories.filter((c) => isLoaderTag(c));

  return (
    // Feinkost-Kopf: Karte mit Kopfband im Salami-Anschnitt, das Projektbild ragt ins Band
    <div className="look-card look-mod-header relative overflow-hidden" style={{ ...PLAIN_CARD, boxShadow: KANTE_SOFT }}>
      <div
        className="salami-fat-spots relative flex h-16 items-start justify-end gap-4 px-4 pt-3"
        style={kopfband(accentColor.value)}
      >
        {/* Kennzahlen auf dem Band */}
        <div className="flex items-center gap-4 font-minecraft text-sm text-[#f3e3d3]">
          <span className="flex items-center gap-1.5" title={project.downloads.toLocaleString()}>
            <Icon icon="solar:download-minimalistic-bold" className="h-4 w-4" />
            {formatNumber(project.downloads)}
          </span>
          <span className="flex items-center gap-1.5" title={project.followers.toLocaleString()}>
            <Icon icon="solar:heart-bold" className="h-4 w-4" />
            {formatNumber(project.followers)}
          </span>
        </div>
      </div>

      <div className="flex gap-5 px-5 pb-5">
        {/* Project Icon */}
        <div
          className="relative z-10 -mt-10 h-28 w-28 flex-shrink-0 self-start overflow-hidden border-[3px]"
          style={{
            borderColor: "#f3e3d3",
            backgroundColor: ICON_WELL,
            borderRadius: "calc(var(--border-radius) * 0.9)",
            boxShadow: "0 3px 0 rgba(0,0,0,0.35)",
          }}
        >
          {project.icon_url ? (
            <img
              src={project.icon_url}
              alt={`${project.title} icon`}
              className="w-full h-full object-cover"
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center">
              <Icon icon={getProjectTypeIcon(project.project_type)} className="w-12 h-12 text-[#f3e3d3]/40" />
            </div>
          )}
        </div>

        {/* Project Info */}
        <div className="flex-1 min-w-0 flex flex-col pt-3">
          {/* Title Row */}
          <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-3">
            <div className="min-w-0">
              <button
                onClick={handleOpenProjectPage}
                className="text-left font-minecraft text-3xl leading-tight text-white transition-colors hover:underline"
                style={{ textDecorationColor: accentColor.value }}
              >
                {project.title}
              </button>
              {project.author && (
                <p className="mt-1 text-sm text-white/55">
                  {t('content.by_creator', { creator: project.author })}
                </p>
              )}
            </div>

            {/* Install Button + Versions Toggle */}
            <div className="flex flex-shrink-0 items-center gap-2">
              <KanteButton
                tone={isInstalling ? "secondary" : "primary"}
                size="lg"
                disabled={isInstalling}
                onClick={(e) => {
                  e.stopPropagation();
                  handleInstallClick();
                }}
                icon={
                  <Icon
                    icon={isInstalling ? "solar:refresh-bold" : "solar:download-minimalistic-bold"}
                    className={isInstalling ? "h-5 w-5 animate-spin-slow" : "h-5 w-5"}
                  />
                }
              >
                {isInstalling ? t('modrinth.installing') : t('modrinth.install')}
              </KanteButton>
              <KanteButton
                size="lg"
                active={showVersions}
                onClick={(e) => {
                  e.stopPropagation();
                  onToggleVersions();
                }}
                title={showVersions ? t('modrinth.hide_versions') : t('modrinth.show_versions')}
                icon={<Icon icon="solar:list-bold" className="h-5 w-5" />}
              >
                {showVersions ? t('modrinth.hide_versions') : t('modrinth.show_versions')}
              </KanteButton>
            </div>
          </div>

          {/* Description */}
          <p className="mt-3 line-clamp-2 text-sm text-white/75">
            {project.description}
          </p>

          {/* Meta Row: Quelle und Typ ruhig, Loader und Kategorien als Theken-Etiketten */}
          <div className="mt-4 flex flex-wrap items-center gap-1.5">
            <QuietTag>
              <Icon
                icon={project.source === ModPlatform.Modrinth ? "simple-icons:modrinth" : "simple-icons:curseforge"}
                className="h-3 w-3"
              />
              {project.source}
            </QuietTag>
            <QuietTag>
              <Icon icon={getProjectTypeIcon(project.project_type)} className="h-3 w-3" />
              {typeKey ? t(typeKey) : project.project_type}
            </QuietTag>

            {loaderTags.map((loader) => (
              <DeliLabel key={loader} size="sm" tone="red">{tagLabel(loader)}</DeliLabel>
            ))}
            {categoryTags.slice(0, 4).map((category) => (
              <DeliLabel key={category} size="sm">{tagLabel(category)}</DeliLabel>
            ))}
            {categoryTags.length > 4 && (
              <span className="text-xs text-white/50 font-minecraft">
                {t('mod_detail.more_categories', { count: categoryTags.length - 4 })}
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
