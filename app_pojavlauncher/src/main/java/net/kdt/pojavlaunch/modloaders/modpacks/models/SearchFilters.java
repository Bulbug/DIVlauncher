package net.kdt.pojavlaunch.modloaders.modpacks.models;

import org.jetbrains.annotations.Nullable;

/** Filters shared by the Modrinth and CurseForge project browsers. */
public class SearchFilters {
    /** Kept for compatibility with the existing modpack installer. */
    public boolean isModpack;
    /** One of mod, modpack, resourcepack, shader. */
    public String projectType = "mod";
    public String name = "";
    /** both, modrinth, or curseforge */
    public String provider = "both";
    @Nullable public String mcVersion;
    /** One of fabric, forge, quilt, neoforge or empty when not applicable. */
    @Nullable public String loader;
    @Nullable public String targetProfileKey;
    /** Actual selected instance compatibility, separate from user-edited search filters. */
    @Nullable public String targetGameVersion;
    @Nullable public String targetLoader;

    public void setProjectType(String type) {
        if (!"mod".equals(type) && !"modpack".equals(type)
                && !"resourcepack".equals(type) && !"shader".equals(type)) {
            type = "mod";
        }
        projectType = type;
        isModpack = "modpack".equals(type);
    }
}
