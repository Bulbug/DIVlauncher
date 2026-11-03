'use strict';
/* DIVlauncher for Windows: window logic. Talks to the Rust side through Tauri commands
   (app_info, list_versions, offline_uuid, load_state, save_state, install_version). In a plain browser it uses small stand-ins,
   so the window can be previewed without building the app. */

const tauri = window.__TAURI__ && window.__TAURI__.core;
const invoke = (cmd, args) => (tauri ? tauri.invoke(cmd, args) : mock(cmd, args));

function mock(cmd, args) {
  switch (cmd) {
    case 'app_info': return Promise.resolve({ name: 'DIVlauncher', version: 'browser preview', data_dir: '(browser preview)', os: 'browser' });
    case 'java_diagnostics': return Promise.resolve({ available: false, path: null, major: null, message: 'Java diagnostics are unavailable in browser preview.' });
    case 'install_java_runtime': return Promise.resolve({ major: args.major, java: '(browser preview)', runtime_dir: '(browser preview)' });
    case 'list_versions': return Promise.resolve({ latest_release: '1.21.4', latest_snapshot: '25w02a', versions: [
      { id: '25w02a', type: 'snapshot', url: '', releaseTime: '2025-01-08' },
      { id: '1.21.4', type: 'release', url: '', releaseTime: '2024-12-03' },
      { id: '1.20.1', type: 'release', url: '', releaseTime: '2023-06-12' },
      { id: '1.12.2', type: 'release', url: '', releaseTime: '2017-09-18' },
      { id: 'b1.7.3', type: 'old_beta', url: '', releaseTime: '2011-07-08' }] });
    case 'offline_uuid':
      return /^[A-Za-z0-9_]{3,16}$/.test(args.name) ? Promise.resolve('00000000-0000-3000-8000-000000000000')
        : Promise.reject('A username needs 3 to 16 letters, numbers or underscores.');
    case 'load_state': return Promise.resolve(localStorage.getItem('div-state') || '{}');
    case 'save_state': localStorage.setItem('div-state', args.json); return Promise.resolve();
    case 'install_version': return Promise.resolve({ version: args.version, directory: '(browser preview)', files_downloaded: 0, assets_downloaded: 0, preview: true });
    case 'is_version_installed': return Promise.resolve(false);
    case 'launch_version': return Promise.resolve({ version: args.version, pid: 0, log_file: '(browser preview)', java: '(browser preview)', preview: true });
    case 'search_mods': return Promise.resolve([{ project_id: 'sample-project', slug: 'sample-mod', title: 'Preview mod', description: 'Sample result shown in browser preview.', downloads: 1234 }]);
    case 'install_mod': return Promise.resolve({ title: 'Preview mod', filename: 'preview-mod.jar', path: '(browser preview)', version: args.gameVersion });
    case 'list_installed_mods': return Promise.resolve([]);
    case 'list_game_files': return Promise.resolve([]);
    case 'delete_game_file': return Promise.resolve();
    case 'open_game_folder': return Promise.resolve();
    case 'export_modpack': return Promise.resolve({ path: '(browser preview)', files: 0, preview: true });
    case 'import_modpack': return Promise.resolve({ path: '(browser preview)', files: 0, preview: true });
    case 'export_world': return Promise.resolve({ path: '(browser preview)', files: 0, preview: true });
    case 'import_world': return Promise.resolve({ path: '(browser preview)', files: 0, preview: true });
    case 'remove_mod': return Promise.resolve();
    default: return Promise.reject('unknown command ' + cmd);
  }
}

const $ = (id) => document.getElementById(id);
const el = (tag, props = {}, ...kids) => {
  const n = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k === 'class') n.className = v;
    else if (k === 'text') n.textContent = v;
    else if (k.startsWith('on')) n.addEventListener(k.slice(2), v);
    else if (k === 'style') n.setAttribute('style', v);
    else n.setAttribute(k, v);
  }
  for (const kid of kids.flat()) if (kid != null) n.append(kid);
  return n;
};
const uid = () => Math.random().toString(36).slice(2, 10);

let info = { version: '', data_dir: '', os: '' };
let state = { accounts: [], account: null, profiles: [], profile: null };
let versions = null;
let screen = 'home';

