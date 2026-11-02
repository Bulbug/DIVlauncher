// No console window in release builds on Windows.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::fs;
use std::io::ErrorKind;
use std::path::PathBuf;
use std::time::Duration;

use serde::Serialize;
use tauri::Manager;

const MANIFEST_URL: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

#[derive(Serialize)]
struct AppInfo {
    name: String,
    version: String,
    data_dir: String,
    os: String,
}

#[derive(Serialize)]
struct VersionList {
    latest_release: String,
    latest_snapshot: String,
    versions: Vec<divcore::VersionEntry>,
}

/// Folder for everything the launcher stores (on Windows: %APPDATA%\com.divlauncher.desktop).
fn data_dir(app: &tauri::AppHandle) -> Result<PathBuf, String> {
    let dir = app.path().app_data_dir().map_err(|e| e.to_string())?;
    fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    Ok(dir)
}

#[tauri::command]
fn app_info(app: tauri::AppHandle) -> Result<AppInfo, String> {
    Ok(AppInfo {
        name: app.package_info().name.clone(),
        version: app.package_info().version.to_string(),
        data_dir: data_dir(&app)?.display().to_string(),
        os: std::env::consts::OS.to_string(),
    })
}

/// Downloads and parses Mojang's list of Minecraft versions.
#[tauri::command]
async fn list_versions() -> Result<VersionList, String> {
    tauri::async_runtime::spawn_blocking(|| {
        let body = ureq::get(MANIFEST_URL)
            .timeout(Duration::from_secs(20))
            .call()
            .map_err(|e| format!("Could not reach Mojang: {e}"))?
            .into_string()
            .map_err(|e| e.to_string())?;
        let manifest = divcore::parse_manifest(&body).map_err(|e| format!("Unexpected version list: {e}"))?;
        Ok(VersionList {
            latest_release: manifest.latest_release,
            latest_snapshot: manifest.latest_snapshot,
            versions: manifest.versions,
        })
    })
    .await
    .map_err(|e| e.to_string())?
}

/// UUID of an offline player, after checking the name.
#[tauri::command]
fn offline_uuid(name: String) -> Result<String, String> {
    if !divcore::is_valid_username(&name) {
        return Err("A username needs 3 to 16 letters, numbers or underscores.".into());
    }
    Ok(divcore::offline_uuid(&name))
}

/// The saved accounts and profiles, as JSON text ("{}" the first time).
#[tauri::command]
fn load_state(app: tauri::AppHandle) -> Result<String, String> {
    let path = data_dir(&app)?.join("state.json");
    match fs::read_to_string(&path) {
        Ok(text) => Ok(text),
        Err(e) if e.kind() == ErrorKind::NotFound => Ok("{}".into()),
        Err(e) => Err(e.to_string()),
    }
}

/// Saves the accounts and profiles. Written to a temporary file first so a crash cannot leave half a file.
#[tauri::command]
fn save_state(app: tauri::AppHandle, json: String) -> Result<(), String> {
    serde_json::from_str::<serde_json::Value>(&json).map_err(|e| format!("Not valid JSON: {e}"))?;
    let dir = data_dir(&app)?;
    let tmp = dir.join("state.json.tmp");
    fs::write(&tmp, json).map_err(|e| e.to_string())?;
    fs::rename(&tmp, dir.join("state.json")).map_err(|e| e.to_string())
}

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            app_info,
            list_versions,
            offline_uuid,
            load_state,
            save_state
        ])
        .run(tauri::generate_context!())
        .expect("error while running DIVlauncher");
}
