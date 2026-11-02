package net.kdt.pojavlaunch.modloaders.modpacks.models;

/** Common provenance and compatibility context for provider-returned projects. */
public abstract class ModSource {
    public int apiSource;
    /** Kept for compatibility with the earlier modpack-only browser. */
    public boolean isModpack;
    public String projectType = "mod";
    public String targetGameVersion;
    public String targetLoader;
    public String targetProfileKey;
}
