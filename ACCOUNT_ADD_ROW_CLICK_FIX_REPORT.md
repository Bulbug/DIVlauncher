# DIVlauncher Add Account Row Click Fix

## Root cause addressed

The previous `performClick()` implementation checked whether `getAdapter()` was a `ListAdapter`. `AppCompatSpinner` may expose an adapter wrapper that does not pass that check, causing the code to fall back to the native Spinner popup. Native Spinner selection callbacks are not guaranteed to fire when the tapped row is already selected. With no saved accounts, the collapsed spinner itself also displayed only `+ Add account`, but the previous implementation still opened a popup containing the same single action row.

## Changes

- When no saved accounts exist, tapping the visible `+ Add account` spinner row now opens the authentication-method screen directly.
- When saved accounts exist, the custom popup is given a new, explicit `AccountAdapter` rather than relying on the Spinner's internal adapter wrapper. Its item-click listener handles the action row directly and activates selected saved accounts.
- Marked the account spinner as clickable and focusable in the layout.
- Kept the existing account storage, multiple offline accounts, and account-selection logic.

## Validation

- Android resource XML parsing: see result for this package's validation run.
- Source-level checks: verified the no-account direct path, explicit popup adapter, and action-row handler are present.
- ZIP integrity: checked after packaging.
- Full Gradle/APK build and on-device tap testing: not verified in this environment; a successful build and phone test are still required.
