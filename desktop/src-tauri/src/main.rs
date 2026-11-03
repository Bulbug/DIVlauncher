// No console window in release builds on Windows.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use serde::Serialize;
use serde_json::Value;
use sha1::{Digest, Sha1};
use std::fs::{self, File};
use std::io::{ErrorKind, Read, Write};
use std::path::{Component, Path, PathBuf};
use std::process::{Command, Stdio};
use std::time::Duration;
use tauri::Manager;

const MANIFEST_URL: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

#[derive(Serialize)]
struct AppInfo { name: String, version: String, data_dir: String, os: String }
#[derive(Serialize)]
struct VersionList { latest_release: String, latest_snapshot: String, versions: Vec<divcore::VersionEntry> }
#[derive(Serialize)]
struct InstallResult { version: String, directory: String, files_downloaded: usize, assets_downloaded: usize }
#[derive(Serialize)]
struct LaunchResult { version: String, pid: u32, log_file: String, java: String }
#[derive(Serialize)]
struct ModSearchHit { project_id: String, slug: String, title: String, description: String, downloads: u64, icon_url: Option<String> }
#[derive(Serialize)]
struct ModInstallResult { title: String, filename: String, path: String, version: String }

fn data_dir(app: &tauri::AppHandle) -> Result<PathBuf, String> {
    let dir = app.path().app_data_dir().map_err(|e| e.to_string())?;
    fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    Ok(dir)
}
fn game_dir(app: &tauri::AppHandle) -> Result<PathBuf, String> {
    let dir = data_dir(app)?.join(".minecraft");
    fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    Ok(dir)
}
#[tauri::command]
fn app_info(app: tauri::AppHandle) -> Result<AppInfo, String> {
    Ok(AppInfo { name: app.package_info().name.clone(), version: app.package_info().version.to_string(), data_dir: data_dir(&app)?.display().to_string(), os: std::env::consts::OS.to_string() })
}
#[tauri::command]
async fn list_versions() -> Result<VersionList, String> {
    tauri::async_runtime::spawn_blocking(|| {
        let body = ureq::get(MANIFEST_URL).timeout(Duration::from_secs(30)).call()
            .map_err(|e| format!("Could not reach Mojang: {e}"))?.into_string().map_err(|e| e.to_string())?;
        let manifest = divcore::parse_manifest(&body).map_err(|e| format!("Unexpected version list: {e}"))?;
        Ok(VersionList { latest_release: manifest.latest_release, latest_snapshot: manifest.latest_snapshot, versions: manifest.versions })
    }).await.map_err(|e| e.to_string())?
}
#[tauri::command]
fn offline_uuid(name: String) -> Result<String, String> {
    if !divcore::is_valid_username(&name) { return Err("A username needs 3 to 16 letters, numbers or underscores.".into()); }
    Ok(divcore::offline_uuid(&name))
}
#[tauri::command]
fn load_state(app: tauri::AppHandle) -> Result<String, String> {
    match fs::read_to_string(data_dir(&app)?.join("state.json")) {
        Ok(text) => Ok(text), Err(e) if e.kind() == ErrorKind::NotFound => Ok("{}".into()), Err(e) => Err(e.to_string())
    }
}
#[tauri::command]
fn save_state(app: tauri::AppHandle, json: String) -> Result<(), String> {
    let value: Value = serde_json::from_str(&json).map_err(|e| format!("Not valid JSON: {e}"))?;
    if !value.is_object() { return Err("Saved state must be a JSON object.".into()); }
    let dir = data_dir(&app)?;
    let tmp = dir.join("state.json.tmp");
    fs::write(&tmp, json).map_err(|e| e.to_string())?;
    fs::rename(&tmp, dir.join("state.json")).map_err(|e| e.to_string())
}

fn safe_id(id: &str) -> bool {
    !id.is_empty() && id.len() <= 64 && id.chars().all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '_' | '-')) && id != "." && id != ".."
}
fn safe_relative_path(s: &str) -> Result<PathBuf, String> {
    let path = Path::new(s);
    if path.is_absolute() || path.components().any(|c| !matches!(c, Component::Normal(_))) {
        return Err("Refusing an unsafe download path.".into());
    }
    Ok(path.to_path_buf())
}
fn sha1_hex(bytes: &[u8]) -> String { Sha1::digest(bytes).iter().map(|b| format!("{b:02x}")).collect() }
fn download_file(url: &str, target: &Path, expected_sha1: Option<&str>) -> Result<bool, String> {
    if !url.starts_with("https://") { return Err(format!("Refusing non-HTTPS download URL: {url}")); }
    if let (Some(expected), Ok(mut f)) = (expected_sha1, File::open(target)) {
        let mut bytes = Vec::new();
        if f.read_to_end(&mut bytes).is_ok() && sha1_hex(&bytes).eq_ignore_ascii_case(expected) { return Ok(false); }
    }
    if let Some(parent) = target.parent() { fs::create_dir_all(parent).map_err(|e| e.to_string())?; }
    let response = ureq::get(url).timeout(Duration::from_secs(90)).call().map_err(|e| format!("Download failed ({url}): {e}"))?;
    let mut bytes = Vec::new();
    response.into_reader().read_to_end(&mut bytes).map_err(|e| format!("Could not read download ({url}): {e}"))?;
    if let Some(expected) = expected_sha1 {
        if !sha1_hex(&bytes).eq_ignore_ascii_case(expected) { return Err(format!("SHA-1 verification failed for {url}")); }
    }
    let tmp = target.with_extension("download-tmp");
    { let mut f = File::create(&tmp).map_err(|e| e.to_string())?; f.write_all(&bytes).map_err(|e| e.to_string())?; f.sync_all().map_err(|e| e.to_string())?; }
    // Windows does not replace an existing destination with rename(), so remove a bad
    // prior download only after the replacement has been fully downloaded and verified.
    if target.exists() { fs::remove_file(target).map_err(|e| format!("Could not replace invalid file {}: {e}", target.display()))?; }
    fs::rename(&tmp, target).map_err(|e| { let _ = fs::remove_file(&tmp); e.to_string() })?;
    Ok(true)
}
fn rules_allow(value: &Value) -> bool {
    let Some(rules) = value.get("rules").and_then(Value::as_array) else { return true; };
    let mut allowed = false;
    for rule in rules {
        let mut matches = true;
        if let Some(os) = rule.get("os").and_then(Value::as_object) {
            if let Some(name) = os.get("name").and_then(Value::as_str) {
                if name != "windows" { matches = false; }
            }
            if let Some(arch) = os.get("arch").and_then(Value::as_str) {
                let process_arch = if cfg!(target_arch = "x86_64") { "x86_64" } else if cfg!(target_arch = "x86") { "x86" } else { std::env::consts::ARCH };
                if arch != process_arch { matches = false; }
            }
        }
        // DIVlauncher currently launches offline profiles, so authentication/demo feature flags are false.
        if let Some(features) = rule.get("features").and_then(Value::as_object) {
            for (name, expected) in features {
                let actual = match name.as_str() {
                    "is_demo_user" | "has_custom_resolution" | "has_quick_plays_support" | "is_quick_play_singleplayer" | "is_quick_play_multiplayer" | "is_quick_play_realms" => false,
                    _ => false,
                };
                if expected.as_bool().is_some_and(|want| want != actual) { matches = false; }
            }
        }
        if matches {
            allowed = rule.get("action").and_then(Value::as_str) == Some("allow");
        }
    }
    allowed
}
fn windows_library_allowed(lib: &Value) -> bool { rules_allow(lib) }

