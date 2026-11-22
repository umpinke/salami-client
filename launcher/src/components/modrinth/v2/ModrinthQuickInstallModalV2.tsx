"use client";

import React, { useState, useEffect } from 'react';
import type {
  ModrinthSearchHit,
  ModrinthVersion,
} from '../../../types/modrinth';
import type { UnifiedVersion } from '../../../types/unified';
import { Icon } from '@iconify/react';
import { Modal } from '../../ui/Modal';
import { Button } from '../../ui/buttons/Button';
import { useThemeStore } from '../../../store/useThemeStore'; // For accent color on Installed button
import { cn } from '../../../lib/utils';
import { ModrinthQuickProfile } from './ModrinthQuickProfile'; // New import
import { LoadingSpinner } from '../../ui/LoadingSpinner';
import { EmptyState } from '../../ui/EmptyState';
import { hexAlpha, SURFACE } from '../../ui/design-system';
import { KanteButton, PLAIN_CARD } from './FeinkostParts';
// Potentially import Profile type if available and not 'any'
// import type { Profile } from '../../../types/profile';
import { toast } from 'react-hot-toast';
import { useTranslation } from 'react-i18next';

interface ModrinthQuickInstallModalV2Props {
  isOpen: boolean;
  onClose: () => void;
  project: ModrinthSearchHit | null;
  versions: UnifiedVersion[] | null;
  isLoading: boolean;
  error: string | null;
  profiles: any[]; // Replace 'any' with actual Profile type
  selectedProfileId?: string | null; // For highlighting
  installStatus: Record<string, boolean>;
  installingProfiles: Record<string, boolean>;
  onInstallToProfile: (profileId: string) => void;
  onUninstallClick?: (profileId: string, project: ModrinthSearchHit, version: UnifiedVersion) => Promise<void>;
  findBestVersionForProfile: (
    profile: any, // Replace 'any' with Profile type
    versions: UnifiedVersion[],
  ) => UnifiedVersion | null;
  onInstallToNewProfile: (
    profileName: string,
    project: ModrinthSearchHit,
    version: UnifiedVersion,
    sourceProfileIdToCopy: string | null, // Added
  ) => Promise<void>;
}

