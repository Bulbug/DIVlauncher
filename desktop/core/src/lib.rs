//! DIVlauncher core logic. Pure functions only (no network, no files, no windows) so it can be
//! tested everywhere. The Tauri app in `../src-tauri` does the I/O and calls into this crate.

use md5::{Digest, Md5};
use serde::{Deserialize, Serialize};

/// One entry of Mojang's version manifest.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct VersionEntry {
    pub id: String,
    /// "release", "snapshot", "old_beta" or "old_alpha"
    #[serde(rename = "type")]
    pub kind: String,
    pub url: String,
    #[serde(rename = "releaseTime")]
    pub release_time: String,
}

impl VersionEntry {
    pub fn is_release(&self) -> bool {
        self.kind == "release"
    }
}

#[derive(Debug, Clone, PartialEq)]
pub struct Manifest {
    pub latest_release: String,
    pub latest_snapshot: String,
    pub versions: Vec<VersionEntry>,
}

#[derive(Deserialize)]
struct RawLatest {
    release: String,
    snapshot: String,
}

#[derive(Deserialize)]
struct RawManifest {
    latest: RawLatest,
    versions: Vec<VersionEntry>,
}

/// Parses `version_manifest_v2.json`.
pub fn parse_manifest(json: &str) -> Result<Manifest, serde_json::Error> {
    let raw: RawManifest = serde_json::from_str(json)?;
    Ok(Manifest {
        latest_release: raw.latest.release,
        latest_snapshot: raw.latest.snapshot,
        versions: raw.versions,
    })
}

/// Minecraft usernames: 3 to 16 letters, digits or underscores.
pub fn is_valid_username(name: &str) -> bool {
    let len = name.chars().count();
    (3..=16).contains(&len) && name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_')
}

/// The UUID Minecraft gives an offline player: a version-3 UUID built from the MD5 of
/// `OfflinePlayer:<name>`. Returned in the usual lowercase 8-4-4-4-12 form.
pub fn offline_uuid(name: &str) -> String {
    let mut hasher = Md5::new();
    hasher.update(format!("OfflinePlayer:{name}").as_bytes());
    let mut b: [u8; 16] = hasher.finalize().into();
    b[6] = (b[6] & 0x0f) | 0x30; // version 3
    b[8] = (b[8] & 0x3f) | 0x80; // RFC 4122 variant
    let hex: String = b.iter().map(|x| format!("{x:02x}")).collect();
    format!("{}-{}-{}-{}-{}", &hex[0..8], &hex[8..12], &hex[12..16], &hex[16..20], &hex[20..32])
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn offline_uuid_matches_the_known_value_for_notch() {
        assert_eq!(offline_uuid("Notch"), "b50ad385-829d-3141-a216-7e7d7539ba7f");
    }

    #[test]
    fn offline_uuid_has_version_3_and_rfc_variant() {
        for name in ["Kazu", "Steve_01", "abc"] {
            let u = offline_uuid(name);
            assert_eq!(u.len(), 36);
            assert_eq!(u.as_bytes()[14], b'3', "version nibble of {name}");
            assert!("89ab".contains(u.as_bytes()[19] as char), "variant nibble of {name}");
            assert_eq!(u, offline_uuid(name), "must be deterministic");
        }
        assert_ne!(offline_uuid("Kazu"), offline_uuid("kazu"), "names are case sensitive");
    }

    #[test]
    fn usernames() {
        assert!(is_valid_username("Kazu"));
        assert!(is_valid_username("Steve_01"));
        assert!(is_valid_username("abc"));
        assert!(is_valid_username("ABCDEFGHIJKLMNOP")); // 16
        assert!(!is_valid_username("ab"));
        assert!(!is_valid_username("ABCDEFGHIJKLMNOPQ")); // 17
        assert!(!is_valid_username("bad name"));
        assert!(!is_valid_username("naïve"));
        assert!(!is_valid_username(""));
    }

    #[test]
    fn manifest_is_parsed() {
        let json = r#"{
          "latest": {"release": "1.21.4", "snapshot": "25w02a"},
          "versions": [
            {"id": "25w02a", "type": "snapshot", "url": "https://x/a.json", "time": "t", "releaseTime": "2025-01-08T10:00:00+00:00", "sha1": "x", "complianceLevel": 1},
            {"id": "1.21.4", "type": "release", "url": "https://x/b.json", "time": "t", "releaseTime": "2024-12-03T10:00:00+00:00"},
            {"id": "b1.7.3", "type": "old_beta", "url": "https://x/c.json", "time": "t", "releaseTime": "2011-07-08T00:00:00+00:00"}
          ]}"#;
        let m = parse_manifest(json).unwrap();
        assert_eq!(m.latest_release, "1.21.4");
        assert_eq!(m.latest_snapshot, "25w02a");
        assert_eq!(m.versions.len(), 3);
        assert_eq!(m.versions.iter().filter(|v| v.is_release()).count(), 1);
        assert_eq!(m.versions[1].release_time, "2024-12-03T10:00:00+00:00");
    }

    #[test]
    fn broken_manifest_is_an_error() {
        assert!(parse_manifest("{}").is_err());
        assert!(parse_manifest("not json").is_err());
    }
}