fn string_field<'a>(v: &'a Value, key: &str) -> Result<&'a str, String> {
    v.get(key).and_then(Value::as_str).ok_or_else(|| format!("Version metadata is missing '{key}'."))
}
fn download_asset_objects(game: &Path, index_id: &str, index_url: &str, index_sha1: Option<&str>) -> Result<usize, String> {
    let index_path = game.join("assets/indexes").join(format!("{index_id}.json"));
    download_file(index_url, &index_path, index_sha1)?;
    let text = fs::read_to_string(&index_path).map_err(|e| e.to_string())?;
    let index: Value = serde_json::from_str(&text).map_err(|e| format!("Invalid asset index: {e}"))?;
    let objects = index.get("objects").and_then(Value::as_object).ok_or("Asset index has no objects map.")?;
    let mut downloaded = 0;
    for obj in objects.values() {
        let hash = string_field(obj, "hash")?;
        if hash.len() != 40 || !hash.chars().all(|c| c.is_ascii_hexdigit()) { return Err("Asset index contains an invalid SHA-1 hash.".into()); }
        let rel = format!("{}/{}", &hash[..2], hash);
        let target = game.join("assets/objects").join(&rel);
        let url = format!("https://resources.download.minecraft.net/{rel}");
        if download_file(&url, &target, Some(hash))? { downloaded += 1; }
    }
    Ok(downloaded)
}

/// Downloads vanilla metadata, client jar, applicable Windows library artifacts and assets.
/// Runtime installation, native extraction and process launch are deliberately separate phases.
#[tauri::command]
async fn install_version(app: tauri::AppHandle, version: String) -> Result<InstallResult, String> {
    if !safe_id(&version) { return Err("Invalid Minecraft version identifier.".into()); }
    tauri::async_runtime::spawn_blocking(move || {
        let game = game_dir(&app)?;
        let manifest_text = ureq::get(MANIFEST_URL).timeout(Duration::from_secs(30)).call()
            .map_err(|e| format!("Could not reach Mojang: {e}"))?.into_string().map_err(|e| e.to_string())?;
        let manifest: Value = serde_json::from_str(&manifest_text).map_err(|e| e.to_string())?;
        let entries = manifest.get("versions").and_then(Value::as_array).ok_or("Mojang manifest has no versions list.")?;
        let entry = entries.iter().find(|v| v.get("id").and_then(Value::as_str) == Some(&version)).ok_or("That version was not found in Mojang's version manifest.")?;
        let meta_url = string_field(entry, "url")?;
        let meta_sha = entry.get("sha1").and_then(Value::as_str);
        let meta_path = game.join("versions").join(&version).join(format!("{version}.json"));
        let mut files = if download_file(meta_url, &meta_path, meta_sha)? { 1 } else { 0 };
        let meta_text = fs::read_to_string(&meta_path).map_err(|e| e.to_string())?;
        let meta: Value = serde_json::from_str(&meta_text).map_err(|e| format!("Invalid version metadata: {e}"))?;
        let client = meta.pointer("/downloads/client").ok_or("This version has no client download.")?;
        let client_url = string_field(client, "url")?;
        let client_sha = client.get("sha1").and_then(Value::as_str);
        let client_path = game.join("versions").join(&version).join(format!("{version}.jar"));
        files += if download_file(client_url, &client_path, client_sha)? { 1 } else { 0 };
        if let Some(libraries) = meta.get("libraries").and_then(Value::as_array) {
            for lib in libraries {
                if !windows_library_allowed(lib) { continue; }
                if let Some(artifact) = lib.pointer("/downloads/artifact") {
                    let url = string_field(artifact, "url")?;
                    let path = string_field(artifact, "path")?;
                    let rel = safe_relative_path(path)?;
                    let target = game.join("libraries").join(rel);
                    files += if download_file(url, &target, artifact.get("sha1").and_then(Value::as_str))? { 1 } else { 0 };
                }
                if let (Some(natives), Some(classifiers)) = (lib.get("natives").and_then(Value::as_object), lib.pointer("/downloads/classifiers").and_then(Value::as_object)) {
                    if let Some(template) = natives.get("windows").and_then(Value::as_str) {
                        let arch = if cfg!(target_arch = "x86") { "32" } else { "64" };
                        let classifier_name = template.replace("${arch}", arch);
                        if let Some(classifier) = classifiers.get(&classifier_name) {
                            let url = string_field(classifier, "url")?;
                            let path = string_field(classifier, "path")?;
                            let rel = safe_relative_path(path)?;
                            let target = game.join("libraries").join(rel);
                            files += if download_file(url, &target, classifier.get("sha1").and_then(Value::as_str))? { 1 } else { 0 };
                        }
                    }
                }
            }
        }
        let mut assets_downloaded = 0;
        if let Some(asset_index) = meta.get("assetIndex") {
            let index_id = string_field(asset_index, "id")?;
            assets_downloaded = download_asset_objects(&game, index_id, string_field(asset_index, "url")?, asset_index.get("sha1").and_then(Value::as_str))?;
        }
        if let Some(log_file) = meta.pointer("/logging/client/file") {
            if let (Some(id), Some(url)) = (log_file.get("id").and_then(Value::as_str), log_file.get("url").and_then(Value::as_str)) {
                if !id.contains('/') && !id.contains('\\') && id != "." && id != ".." {
                    let target = game.join("assets/log_configs").join(id);
                    files += if download_file(url, &target, log_file.get("sha1").and_then(Value::as_str))? { 1 } else { 0 };
                }
            }
        }
        Ok(InstallResult { version, directory: game.display().to_string(), files_downloaded: files, assets_downloaded })
    }).await.map_err(|e| e.to_string())?
}


