use directories::ProjectDirs;
use once_cell::sync::Lazy;
use reqwest::Client;
use std::path::PathBuf;
use std::sync::RwLock;
use std::time::Duration;

pub static LAUNCHER_DIRECTORY: Lazy<ProjectDirs> =
    Lazy::new(
        || match ProjectDirs::from(
            crate::branding::DIRS_QUALIFIER,
            crate::branding::DIRS_ORGANIZATION,
            crate::branding::DIRS_APPLICATION,
        ) {
            Some(proj_dirs) => proj_dirs,
            None => panic!("Failed to get application directory"),
        },
    );

pub static CUSTOM_GAME_DIR_CACHE: Lazy<RwLock<Option<Option<PathBuf>>>> = 
    Lazy::new(|| RwLock::new(None));

/// Wird bei allen Anfragen mitgeschickt (Modrinth & Co. wünschen Name, Version und Kontakt).
static APP_USER_AGENT: &str = concat!(
    "SalamiClient/",
    env!("CARGO_PKG_VERSION"),
    " (+https://github.com/umpinke/salami-client)"
);

pub const HTTP_CONNECT_TIMEOUT: Duration = Duration::from_secs(10);

/// Namensauflösung mit Sperre: Server, die `branding::is_blocked_host` sperrt (NoRisk), sind
/// für alle Anfragen über diese Clients unerreichbar – egal, welche Stelle im Launcher sie baut.
pub struct BlockingResolver;

impl reqwest::dns::Resolve for BlockingResolver {
    fn resolve(&self, name: reqwest::dns::Name) -> reqwest::dns::Resolving {
        let host = name.as_str().to_string();
        Box::pin(async move {
            if crate::branding::is_blocked_host(Some(&host)) {
                return Err(format!("{} is blocked in {}", host, crate::branding::PRODUCT_NAME).into());
            }
            let addrs: Vec<std::net::SocketAddr> = tokio::net::lookup_host((host.as_str(), 0)).await?.collect();
            Ok(Box::new(addrs.into_iter()) as reqwest::dns::Addrs)
        })
    }
}

/// Basis für alle HTTP-Clients des Launchers: User-Agent und gesperrte Hosts.
pub fn http_client_builder() -> reqwest::ClientBuilder {
    reqwest::ClientBuilder::new()
        .user_agent(APP_USER_AGENT)
        .dns_resolver(std::sync::Arc::new(BlockingResolver))
}

/// HTTP Client with launcher agent
pub static HTTP_CLIENT: Lazy<Client> = Lazy::new(|| {
    http_client_builder()
        .connect_timeout(HTTP_CONNECT_TIMEOUT)
        .build()
        .expect("Failed to build HTTP client")
});

// Extension trait for ProjectDirs to add meta_dir functionality
pub trait ProjectDirsExt {
    fn meta_dir(&self) -> PathBuf;
    fn root_dir(&self) -> PathBuf;
}

impl ProjectDirsExt for ProjectDirs {
    fn meta_dir(&self) -> PathBuf {
        // Check cache first
        if let Ok(guard) = CUSTOM_GAME_DIR_CACHE.read() {
            if let Some(cached_value) = guard.as_ref() {
                if let Some(custom_dir) = cached_value {
                    return custom_dir.clone();
                }
            }
        }
        
        // Fallback to standard logic
        standard_meta_dir()
    }

    fn root_dir(&self) -> PathBuf {
        if cfg!(target_os = "windows") {
            // Windows: Alte Logik (wie sie war)
            self.data_dir().parent().unwrap().to_path_buf()
        } else {
            // macOS (und andere): Setze root_dir auf data_dir
            self.data_dir().to_path_buf()
        }
    }
}

/// Returns the standard meta directory (ignores custom directory setting)
/// Used for Java and other system components that need to stay in standard location due to macos...x
pub fn standard_meta_dir() -> PathBuf {
    if cfg!(target_os = "windows") {
        LAUNCHER_DIRECTORY.data_dir().parent().unwrap().join("meta")
    } else {
        LAUNCHER_DIRECTORY.data_dir().join("meta")
    }
}

/// Update the cached custom game directory
pub fn update_custom_game_dir(path: Option<PathBuf>) {
    if let Ok(mut guard) = CUSTOM_GAME_DIR_CACHE.write() {
        *guard = Some(path);
    }
}

#[cfg(test)]
mod blocking_resolver_tests {
    use super::HTTP_CLIENT;

    #[tokio::test]
    async fn norisk_hosts_are_unreachable() {
        for url in ["https://api.norisk.gg/api/v1/launcher/modpacks-v3", "https://cdn.norisk.gg/x.png"] {
            let err = HTTP_CLIENT.get(url).send().await.expect_err("request must fail");
            let text = format!("{:?}", err);
            assert!(text.contains("blocked"), "{}: {}", url, text);
        }
    }
}
