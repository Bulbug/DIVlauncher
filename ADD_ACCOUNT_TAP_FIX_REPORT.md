# DIVlauncher Add Account tap fix report

> **Superseded in part.** The first version of this fix registered an explicit click listener on the
> account spinner. That crashes the launcher on start, because `AdapterView` (the base class of
> `Spinner`) throws when `setOnClickListener` is called. See `ADAPTERVIEW_CRASH_FIX_REPORT.md` for the
> crash and the current behaviour. The description below reflects the code as it is now.

## What the code does now

- `app_pojavlauncher/src/main/java/com/kdt/mcgui/mcAccountSpinner.java`
  - Taps are handled by overriding `performClick()`; no click listener is registered on the spinner.
  - When only the "Add account" row exists, `performClick()` opens the sign-in chooser directly.
  - When saved accounts exist, a `ListPopupWindow` with an explicit adapter is shown; the "Add account"
    row opens the chooser and the other rows select that account.
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/LauncherActivity.java`
  - `openAddAccountScreen()` resolves the fragment container at tap time, no longer returns silently when
    FragmentManager state is saved (it uses `commitAllowingStateLoss()`), and logs/toasts if the container
    is missing or navigation throws.

## Validation

- Android XML parsing: passed (see `ADAPTERVIEW_CRASH_FIX_REPORT.md` for the latest run).
- Gradle build and on-device tap test: **not performed** in the authoring environment (no network for the
  Gradle download). Treat this as source-level only until a CI build and a phone test confirm it.