fn url_encode(input: &str) -> String {
    let mut out = String::new();
    for b in input.bytes() {
        if b.is_ascii_alphanumeric() || matches!(b, b'-' | b'_' | b'.' | b'~') { out.push(b as char); }
        else { out.push_str(&format!("%{b:02X}")); }
    }
    out
}
fn validate_loader(loader: &str) -> Result<&'static str, String> {
    match loader { "fabric" => Ok("fabric"), "forge" => Ok("forge"), "neoforge" => Ok("neoforge"), "quilt" => Ok("quilt"), _ => Err("Choose Fabric, Forge, NeoForge, or Quilt.".into()) }
}
#[tauri::command]
async fn search_mods(query: String, game_version: String, loader: String) -> Result<Vec<ModSearchHit>, String> {
    if !safe_id(&game_version) { return Err("Invalid Minecraft version identifier.".into()); }
    let loader = validate_loader(&loader)?.to_string();
    tauri::async_runtime::spawn_blocking(move || {
        let facets = vec![vec!["project_type:mod".to_string()], vec![format!("versions:{game_version}")], vec![format!("categories:{loader}")]];
        let facets_json = serde_json::to_string(&facets).map_err(|e| e.to_string())?;
        let url = format!("https://api.modrinth.com/v2/search?query={}&facets={}&limit=24&index=relevance", url_encode(query.trim()), url_encode(&facets_json));
        let text = ureq::get(&url).set("User-Agent", "DIVlauncher/1.0 (https://github.com/)" ).timeout(Duration::from_secs(25)).call()
            .map_err(|e| format!("Modrinth search failed: {e}"))?.into_string().map_err(|e| e.to_string())?;
        let value: Value = serde_json::from_str(&text).map_err(|e| format!("Invalid Modrinth response: {e}"))?;
        let hits = value.get("hits").and_then(Value::as_array).ok_or("Modrinth returned no search results.")?;
        Ok(hits.iter().filter_map(|hit| Some(ModSearchHit {
            project_id: hit.get("project_id")?.as_str()?.to_owned(),
            slug: hit.get("slug")?.as_str()?.to_owned(),
            title: hit.get("title")?.as_str()?.to_owned(),
            description: hit.get("description").and_then(Value::as_str).unwrap_or("").to_owned(),
            downloads: hit.get("downloads").and_then(Value::as_u64).unwrap_or(0),
            icon_url: hit.get("icon_url").and_then(Value::as_str).map(str::to_owned),
        })).collect())
    }).await.map_err(|e| e.to_string())?
}
#[tauri::command]
async fn install_mod(app: tauri::AppHandle, project_id: String, game_version: String, loader: String) -> Result<ModInstallResult, String> {
    if !safe_id(&project_id) || !safe_id(&game_version) { return Err("Invalid project or Minecraft version identifier.".into()); }
    let loader = validate_loader(&loader)?.to_string();
    tauri::async_runtime::spawn_blocking(move || {
        let versions_url = format!("https://api.modrinth.com/v2/project/{}/version?game_versions={}&loaders={}", project_id,
            url_encode(&serde_json::to_string(&vec![game_version.clone()]).map_err(|e| e.to_string())?),
            url_encode(&serde_json::to_string(&vec![loader]).map_err(|e| e.to_string())?));
        let text = ureq::get(&versions_url).set("User-Agent", "DIVlauncher/1.0 (https://github.com/)" ).timeout(Duration::from_secs(25)).call()
            .map_err(|e| format!("Could not fetch compatible mod versions: {e}"))?.into_string().map_err(|e| e.to_string())?;
        let versions: Value = serde_json::from_str(&text).map_err(|e| format!("Invalid Modrinth version response: {e}"))?;
        let list = versions.as_array().ok_or("Modrinth returned an invalid version list.")?;
        let chosen = list.first().ok_or("No compatible version was found for this Minecraft version and mod loader.")?;
        let title = chosen.get("name").and_then(Value::as_str).or_else(|| chosen.get("version_number").and_then(Value::as_str)).unwrap_or("Mod").to_owned();
        let files = chosen.get("files").and_then(Value::as_array).ok_or("This mod version has no downloadable files.")?;
        let file = files.iter().find(|f| f.get("primary").and_then(Value::as_bool) == Some(true)).or_else(|| files.first()).ok_or("This mod version has no downloadable files.")?;
        let filename = file.get("filename").and_then(Value::as_str).ok_or("Mod file has no filename.")?.to_owned();
        if filename.contains('/') || filename.contains('\\') || !filename.to_ascii_lowercase().ends_with(".jar") { return Err("Refusing an unsafe or non-JAR mod file.".into()); }
        let url = string_field(file, "url")?;
        let hash = file.pointer("/hashes/sha1").and_then(Value::as_str);
        let game = game_dir(&app)?;
        let target = game.join("mods").join(&filename);
        download_file(url, &target, hash)?;
        Ok(ModInstallResult { title, filename, path: target.display().to_string(), version: game_version })
    }).await.map_err(|e| e.to_string())?
}

#[derive(Serialize)]
struct InstalledMod { filename: String, size_bytes: u64 }
#[tauri::command]
async fn list_installed_mods(app: tauri::AppHandle) -> Result<Vec<InstalledMod>, String> {
    let game = game_dir(&app)?;
    let mods = game.join("mods");
    if !mods.exists() { return Ok(Vec::new()); }
    let mut result = Vec::new();
    for entry in fs::read_dir(mods).map_err(|e| e.to_string())? {
        let entry = entry.map_err(|e| e.to_string())?;
        let path = entry.path();
        if path.is_file() && path.extension().and_then(|x| x.to_str()).is_some_and(|x| x.eq_ignore_ascii_case("jar")) {
            result.push(InstalledMod { filename: entry.file_name().to_string_lossy().into_owned(), size_bytes: entry.metadata().map(|m| m.len()).unwrap_or(0) });
        }
    }
    result.sort_by(|a, b| a.filename.to_lowercase().cmp(&b.filename.to_lowercase()));
    Ok(result)
}
#[tauri::command]
async fn remove_mod(app: tauri::AppHandle, filename: String) -> Result<(), String> {
    if filename.is_empty() || filename.contains('/') || filename.contains('\\') || !filename.to_ascii_lowercase().ends_with(".jar") { return Err("Invalid mod filename.".into()); }
    let path = game_dir(&app)?.join("mods").join(filename);
    if !path.is_file() { return Err("That mod file is no longer present.".into()); }
    fs::remove_file(path).map_err(|e| e.to_string())
}

