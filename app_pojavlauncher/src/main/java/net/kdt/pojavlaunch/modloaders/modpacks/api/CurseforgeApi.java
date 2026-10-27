package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.CurseManifest;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.GsonJsonUtils;
import net.kdt.pojavlaunch.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

public class CurseforgeApi implements ModpackApi{
    private static final Pattern sMcVersionPattern = Pattern.compile("([0-9]+)\\.([0-9]+)\\.?([0-9]+)?");
    private static final int ALGO_SHA_1 = 1;
    // Stolen from
    // https://github.com/AnzhiZhang/CurseForgeModpackDownloader/blob/6cb3f428459f0cc8f444d16e54aea4cd1186fd7b/utils/requester.py#L93
    private static final int CURSEFORGE_MINECRAFT_GAME_ID = 432;
    private static final int CURSEFORGE_MODPACK_CLASS_ID = 4471;
    // https://api.curseforge.com/v1/categories?gameId=432 and search for "Mods" (case-sensitive)
    private static final int CURSEFORGE_MOD_CLASS_ID = 6;
    private static final int CURSEFORGE_SORT_RELEVANCY = 1;
    private static final int CURSEFORGE_PAGINATION_SIZE = 50;
    private static final int CURSEFORGE_PAGINATION_END_REACHED = -1;
    private static final int CURSEFORGE_PAGINATION_ERROR = -2;

    private final ApiHandler mApiHandler;
    public CurseforgeApi(String proxyBaseUrl) {
        if (proxyBaseUrl == null || !proxyBaseUrl.startsWith("https://"))
            throw new IllegalArgumentException("CurseForge requires a configured HTTPS server-side API proxy.");
        while (proxyBaseUrl.endsWith("/")) proxyBaseUrl = proxyBaseUrl.substring(0, proxyBaseUrl.length() - 1);
        mApiHandler = new ApiHandler(proxyBaseUrl);
    }

    private static int classId(String projectType) {
        if ("modpack".equals(projectType)) return CURSEFORGE_MODPACK_CLASS_ID;
        if ("resourcepack".equals(projectType)) return 12;
        if ("shader".equals(projectType)) return 6552;
        return CURSEFORGE_MOD_CLASS_ID;
    }

