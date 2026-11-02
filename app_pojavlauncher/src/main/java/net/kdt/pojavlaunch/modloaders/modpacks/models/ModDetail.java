package net.kdt.pojavlaunch.modloaders.modpacks.models;

import androidx.annotation.NonNull;
import java.util.Arrays;

public class ModDetail extends ModItem {
    public String[] versionNames;
    public String[] mcVersionNames;
    public String[] versionUrls;
    /** SHA-1 hashes where the provider returns one. */
    public String[] versionHashes;
    public String[] versionHashAlgorithms;
    /** Provider-native version IDs; required for reliable dependency/update operations. */
    public String[] versionIds;
    public String[] versionFileNames;
    /** Required dependency project IDs grouped by version. */
    public String[][] versionRequiredDependencies;

    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames,
                     String[] versionUrls, String[] hashes) {
        this(item, versionNames, mcVersionNames, versionUrls, hashes,
                new String[versionNames.length], new String[versionNames.length],
                new String[versionNames.length][], new String[versionNames.length]);
    }

    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames,
                     String[] versionUrls, String[] hashes, String[] versionIds,
                     String[] fileNames, String[][] requiredDependencies) {
        this(item, versionNames, mcVersionNames, versionUrls, hashes, versionIds, fileNames, requiredDependencies, new String[versionNames.length]);
    }

    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames,
                     String[] versionUrls, String[] hashes, String[] versionIds,
                     String[] fileNames, String[][] requiredDependencies, String[] hashAlgorithms) {
        super(item.apiSource, item.projectType, item.id, item.title, item.description, item.imageUrl);
        targetGameVersion = item.targetGameVersion;
        targetLoader = item.targetLoader;
        targetProfileKey = item.targetProfileKey;
        this.versionNames = versionNames;
        this.mcVersionNames = mcVersionNames;
        this.versionUrls = versionUrls;
        this.versionHashes = hashes;
        this.versionHashAlgorithms = hashAlgorithms;
        this.versionIds = versionIds;
        this.versionFileNames = fileNames;
        this.versionRequiredDependencies = requiredDependencies;
        for (int i = 0; i < versionNames.length; i++) {
            if (versionNames[i] != null && mcVersionNames[i] != null
                    && !mcVersionNames[i].isEmpty() && !versionNames[i].contains(mcVersionNames[i])) {
                versionNames[i] += " - " + mcVersionNames[i];
            }
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "ModDetail{" + "versionNames=" + Arrays.toString(versionNames) +
                ", id='" + id + '\'' + ", title='" + title + '\'' +
                ", projectType='" + projectType + '\'' + ", apiSource=" + apiSource + '}';
    }
}