#[derive(Serialize)]
struct JavaInfo { available: bool, path: Option<String>, major: Option<u32>, message: String }

#[tauri::command]
async fn java_diagnostics(app: tauri::AppHandle) -> JavaInfo {
    let managed = [21, 17, 8].iter().filter_map(|m| managed_java_path(&app, *m).ok()).find(|p| p.is_file());
    match managed.map(Ok).unwrap_or_else(find_java) {
        Ok(path) => match java_major(&path) {
            Ok(major) => JavaInfo {
                available: true,
                path: Some(path.display().to_string()),
                major: Some(major),
                message: if major >= 21 { format!("Java {major} detected. Suitable for current Minecraft releases that require Java 21.") }
                    else if major >= 17 { format!("Java {major} detected. Suitable for many older versions, but newer Minecraft releases may require Java 21.") }
                    else { format!("Java {major} detected. This is too old for many modern Minecraft versions; install Java 17 or 21 as required by your selected version.") },
            },
            Err(error) => JavaInfo { available: false, path: Some(path.display().to_string()), major: None, message: format!("Java was found but its version could not be read: {error}") },
        },
        Err(error) => JavaInfo { available: false, path: None, major: None, message: format!("{error}. Install a Java runtime matching your Minecraft version, then restart DIVlauncher.") },
    }
}

#[derive(Serialize)]
struct JavaInstallResult { major: u32, java: String, runtime_dir: String }

fn managed_java_path(app: &tauri::AppHandle, major: u32) -> Result<PathBuf, String> {
    let root = data_dir(app)?.join("runtimes").join(format!("temurin-{major}"));
    Ok(root.join("bin").join("java.exe"))
}

#[tauri::command]
async fn install_java_runtime(app: tauri::AppHandle, major: u32) -> Result<JavaInstallResult, String> {
    if !matches!(major, 8 | 17 | 21) { return Err("Choose a supported Java LTS runtime: 8, 17, or 21.".into()); }
    tauri::async_runtime::spawn_blocking(move || {
        let root = data_dir(&app)?.join("runtimes").join(format!("temurin-{major}"));
        let java = root.join("bin").join("java.exe");
        if java.is_file() { return Ok(JavaInstallResult { major, java: java.display().to_string(), runtime_dir: root.display().to_string() }); }
        fs::create_dir_all(root.parent().ok_or("Invalid runtime directory")?).map_err(|e| e.to_string())?;
        let url = format!("https://api.adoptium.net/v3/assets/latest/{major}/hotspot?architecture=x64&image_type=jre&os=windows&vendor=eclipse");
        let body = ureq::get(&url).timeout(Duration::from_secs(45)).call().map_err(|e| format!("Could not query Eclipse Adoptium: {e}"))?.into_string().map_err(|e| e.to_string())?;
        let assets: Value = serde_json::from_str(&body).map_err(|e| format!("Invalid runtime catalog response: {e}"))?;
        let download_url = assets.get(0).and_then(|v| v.pointer("/binary/package/link")).and_then(Value::as_str).ok_or("No compatible Windows x64 Java runtime was published.")?;
        if !download_url.starts_with("https://") || !download_url.contains("github.com/adoptium/") { return Err("Runtime provider returned an unexpected download host.".into()); }
        let response = ureq::get(download_url).timeout(Duration::from_secs(180)).call().map_err(|e| format!("Runtime download failed: {e}"))?;
        let mut bytes = Vec::new(); response.into_reader().take(512 * 1024 * 1024).read_to_end(&mut bytes).map_err(|e| e.to_string())?;
        if bytes.is_empty() { return Err("Downloaded Java runtime archive is empty.".into()); }
        let archive_path = root.with_extension("zip"); fs::write(&archive_path, &bytes).map_err(|e| e.to_string())?;
        let file = File::open(&archive_path).map_err(|e| e.to_string())?;
        let mut archive = zip::ZipArchive::new(file).map_err(|e| format!("Invalid Java runtime archive: {e}"))?;
        let staging = root.with_extension("staging"); if staging.exists() { fs::remove_dir_all(&staging).map_err(|e| e.to_string())?; }
        fs::create_dir_all(&staging).map_err(|e| e.to_string())?;
        for i in 0..archive.len() {
            let mut entry = archive.by_index(i).map_err(|e| e.to_string())?;
            let name = entry.name().replace('\\', "/");
            let parts: Vec<&str> = name.split('/').filter(|x| !x.is_empty()).collect();
            if parts.len() < 2 { continue; }
            let rel = parts[1..].join("/"); if rel.is_empty() { continue; }
            let rel_path = safe_relative_path(&rel)?; let target = staging.join(rel_path);
            if entry.is_dir() { fs::create_dir_all(&target).map_err(|e| e.to_string())?; continue; }
            if let Some(parent) = target.parent() { fs::create_dir_all(parent).map_err(|e| e.to_string())?; }
            let mut out = File::create(&target).map_err(|e| e.to_string())?;
            std::io::copy(&mut entry, &mut out).map_err(|e| e.to_string())?;
        }
        let staged_java = staging.join("bin/java.exe");
        if !staged_java.is_file() { let _ = fs::remove_dir_all(&staging); let _ = fs::remove_file(&archive_path); return Err("Runtime archive did not contain bin/java.exe.".into()); }
        if root.exists() { fs::remove_dir_all(&root).map_err(|e| e.to_string())?; }
        fs::rename(&staging, &root).map_err(|e| e.to_string())?;
        let _ = fs::remove_file(&archive_path);
        Ok(JavaInstallResult { major, java: java.display().to_string(), runtime_dir: root.display().to_string() })
    }).await.map_err(|e| e.to_string())?
}

