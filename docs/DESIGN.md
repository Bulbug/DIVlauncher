# Product and Interface Design

## Design intent

DIVlauncher should make the path from selecting a profile to playing Minecraft understandable, while exposing advanced controls without overwhelming new users. Android and Windows share concepts and terminology, not necessarily identical layouts.

## Information architecture

Recommended desktop navigation:

- **Home:** selected profile, selected account, version status and primary Install/Play action.
- **Profiles:** create/select/rename/delete profiles and choose Minecraft version/loader.
- **Mods:** discover and manage compatible mods; show loader/version compatibility clearly.
- **Library:** worlds, screenshots, resource packs, shaders and logs.
- **Settings:** Java runtime, memory, JVM arguments, game directory, appearance and diagnostics.
- **Accounts:** signed-in identities, account status and sign-out.

Mobile navigation should adapt to narrow screens and touch, retaining the same core concepts where feasible.

## Key user journeys

1. First run → choose game directory/defaults → sign in or choose an explicitly offline/local mode → select profile → install → launch.
2. Add mod → select a profile → verify loader and Minecraft compatibility → review source/version → install → restart game if required.
3. Import pack → choose file → inspect metadata and requested files → validate compatibility → preview changes → confirm → import with rollback/backup strategy.
4. Diagnose crash → show concise summary → open relevant log → copy redacted diagnostics → link to troubleshooting guidance.

## Interaction rules

- One primary action per screen; label actions with verbs.
- Distinguish Install, Update, Repair and Play states.
- Show download size/progress and allow safe cancellation when supported.
- Never imply that an offline account is a Microsoft-authenticated account.
- Explain destructive actions and require confirmation for deleting worlds or replacing files.
- Display the origin and compatibility of mods and packs.
- Use inline validation and actionable error messages; avoid raw stack traces as the only feedback.

## Visual and accessibility principles

Use the existing visual system and bundled fonts where appropriate. Preserve consistent spacing, typography, icons and component states. Desktop must support keyboard focus, visible focus rings, sensible tab order and standard window resizing. Mobile must support touch targets and system back behavior. Respect reduced-motion preferences and avoid conveying status by color alone.

## States to design

Loading, empty, populated, offline, permission denied, unsupported version, downloading, verifying, extracting, ready, failed, cancelled and recovery-required. Every asynchronous view needs a clear terminal state.

## Design deliverables

Before a major feature ships, provide a flow diagram or annotated screen, component/state inventory, accessibility review and copy for success/failure states. Keep screenshots and prototypes in a separate design-assets location; do not store secrets or real account data in design files.