/* ---------- saved state ---------- */
let saveTimer = null;
function save() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => invoke('save_state', { json: JSON.stringify(state) }).catch((e) => toast('Could not save: ' + e)), 150);
}
async function load() {
  try { state = Object.assign(state, JSON.parse(await invoke('load_state'))); } catch (e) { toast('Could not read saved data: ' + e); }
  if (!Array.isArray(state.accounts)) state.accounts = [];
  if (!Array.isArray(state.profiles)) state.profiles = [];
  if (!state.settings || typeof state.settings !== 'object') state.settings = {};
  if (!Number.isFinite(Number(state.settings.memory_mb))) state.settings.memory_mb = 4096;
  if (state.profiles.length === 0) {
    state.profiles.push({ id: uid(), name: 'Latest release', version: 'latest-release' });
  }
  if (!state.profiles.some((p) => p.id === state.profile)) state.profile = state.profiles[0].id;
  if (!state.accounts.some((a) => a.name === state.account)) state.account = state.accounts[0] ? state.accounts[0].name : null;
}
const currentProfile = () => state.profiles.find((p) => p.id === state.profile);
const versionLabel = (p) => (p.version === 'latest-release' ? (versions ? versions.latest_release + ' (latest release)' : 'Latest release') : p.version);

/* ---------- small helpers ---------- */
let toastTimer = null;
function toast(text) {
  const t = $('toast');
  t.textContent = String(text);
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (t.hidden = true), 4500);
}
function pixels(seed, rows) {
  const art = el('div', { class: 'art' });
  const alpha = [.9, .15, .5, .25, .7, .1, .4, .6, .2, .8, .3, .55, .12, .65, .35, .85];
  for (let i = 0; i < 8 * rows; i++) art.append(el('i', { style: `opacity:${alpha[(i + seed) % alpha.length]}` }));
  return art;
}
function closePopups() { $('profile-popup').hidden = true; $('account-popup').hidden = true; }

/* ---------- dialogs ---------- */
function dialog(title, text, body, buttons) {
  const d = $('dialog');
  d.replaceChildren(el('h2', { text: title }), text ? el('p', { text }) : null, body, el('div', { class: 'buttons' }, buttons));
  $('scrim').hidden = false;
  const first = d.querySelector('input,select');
  if (first) first.focus();
}
function closeDialog() { $('scrim').hidden = true; $('dialog').replaceChildren(); }

function addAccountDialog() {
  const input = el('input', { type: 'text', maxlength: '16', placeholder: 'For example Steve_01', autocomplete: 'off', spellcheck: 'false' });
  const err = el('p', { class: 'err' });
  const submit = async () => {
    const name = input.value.trim();
    try {
      const uuid = await invoke('offline_uuid', { name });
      if (state.accounts.some((a) => a.name.toLowerCase() === name.toLowerCase())) { err.textContent = 'That account already exists.'; return; }
      state.accounts.push({ name, uuid, type: 'offline' });
      state.account = name;
      save(); closeDialog(); render(); toast('Offline account "' + name + '" added.');
    } catch (e) { err.textContent = String(e); }
  };
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter') submit(); });
  dialog('Add an offline account', 'Pick any username. Offline accounts cannot join online servers that check your account.',
    el('label', { class: 'field' }, el('span', { text: 'Username' }), input), [
      el('div', { style: 'flex:1' }, err),
      el('button', { class: 'btn', text: 'Cancel', onclick: closeDialog }),
      el('button', { class: 'btn fill', text: 'Add account', onclick: submit })]);
}

async function newProfileDialog() {
  const name = el('input', { type: 'text', maxlength: '40', value: 'Profile ' + (state.profiles.length + 1), autocomplete: 'off' });
  const select = el('select');
  const showAll = el('input', { type: 'checkbox' });
  const err = el('p', { class: 'err' });
  const fill = () => {
    select.replaceChildren(el('option', { value: 'latest-release', text: 'Latest release' }));
    if (!versions) return;
    for (const v of versions.versions) if (showAll.checked || v.type === 'release') select.append(el('option', { value: v.id, text: v.id + (v.type === 'release' ? '' : '  (' + v.type.replace('_', ' ') + ')') }));
  };
  showAll.addEventListener('change', fill);
  fill();
  if (!versions) {
    try { versions = await invoke('list_versions'); fill(); } catch (e) { err.textContent = String(e) + ' You can still use "Latest release".'; }
  }
  const create = () => {
    const n = name.value.trim();
    if (!n) { err.textContent = 'Give the profile a name.'; return; }
    const p = { id: uid(), name: n, version: select.value };
    state.profiles.push(p); state.profile = p.id;
    save(); closeDialog(); render(); toast('Profile "' + n + '" created.');
  };
  dialog('New profile', 'A profile is a Minecraft version with its own settings.',
    el('div', {}, el('label', { class: 'field' }, el('span', { text: 'Name' }), name), el('label', { class: 'field' }, el('span', { text: 'Version' }), select),
      el('label', { class: 'check' }, showAll, 'Show snapshots and old versions')), [
      el('div', { style: 'flex:1' }, err),
      el('button', { class: 'btn', text: 'Cancel', onclick: closeDialog }),
      el('button', { class: 'btn fill', text: 'Create', onclick: create })]);
}