fn find_java() -> Result<PathBuf, String> {
    // Prefer launcher-managed runtimes; newest compatible LTS is checked first.
    // Managed runtimes are discovered from the standard app data location by the command below.
    if let Some(home) = std::env::var_os("JAVA_HOME") {
        let mut candidate = PathBuf::from(home).join("bin");
        candidate.push(if cfg!(windows) { "java.exe" } else { "java" });
        if candidate.is_file() { return Ok(candidate); }
    }
    let executable = if cfg!(windows) { "java.exe" } else { "java" };
    if let Some(path) = std::env::var_os("PATH") {
        for folder in std::env::split_paths(&path) {
            let candidate = folder.join(executable);
            if candidate.is_file() { return Ok(candidate); }
        }
    }
    Err("Java was not found. Install a compatible 64-bit Java runtime, then set JAVA_HOME or add Java to PATH. Automatic runtime installation is planned for a later update.".into())
}

fn java_major(java: &Path) -> Result<u32, String> {
    let output = Command::new(java).arg("-version").output().map_err(|e| format!("Could not run Java: {e}"))?;
    let text = format!("{}{}", String::from_utf8_lossy(&output.stdout), String::from_utf8_lossy(&output.stderr));
    let marker = text.split('"').nth(1).ok_or_else(|| format!("Could not read Java version from: {text}"))?;
    let first = marker.split('.').next().unwrap_or(marker);
    let major = if first == "1" {
        marker.split('.').nth(1).and_then(|x| x.parse::<u32>().ok())
    } else { first.parse::<u32>().ok() };
    major.ok_or_else(|| format!("Could not parse Java version from: {marker}"))
}

fn replace_placeholders(input: &str, values: &std::collections::HashMap<&str, String>) -> String {
    let mut result = input.to_owned();
    for (key, value) in values { result = result.replace(&format!("${{{key}}}"), value); }
    result
}

fn argument_values(value: &Value) -> Vec<String> {
    if let Some(s) = value.as_str() { return vec![s.to_owned()]; }
    if !rules_allow(value) { return Vec::new(); }
    match value.get("value") {
        Some(Value::String(s)) => vec![s.clone()],
        Some(Value::Array(items)) => items.iter().filter_map(Value::as_str).map(str::to_owned).collect(),
        _ => Vec::new(),
    }
}

fn extract_natives(meta: &Value, game: &Path, version: &str) -> Result<PathBuf, String> {
    let native_dir = game.join("versions").join(version).join("natives");
    fs::create_dir_all(&native_dir).map_err(|e| e.to_string())?;
    if let Some(libraries) = meta.get("libraries").and_then(Value::as_array) {
        for lib in libraries {
            if !windows_library_allowed(lib) { continue; }
            let Some(natives) = lib.get("natives").and_then(Value::as_object) else { continue; };
            let Some(template) = natives.get("windows").and_then(Value::as_str) else { continue; };
            let arch = if cfg!(target_arch = "x86") { "32" } else { "64" };
            let classifier_name = template.replace("${arch}", arch);
            let Some(classifier) = lib.pointer("/downloads/classifiers").and_then(Value::as_object).and_then(|m| m.get(&classifier_name)) else { continue; };
            let rel = safe_relative_path(string_field(classifier, "path")?)?;
            let jar_path = game.join("libraries").join(rel);
            if !jar_path.is_file() { continue; }
            let file = File::open(&jar_path).map_err(|e| format!("Could not open native library {}: {e}", jar_path.display()))?;
            let mut archive = zip::ZipArchive::new(file).map_err(|e| format!("Invalid native archive {}: {e}", jar_path.display()))?;
            for i in 0..archive.len() {
                let mut item = archive.by_index(i).map_err(|e| e.to_string())?;
                if item.is_dir() { continue; }
                let name = item.name().replace('\\', "/");
                if name.starts_with("META-INF/") || name.starts_with('/') { continue; }
                let rel = safe_relative_path(&name)?;
                let target = native_dir.join(rel);
                if let Some(parent) = target.parent() { fs::create_dir_all(parent).map_err(|e| e.to_string())?; }
                let mut out = File::create(&target).map_err(|e| e.to_string())?;
                std::io::copy(&mut item, &mut out).map_err(|e| e.to_string())?;
            }
        }
    }
    Ok(native_dir)
}

#[tauri::command]
async fn is_version_installed(app: tauri::AppHandle, version: String) -> Result<bool, String> {
    if !safe_id(&version) { return Err("Invalid Minecraft version identifier.".into()); }
    let game = game_dir(&app)?;
    let folder = game.join("versions").join(&version);
    Ok(folder.join(format!("{version}.json")).is_file() && folder.join(format!("{version}.jar")).is_file())
}

