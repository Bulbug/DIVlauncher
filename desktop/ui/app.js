'use strict';
/* DIVlauncher for Windows: window logic. Talks to the Rust side through Tauri commands
   (app_info, list_versions, offline_uuid, load_state, save_state, install_version). In a plain browser it uses small stand-ins,
   so the window can be previewed without building the app. */

const tauri = window.__TAURI__ && window.__TAURI__.core;
const invoke = (cmd, args) => (tauri ? tauri.invoke(cmd, args) : mock(cmd, args));

function mock(cmd, args) {
  switch (cmd) {
    case 'app_info': return Promise.resolve({ name: 'DIVlauncher', version: 'browser preview', data_dir: '(browser preview)', os: 'browser' });
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
      tile('var(--mint)', svgIcon('M12 3l8 4.5v9L12 21l-8-4.5v-9zM12 12l8-4.5M12 12v9M12 12L4 7.5'), 'Browse mods', 'Coming soon', () => go('mods')),
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
        state.profiles.length > 1 ? el('button', { class: 'btn small danger', text: 'Delete', onclick: () => confirmDialog('Delete "' + p.name + '"?', 'The profile is removed from the launcher.', 'Delete', () => {
          state.profiles = state.profiles.filter((x) => x.id !== p.id);
          if (state.profile === p.id) state.profile = state.profiles[0].id;
          save(); render();
        }) }) : null)));
  });
  return el('div', {}, el('div', { class: 'toolbar' }, el('span', { class: 'mu', text: state.profiles.length + ' profile' + (state.profiles.length === 1 ? '' : 's') }),
    el('button', { class: 'btn', text: '+ New profile', onclick: newProfileDialog })), grid);
}

function modsScreen() {
  return el('div', { class: 'card' }, el('h2', { text: 'Mods', style: 'margin:0 0 8px' }),
    el('p', { class: 'mu', style: 'font-size:14px;margin:0', text: 'The mod browser (Modrinth and CurseForge) and the Performance toggle are coming in a later build.' }));
}

function settingsScreen() {
  const accounts = el('div', {});
  if (state.accounts.length === 0) accounts.append(el('p', { class: 'mu', text: 'No accounts yet.' }));
  for (const a of state.accounts) {
    accounts.append(el('div', { class: 'row', style: 'cursor:default' }, el('i', { class: 'sq' }), el('div', {}, el('b', { text: a.name }), el('small', { text: 'Offline · ' + a.uuid })),
      el('button', { class: 'btn small danger', style: 'margin-left:auto', text: 'Remove', onclick: () => confirmDialog('Remove "' + a.name + '"?', 'The account is removed from the launcher.', 'Remove', () => {
        state.accounts = state.accounts.filter((x) => x.name !== a.name);
        if (state.account === a.name) state.account = state.accounts[0] ? state.accounts[0].name : null;
        save(); render();
      }) })));
  }
  accounts.append(el('button', { class: 'btn small', style: 'margin-top:10px', text: '+ Add account', onclick: addAccountDialog }));
  const kv = (k, v) => [el('dt', { text: k }), el('dd', { text: v })];
  return el('div', { class: 'stack' },
    el('div', { class: 'card' }, el('p', { class: 'section', text: 'Accounts' }), accounts),
    el('div', { class: 'card' }, el('p', { class: 'section', text: 'About' }),
      el('dl', { class: 'kv' }, kv('Version', info.version), kv('System', info.os), kv('Saved data', info.data_dir))));
}

const screens = { home: ['Home', homeScreen], profiles: ['Profiles', profilesScreen], mods: ['Mods', modsScreen], settings: ['Settings', settingsScreen] };
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
    const result = await invoke('launch_version', { version, username: account.name });
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