    private static int loaderId(String loader) {
        if ("forge".equals(loader)) return 1;
        if ("fabric".equals(loader)) return 4;
        if ("quilt".equals(loader)) return 5;
        if ("neoforge".equals(loader)) return 6;
        return 0;
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        CurseforgeSearchResult curseforgeSearchResult = (CurseforgeSearchResult) previousPageResult;

        HashMap<String, Object> params = new HashMap<>();
        params.put("gameId", CURSEFORGE_MINECRAFT_GAME_ID);
        params.put("classId", classId(searchFilters.projectType));
        params.put("searchFilter", searchFilters.name);
        params.put("sortField", CURSEFORGE_SORT_RELEVANCY);
        params.put("sortOrder", "desc");
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            params.put("gameVersion", searchFilters.mcVersion);
        int selectedLoader = loaderId(searchFilters.loader);
        if (selectedLoader != 0 && "mod".equals(searchFilters.projectType) && searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            params.put("modLoaderType", selectedLoader);
        if(previousPageResult != null)
            params.put("index", curseforgeSearchResult.previousOffset);

        JsonObject response = mApiHandler.get("mods/search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray dataArray = response.getAsJsonArray("data");
        if(dataArray == null) return null;
        JsonObject paginationInfo = response.getAsJsonObject("pagination");
        if (paginationInfo == null || !paginationInfo.has("totalCount") || paginationInfo.get("totalCount").isJsonNull()) return null;
        ArrayList<ModItem> modItemList = new ArrayList<>(dataArray.size());
        for(int i = 0; i < dataArray.size(); i++) {
            JsonObject dataElement = dataArray.get(i).getAsJsonObject();
            JsonElement allowModDistribution = dataElement.get("allowModDistribution");
            JsonElement available = dataElement.get("isAvailable");
            if (allowModDistribution == null || allowModDistribution.isJsonNull() || !allowModDistribution.getAsBoolean()) {
                Log.i("CurseforgeApi", "Skipping project without permission for third-party distribution");
                continue;
            }
            if (available == null || available.isJsonNull() || !available.getAsBoolean()) {
                Log.i("CurseforgeApi", "Skipping project which is not available through the official API");
                continue;
            }
            String imageUrl = "";
            if (dataElement.has("logo") && dataElement.get("logo").isJsonObject()) {
                JsonObject logo = dataElement.getAsJsonObject("logo");
                imageUrl = nullableJsonString(logo, "thumbnailUrl");
                if (imageUrl == null) imageUrl = "";
            }
            ModItem modItem = new ModItem(Constants.SOURCE_CURSEFORGE,
                    searchFilters.projectType,
                    dataElement.get("id").getAsString(),
                    dataElement.get("name").getAsString(),
                    dataElement.has("summary") && !dataElement.get("summary").isJsonNull() ? dataElement.get("summary").getAsString() : "",
                    imageUrl);
            modItem.targetGameVersion = "modpack".equals(searchFilters.projectType) ? searchFilters.mcVersion : searchFilters.targetGameVersion;
            modItem.targetLoader = "modpack".equals(searchFilters.projectType) ? searchFilters.loader : searchFilters.targetLoader;
            modItem.targetProfileKey = searchFilters.targetProfileKey;
            modItemList.add(modItem);
        }
        if(curseforgeSearchResult == null) curseforgeSearchResult = new CurseforgeSearchResult();
        curseforgeSearchResult.results = modItemList.toArray(new ModItem[0]);
        curseforgeSearchResult.totalResultCount = paginationInfo.get("totalCount").getAsInt();
        curseforgeSearchResult.previousOffset += dataArray.size();
        return curseforgeSearchResult;

    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        // Dependents can reference a project that opted out of third-party distribution.
        // Check the official project metadata before displaying or installing its files.
        JsonObject projectResponse = mApiHandler.get("mods/" + item.id, JsonObject.class);
        JsonObject projectData = GsonJsonUtils.getJsonObjectSafe(projectResponse, "data");
        if (projectData == null) return null; // Fail closed if the official distribution setting cannot be checked.
        JsonElement allowed = projectData.get("allowModDistribution");
        JsonElement available = projectData.get("isAvailable");
        if (allowed == null || allowed.isJsonNull() || !allowed.getAsBoolean()) return null;
        if (available == null || available.isJsonNull() || !available.getAsBoolean()) return null;
        ArrayList<JsonObject> allModDetails = new ArrayList<>();
        int index = 0;
        while(index != CURSEFORGE_PAGINATION_END_REACHED && index != CURSEFORGE_PAGINATION_ERROR) {
            index = getPaginatedDetails(allModDetails, index, item);
        }
        if(index == CURSEFORGE_PAGINATION_ERROR) return null;
        ArrayList<String> versionNames = new ArrayList<>();
        ArrayList<String> mcVersionNames = new ArrayList<>();
        ArrayList<String> versionUrls = new ArrayList<>();
        ArrayList<String> hashes = new ArrayList<>();
        ArrayList<String> versionIds = new ArrayList<>();
        ArrayList<String> fileNames = new ArrayList<>();
        for(JsonObject file : allModDetails) {
            String gameVersion = null;
            JsonArray gameVersions = GsonJsonUtils.getJsonArraySafe(file, "gameVersions");
            if(gameVersions != null) {
                for(JsonElement version : gameVersions) {
                    String candidate = version.getAsString();
                    if(!sMcVersionPattern.matcher(candidate).matches()) continue;
                    if(item.targetGameVersion == null || item.targetGameVersion.isEmpty() || item.targetGameVersion.equals(candidate)) {
                        gameVersion = candidate;
                        break;
                    }
                }
            }
            if(item.targetGameVersion != null && !item.targetGameVersion.isEmpty() && gameVersion == null) continue;
            String fileName = nullableJsonString(file, "fileName");
            if(fileName == null || fileName.isEmpty()) continue;
            String fileId = nullableJsonString(file, "id");
            if (fileId == null || fileId.isEmpty()) continue;
            String url = nullableJsonString(file, "downloadUrl");
            // Ask the documented download-url endpoint when the listing omits the URL.
            // Never reconstruct a CDN URL; null from the official API means it is not downloadable here.
            if ((url == null || url.isEmpty()) && item.id.matches("\\d+") && fileId.matches("\\d+")) {
                url = getDownloadUrl(Long.parseLong(item.id), Long.parseLong(fileId));
            }
            if(url == null || url.isEmpty() || !url.startsWith("https://")) continue;
            String displayName = nullableJsonString(file, "displayName");
            if(displayName == null || displayName.isEmpty()) displayName = fileName;
            versionNames.add(displayName);
            mcVersionNames.add(gameVersion == null ? "" : gameVersion);
            versionUrls.add(url);
            String hash = getSha1FromModData(file);
            if ((hash == null || hash.isEmpty()) && item.id.matches("\\d+") && fileId.matches("\\d+")) {
                hash = getDownloadSha1(Long.parseLong(item.id), Long.parseLong(fileId));
            }
            hashes.add(hash == null ? "" : hash);
            versionIds.add(fileId);
            fileNames.add(fileName);
        }
        if(versionNames.isEmpty()) return new ModDetail(item, new String[0], new String[0], new String[0], new String[0]);
        String[][] dependencies = new String[versionNames.size()][];
        String[] algorithms = new String[hashes.size()];
        for(int i = 0; i < dependencies.length; i++) dependencies[i] = new String[0];
        for(int i = 0; i < algorithms.length; i++) algorithms[i] = hashes.get(i).isEmpty() ? "" : "sha1";
        return new ModDetail(item, versionNames.toArray(new String[0]), mcVersionNames.toArray(new String[0]),
                versionUrls.toArray(new String[0]), hashes.toArray(new String[0]), versionIds.toArray(new String[0]),
                fileNames.toArray(new String[0]), dependencies, algorithms);
    }

    @Override
    public ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException {
        return ModpackInstaller.installModpack(modDetail, selectedVersion, this::installCurseforgeZip);
    }

    @Override
    public void installProject(ModDetail detail, int selectedVersion, File targetGameDirectory) throws IOException {
        if(selectedVersion < 0 || selectedVersion >= detail.versionIds.length || detail.versionIds[selectedVersion] == null)
            throw new IOException("No downloadable version is available for this instance.");
        HashSet<String> visited = new HashSet<>();
        installCurseforgeFileRecursive(targetGameDirectory, detail.projectType, detail.id, detail.title,
                detail.versionIds[selectedVersion], detail.versionNames[selectedVersion], detail.versionUrls[selectedVersion],
                detail.versionFileNames[selectedVersion], detail.versionHashes[selectedVersion], visited, 0,
                detail.targetGameVersion, detail.targetLoader);
    }

    private void installCurseforgeFileRecursive(File gameDirectory, String type, String projectId, String title,
                                                String fileId, String versionName, String downloadUrl, String fileName,
                                                String sha1, java.util.Set<String> visited, int depth,
                                                String gameVersion, String loader) throws IOException {
        if(depth > 20) throw new IOException("Too many required dependencies; stopping to prevent a dependency loop.");
        String visitKey = projectId + ":" + fileId;
        if(visited.contains(visitKey)) return;
        visited.add(visitKey);
        JsonObject depResponse = mApiHandler.get("mods/" + projectId + "/files/" + fileId + "/dependencies", JsonObject.class);
        JsonArray depArray = GsonJsonUtils.getJsonArraySafe(depResponse, "data");
        if(depArray != null) {
            for(JsonElement element : depArray) {
                if(!element.isJsonObject()) continue;
                JsonObject dep = element.getAsJsonObject();
                if(GsonJsonUtils.getIntSafe(dep, "relationType", 0) != 3) continue;
                String depId = nullableJsonString(dep, "modId");
                if(depId == null || depId.isEmpty()) continue;
                ModItem dependency = new ModItem(Constants.SOURCE_CURSEFORGE, "mod", depId, depId, "", "");
                dependency.targetGameVersion = gameVersion;
                dependency.targetLoader = loader;
                ModDetail compatible = getModDetails(dependency);
                if(compatible == null || compatible.versionIds.length == 0)
                    throw new IOException("Could not find a compatible required CurseForge dependency: " + depId);
                // Prefer the exact required file returned by CurseForge; use the newest compatible file only
                // when the dependency metadata did not specify a file ID.
                String requiredFileId = nullableJsonString(dep, "fileId");
                int selectedDependencyVersion = 0;
                if (requiredFileId != null && !requiredFileId.isEmpty()) {
                    boolean matchedRequiredFile = false;
                    for (int versionIndex = 0; versionIndex < compatible.versionIds.length; versionIndex++) {
                        if (requiredFileId.equals(compatible.versionIds[versionIndex])) {
                            selectedDependencyVersion = versionIndex;
                            matchedRequiredFile = true;
                            break;
                        }
                    }
                    if (!matchedRequiredFile) {
                        throw new IOException("CurseForge required dependency file " + requiredFileId
                                + " is not available for the selected instance version.");
                    }
                }
                installCurseforgeFileRecursive(gameDirectory, "mod", depId, compatible.title,
                        compatible.versionIds[selectedDependencyVersion], compatible.versionNames[selectedDependencyVersion],
                        compatible.versionUrls[selectedDependencyVersion], compatible.versionFileNames[selectedDependencyVersion],
                        compatible.versionHashes[selectedDependencyVersion], visited, depth + 1, gameVersion, loader);
            }
        }
        ProjectFileInstaller.downloadAndRecord(gameDirectory, type, Constants.SOURCE_CURSEFORGE,
                projectId, title, fileId, versionName, downloadUrl, fileName, "sha1", sha1);
    }

    private int getPaginatedDetails(ArrayList<JsonObject> objectList, int index, ModItem item) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("index", index);
        params.put("pageSize", CURSEFORGE_PAGINATION_SIZE);
        if(item.targetGameVersion != null && !item.targetGameVersion.isEmpty()) params.put("gameVersion", item.targetGameVersion);
        int selectedLoader = loaderId(item.targetLoader);
        if(selectedLoader != 0 && "mod".equals(item.projectType) && item.targetGameVersion != null && !item.targetGameVersion.isEmpty()) params.put("modLoaderType", selectedLoader);
        JsonObject response = mApiHandler.get("mods/" + item.id + "/files", params, JsonObject.class);
        JsonArray data = GsonJsonUtils.getJsonArraySafe(response, "data");
        if(data == null) return CURSEFORGE_PAGINATION_ERROR;
        for(int i = 0; i < data.size(); i++) {
            JsonObject fileInfo = data.get(i).getAsJsonObject();
            if(fileInfo.has("isServerPack") && !fileInfo.get("isServerPack").isJsonNull() && fileInfo.get("isServerPack").getAsBoolean()) continue;
            if(item.targetGameVersion != null && !item.targetGameVersion.isEmpty()) {
                boolean matches = false;
                JsonArray versions = GsonJsonUtils.getJsonArraySafe(fileInfo, "gameVersions");
                if(versions != null) for(JsonElement version : versions) if(item.targetGameVersion.equals(version.getAsString())) matches = true;
                if(!matches) continue;
            }
            objectList.add(fileInfo);
        }
        return data.size() < CURSEFORGE_PAGINATION_SIZE ? CURSEFORGE_PAGINATION_END_REACHED : index + data.size();
    }

    private ModLoader installCurseforgeZip(File zipFile, File instanceDestination) throws IOException {
        try (ZipFile modpackZipFile = new ZipFile(zipFile)){
            CurseManifest curseManifest = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "manifest.json")),
                    CurseManifest.class);
            if(!verifyManifest(curseManifest)) {
                Log.i("CurseforgeApi","manifest verification failed");
                return null;
            }
            ModDownloader modDownloader = new ModDownloader(new File(instanceDestination,"mods"), true);
            int fileCount = curseManifest.files.length;
            for(int i = 0; i < fileCount; i++) {
                final CurseManifest.CurseFile curseFile = curseManifest.files[i];
                modDownloader.submitDownload(()->{
                    String url = getDownloadUrl(curseFile.projectID, curseFile.fileID);
                    if(url == null && curseFile.required)
                        throw new IOException("Failed to obtain download URL for "+curseFile.projectID+" "+curseFile.fileID);
                    else if(url == null) return null;
                    return new ModDownloader.FileInfo(url, FileUtils.getFileName(url), getDownloadSha1(curseFile.projectID, curseFile.fileID));
                });
            }
            modDownloader.awaitFinish((c,m)->
                    ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, (int) Math.max((float)c/m*100,0), R.string.modpack_download_downloading_mods_fc, c, m)
            );
            String overridesDir = "overrides";
            if(curseManifest.overrides != null) overridesDir = curseManifest.overrides;
            ZipUtils.zipExtract(modpackZipFile, overridesDir, instanceDestination);
            return createInfo(curseManifest.minecraft);
        }
    }

    private ModLoader createInfo(CurseManifest.CurseMinecraft minecraft) {
        CurseManifest.CurseModLoader primaryModLoader = null;
        for(CurseManifest.CurseModLoader modLoader : minecraft.modLoaders) {
            if(modLoader.primary) {
                primaryModLoader = modLoader;
                break;
            }
        }
        if(primaryModLoader == null) primaryModLoader = minecraft.modLoaders[0];
        String modLoaderId = primaryModLoader.id;
        int dashIndex = modLoaderId.indexOf('-');
        String modLoaderName = modLoaderId.substring(0, dashIndex);
        String modLoaderVersion = modLoaderId.substring(dashIndex+1);
        Log.i("CurseforgeApi", modLoaderId + " " + modLoaderName + " "+modLoaderVersion);
        int modLoaderTypeInt;
        switch (modLoaderName) {
            case "forge":
                modLoaderTypeInt = ModLoader.MOD_LOADER_FORGE;
                break;
            case "fabric":
                modLoaderTypeInt = ModLoader.MOD_LOADER_FABRIC;
                break;
            case "quilt":
                modLoaderTypeInt = ModLoader.MOD_LOADER_QUILT;
                break;
            default:
                return null;
            //TODO: Quilt is also Forge? How does that work?
        }
        return new ModLoader(modLoaderTypeInt, modLoaderVersion, minecraft.version);
    }

    private String getDownloadUrl(long projectID, long fileID) {
        JsonObject response = mApiHandler.get("mods/" + projectID + "/files/" + fileID + "/download-url", JsonObject.class);
        JsonElement data = response == null ? null : response.get("data");
        if(data == null || data.isJsonNull()) return null;
        String url = data.getAsString();
        return url.startsWith("https://") ? url : null;
    }

    private @Nullable String getDownloadSha1(long projectID, long fileID) {
        // Try the api endpoint, die in the other case
        JsonObject response = mApiHandler.get("mods/"+projectID+"/files/"+fileID, JsonObject.class);
        JsonObject data = GsonJsonUtils.getJsonObjectSafe(response, "data");
        if(data == null) return null;
        return getSha1FromModData(data);
    }

    private String getSha1FromModData(@NonNull JsonObject object) {
        JsonArray hashes = GsonJsonUtils.getJsonArraySafe(object, "hashes");
        if(hashes == null) return null;
        for (JsonElement jsonElement : hashes) {
            // The sha1 = 1; md5 = 2;
            JsonObject jsonObject = GsonJsonUtils.getJsonObjectSafe(jsonElement);
            if(GsonJsonUtils.getIntSafe(
                    jsonObject,
                    "algo",
                    -1) == ALGO_SHA_1) {
                return GsonJsonUtils.getStringSafe(jsonObject, "value");
            }
        }
        return null;
    }

    private static String nullableJsonString(JsonObject object, String key) {
        if(object == null || !object.has(key) || object.get(key).isJsonNull()) return null;
        try { return object.get(key).getAsString(); } catch (Exception ignored) { return null; }
    }

    private boolean verifyManifest(CurseManifest manifest) {
        if(!"minecraftModpack".equals(manifest.manifestType)) return false;
        if(manifest.manifestVersion != 1) return false;
        if(manifest.minecraft == null) return false;
        if(manifest.minecraft.version == null) return false;
        if(manifest.minecraft.modLoaders == null) return false;
        return manifest.minecraft.modLoaders.length >= 1;
    }

    static class CurseforgeSearchResult extends SearchResult {
        int previousOffset;
    }
}