/// Launches an already-installed vanilla version with an existing local Java runtime and an offline profile.
#[tauri::command]
async fn launch_version(app: tauri::AppHandle, version: String, username: String, memory_mb: Option<u32>, extra_jvm_args: Option<Vec<String>>) -> Result<LaunchResult, String> {
    if !safe_id(&version) { return Err("Invalid Minecraft version identifier.".into()); }
    if !divcore::is_valid_username(&username) { return Err("Choose a valid offline username (3–16 letters, numbers or underscores) before launching.".into()); }
    tauri::async_runtime::spawn_blocking(move || {
        let game = game_dir(&app)?;
        let version_dir = game.join("versions").join(&version);
        let meta_path = version_dir.join(format!("{version}.json"));
        let client_path = version_dir.join(format!("{version}.jar"));
        if !meta_path.is_file() || !client_path.is_file() {
            return Err("This version is not installed yet. Click Install first.".to_string());
        }
        let meta_text = fs::read_to_string(&meta_path).map_err(|e| e.to_string())?;
        let meta: Value = serde_json::from_str(&meta_text).map_err(|e| format!("Invalid version metadata: {e}"))?;
        if meta.get("inheritsFrom").is_some() {
            return Err("This launcher update currently supports vanilla versions only; inherited mod-loader profiles are not supported yet.".into());
        }
        let required = meta.pointer("/javaVersion/majorVersion").and_then(Value::as_u64).unwrap_or(8) as u32;
        let managed = [21, 17, 8].iter().filter(|m| **m >= required).filter_map(|m| managed_java_path(&app, *m).ok()).find(|p| p.is_file());
        let java = managed.map(Ok).unwrap_or_else(find_java)?;
        let java_ver = java_major(&java)?;
        if java_ver < required {
            return Err(format!("Minecraft {version} requires Java {required} or newer, but the Java on PATH is version {java_ver}. Install Java {required} or newer from Settings → Java runtime."));
        }
        let native_dir = extract_natives(&meta, &game, &version)?;
        let assets_dir = game.join("assets");
        let asset_index = meta.pointer("/assetIndex/id").and_then(Value::as_str).unwrap_or(&version).to_owned();
        let uuid = divcore::offline_uuid(&username).replace("-", "");
        let mut values: std::collections::HashMap<&str, String> = std::collections::HashMap::new();
        values.insert("natives_directory", native_dir.display().to_string());
        let log_config_path = meta.pointer("/logging/client/file/id").and_then(Value::as_str)
            .map(|id| game.join("assets/log_configs").join(id).display().to_string()).unwrap_or_default();
        values.insert("path", log_config_path);
        values.insert("launcher_name", "DIVlauncher".into());
        values.insert("launcher_version", env!("CARGO_PKG_VERSION").into());
        values.insert("classpath_separator", ";".into());
        values.insert("library_directory", game.join("libraries").display().to_string());
        values.insert("version_name", version.clone());
        values.insert("version_type", meta.get("type").and_then(Value::as_str).unwrap_or("release").into());
        values.insert("game_directory", game.display().to_string());
        values.insert("assets_root", assets_dir.display().to_string());
        values.insert("assets_index_name", asset_index.clone());
        values.insert("auth_player_name", username.clone());
        values.insert("auth_uuid", uuid.clone());
        values.insert("auth_access_token", "0".into());
        values.insert("auth_session", "0".into());
        values.insert("user_properties", "{}".into());
        values.insert("clientid", "".into());
        values.insert("auth_xuid", "".into());
        values.insert("user_type", "legacy".into());
        values.insert("version", version.clone());
        values.insert("resolution_width", "854".into());
        values.insert("resolution_height", "480".into());

        let mut classpath_items = Vec::new();
        if let Some(libraries) = meta.get("libraries").and_then(Value::as_array) {
            for lib in libraries {
                if !windows_library_allowed(lib) { continue; }
                if let Some(path) = lib.pointer("/downloads/artifact/path").and_then(Value::as_str) {
                    let rel = safe_relative_path(path)?;
                    let full = game.join("libraries").join(rel);
                    if full.is_file() { classpath_items.push(full.display().to_string()); }
                }
            }
        }
        classpath_items.push(client_path.display().to_string());
        let classpath = classpath_items.join(";");
        values.insert("classpath", classpath);

        let mut jvm_args: Vec<String> = Vec::new();
        if let Some(jvm) = meta.pointer("/arguments/jvm").and_then(Value::as_array) {
            for item in jvm {
                for arg in argument_values(item) {
                    jvm_args.push(replace_placeholders(&arg, &values));
                }
            }
        } else {
            jvm_args.push(format!("-Djava.library.path={}", native_dir.display()));
        }
        // Let the user-selected memory limit override Mojang's default heap argument.
        let memory = memory_mb.unwrap_or(4096).clamp(1024, 32768);
        jvm_args.retain(|arg| !arg.starts_with("-Xmx"));
        jvm_args.push(format!("-Xmx{}M", memory));
        // Custom JVM options are passed as individual process arguments (never through a shell).
        // Block heap/classpath/agent overrides that can break launcher-managed arguments.
        if let Some(custom) = extra_jvm_args {
            if custom.len() > 20 { return Err("At most 20 custom JVM arguments are allowed.".into()); }
            for arg in custom {
                let arg = arg.trim();
                if arg.is_empty() { continue; }
                if arg.len() > 512 || arg.chars().any(|c| c.is_control()) {
                    return Err("A custom JVM argument is too long or contains control characters.".into());
                }
                if arg.starts_with("-Xmx") || arg == "-cp" || arg == "-classpath" || arg == "--class-path"
                    || arg.starts_with("-javaagent") || arg.starts_with("-agentlib") || arg.starts_with("-agentpath") {
                    return Err(format!("The custom JVM argument '{arg}' is reserved or unsafe for launcher-managed startup."));
                }
                jvm_args.push(arg.to_owned());
            }
        }
        if !jvm_args.iter().any(|x| x == "-cp" || x == "-classpath" || x == "--class-path") {
            jvm_args.push("-cp".into());
            jvm_args.push(values.get("classpath").cloned().unwrap_or_default());
        }

        let mut game_args: Vec<String> = Vec::new();
        if let Some(items) = meta.pointer("/arguments/game").and_then(Value::as_array) {
            for item in items {
                for arg in argument_values(item) {
                    game_args.push(replace_placeholders(&arg, &values));
                }
            }
        } else if let Some(legacy) = meta.get("minecraftArguments").and_then(Value::as_str) {
            // Legacy metadata format; placeholders are substituted before whitespace tokenization.
            let expanded = replace_placeholders(legacy, &values);
            game_args.extend(expanded.split_whitespace().map(str::to_owned));
        } else {
            return Err("Version metadata contains no supported game arguments.".into());
        }
        let main_class = string_field(&meta, "mainClass")?;
        let mut args = jvm_args;
        args.push(main_class.into());
        args.extend(game_args);

        let logs_dir = game.join("logs");
        fs::create_dir_all(&logs_dir).map_err(|e| e.to_string())?;
        let log_path = logs_dir.join(format!("launcher-{version}.log"));
        let log_file = File::create(&log_path).map_err(|e| e.to_string())?;
        let stderr_file = log_file.try_clone().map_err(|e| e.to_string())?;
        let child = Command::new(&java).args(&args).current_dir(&game)
            .stdout(Stdio::from(log_file)).stderr(Stdio::from(stderr_file))
            .spawn().map_err(|e| format!("Could not start Minecraft: {e}"))?;
        Ok(LaunchResult { version, pid: child.id(), log_file: log_path.display().to_string(), java: java.display().to_string() })
    }).await.map_err(|e| e.to_string())?
}