export const ModrinthQuickInstallModalV2: React.FC<ModrinthQuickInstallModalV2Props> = ({
  isOpen,
  onClose,
  project,
  versions,
  isLoading,
  error,
  profiles,
  selectedProfileId,
  installStatus,
  installingProfiles,
  onInstallToProfile,
  onUninstallClick,
  findBestVersionForProfile,
  onInstallToNewProfile,
}) => {
  const { t } = useTranslation();
  const accentColor = useThemeStore((state) => state.accentColor); // Moved hook to the top
  const [showQuickProfileView, setShowQuickProfileView] = useState(false);
  const [isCreatingProfile, setIsCreatingProfile] = useState(false);
  const [quickProfileName, setQuickProfileName] = useState('');
  const [quickProfileError, setQuickProfileError] = useState<string | null>(null);
  const [creationResult, setCreationResult] = useState<'success' | 'error' | null>(null); // New state
  const [lastCreationErrorMessage, setLastCreationErrorMessage] = useState<string | null>(null); // New state for creation error
  const [sourceProfileToCopyId, setSourceProfileToCopyId] = useState<string | null>(null); // New state for copy

  useEffect(() => {
    if (!isOpen) {
      setShowQuickProfileView(false);
      setIsCreatingProfile(false);
      setQuickProfileName('');
      setQuickProfileError(null);
      setCreationResult(null); // Reset on close
      setLastCreationErrorMessage(null); // Reset on close
      setSourceProfileToCopyId(null); // Reset on close
    }
  }, [isOpen]);

  if (!isOpen || !project) return null;

  const switchToQuickProfileView = () => {
    setQuickProfileName(''); // Ensure name is empty when switching
    setQuickProfileError(null);
    setCreationResult(null); // Clear previous result
    setSourceProfileToCopyId(null); // Reset for fresh start
    setShowQuickProfileView(true);
  };

  const switchToProfileListView = () => {
    setShowQuickProfileView(false);
    setQuickProfileName('');
    setQuickProfileError(null);
    setCreationResult(null); // Clear previous result
  };

  const handleCreateAndInstallProfile = async () => {
    if (!quickProfileName.trim()) {
      setQuickProfileError(t('modrinth.profile_name_empty'));
      return;
    }
    setQuickProfileError(null);
    setLastCreationErrorMessage(null);

    if (!project || !versions || versions.length === 0) { // Guard clause
        toast.error(t('modrinth.errors.missing_data'));
        setCreationResult('error'); // Set error state if essential data is missing
        setLastCreationErrorMessage(t('modrinth.errors.missing_data'));
        return;
    }
    const versionToInstall = versions[0];
    if (!versionToInstall) {
        toast.error(t('modrinth.errors.no_suitable_version'));
        setCreationResult('error');
        setLastCreationErrorMessage(t('modrinth.errors.no_suitable_version'));
        return;
    }

    setIsCreatingProfile(true);
    try {
      await onInstallToNewProfile(quickProfileName.trim(), project, versionToInstall, sourceProfileToCopyId);
      setCreationResult('success');
    } catch (err: any) {
      console.error("Error in handleCreateAndInstallProfile (quick install):", err);
      setCreationResult('error');
      setLastCreationErrorMessage(err?.message || t('modrinth.unknown_error'));
    } finally {
      setIsCreatingProfile(false);
    }
  };

  const modalContent = (
    <>
      {showQuickProfileView && project ? (
        <div className="px-6 py-2 sm:py-4">
          <ModrinthQuickProfile
            accentColor={accentColor}
            projectTitle={project.title}
            versionNumber={versions && versions.length > 0 ? versions[0].version_number : undefined}
            profileName={quickProfileName}
            onProfileNameChange={(name) => {
              setQuickProfileName(name);
              if (quickProfileError && name.trim()) setQuickProfileError(null);
            }}
            error={quickProfileError}
            isLoading={isCreatingProfile}
            selectedSourceProfileId={sourceProfileToCopyId}
            onSourceProfileChange={setSourceProfileToCopyId}
          />
        </div>
      ) : isLoading ? (
        <div className="flex justify-center items-center py-8">
          <LoadingSpinner size="md" message={t('modrinth.finding_compatible_versions')} />
        </div>
      ) : error ? (
        <EmptyState icon="solar:danger-triangle-bold" message={error} compact fullHeight={false} />
      ) : versions && versions.length > 0 ? (
        <div className="px-6 py-4">
          <p className="text-white/70 mb-4 text-xs">
            {t('modrinth.quick_install_description')}
          </p>

          <div className="mb-1">
            <div 
              className="look-card max-h-60 overflow-y-auto custom-scrollbar"
              style={PLAIN_CARD}
            >
              {profiles.length > 0 ? (
                [...profiles]
                  .sort((a, b) => {
                    if (a.id === selectedProfileId) return -1;
                    if (b.id === selectedProfileId) return 1;
                    return 0;
                  })
                  .map((profile) => {
                    const bestVersion = findBestVersionForProfile(
                      profile,
                      versions,
                    );
                    const isCompatible = !!bestVersion;

                    return (
                      <div
                        key={profile.id}
                        className={cn(
                          "p-3 border-b last:border-b-0 flex justify-between items-center hover:bg-white/[0.06] transition-colors duration-150"
                        )}
                        style={{
                          backgroundColor: profile.id === selectedProfileId ? hexAlpha(accentColor.value, 0.16) : undefined,
                          borderBottomColor: SURFACE.line,
                        }}
                      >
                        <div className="text-white flex-grow mr-3 min-w-0">
                          <span className="font-medium text-base font-minecraft truncate">{profile.name}</span>
                          {profile.id === selectedProfileId && (
                            <span className="ml-2 text-xs text-[#f3e3d3]">
                              ({t('modrinth.current_profile')})
                            </span>
                          )}
                          {isCompatible && bestVersion && (
                            <div className="text-xs text-white/50 mt-0.5 truncate">
                              {t('modrinth.version_label', { version: bestVersion.version_number })}
                            </div>
                          )}
                        </div>

                        {installingProfiles[profile.id] ? (
                          <KanteButton
                            size="sm"
                            disabled
                            className="cursor-wait"
                            icon={<Icon icon="svg-spinners:ring-resize" className="w-3.5 h-3.5" />}
                          >
                            {t('modrinth.installing')}
                          </KanteButton>
                        ) : installStatus[profile.id] && bestVersion && onUninstallClick ? (
                          <KanteButton
                            onClick={async () => {
                              if (project && bestVersion) {
                                try {
                                  await onUninstallClick(profile.id, project, bestVersion);
                                } catch (err) {
                                  console.error("Uninstall from quick modal failed:", err);
                                }
                              }
                            }}
                            size="sm"
                            tone="danger"
                            icon={<Icon icon="solar:trash-bin-trash-bold" className="w-3.5 h-3.5" />}
                          >
                            {t('common.uninstall')}
                          </KanteButton>
                        ) : !isCompatible ? (
                          <KanteButton
                            size="sm"
                            disabled
                            icon={<Icon icon="solar:danger-bold" className="w-3.5 h-3.5" />}
                          >
                            {t('modrinth.not_compatible')}
                          </KanteButton>
                        ) : (
                          <KanteButton
                            onClick={() => onInstallToProfile(profile.id)}
                            disabled={installingProfiles[profile.id]}
                            size="sm"
                            tone="primary"
                            icon={<Icon icon="solar:download-minimalistic-bold" className="w-3.5 h-3.5" />}
                          >
                            {t('modrinth.install')}
                          </KanteButton>
                        )}
                      </div>
                    );
                  })
              ) : (
                <p className="p-4 text-center text-white/50">
                  {t('modrinth.no_profiles_available')}
                </p>
              )}
            </div>
          </div>
        </div>
      ) : (
        <div className="text-white/60 text-center py-6 px-6">
          {t('modrinth.no_versions_found')}
        </div>
      )}
    </>
  );

  const handleModalClose = () => {
    setShowQuickProfileView(false); 
    setIsCreatingProfile(false);
    setQuickProfileName('');
    setQuickProfileError(null);
    setCreationResult(null); // Reset creation result
    setLastCreationErrorMessage(null);
    setSourceProfileToCopyId(null);
    onClose();
  };

  let modalContentLayout;

  if (creationResult === 'success') {
    modalContentLayout = (
      <EmptyState
        icon="solar:check-circle-bold"
        compact
        fullHeight={false}
        smallDescription
        message={sourceProfileToCopyId ? t('modrinth.profile_copied_installed') : t('modrinth.profile_created_installed')}
        description={
          t('modrinth.install_success_message', { title: project.title, version: versions?.[0]?.version_number || 'latest', profile: quickProfileName.trim() }) +
          (sourceProfileToCopyId && profiles.find(p => p.id === sourceProfileToCopyId)
            ? ` ${t('modrinth.settings_copied_from', { source: profiles.find(p => p.id === sourceProfileToCopyId)?.name })}`
            : '')
        }
      />
    );
  } else if (creationResult === 'error') {
    modalContentLayout = (
      <EmptyState
        icon="solar:close-circle-bold"
        compact
        fullHeight={false}
        smallDescription
        message={t('modrinth.operation_failed')}
        description={lastCreationErrorMessage || t('modrinth.error_creating_or_installing')}
      />
    );
  } else {
    modalContentLayout = modalContent;
  }

  return (
    <Modal
      title={
        creationResult ?
          (creationResult === 'success' ? t('modrinth.operation_successful') : t('modrinth.operation_failed')) :
        showQuickProfileView ?
          (sourceProfileToCopyId ? t('modrinth.copy_profile_install_title', { title: project.title }) : t('modrinth.new_profile_for', { title: project.title })) :
          t('modrinth.quick_install_title', { title: project.title })
      }
      titleIcon={
        creationResult ? 
          (creationResult === 'success' ? <Icon icon="solar:check-circle-bold-duotone" className="w-5 h-5 mr-2" /> : <Icon icon="solar:close-circle-bold-duotone" className="w-5 h-5 mr-2" />) :
        showQuickProfileView ? 
          (sourceProfileToCopyId ? <Icon icon="solar:copy-bold-duotone" className="w-5 h-5 mr-2" /> : <Icon icon="solar:folder-with-files-bold-duotone" className="w-5 h-5 mr-2" />) : 
          <Icon icon="solar:bolt-bold-duotone" className="w-5 h-5" />
      }
      onClose={handleModalClose}
      width="md"
      footer={
        <div className="flex justify-between items-center w-full">
          {creationResult ? (
            <div className="w-full flex justify-end">
              <Button onClick={handleModalClose} variant="secondary" shadowDepth="short">
                {t('common.close')}
              </Button>
            </div>
          ) : showQuickProfileView ? (
            <>
              <Button
                variant="secondary"
                onClick={switchToProfileListView}
                disabled={isCreatingProfile}
                shadowDepth="short"
                size="sm"
                icon={<Icon icon="solar:arrow-left-linear" className="w-4 h-4" />}
                className="mr-auto"
              >
                {t('modrinth.back_to_profiles')}
              </Button>
              <KanteButton
                tone="primary"
                size="lg"
                onClick={handleCreateAndInstallProfile}
                disabled={isCreatingProfile || !quickProfileName.trim()}
                icon={isCreatingProfile ? <Icon icon="line-md:loading-twotone-loop" className="w-4 h-4" /> : <Icon icon="solar:ssd-round-bold-duotone" className="w-4 h-4" />}
              >
                {isCreatingProfile ? (sourceProfileToCopyId ? t('modrinth.copying') : t('modrinth.creating')) : (sourceProfileToCopyId ? t('modrinth.copy_and_install') : t('modrinth.create_and_install'))}
              </KanteButton>
            </>
          ) : (
            // Footer for profile list view (or initial loading/error states)
            <>
              { !isLoading && !error && versions && versions.length > 0 ? (
                <Button
                  variant="secondary"
                  onClick={switchToQuickProfileView}
                  icon={<Icon icon="solar:add-folder-line-duotone" className="w-4 h-4 mr-1" />}
                  shadowDepth="short"
                  size="sm"
                  className="mr-auto"
                >
                  {t('modrinth.new_profile')}
                </Button>
              ) : <div className="mr-auto"></div> }
              <Button
                onClick={handleModalClose}
                variant="secondary"
                shadowDepth="short"
                size="sm"
              >
                {t('common.close')}
              </Button>
            </>
          )}
        </div>
      }
    >
      <div className="px-6 py-4">
        {modalContentLayout}
      </div>
    </Modal>
  );
}; 