function renameProfileDialog(profile) {
  const input = el('input', { type: 'text', maxlength: '40', value: profile.name, autocomplete: 'off' });
  const err = el('p', { class: 'err' });
  const saveName = () => {
    const name = input.value.trim();
    if (!name) { err.textContent = 'Give the profile a name.'; return; }
    profile.name = name; save(); closeDialog(); render(); toast('Profile renamed.');
  };
  input.addEventListener('keydown', e => { if (e.key === 'Enter') saveName(); });
  dialog('Rename profile', 'Change the display name without changing its Minecraft version.', el('label', { class: 'field' }, el('span', { text: 'Profile name' }), input), [
    el('div', { style: 'flex:1' }, err), el('button', { class: 'btn', text: 'Cancel', onclick: closeDialog }), el('button', { class: 'btn fill', text: 'Save', onclick: saveName })]);
}
async function renameAccountDialog(account) {
  const input = el('input', { type: 'text', maxlength: '16', value: account.name, autocomplete: 'off', spellcheck: 'false' });
  const err = el('p', { class: 'err' });
  const saveName = async () => {
    const name = input.value.trim();
    try {
      const uuid = await invoke('offline_uuid', { name });
      if (state.accounts.some(a => a !== account && a.name.toLowerCase() === name.toLowerCase())) { err.textContent = 'That account already exists.'; return; }
      const oldName = account.name; account.name = name; account.uuid = uuid;
      if (state.account === oldName) state.account = name;
      save(); closeDialog(); render(); toast('Offline account renamed.');
    } catch(e) { err.textContent = String(e); }
  };
  input.addEventListener('keydown', e => { if (e.key === 'Enter') saveName(); });
  dialog('Rename offline account', 'The offline UUID is recalculated for the new username.', el('label', { class: 'field' }, el('span', { text: 'Username' }), input), [
    el('div', { style: 'flex:1' }, err), el('button', { class: 'btn', text: 'Cancel', onclick: closeDialog }), el('button', { class: 'btn fill', text: 'Save', onclick: saveName })]);
}

function confirmDialog(title, text, okText, onOk) {
  dialog(title, text, null, [
    el('button', { class: 'btn', text: 'Cancel', onclick: closeDialog }),
    el('button', { class: 'btn danger', text: okText, onclick: () => { closeDialog(); onOk(); } })]);
}