#[derive(Serialize)]
struct GameFileEntry { name: String, kind: String, size_bytes: u64 }
fn game_category_dir(app: &tauri::AppHandle, category: &str) -> Result<PathBuf, String> {
    let base = game_dir(app)?;
    let dir = match category {
        "worlds" => base.join("saves"),
        "screenshots" => base.join("screenshots"),
        "resourcepacks" => base.join("resourcepacks"),
        "shaderpacks" => base.join("shaderpacks"),
        "logs" => base.join("logs"),
        _ => return Err("Unknown Minecraft folder category.".into()),
    };
    fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    Ok(dir)
}
#[tauri::command]
fn list_game_files(app: tauri::AppHandle, category: String) -> Result<Vec<GameFileEntry>, String> {
    let dir = game_category_dir(&app, &category)?;
    let mut entries = Vec::new();
    for item in fs::read_dir(&dir).map_err(|e| e.to_string())? {
        let item = item.map_err(|e| e.to_string())?;
        let path = item.path();
        let meta = fs::symlink_metadata(&path).map_err(|e| e.to_string())?;
        if meta.file_type().is_symlink() { continue; }
        let name = item.file_name().to_string_lossy().into_owned();
        if name.starts_with('.') { continue; }
        entries.push(GameFileEntry { name, kind: if meta.is_dir() { "folder".into() } else { "file".into() }, size_bytes: if meta.is_file() { meta.len() } else { 0 } });
    }
    entries.sort_by_key(|x| (x.kind != "folder", x.name.to_lowercase()));
    Ok(entries)
}
#[tauri::command]
fn delete_game_file(app: tauri::AppHandle, category: String, name: String) -> Result<(), String> {
    if name.is_empty() || name == "." || name == ".." || name.contains('/') || name.contains('\\') { return Err("Invalid file name.".into()); }
    let dir = game_category_dir(&app, &category)?;
    let target = dir.join(&name);
    let meta = fs::symlink_metadata(&target).map_err(|e| e.to_string())?;
    if meta.file_type().is_symlink() { return Err("Refusing to delete a symbolic link.".into()); }
    if meta.is_dir() { fs::remove_dir_all(target).map_err(|e| e.to_string()) } else { fs::remove_file(target).map_err(|e| e.to_string()) }
}
#[tauri::command]
fn open_game_folder(app: tauri::AppHandle, category: String) -> Result<(), String> {
    let dir = game_category_dir(&app, &category)?;
    #[cfg(target_os = "windows")]
    { Command::new("explorer.exe").arg(&dir).spawn().map_err(|e| e.to_string())?; }
    #[cfg(target_os = "macos")]
    { Command::new("open").arg(&dir).spawn().map_err(|e| e.to_string())?; }
    #[cfg(all(unix, not(target_os = "macos")))]
    { Command::new("xdg-open").arg(&dir).spawn().map_err(|e| e.to_string())?; }
    Ok(())
}


#[derive(Serialize)]
struct ArchiveResult { path: String, files: usize }

fn archive_category_dir(app: &tauri::AppHandle, category: &str) -> Result<PathBuf, String> {
    match category {
        "mods" => Ok(game_dir(app)?.join("mods")),
        "resourcepacks" | "shaderpacks" | "worlds" => game_category_dir(app, category),
        _ => Err("Unsupported archive category.".into()),
    }
}

fn append_directory<W: Write + std::io::Seek>(archive: &mut zip::ZipWriter<W>, root: &Path, prefix: &str) -> Result<usize, String> {
    use zip::write::FileOptions;
    let options = FileOptions::default().compression_method(zip::CompressionMethod::Deflated);
    let mut count = 0usize;
    fn add_dir<W: Write + std::io::Seek>(z: &mut zip::ZipWriter<W>, root: &Path, dir: &Path, prefix: &str, options: zip::write::FileOptions, count: &mut usize) -> Result<(), String> {
        for entry in fs::read_dir(dir).map_err(|e| e.to_string())? {
            let entry = entry.map_err(|e| e.to_string())?;
            let path = entry.path();
            let meta = fs::symlink_metadata(&path).map_err(|e| e.to_string())?;
            if meta.file_type().is_symlink() { continue; }
            let rel = path.strip_prefix(root).map_err(|e| e.to_string())?;
            let name = format!("{}/{}", prefix, rel.to_string_lossy().replace('\\', "/"));
            if meta.is_dir() { add_dir(z, root, &path, prefix, options, count)?; }
            else if meta.is_file() {
                if meta.len() > 512 * 1024 * 1024 { return Err(format!("Skipping oversized file: {}", path.display())); }
                z.start_file(name, options).map_err(|e| e.to_string())?;
                let mut f = File::open(&path).map_err(|e| e.to_string())?;
                std::io::copy(&mut f, z).map_err(|e| e.to_string())?;
                *count += 1;
            }
        }
        Ok(())
    }
    add_dir(archive, root, root, prefix, options, &mut count)?;
    Ok(count)
}

fn extract_zip_safely<R: Read + std::io::Seek>(reader: R, target: &Path, allowed_roots: &[&str]) -> Result<usize, String> {
    let mut archive = zip::ZipArchive::new(reader).map_err(|e| format!("Invalid ZIP archive: {e}"))?;
    if archive.len() > 30000 { return Err("Archive contains too many entries.".into()); }
    let mut total = 0u64;
    let mut files = 0usize;
    for i in 0..archive.len() {
        let mut entry = archive.by_index(i).map_err(|e| e.to_string())?;
        let name = entry.name().replace('\\', "/");
        let path = Path::new(&name);
        if path.is_absolute() || path.components().any(|c| !matches!(c, Component::Normal(_))) { return Err("Archive contains an unsafe path.".into()); }
        let first = path.components().next().and_then(|c| match c { Component::Normal(x) => x.to_str(), _ => None }).ok_or("Archive entry has no root folder.")?;
        if !allowed_roots.is_empty() && !allowed_roots.iter().any(|r| *r == first) { continue; }
        if entry.unix_mode().is_some_and(|m| (m & 0o170000) == 0o120000) { return Err("Archive contains a symbolic link.".into()); }
        total = total.saturating_add(entry.size());
        if total > 2 * 1024 * 1024 * 1024 { return Err("Archive expands beyond the 2 GB safety limit.".into()); }
        let out = target.join(path);
        if entry.is_dir() { fs::create_dir_all(&out).map_err(|e| e.to_string())?; continue; }
        if let Some(parent) = out.parent() { fs::create_dir_all(parent).map_err(|e| e.to_string())?; }
        let mut file = File::create(&out).map_err(|e| e.to_string())?;
        std::io::copy(&mut entry, &mut file).map_err(|e| e.to_string())?;
        files += 1;
    }
    Ok(files)
}

