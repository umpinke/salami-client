use salami_client_lib::error::AppError;
use salami_client_lib::minecraft::auth::minecraft_auth::*;
use reqwest::StatusCode;

fn deserialize_failure(status: StatusCode) -> AppError {
    let source = serde_json::from_str::<serde_json::Value>("<html>login</html>").unwrap_err();
    AppError::MinecraftAuthenticationError(MinecraftAuthenticationError::DeserializeResponse {
        step: MinecraftAuthStep::RefreshOAuthToken,
        raw: "<html>login</html>".to_string(),
        source,
        status_code: status,
    })
}

#[test]
fn keeps_cached_credentials_on_captive_portal_body() {
    assert!(keep_cached_credentials_reason(&deserialize_failure(StatusCode::OK)).is_some());
}

#[test]
fn keeps_cached_credentials_on_rate_limit_and_server_errors() {
    assert!(
        keep_cached_credentials_reason(&deserialize_failure(StatusCode::TOO_MANY_REQUESTS))
            .is_some()
    );
    assert!(keep_cached_credentials_reason(&deserialize_failure(
        StatusCode::INTERNAL_SERVER_ERROR
    ))
    .is_some());
    assert!(
        keep_cached_credentials_reason(&deserialize_failure(StatusCode::BAD_GATEWAY)).is_some()
    );
}

#[test]
fn does_not_mask_real_auth_rejections() {
    assert!(
        keep_cached_credentials_reason(&deserialize_failure(StatusCode::BAD_REQUEST)).is_none()
    );
    assert!(
        keep_cached_credentials_reason(&deserialize_failure(StatusCode::UNAUTHORIZED)).is_none()
    );
    assert!(keep_cached_credentials_reason(&deserialize_failure(StatusCode::FORBIDDEN)).is_none());
}

#[test]
fn ignores_unrelated_errors() {
    assert!(keep_cached_credentials_reason(&AppError::NoCredentialsError).is_none());
    assert!(keep_cached_credentials_reason(&AppError::Other("boom".to_string())).is_none());
}

#[test]
fn does_not_treat_request_error_as_unreachable() {
    assert!(keep_cached_credentials_reason(&AppError::RequestError(
        "Request failed with status 401 Unauthorized".to_string()
    ))
    .is_none());
}

#[test]
fn offline_toast_only_on_true_unreachable() {
    assert!(!is_offline_error(&deserialize_failure(StatusCode::OK)));
    assert!(!is_offline_error(&deserialize_failure(
        StatusCode::TOO_MANY_REQUESTS
    )));
    assert!(!is_offline_error(&deserialize_failure(
        StatusCode::INTERNAL_SERVER_ERROR
    )));
    assert!(!is_offline_error(&deserialize_failure(
        StatusCode::BAD_REQUEST
    )));
    assert!(!is_offline_error(&AppError::RequestError(
        "boom".to_string()
    )));
    assert!(!is_offline_error(&AppError::NoCredentialsError));
}

#[test]
fn reads_accounts_saved_with_norisk_token_fields() {
    let saved = serde_json::json!({
        "id": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
        "username": "Notch",
        "access_token": "mc",
        "refresh_token": "ms",
        "expires": "2030-01-01T00:00:00Z",
        "norisk_credentials": { "production": { "value": "jwt" }, "experimental": null },
        "active": true,
        "ignore_child_protection_warning": true,
        "auth_flow": "Sisu"
    });
    let creds: Credentials = serde_json::from_value(saved).expect("old account must load");
    assert_eq!(creds.username, "Notch");
    assert!(creds.active);
    assert_eq!(creds.auth_flow, Some(AuthFlow::Sisu));

    let written = serde_json::to_value(&creds).unwrap();
    assert!(written.get("norisk_credentials").is_none());
}
