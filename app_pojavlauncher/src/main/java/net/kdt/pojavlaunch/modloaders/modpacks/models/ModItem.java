package net.kdt.pojavlaunch.modloaders.modpacks.models;

import androidx.annotation.NonNull;

public class ModItem extends ModSource {

    public String id;
    public String title;
    public String description;
    public String imageUrl;

    public ModItem(int apiSource, boolean isModpack, String id, String title, String description, String imageUrl) {
        this(apiSource, isModpack ? "modpack" : "mod", id, title, description, imageUrl);
    }

    public ModItem(int apiSource, String projectType, String id, String title, String description, String imageUrl) {
        this.apiSource = apiSource;
        this.projectType = projectType == null ? "mod" : projectType;
        this.isModpack = "modpack".equals(this.projectType);
        this.id = id;
        this.title = title;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    @NonNull
    @Override
    public String toString() {
        return "ModItem{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", projectType='" + projectType + '\'' +
                ", apiSource=" + apiSource +
                '}';
    }

    public String getIconCacheTag() {
        return apiSource + "_" + id;
    }
}
