//! Quelle für Pack-Definitionen und Standardprofile des Salami Client.
//!
//! Ersetzt die NoRisk-Endpunkte `launcher/modpacks-v3` und `launcher/versions-v3`:
//! Ist in [`crate::branding`] eine Remote-URL gesetzt, wird sie ohne Token
//! abgefragt; schlägt das fehl (oder ist keine gesetzt), gelten die
//! eingebetteten Dateien aus `packs/`. Der Launcher funktioniert damit auch
//! komplett offline.

use crate::branding;
use crate::config::HTTP_CLIENT;
use crate::error::{AppError, Result};
use crate::integrations::norisk_packs::{
    NoriskModEntryDefinition, NoriskModSourceDefinition, NoriskModpacksConfig,
};
use crate::integrations::norisk_versions::NoriskVersionsConfig;
use log::{info, warn};
use serde::de::DeserializeOwned;

/// Lädt die Pack-Definitionen (Remote, sonst eingebettet).
pub async fn load_packs() -> Result<NoriskModpacksConfig> {
    load(branding::PACKS_URL, branding::BUNDLED_PACKS_JSON, "packs").await
}

/// Lädt die Standardprofile (Remote, sonst eingebettet).
pub async fn load_versions() -> Result<NoriskVersionsConfig> {
    load(branding::VERSIONS_URL, branding::BUNDLED_VERSIONS_JSON, "versions").await
}

async fn load<T: DeserializeOwned>(remote: Option<&str>, bundled: &str, what: &str) -> Result<T> {
    if let Some(url) = remote {
        match fetch(url).await {
            Ok(value) => {
                info!("[Salami] Loaded {} from {}", what, url);
                return Ok(value);
            }
            Err(e) => warn!("[Salami] Could not load {} from {} ({}), using bundled copy", what, url, e),
        }
    }
    serde_json::from_str(bundled)
        .map_err(|e| AppError::ParseError(format!("Bundled Salami {} are invalid: {}", what, e)))
}

async fn fetch<T: DeserializeOwned>(url: &str) -> Result<T> {
    let response = HTTP_CLIENT
        .get(url)
        .send()
        .await
        .map_err(|e| AppError::RequestError(e.to_string()))?
        .error_for_status()
        .map_err(|e| AppError::RequestError(e.to_string()))?;
    response
        .json::<T>()
        .await
        .map_err(|e| AppError::ParseError(e.to_string()))
}

/// „NoRisk Client im Spiel“: Übernimmt die Mods aus NoRisks Pack [`branding::NORISK_INGAME_PACK`]
/// (den NoRisk-Mod `nrc-*` und seine Abhängigkeiten wie Fabric Language Kotlin) samt
/// NoRisk-Assets in jedes Salami-Pack. Mods, die das Salami-Pack schon hat (gleiche ID oder
/// gleiches Modrinth-Projekt), bleiben in der Salami-Version – sonst lädt Fabric sie doppelt.
pub fn with_norisk_ingame(
    mut salami: NoriskModpacksConfig,
    norisk: &NoriskModpacksConfig,
) -> Result<NoriskModpacksConfig> {
    let norisk_pack = norisk.get_resolved_pack_definition(branding::NORISK_INGAME_PACK)?;
    for (pack_id, pack) in salami.packs.iter_mut() {
        let mut added = 0;
        for norisk_mod in &norisk_pack.mods {
            if !pack.mods.iter().any(|own| same_mod(own, norisk_mod)) {
                pack.mods.push(norisk_mod.clone());
                added += 1;
            }
        }
        for asset in &norisk_pack.assets {
            if !pack.assets.contains(asset) {
                pack.assets.push(asset.clone());
            }
        }
        info!("[Salami] NoRisk Client: {} mods from '{}' added to '{}'", added, branding::NORISK_INGAME_PACK, pack_id);
    }
    for (name, url) in &norisk.repositories {
        salami.repositories.entry(name.clone()).or_insert_with(|| url.clone());
    }
    Ok(salami)
}

