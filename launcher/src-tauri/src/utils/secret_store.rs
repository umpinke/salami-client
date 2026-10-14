//! Schützt gespeicherte Login-Daten mit Windows DPAPI: `accounts.json` lässt sich nur unter dem
//! eigenen Windows-Benutzer auf diesem PC entschlüsseln. Wird die Datei kopiert (geteilter
//! Ordner, Backup, „schick mal deinen Launcher-Ordner“-Masche), sind die Microsoft-Token darin
//! wertlos. Alte Klartext-Dateien werden beim Laden erkannt und beim nächsten Speichern
//! verschlüsselt.

use crate::error::{AppError, Result};
use serde::{Deserialize, Serialize};

const FORMAT: &str = "salami-dpapi-v1";
/// Zusätzlicher Schlüsselanteil, damit andere Programme die Daten nicht ohne Weiteres öffnen.
#[cfg_attr(not(windows), allow(dead_code))]
const ENTROPY: &[u8] = b"SalamiClient/accounts/v1";

#[derive(Serialize, Deserialize)]
struct SealedFile {
    format: String,
    data: String,
}

pub enum Opened {
    /// Entschlüsselter Inhalt einer geschützten Datei.
    Protected(String),
    /// Alte Klartext-Datei – Inhalt unverändert verwenden und neu speichern.
    Plain,
}

/// Verschlüsselt JSON für die Ablage (Windows). Auf anderen Systemen unverändert.
pub fn seal(json: &str) -> Result<String> {
    seal_impl(json)
}

#[cfg(windows)]
fn seal_impl(json: &str) -> Result<String> {
    use base64::Engine;
    let blob = dpapi::protect(json.as_bytes())?;
    let file = SealedFile {
        format: FORMAT.to_string(),
        data: base64::engine::general_purpose::STANDARD.encode(blob),
    };
    Ok(serde_json::to_string_pretty(&file)?)
}

#[cfg(not(windows))]
fn seal_impl(json: &str) -> Result<String> {
    Ok(json.to_string())
}

/// Öffnet eine abgelegte Datei. Fehler heißt: geschützt, aber hier nicht entschlüsselbar.
pub fn open(contents: &str) -> Result<Opened> {
    let Ok(file) = serde_json::from_str::<SealedFile>(contents) else {
        return Ok(Opened::Plain);
    };
    if file.format != FORMAT {
        return Err(AppError::Other(format!(
            "Unknown account file format: {}",
            file.format
        )));
    }
    open_impl(&file.data).map(Opened::Protected)
}

#[cfg(windows)]
fn open_impl(data: &str) -> Result<String> {
    use base64::Engine;
    let blob = base64::engine::general_purpose::STANDARD
        .decode(data.trim())
        .map_err(|e| AppError::Other(format!("Account file is damaged: {e}")))?;
    let plain = dpapi::unprotect(&blob)?;
    String::from_utf8(plain).map_err(|_| AppError::Other("Account file is damaged".to_string()))
}

#[cfg(not(windows))]
fn open_impl(_data: &str) -> Result<String> {
    Err(AppError::Other(
        "Encrypted account files can only be opened on Windows".to_string(),
    ))
}

#[cfg(windows)]
mod dpapi {
    use crate::error::{AppError, Result};
    use windows::core::PCWSTR;
    use windows::Win32::Foundation::{LocalFree, HLOCAL};
    use windows::Win32::Security::Cryptography::{
        CryptProtectData, CryptUnprotectData, CRYPTPROTECT_UI_FORBIDDEN, CRYPT_INTEGER_BLOB,
    };

    fn blob(data: &[u8]) -> CRYPT_INTEGER_BLOB {
        CRYPT_INTEGER_BLOB {
            cbData: data.len() as u32,
            pbData: data.as_ptr() as *mut u8,
        }
    }

    /// Kopiert das von Windows angelegte Ergebnis und gibt den Speicher frei.
    unsafe fn take(out: CRYPT_INTEGER_BLOB) -> Vec<u8> {
        if out.pbData.is_null() {
            return Vec::new();
        }
        let bytes = std::slice::from_raw_parts(out.pbData, out.cbData as usize).to_vec();
        let _ = LocalFree(HLOCAL(out.pbData as *mut core::ffi::c_void));
        bytes
    }

    pub fn protect(data: &[u8]) -> Result<Vec<u8>> {
        let input = blob(data);
        let entropy = blob(super::ENTROPY);
        let mut out = CRYPT_INTEGER_BLOB::default();
        unsafe {
            CryptProtectData(
                &input,
                PCWSTR::null(),
                Some(&entropy as *const _),
                None,
                None,
                CRYPTPROTECT_UI_FORBIDDEN,
                &mut out,
            )
            .map_err(|e| AppError::Other(format!("Could not encrypt account data: {e}")))?;
            Ok(take(out))
        }
    }

    pub fn unprotect(data: &[u8]) -> Result<Vec<u8>> {
        let input = blob(data);
        let entropy = blob(super::ENTROPY);
        let mut out = CRYPT_INTEGER_BLOB::default();
        unsafe {
            CryptUnprotectData(
                &input,
                None,
                Some(&entropy as *const _),
                None,
                None,
                CRYPTPROTECT_UI_FORBIDDEN,
                &mut out,
            )
            .map_err(|e| AppError::Other(format!("Could not decrypt account data: {e}")))?;
            Ok(take(out))
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn plain_files_are_recognised() {
        assert!(matches!(open(r#"{"accounts":[],"token":null}"#), Ok(Opened::Plain)));
        assert!(open(r#"{"format":"other","data":""}"#).is_err());
    }

    #[cfg(windows)]
    #[test]
    fn roundtrip_and_no_plaintext_on_disk() {
        let json = r#"{"accounts":[{"access_token":"secret-token-123"}],"token":null}"#;
        let sealed = seal(json).unwrap();
        assert!(!sealed.contains("secret-token-123"));
        match open(&sealed).unwrap() {
            Opened::Protected(plain) => assert_eq!(plain, json),
            Opened::Plain => panic!("sealed file read as plain"),
        }
        // Manipulierte Daten dürfen nicht still durchgehen
        let mut file: serde_json::Value = serde_json::from_str(&sealed).unwrap();
        file["data"] = serde_json::Value::String("AAAA".to_string());
        assert!(open(&file.to_string()).is_err());
    }
}
