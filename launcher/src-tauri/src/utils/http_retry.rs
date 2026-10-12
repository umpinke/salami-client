//! Salami: Wiederholung von API-Requests bei Rate-Limit (429) und Serverfehlern (5xx).
//!
//! Modrinth und CurseForge drosseln bei vielen Anfragen (z. B. Warenkorb-Installation,
//! Update-Checks über viele Mods). Statt sofort zu scheitern, wird bis zu
//! [`MAX_ATTEMPTS`]-mal gewartet und erneut gesendet – mit `Retry-After` bzw.
//! `X-Ratelimit-Reset`, sonst exponentiellem Backoff.

use log::warn;
use reqwest::{RequestBuilder, Response, StatusCode};
use std::future::Future;
use std::time::Duration;

/// Versuche insgesamt (1 + 2 Wiederholungen).
pub const MAX_ATTEMPTS: u32 = 3;
const MAX_WAIT: Duration = Duration::from_secs(10);

pub trait SendWithRetry {
    /// Wie `send()`, wiederholt aber bei 429/5xx. Requests mit Streaming-Body
    /// (nicht klonbar) werden genau einmal gesendet.
    fn send_with_retry(self) -> impl Future<Output = reqwest::Result<Response>> + Send;
}

impl SendWithRetry for RequestBuilder {
    fn send_with_retry(self) -> impl Future<Output = reqwest::Result<Response>> + Send {
        async move {
            let mut builder = self;
            let mut attempt = 1;
            loop {
                let next = builder.try_clone();
                let response = builder.send().await?;
                let status = response.status();
                match next {
                    Some(next) if is_retryable(status) && attempt < MAX_ATTEMPTS => {
                        let wait = wait_time(&response, attempt);
                        warn!(
                            "[HTTP] {} from {} – retry {}/{} in {:?}",
                            status,
                            response.url().host_str().unwrap_or("?"),
                            attempt,
                            MAX_ATTEMPTS - 1,
                            wait
                        );
                        tokio::time::sleep(wait).await;
                        builder = next;
                        attempt += 1;
                    }
                    _ => return Ok(response),
                }
            }
        }
    }
}

fn is_retryable(status: StatusCode) -> bool {
    status == StatusCode::TOO_MANY_REQUESTS || status.is_server_error()
}

/// Wartezeit aus `Retry-After` / `X-Ratelimit-Reset` (Sekunden), sonst 0,5 s · 2^(Versuch-1).
fn wait_time(response: &Response, attempt: u32) -> Duration {
    let header_secs = ["retry-after", "x-ratelimit-reset"].iter().find_map(|name| {
        response
            .headers()
            .get(*name)
            .and_then(|v| v.to_str().ok())
            .and_then(|v| v.trim().parse::<u64>().ok())
    });
    let wait = match header_secs {
        Some(secs) => Duration::from_secs(secs.max(1)),
        None => Duration::from_millis(500 * 2u64.pow(attempt - 1)),
    };
    wait.min(MAX_WAIT)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn retries_only_rate_limit_and_server_errors() {
        assert!(is_retryable(StatusCode::TOO_MANY_REQUESTS));
        assert!(is_retryable(StatusCode::BAD_GATEWAY));
        assert!(!is_retryable(StatusCode::NOT_FOUND));
        assert!(!is_retryable(StatusCode::OK));
    }
}