fn same_mod(a: &NoriskModEntryDefinition, b: &NoriskModEntryDefinition) -> bool {
    if a.id == b.id {
        return true;
    }
    match (&a.source, &b.source) {
        (
            NoriskModSourceDefinition::Modrinth { project_id: x, .. },
            NoriskModSourceDefinition::Modrinth { project_id: y, .. },
        ) => x == y,
        _ => false,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn norisk_config() -> NoriskModpacksConfig {
        serde_json::from_value(serde_json::json!({
            "packs": {
                "norisk-prod": {
                    "displayName": "Norisk Production",
                    "description": "",
                    "assets": ["norisk-prod"],
                    "mods": [
                        {
                            "id": "nrc-client",
                            "displayName": "nrc-client",
                            "source": { "type": "maven", "repositoryRef": "noriskproduction", "groupId": "gg.norisk", "artifactId": "nrc-client" },
                            "compatibility": { "1.21.11": { "fabric": { "identifier": "26.4.1-working+fabric.1.21.11", "filename": null, "source": null } } }
                        },
                        {
                            "id": "fabric-language-kotlin",
                            "displayName": "Fabric Language Kotlin",
                            "source": { "type": "modrinth", "projectId": "Ha28R6CL", "projectSlug": "fabric-language-kotlin" },
                            "compatibility": { "1.21.11": { "fabric": { "identifier": "ViT4gucI", "filename": null, "source": null } } }
                        },
                        {
                            "id": "waveycapes",
                            "displayName": "Wavey Capes",
                            "source": { "type": "modrinth", "projectId": "kYuIpRLv", "projectSlug": "wavey-capes" },
                            "compatibility": { "1.21.11": { "fabric": { "identifier": "lGSZdVwH", "filename": null, "source": null } } }
                        },
                        {
                            "id": "fabric-api",
                            "displayName": "Fabric API",
                            "source": { "type": "modrinth", "projectId": "P7dR8mSH", "projectSlug": "fabric-api" },
                            "compatibility": { "1.21.11": { "fabric": { "identifier": "i5tSkVBH", "filename": null, "source": null } } }
                        }
                    ]
                }
            },
            "repositories": {
                "noriskproduction": "https://maven.norisk.gg/repository/norisk-production/",
                "modrinth": "https://example.invalid/should-not-replace/"
            }
        }))
        .unwrap()
    }

    #[test]
    fn adds_norisk_mod_without_duplicates() {
        let mut salami: NoriskModpacksConfig = serde_json::from_str(branding::BUNDLED_PACKS_JSON).unwrap();
        salami.repositories.insert("modrinth".into(), "https://api.modrinth.com/maven/".into());
        let before = salami.packs["salami"].mods.len();

        let merged = with_norisk_ingame(salami, &norisk_config()).unwrap();
        let pack = &merged.packs["salami"];
        let ids: Vec<&str> = pack.mods.iter().map(|m| m.id.as_str()).collect();

        // neu: NoRisk-Mod + Kotlin; nicht doppelt: Wavey Capes (andere ID, gleiches Projekt) und Fabric API
        assert_eq!(pack.mods.len(), before + 2, "{:?}", ids);
        assert!(ids.contains(&"nrc-client") && ids.contains(&"fabric-language-kotlin"));
        assert!(!ids.contains(&"waveycapes"));
        assert_eq!(ids.iter().filter(|id| **id == "fabric-api").count(), 1);
        assert!(ids.contains(&"salami-core"));
        assert_eq!(pack.assets, vec!["norisk-prod".to_string()]);
        assert_eq!(
            merged.repositories["noriskproduction"],
            "https://maven.norisk.gg/repository/norisk-production/"
        );
        assert_eq!(merged.repositories["modrinth"], "https://api.modrinth.com/maven/");
    }

    #[test]
    fn missing_norisk_pack_is_an_error() {
        let salami: NoriskModpacksConfig = serde_json::from_str(branding::BUNDLED_PACKS_JSON).unwrap();
        let mut norisk = norisk_config();
        norisk.packs.clear();
        assert!(with_norisk_ingame(salami, &norisk).is_err());
    }
}
