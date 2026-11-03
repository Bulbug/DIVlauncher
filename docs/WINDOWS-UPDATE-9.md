# Windows Update 9 — Advanced JVM options

The Windows desktop Settings screen now lets users configure additional JVM arguments, one argument per line. The settings are persisted with launcher state and passed to Java as separate process arguments, not through a command shell.

## Validation and restrictions

- At most 20 additional arguments are accepted.
- Each argument is limited to 512 characters and control characters are rejected.
- Launcher-managed heap and classpath flags, plus Java agent flags, are blocked to reduce accidental breakage and unsafe overrides.
- This is an advanced setting: invalid JVM options can stop Minecraft from starting.

## Test checklist

1. Open Settings and enter a harmless JVM option, such as `-XX:+UseG1GC`.
2. Launch an installed vanilla version and inspect `logs/launcher-<version>.log`.
3. Try a reserved argument such as `-Xmx1G`; the launcher should reject it with an error.
4. Clear the field and confirm the next launch uses the default JVM arguments.

Rust compilation and Windows runtime behavior still require verification on a Windows runner. This feature does not add Java downloading, Microsoft authentication, or mod-loader support.
