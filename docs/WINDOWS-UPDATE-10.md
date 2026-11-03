# DIVlauncher Windows Update 10

## Managed Java runtimes

Settings now offers installation of Eclipse Temurin JRE 8, 17 and 21 for Windows x64 through the Eclipse Adoptium API. Runtime archives are downloaded over HTTPS, extracted into the application data directory under `runtimes/temurin-<major>`, and checked for `bin/java.exe` before being activated. Existing managed runtimes are reused. Java diagnostics prefers managed runtimes, and the vanilla launcher selects a managed runtime meeting the version metadata's `javaVersion.majorVersion` requirement before falling back to `JAVA_HOME`/`PATH`.

The download is a sizable network operation and requires internet access. This first implementation targets Windows x64 only; ARM64 is not yet supported. Runtime installation and launch must be exercised on a real Windows machine before release. Microsoft authentication and mod-loader installation/launch remain separate milestones; this update does not claim to implement them.