/* ---------- screens ---------- */
function tile(color, icon, title, sub, onclick) {
  return el('button', { class: 'tile', onclick }, el('span', { class: 'ic', style: 'background:' + color }, icon), el('b', { text: title }), el('span', { text: sub }));
}
const svgIcon = (path) => { const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg'); s.setAttribute('viewBox', '0 0 24 24'); const p = document.createElementNS('http://www.w3.org/2000/svg', 'path'); p.setAttribute('d', path); s.append(p); return s; };

function homeScreen() {
  const p = currentProfile();
  return el('div', {},
    el('div', { class: 'card hero' }, pixels(0, 3),
      el('div', { style: 'flex:1' }, el('div', { class: 'mu', text: 'Continue playing' }), el('h2', { text: p.name }), el('div', { class: 'mu', text: versionLabel(p) })),
      el('button', { class: 'btn', text: 'Edit profiles', onclick: () => go('profiles') })),
    el('div', { class: 'tiles' },
      tile('var(--accent)', svgIcon('M12 5v14M5 12h14'), 'New profile', 'Pick a version', newProfileDialog),
      tile('var(--mint)', svgIcon('M12 3l8 4.5v9L12 21l-8-4.5v-9zM12 12l8-4.5M12 12v9M12 12L4 7.5'), 'Browse mods', 'Search Modrinth', () => go('mods')),
      tile('var(--peach)', svgIcon('M4 20c0-3.6 3-6 8-6s8 2.4 8 6M12 4a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z'), 'Add account', 'Offline for now', addAccountDialog),
      tile('var(--neutral)', svgIcon('M4 7h10M18 7h2M4 17h2M10 17h10'), 'Settings', 'About and accounts', () => go('settings'))));
}

function profilesScreen() {
  const grid = el('div', { class: 'grid' });
  state.profiles.forEach((p, i) => {
    const sel = p.id === state.profile;
    grid.append(el('div', { class: 'card pcard' + (sel ? ' sel' : '') },
      el('div', { class: 'top' }, pixels(i * 3, 2), el('div', {}, el('b', { text: p.name }), el('div', { class: 'mu', text: versionLabel(p) }))),
      el('div', { class: 'actions' },
        el('button', { class: 'btn small' + (sel ? ' fill' : ''), text: sel ? 'Selected' : 'Select', onclick: () => { state.profile = p.id; save(); render(); } }),
        el('button', { class: 'btn small', text: 'Rename', onclick: () => renameProfileDialog(p) }),
        state.profiles.length > 1 ? el('button', { class: 'btn small danger', text: 'Delete', onclick: () => confirmDialog('Delete "' + p.name + '"?', 'The profile is removed from the launcher.', 'Delete', () => {
          state.profiles = state.profiles.filter((x) => x.id !== p.id);
          if (state.profile === p.id) state.profile = state.profiles[0].id;
          save(); render();
        }) }) : null)));
  });
  const exportButton = el('button', { class: 'btn', text: 'Export backup', onclick: exportLauncherBackup });
  const importInput = el('input', { type: 'file', accept: '.json,application/json', style: 'display:none', onchange: async (event) => {
    const file = event.target.files && event.target.files[0];
    if (!file) return;
    try {
      const backup = JSON.parse(await file.text());
      if (backup.format !== 'divlauncher-backup' || backup.version !== 1 || !backup.state || typeof backup.state !== 'object' || Array.isArray(backup.state)) throw new Error('This is not a supported DIVlauncher backup.');
      const candidate = backup.state;
      if (!Array.isArray(candidate.profiles) || candidate.profiles.length < 1 || candidate.profiles.length > 100) throw new Error('Backup must contain between 1 and 100 profiles.');
      if (!candidate.profiles.every(p => p && typeof p === 'object' && typeof p.id === 'string' && p.id.length > 0 && p.id.length <= 80 && typeof p.name === 'string' && p.name.length > 0 && p.name.length <= 80 && typeof p.version === 'string' && p.version.length > 0 && p.version.length <= 80)) throw new Error('Backup contains an invalid profile entry.');
      if (candidate.accounts !== undefined && (!Array.isArray(candidate.accounts) || candidate.accounts.length > 100 || !candidate.accounts.every(a => a && typeof a === 'object' && typeof a.name === 'string' && a.name.length <= 16 && typeof a.uuid === 'string' && /^[0-9a-f-]{36}$/i.test(a.uuid)))) throw new Error('Backup contains invalid account metadata.');
      if (candidate.settings !== undefined && (!candidate.settings || typeof candidate.settings !== 'object' || Array.isArray(candidate.settings))) throw new Error('Backup settings must be an object.');
      if (!confirm('Import this backup? Existing launcher settings, profiles and accounts will be replaced.')) return;
      state = { profiles: candidate.profiles, accounts: candidate.accounts || [], settings: candidate.settings || {}, profile: candidate.profile, account: candidate.account };
      if (!state.profiles.some(p => p.id === state.profile)) state.profile = state.profiles[0].id;
      if (!state.accounts.some(a => a.name === state.account)) state.account = state.accounts[0]?.name || null;
      save(); render(); toast('Backup imported successfully.');
    } catch (error) { toast('Could not import backup: ' + error.message); }
    finally { event.target.value = ''; }
  }});
  const importButton = el('button', { class: 'btn', text: 'Import backup', onclick: () => importInput.click() });
  return el('div', {}, el('div', { class: 'toolbar' }, el('span', { class: 'mu', text: state.profiles.length + ' profile' + (state.profiles.length === 1 ? '' : 's') }),
    el('button', { class: 'btn', text: '+ New profile', onclick: newProfileDialog }), exportButton, importButton, importInput), grid);
}

function exportLauncherBackup() {
  const payload = { format: 'divlauncher-backup', version: 1, exported_at: new Date().toISOString(), state };
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `divlauncher-backup-${new Date().toISOString().slice(0, 10)}.json`;
  document.body.appendChild(link); link.click(); link.remove(); URL.revokeObjectURL(url);
  toast('Launcher backup exported. Keep it somewhere safe.');
}

function modsScreen() {
  const query = el('input', { type: 'search', placeholder: 'Search Modrinth mods…', value: modSearchQuery, autocomplete: 'off' });
  const loader = el('select', {}, ...['fabric','forge','neoforge','quilt'].map(x => el('option', { value: x, text: x[0].toUpperCase() + x.slice(1) })));
  loader.value = modLoader;
  const status = el('p', { class: 'mu', text: modStatus });
  const results = el('div', { class: 'stack', style: 'max-width:none;margin-top:12px' });
  const installedBox = el('div', { class: 'card', style: 'margin-top:14px' }, el('h3', { text: 'Downloaded mod files', style: 'margin:0 0 10px' }));
  const renderInstalled = async () => {
    installedBox.replaceChildren(el('h3', { text: 'Downloaded mod files', style: 'margin:0 0 10px' }));
    try {
      const items = await invoke('list_installed_mods');
      if (!items.length) installedBox.append(el('p', { class: 'mu', text: 'No .jar files in the mods folder yet.' }));
      for (const item of items) installedBox.append(el('div', { class: 'row', style: 'cursor:default' }, el('div', { style: 'flex:1;min-width:0' }, el('b', { text: item.filename }), el('small', { text: formatBytes(item.size_bytes) })), el('button', { class: 'btn small danger', text: 'Remove', onclick: async () => { try { await invoke('remove_mod', { filename: item.filename }); toast('Removed ' + item.filename); renderInstalled(); } catch(e) { toast('Could not remove mod: ' + e); } } })));
    } catch(e) { installedBox.append(el('p', { class: 'mu', text: String(e) })); }
  };
  const search = async () => {
    modSearchQuery = query.value.trim(); modLoader = loader.value;
    results.replaceChildren(); status.textContent = 'Searching Modrinth…';
    try {
      const version = await selectedVersionId();
      if (!version) throw new Error('Choose a Minecraft profile first.');
      const hits = await invoke('search_mods', { query: modSearchQuery, gameVersion: version, loader: modLoader });
      status.textContent = hits.length ? `${hits.length} results for Minecraft ${version} · ${modLoader}` : 'No compatible mods found. Try another search or loader.';
      for (const hit of hits) {
        const install = el('button', { class: 'btn small', text: 'Download', onclick: async () => {
          install.disabled = true; install.textContent = 'Downloading…';
          try { const done = await invoke('install_mod', { projectId: hit.project_id, gameVersion: version, loader: modLoader }); toast('Downloaded ' + done.filename); await renderInstalled(); }
          catch(e) { toast('Could not download mod: ' + e); }
          finally { install.disabled = false; install.textContent = 'Download'; }
        }});
        results.append(el('div', { class: 'card', style: 'display:flex;gap:12px;align-items:flex-start' },
          hit.icon_url ? el('img', { src: hit.icon_url, alt: '', style: 'width:48px;height:48px;border-radius:12px;object-fit:cover' }) : el('div', { class: 'sq', style: 'width:48px;height:48px;border-radius:12px;background:var(--surface2);flex:none' }),
          el('div', { style: 'flex:1;min-width:0' }, el('b', { text: hit.title }), el('p', { class: 'mu', style: 'margin:5px 0', text: hit.description }), el('small', { class: 'mu', text: `${Number(hit.downloads || 0).toLocaleString()} downloads · ${hit.slug}` })), install));
      }
    } catch(e) { status.textContent = 'Search failed: ' + e; }
  };
  const packActions = el('div', { class: 'toolbar' },
    el('button', { class: 'btn small', text: 'Export modpack ZIP', onclick: async () => { try { const r = await invoke('export_modpack'); toast(`Exported ${r.files} files to ${r.path}`); } catch(e) { if (!String(e).includes('cancelled')) toast('Modpack export failed: ' + e); } } }),
    el('button', { class: 'btn small', text: 'Import modpack ZIP', onclick: async () => { if (!confirm('Import mod files and configuration into this Minecraft folder? Existing files with matching names may be replaced.')) return; try { const r = await invoke('import_modpack'); toast(`Imported ${r.files} files from ${r.path}`); await renderInstalled(); } catch(e) { if (!String(e).includes('cancelled')) toast('Modpack import failed: ' + e); } } }));
  const header = el('div', { class: 'card' }, el('h2', { text: 'Modrinth browser', style: 'margin:0 0 8px' }),
    el('p', { class: 'mu', style: 'font-size:13px;line-height:1.5;margin:0 0 12px', text: 'Search and download mod JARs compatible with the selected Minecraft version and loader. This build manages mod files, but launching Fabric/Forge/NeoForge/Quilt profiles is not implemented yet; vanilla Minecraft will not load most mods.' }),
    el('div', { class: 'mod-search' }, el('label', { class: 'field', style: 'flex:1;margin:0' }, el('span', { text: 'Search' }), query), el('label', { class: 'field', style: 'width:150px;margin:0' }, el('span', { text: 'Loader' }), loader), el('button', { class: 'btn fill', text: 'Search', onclick: search })), status, packActions);
  query.addEventListener('keydown', e => { if (e.key === 'Enter') search(); });
  renderInstalled();
  return el('div', {}, header, results, installedBox);
}
let modSearchQuery = '';
let modLoader = 'fabric';
let modStatus = 'Search the Modrinth catalog for a selected Minecraft version.';
function formatBytes(bytes) { if (!bytes) return 'Size unknown'; const units=['B','KB','MB','GB']; let n=bytes,i=0; while(n>=1024&&i<units.length-1){n/=1024;i++;} return `${n.toFixed(i ? 1 : 0)} ${units[i]}`; }



const libraryCategories = [
  { id: 'worlds', label: 'Worlds', note: 'Saved single-player worlds' },
  { id: 'screenshots', label: 'Screenshots', note: 'In-game screenshots' },
  { id: 'resourcepacks', label: 'Resource packs', note: 'Texture and resource packs' },
  { id: 'shaderpacks', label: 'Shader packs', note: 'Shader pack files' },
  { id: 'logs', label: 'Logs', note: 'Game and launcher logs' },
];
let libraryCategory = 'worlds';
let selectedWorldName = null;
function libraryScreen() {
  const select = el('select', {}, ...libraryCategories.map(c => el('option', { value: c.id, text: c.label })));
  select.value = libraryCategory;
  const list = el('div', { class: 'stack', style: 'max-width:none;margin-top:14px' });
  const status = el('p', { class: 'mu', text: 'Browse files in the launcher game directory.' });
  const refresh = async () => {
    libraryCategory = select.value; list.replaceChildren(); status.textContent = 'Loading…';
    try {
      const items = await invoke('list_game_files', { category: libraryCategory });
      status.textContent = `${items.length} item${items.length === 1 ? '' : 's'} · ${libraryCategories.find(c => c.id === libraryCategory).note}`;
      if (!items.length) list.append(el('div', { class: 'card' }, el('p', { class: 'mu', text: 'Nothing here yet. This folder will populate as you play or add files.' })));
      for (const item of items) {
        const row = el('div', { class: 'row', style: 'cursor:default' });
        if (libraryCategory === 'worlds' && item.kind === 'folder') { row.style.cursor = 'pointer'; row.addEventListener('click', () => { selectedWorldName = item.name; toast('Selected world: ' + item.name); }); }
        row.append( el('div', { class: 'sq', text: item.kind === 'folder' ? '📁' : '▤' }),
          el('div', { style: 'flex:1;min-width:0' }, el('b', { text: item.name }), el('small', { text: item.kind === 'folder' ? 'Folder' : formatBytes(item.size_bytes) })),
          el('button', { class: 'btn small danger', text: 'Delete', onclick: (event) => { event.stopPropagation(); confirmDialog(`Delete “${item.name}”?`, 'This permanently deletes this item from the selected Minecraft folder.', 'Delete', async () => { try { await invoke('delete_game_file', { category: libraryCategory, name: item.name }); toast('Deleted ' + item.name); await refresh(); } catch (e) { toast('Delete failed: ' + e); } }); } }));
        list.append(row);
      }
    } catch (e) { status.textContent = 'Could not read folder: ' + e; }
  };
  select.addEventListener('change', refresh);
  const open = el('button', { class: 'btn', text: 'Open folder in Explorer', onclick: async () => { try { await invoke('open_game_folder', { category: libraryCategory }); } catch(e) { toast('Could not open folder: ' + e); } } });
  const worldActions = el('div', { class: 'toolbar' },
    el('button', { class: 'btn small', text: 'Import world ZIP', onclick: async () => { try { const r = await invoke('import_world'); toast(`Imported world (${r.files} files): ${r.path}`); await refresh(); } catch(e) { if (!String(e).includes('cancelled')) toast('World import failed: ' + e); } } }),
    el('button', { class: 'btn small', text: 'Export selected world', onclick: async () => { const item = list.querySelector('[data-selected-world]'); if (!selectedWorldName) { toast('Choose a world from the list first.'); return; } try { const r = await invoke('export_world', { name: selectedWorldName }); toast(`Exported ${r.files} files to ${r.path}`); } catch(e) { if (!String(e).includes('cancelled')) toast('World export failed: ' + e); } } }));
  const reload = el('button', { class: 'btn', text: 'Refresh', onclick: refresh });
  refresh();
  return el('div', {}, el('div', { class: 'card' }, el('h2', { text: 'Minecraft library', style: 'margin:0 0 8px' }), el('p', { class: 'mu', text: 'Manage worlds, screenshots, resource packs, shader packs and logs. Deleting a world or file is permanent.' }), el('div', { class: 'toolbar' }, select, open, reload), ...(libraryCategory === 'worlds' ? [worldActions] : []), status), list);
}

function settingsScreen() {
  const accounts = el('div', {});
  if (state.accounts.length === 0) accounts.append(el('p', { class: 'mu', text: 'No accounts yet.' }));
  for (const a of state.accounts) {
    accounts.append(el('div', { class: 'row', style: 'cursor:default' }, el('i', { class: 'sq' }), el('div', {}, el('b', { text: a.name }), el('small', { text: 'Offline · ' + a.uuid })),
      el('button', { class: 'btn small', text: 'Rename', onclick: () => renameAccountDialog(a) }),
      el('button', { class: 'btn small danger', style: 'margin-left:auto', text: 'Remove', onclick: () => confirmDialog('Remove "' + a.name + '"?', 'The account is removed from the launcher.', 'Remove', () => {
        state.accounts = state.accounts.filter((x) => x.name !== a.name);
        if (state.account === a.name) state.account = state.accounts[0] ? state.accounts[0].name : null;
        save(); render();
      }) })));
  }
  accounts.append(el('button', { class: 'btn small', style: 'margin-top:10px', text: '+ Add account', onclick: addAccountDialog }));
  const kv = (k, v) => [el('dt', { text: k }), el('dd', { text: v })];
  const memory = el('input', { type: 'range', min: '1024', max: '16384', step: '512', value: String(state.settings.memory_mb || 4096) });
  const jvmArgs = el('textarea', { class: 'input', rows: '4', placeholder: '-XX:+UseG1GC\n-Dsome.option=value', style: 'width:100%;resize:vertical' });
  jvmArgs.value = String(state.settings.jvm_args || '');
  jvmArgs.addEventListener('change', () => { state.settings.jvm_args = jvmArgs.value.split(/\r?\n/).map(x => x.trim()).filter(Boolean).slice(0, 20); save(); });
  const memoryValue = el('b', { text: formatBytes((state.settings.memory_mb || 4096) * 1024 * 1024) });
  memory.addEventListener('input', () => { state.settings.memory_mb = Number(memory.value); memoryValue.textContent = `${Math.round(Number(memory.value)/1024*10)/10} GB`; save(); });
  return el('div', { class: 'stack' },
    el('div', { class: 'card' }, el('p', { class: 'section', text: 'Performance' }), el('label', { class: 'field' }, el('span', { text: 'Maximum memory (RAM)' }), memory), el('div', { class: 'mu', text: 'Selected: ' }, memoryValue), el('p', { class: 'mu', style: 'margin:8px 0 0', text: 'This sets the Java maximum heap on the next launch. Keep enough memory available for Windows and other apps.' }), el('label', { class: 'field' }, el('span', { text: 'Additional JVM arguments (one per line)' }), jvmArgs), el('p', { class: 'mu', text: 'Advanced setting. Invalid options can prevent Minecraft from starting. Heap, classpath and Java agent options are managed by DIVlauncher.' })),
    el('div', { class: 'card' }, el('p', { class: 'section', text: 'Accounts' }), accounts),
    el('div', { class: 'card' }, el('p', { class: 'section', text: 'Java runtime' }),
      el('p', { id: 'java-diagnostics-status', class: 'mu', text: 'Checking Java runtime…', style: 'overflow-wrap:anywhere' }),
      el('div', { class: 'toolbar' },
        el('button', { class: 'btn small', text: 'Check Java again', onclick: () => refreshJavaDiagnostics() }),
        ...[8, 17, 21].map((major) => el('button', { class: 'btn small', text: `Install Java ${major}`, onclick: async () => {
          const node = $('java-diagnostics-status'); node.textContent = `Downloading and installing Java ${major}…`;
          try { const result = await invoke('install_java_runtime', { major }); node.textContent = `Java ${result.major} installed. Path: ${result.java}`; }
          catch (error) { node.textContent = `Java ${major} installation failed: ${error}`; }
        } })))),
    el('div', { class: 'card' }, el('p', { class: 'section', text: 'About' }),
      el('dl', { class: 'kv' }, kv('Version', info.version), kv('System', info.os), kv('Saved data', info.data_dir))));
}

async function refreshJavaDiagnostics() {
  const node = $('java-diagnostics-status');
  if (!node) return;
  node.textContent = 'Checking Java runtime…';
  try {
    const result = await invoke('java_diagnostics');
    node.textContent = result.message + (result.path ? `\nPath: ${result.path}` : '');
  } catch (error) { node.textContent = 'Java check failed: ' + error; }
}

const screens = { home: ['Home', homeScreen], profiles: ['Profiles', profilesScreen], mods: ['Mods', modsScreen], library: ['Library', libraryScreen], settings: ['Settings', settingsScreen] };
function go(name) { screen = name; closePopups(); render(); }

/* ---------- top bar and popups ---------- */
function renderTopbar() {
  const p = currentProfile();
  $('profile-name').textContent = p.name;
  $('profile-version').textContent = versionLabel(p);
  const a = state.accounts.find((x) => x.name === state.account);
  $('account-name').textContent = a ? a.name : 'Add account';
  $('account-avatar').textContent = a ? a.name[0].toUpperCase() : '+';

  const pp = $('profile-popup');
  pp.replaceChildren(...state.profiles.map((x) => el('button', { class: 'row', onclick: () => { state.profile = x.id; save(); closePopups(); render(); } },
    el('i', { class: 'sq' }), el('div', {}, el('b', { text: x.name }), el('small', { text: versionLabel(x) })), x.id === state.profile ? el('span', { class: 'tag', text: 'Selected' }) : null)));

  const ap = $('account-popup');
  ap.replaceChildren(el('button', { class: 'row add', onclick: () => { closePopups(); addAccountDialog(); } }, el('b', { text: '+ Add account' })),
    ...state.accounts.map((x) => el('button', { class: 'row', onclick: () => { state.account = x.name; save(); closePopups(); render(); } },
      el('i', { class: 'sq' }), el('div', {}, el('b', { text: x.name }), el('small', { text: 'Offline' })), x.name === state.account ? el('span', { class: 'tag', text: 'Active' }) : null)));
}

function render() {
  const [title, build] = screens[screen];
  $('title').textContent = title;
  document.querySelectorAll('.nav').forEach((n) => n.classList.toggle('on', n.dataset.screen === screen));
  renderTopbar();
  $('content').replaceChildren(build());
  if (screen === 'settings') refreshJavaDiagnostics();
  refreshPlayButton();
}

/* ---------- start ---------- */
function toggle(popupId, otherId) {
  const pop = $(popupId);
  $(otherId).hidden = true;
  pop.hidden = !pop.hidden;
}
document.querySelectorAll('.nav').forEach((n) => n.addEventListener('click', () => go(n.dataset.screen)));
$('profile-pill').addEventListener('click', (e) => { e.stopPropagation(); toggle('profile-popup', 'account-popup'); });
$('account-pill').addEventListener('click', (e) => { e.stopPropagation(); if (state.accounts.length === 0) { closePopups(); addAccountDialog(); } else toggle('account-popup', 'profile-popup'); });
document.addEventListener('click', (e) => { if (!e.target.closest('.popup')) closePopups(); });
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') { closePopups(); closeDialog(); } });
$('scrim').addEventListener('mousedown', (e) => { if (e.target === $('scrim')) closeDialog(); });
let installing = false;
let actionCheck = 0;
async function selectedVersionId() {
  const p = currentProfile();
  if (!p) return null;
  if (p.version === 'latest-release') {
    versions = versions || await invoke('list_versions');
    return versions.latest_release;
  }
  return p.version;
}
async function refreshPlayButton() {
  const check = ++actionCheck;
  const button = $('play');
  if (installing) return;
  try {
    const version = await selectedVersionId();
    if (check !== actionCheck || installing) return;
    if (!version) { button.textContent = 'Install'; return; }
    const installed = await invoke('is_version_installed', { version });
    if (check !== actionCheck || installing) return;
    button.textContent = installed ? 'Play' : 'Install';
    button.title = installed ? 'Launch this installed Minecraft version' : 'Download this Minecraft version';
  } catch (_) {
    if (check === actionCheck && !installing) button.textContent = 'Install';
  }
}
$('play').addEventListener('click', async () => {
  if (installing) return;
  const p = currentProfile();
  if (!p) { toast('Create a profile before installing Minecraft.'); return; }
  let version;
  try { version = await selectedVersionId(); }
  catch (e) { toast('Could not resolve the selected Minecraft version: ' + e); return; }
  if (!version) { toast('Choose a Minecraft version first.'); return; }

  installing = true;
  const button = $('play');
  button.disabled = true;
  button.textContent = 'Checking…';
  try {
    const installed = await invoke('is_version_installed', { version });
    if (!installed) {
      button.textContent = 'Installing…';
      toast('Preparing Minecraft ' + version + '. The first download may take a while.');
      const result = await invoke('install_version', { version });
      if (result.preview) {
        toast('Browser preview only: installation runs in the Windows app.');
      } else {
        toast('Installed Minecraft ' + result.version + ' (' + result.files_downloaded + ' new files; ' + result.assets_downloaded + ' assets). Add/select an offline account, then press Play.');
      }
      return;
    }

    const account = state.accounts.find((a) => a.name === state.account);
    if (!account) {
      toast('Minecraft is installed. Add an offline account before pressing Play.');
      closePopups();
      addAccountDialog();
      return;
    }
    button.textContent = 'Launching…';
    const result = await invoke('launch_version', { version, username: account.name, memoryMb: Number(state.settings.memory_mb || 4096), extraJvmArgs: Array.isArray(state.settings.jvm_args) ? state.settings.jvm_args : String(state.settings.jvm_args || '').split(/\r?\n/).map(x => x.trim()).filter(Boolean).slice(0, 20) });
    if (result.preview) toast('Browser preview only: Minecraft launches from the Windows app.');
    else toast('Minecraft ' + result.version + ' started (PID ' + result.pid + '). Log: ' + result.log_file);
  } catch (e) {
    toast('Could not continue: ' + e);
  } finally {
    installing = false;
    button.disabled = false;
    await refreshPlayButton();
  }
});

(async function start() {
  try { info = await invoke('app_info'); } catch (e) { /* keep defaults */ }
  await load();
  render();
  invoke('list_versions').then((v) => { versions = v; render(); }).catch(() => { /* offline: "Latest release" still works */ });
})();