#[tauri::command]
fn export_modpack(app: tauri::AppHandle) -> Result<ArchiveResult, String> {
    let selected = rfd::FileDialog::new().add_filter("Modpack ZIP", &["zip"]).set_file_name("divlauncher-modpack.zip").save_file().ok_or("Export cancelled.")?;
    let game = game_dir(&app)?;
    let file = File::create(&selected).map_err(|e| e.to_string())?;
    let mut zip = zip::ZipWriter::new(file);
    use zip::write::FileOptions;
    let opts = FileOptions::default().compression_method(zip::CompressionMethod::Deflated);
    let mut count = 0usize;
    for folder in ["mods", "config", "resourcepacks", "shaderpacks"] {
        let root = game.join(folder);
        if root.exists() { count += append_directory(&mut zip, &root, folder)?; }
    }
    let manifest = serde_json::json!({"format":"divlauncher-modpack","version":1,"minecraft":"","loader":"unknown","note":"Files exported from DIVlauncher. Set the Minecraft version and loader in a compatible launcher."});
    zip.start_file("divlauncher-manifest.json", opts).map_err(|e| e.to_string())?;
    zip.write_all(manifest.to_string().as_bytes()).map_err(|e| e.to_string())?;
    zip.finish().map_err(|e| e.to_string())?;
    Ok(ArchiveResult { path: selected.display().to_string(), files: count })
}

#[tauri::command]
fn import_modpack(app: tauri::AppHandle) -> Result<ArchiveResult, String> {
    let selected = rfd::FileDialog::new().add_filter("Modpack ZIP", &["zip"]).pick_file().ok_or("Import cancelled.")?;
    let game = game_dir(&app)?;
    let staging = game.join(".divlauncher-modpack-staging");
    if staging.exists() { fs::remove_dir_all(&staging).map_err(|e| e.to_string())?; }
    fs::create_dir_all(&staging).map_err(|e| e.to_string())?;
    let file = File::open(&selected).map_err(|e| e.to_string())?;
    let count = match extract_zip_safely(file, &staging, &["mods", "config", "resourcepacks", "shaderpacks"]) {
        Ok(n) => n, Err(e) => { let _ = fs::remove_dir_all(&staging); return Err(e); }
    };
    for folder in ["mods", "config", "resourcepacks", "shaderpacks"] {
        let src = staging.join(folder); if !src.exists() { continue; }
        let dst = game.join(folder); fs::create_dir_all(&dst).map_err(|e| e.to_string())?;
        copy_tree_no_links(&src, &dst)?;
    }
    let _ = fs::remove_dir_all(&staging);
    Ok(ArchiveResult { path: selected.display().to_string(), files: count })
}

fn copy_tree_no_links(src: &Path, dst: &Path) -> Result<(), String> {
    for entry in fs::read_dir(src).map_err(|e| e.to_string())? {
        let entry = entry.map_err(|e| e.to_string())?; let from = entry.path(); let meta = fs::symlink_metadata(&from).map_err(|e| e.to_string())?;
        if meta.file_type().is_symlink() { continue; }
        let to = dst.join(entry.file_name());
        if meta.is_dir() { fs::create_dir_all(&to).map_err(|e| e.to_string())?; copy_tree_no_links(&from, &to)?; }
        else if meta.is_file() { fs::copy(&from, &to).map_err(|e| e.to_string())?; }
    }
    Ok(())
}

#[tauri::command]
fn export_world(app: tauri::AppHandle, name: String) -> Result<ArchiveResult, String> {
    if name.is_empty() || name.contains('/') || name.contains('\\') || name == "." || name == ".." { return Err("Invalid world name.".into()); }
    let root = game_category_dir(&app, "worlds")?.join(&name);
    if !root.is_dir() { return Err("World folder was not found.".into()); }
    let selected = rfd::FileDialog::new().add_filter("World ZIP", &["zip"]).set_file_name(&format!("{name}.zip")).save_file().ok_or("Export cancelled.")?;
    let file = File::create(&selected).map_err(|e| e.to_string())?;
    let mut zip = zip::ZipWriter::new(file);
    let count = append_directory(&mut zip, &root, &name)?;
    zip.finish().map_err(|e| e.to_string())?;
    Ok(ArchiveResult { path: selected.display().to_string(), files: count })
}

#[tauri::command]
fn import_world(app: tauri::AppHandle) -> Result<ArchiveResult, String> {
    let selected = rfd::FileDialog::new().add_filter("World ZIP", &["zip"]).pick_file().ok_or("Import cancelled.")?;
    let stem = selected.file_stem().and_then(|s| s.to_str()).unwrap_or("Imported World");
    let safe_name: String = stem.chars().map(|c| if c.is_ascii_alphanumeric() || c == ' ' || c == '_' || c == '-' { c } else { '_' }).collect();
    let name = safe_name.trim().to_string(); if name.is_empty() { return Err("Could not determine a world folder name.".into()); }
    let saves = game_category_dir(&app, "worlds")?; let dest = saves.join(&name);
    if dest.exists() { return Err(format!("A world named '{name}' already exists. Rename or move it before importing.")); }
    let staging = saves.join(format!(".divlauncher-import-{}", std::process::id()));
    if staging.exists() { fs::remove_dir_all(&staging).map_err(|e| e.to_string())?; }
    fs::create_dir_all(&staging).map_err(|e| e.to_string())?;
    let file = File::open(&selected).map_err(|e| e.to_string())?;
    let count = match extract_zip_safely(file, &staging, &[]) {
        Ok(n) => n, Err(e) => { let _ = fs::remove_dir_all(&staging); return Err(e); }
    };
    // Accept either a named top-level folder or a direct world-folder archive,
    // but require Minecraft's level.dat before moving anything into saves/.
    let nested = staging.join(&name);
    let source = if nested.join("level.dat").is_file() { nested } else { staging.clone() };
    if !source.join("level.dat").is_file() { let _ = fs::remove_dir_all(&staging); return Err("This ZIP does not appear to contain a Minecraft world (level.dat is missing).".into()); }
    fs::rename(&source, &dest).map_err(|e| e.to_string())?;
    let _ = fs::remove_dir_all(&staging);
    Ok(ArchiveResult { path: dest.display().to_string(), files: count })
}


fn main() {
    tauri::Builder::default().invoke_handler(tauri::generate_handler![app_info, list_versions, offline_uuid, load_state, save_state, install_version, is_version_installed, launch_version, search_mods, install_mod, list_installed_mods, remove_mod, java_diagnostics, install_java_runtime, list_game_files, delete_game_file, open_game_folder, export_modpack, import_modpack, export_world, import_world])
        .run(tauri::generate_context!()).expect("error while running DIVlauncher");
}
