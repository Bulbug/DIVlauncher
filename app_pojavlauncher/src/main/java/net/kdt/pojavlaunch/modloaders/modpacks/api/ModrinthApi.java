package net.kdt.pojavlaunch.modloaders.modpacks.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModrinthIndex;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

/** Modrinth provider for mods, modpacks, resource packs and shaders. */
public class ModrinthApi implements ModpackApi {
    private final ApiHandler mApiHandler = new ApiHandler("https://api.modrinth.com/v2");

    @Override
    public SearchResult searchMod(SearchFilters filters, SearchResult previousPageResult) {
        ModrinthSearchResult previous = (ModrinthSearchResult) previousPageResult;
        if (previous != null && previous.previousOffset >= previous.totalResultCount) {
            ModrinthSearchResult empty = new ModrinthSearchResult();
            empty.results = new ModItem[0];
            empty.totalResultCount = previous.totalResultCount;
            empty.previousOffset = previous.previousOffset;
            return empty;
        }

        String projectType = validProjectType(filters.projectType, filters.isModpack);
        ArrayList<String> facets = new ArrayList<>();
        facets.add("[\"project_type:" + projectType + "\"]");
        if (notEmpty(filters.mcVersion)) facets.add("[\"versions:" + jsonSafe(filters.mcVersion) + "\"]");
        if ("mod".equals(projectType) && notEmpty(filters.loader)) {
            facets.add("[\"categories:" + jsonSafe(filters.loader) + "\"]");
        }
        HashMap<String, Object> params = new HashMap<>();
        params.put("facets", "[" + join(facets, ",") + "]");
        params.put("query", filters.name == null ? "" : filters.name);
        params.put("limit", 50);
        params.put("index", "relevance");
        if (previous != null) params.put("offset", previous.previousOffset);

        JsonObject response = mApiHandler.get("search", params, JsonObject.class);
        if (response == null || !response.has("hits") || !response.get("hits").isJsonArray()) return null;
        JsonArray hits = response.getAsJsonArray("hits");
        ModItem[] items = new ModItem[hits.size()];
        for (int i = 0; i < hits.size(); i++) {
            JsonObject hit = hits.get(i).getAsJsonObject();
            String resultType = stringOr(hit, "project_type", projectType);
            ModItem item = new ModItem(Constants.SOURCE_MODRINTH, resultType,
                    stringOr(hit, "project_id", ""), stringOr(hit, "title", "Untitled project"),
                    stringOr(hit, "description", ""), nullableString(hit, "icon_url"));
            // The search can be filtered independently, but installations must match the selected instance.
            item.targetGameVersion = "modpack".equals(projectType) ? filters.mcVersion : filters.targetGameVersion;
            item.targetLoader = "modpack".equals(projectType) ? filters.loader : filters.targetLoader;
            item.targetProfileKey = filters.targetProfileKey;
            items[i] = item;
        }
        if (previous == null) previous = new ModrinthSearchResult();
        previous.previousOffset += hits.size();
        previous.results = items;
        previous.totalResultCount = response.has("total_hits") ? response.get("total_hits").getAsInt() : items.length;
        return previous;
    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        HashMap<String, Object> params = new HashMap<>();
        if (notEmpty(item.targetGameVersion)) params.put("game_versions", "[\"" + jsonSafe(item.targetGameVersion) + "\"]");
        if ("mod".equals(item.projectType) && notEmpty(item.targetLoader)) {
            params.put("loaders", "[\"" + jsonSafe(item.targetLoader) + "\"]");
        }
        JsonArray response = mApiHandler.get("project/" + urlSegment(item.id) + "/version", params, JsonArray.class);
        if (response == null) return null;

        ArrayList<String> names = new ArrayList<>(), mcNames = new ArrayList<>(), urls = new ArrayList<>();
        ArrayList<String> hashes = new ArrayList<>(), hashAlgorithms = new ArrayList<>(), ids = new ArrayList<>(), fileNames = new ArrayList<>();
        ArrayList<String[]> dependencyIds = new ArrayList<>();
        for (JsonElement element : response) {
            if (!element.isJsonObject()) continue;
            JsonObject version = element.getAsJsonObject();
            JsonArray files = safeArray(version, "files");
            JsonObject selectedFile = choosePrimaryFile(files);
            if (selectedFile == null) continue;
            String url = stringOr(selectedFile, "url", "");
            String fileName = stringOr(selectedFile, "filename", "");
            if (url.isEmpty() || !url.startsWith("https://") || fileName.isEmpty()) continue;

            String mcVersion = firstGameVersion(version, item.targetGameVersion);
            names.add(stringOr(version, "name", stringOr(version, "version_number", "Version")));
            mcNames.add(mcVersion == null ? "" : mcVersion);
            urls.add(url);
            ids.add(stringOr(version, "id", ""));
            fileNames.add(fileName);
            JsonObject hashesObject = selectedFile.has("hashes") && selectedFile.get("hashes").isJsonObject()
                    ? selectedFile.getAsJsonObject("hashes") : new JsonObject();
            if (hasNonEmpty(hashesObject, "sha1")) {
                hashes.add(stringOr(hashesObject, "sha1", ""));
                hashAlgorithms.add("sha1");
            } else if (hasNonEmpty(hashesObject, "sha512")) {
                hashes.add(stringOr(hashesObject, "sha512", ""));
                hashAlgorithms.add("sha512");
            } else {
                hashes.add(""); hashAlgorithms.add("");
            }
            dependencyIds.add(readRequiredDependencies(safeArray(version, "dependencies")));
        }
        if (names.isEmpty()) return new ModDetail(item, new String[0], new String[0], new String[0], new String[0]);
        return new ModDetail(item, names.toArray(new String[0]), mcNames.toArray(new String[0]),
                urls.toArray(new String[0]), hashes.toArray(new String[0]), ids.toArray(new String[0]),
                fileNames.toArray(new String[0]), dependencyIds.toArray(new String[0][]), hashAlgorithms.toArray(new String[0]));
    }

    @Override
    public ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException {
        // Existing modpack flow creates a brand-new launcher profile from the archive manifest.
        return ModpackInstaller.installModpack(modDetail, selectedVersion, this::installMrpack);
    }

    @Override
    public void installProject(ModDetail detail, int selectedVersion, File targetGameDirectory) throws IOException {
        if (detail.isModpack) throw new IOException("Modpacks are installed as new instances.");
        if (selectedVersion < 0 || selectedVersion >= detail.versionIds.length || !notEmpty(detail.versionIds[selectedVersion])) {
            throw new IOException("No compatible file version is available for this instance.");
        }
        HashSet<String> visited = new HashSet<>();
        installVersionRecursive(targetGameDirectory, detail.projectType, detail.id, detail.title,
                detail.versionIds[selectedVersion], detail.targetGameVersion, detail.targetLoader, visited, 0);
    }

    private void installVersionRecursive(File gameDirectory, String projectType, String projectId, String title,
                                         String versionId, String gameVersion, String loader,
                                         Set<String> visited, int depth) throws IOException {
        if (depth > 20) throw new IOException("Too many nested dependencies; stopping to prevent a dependency loop.");
        if (!notEmpty(projectId) || !notEmpty(versionId)) throw new IOException("A required project/version ID is missing.");
        String visitKey = projectId + ":" + versionId;
        if (visited.contains(visitKey)) return;
        visited.add(visitKey);

        JsonObject version = mApiHandler.get("version/" + urlSegment(versionId), JsonObject.class);
        if (version == null) throw new IOException("Could not read a project version from Modrinth.");

        JsonArray dependencies = safeArray(version, "dependencies");
        for (JsonElement depElement : dependencies) {
            if (!depElement.isJsonObject()) continue;
            JsonObject dep = depElement.getAsJsonObject();
            if (!"required".equals(stringOr(dep, "dependency_type", ""))) continue;
            String depProjectId = stringOr(dep, "project_id", "");
            if (depProjectId.isEmpty()) continue;
            String depVersionId = stringOr(dep, "version_id", "");
            if (depVersionId.isEmpty()) depVersionId = findCompatibleVersionId(depProjectId, gameVersion, loader);
            if (depVersionId == null || depVersionId.isEmpty()) {
                throw new IOException("Could not find a compatible required dependency (" + depProjectId + ").");
            }
            String depType = getProjectType(depProjectId);
            if ("modpack".equals(depType)) depType = "mod";
            String depTitle = getProjectTitle(depProjectId);
            installVersionRecursive(gameDirectory, depType == null ? "mod" : depType, depProjectId,
                    depTitle == null ? depProjectId : depTitle, depVersionId, gameVersion, loader, visited, depth + 1);
        }

        JsonArray files = safeArray(version, "files");
        JsonObject file = choosePrimaryFile(files);
        if (file == null) throw new IOException("This version has no downloadable file.");
        String url = stringOr(file, "url", "");
        String fileName = stringOr(file, "filename", "");
        if (url.isEmpty() || fileName.isEmpty()) throw new IOException("This version has incomplete download metadata.");
        JsonObject hashesObject = file.has("hashes") && file.get("hashes").isJsonObject() ? file.getAsJsonObject("hashes") : new JsonObject();
        String algorithm = hasNonEmpty(hashesObject, "sha1") ? "sha1" : (hasNonEmpty(hashesObject, "sha512") ? "sha512" : "");
        String hash = algorithm.isEmpty() ? "" : stringOr(hashesObject, algorithm, "");
        String resolvedTitle = title == null || title.isEmpty() ? stringOr(version, "name", projectId) : title;
        ProjectFileInstaller.downloadAndRecord(gameDirectory, projectType, Constants.SOURCE_MODRINTH,
                projectId, resolvedTitle, versionId, stringOr(version, "version_number", stringOr(version, "name", "")),
                url, fileName, algorithm, hash);
    }

    public static final int PERF_INSTALLED = 0;
    public static final int PERF_NOT_AVAILABLE = 1;

    /**
     * Performance pack: installs the newest release of a project (given by its Modrinth slug) that fits the
     * game version and loader, together with its required dependencies.
     * @param outProjectId receives the Modrinth project id when something was found
     * @return PERF_INSTALLED, or PERF_NOT_AVAILABLE when the project has no matching release
     */
    public int installLatestRelease(File gameDirectory, String slug, String gameVersion, String loader,
                                    String[] outProjectId) throws IOException {
        JsonObject project = mApiHandler.get("project/" + urlSegment(slug), JsonObject.class);
        if (project == null) return PERF_NOT_AVAILABLE;
        String projectId = stringOr(project, "id", "");
        if (projectId.isEmpty()) return PERF_NOT_AVAILABLE;
        String versionId = findCompatibleReleaseVersionId(projectId, gameVersion, loader);
        if (versionId == null) return PERF_NOT_AVAILABLE;
        if (outProjectId != null && outProjectId.length > 0) outProjectId[0] = projectId;
        installVersionRecursive(gameDirectory, "mod", projectId, stringOr(project, "title", slug),
                versionId, gameVersion, loader, new HashSet<>(), 0);
        return PERF_INSTALLED;
    }

    /** Like findCompatibleVersionId but prefers a release, accepts a beta if there is none, and never an alpha */
    private String findCompatibleReleaseVersionId(String projectId, String gameVersion, String loader) {
        HashMap<String, Object> params = new HashMap<>();
        if (notEmpty(gameVersion)) params.put("game_versions", "[\"" + jsonSafe(gameVersion) + "\"]");
        if (notEmpty(loader) && !"minecraft".equals(loader)) params.put("loaders", "[\"" + jsonSafe(loader) + "\"]");
        JsonArray versions = mApiHandler.get("project/" + urlSegment(projectId) + "/version", params, JsonArray.class);
        if (versions == null) return null;
        String beta = null;
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) continue;
            JsonObject version = element.getAsJsonObject();
            String id = stringOr(version, "id", "");
            if (id.isEmpty()) continue;
            String type = stringOr(version, "version_type", "release");
            if ("release".equals(type)) return id;
            if ("beta".equals(type) && beta == null) beta = id;
        }
        return beta;
    }

    private String findCompatibleVersionId(String projectId, String gameVersion, String loader) {
        HashMap<String, Object> params = new HashMap<>();
        if (notEmpty(gameVersion)) params.put("game_versions", "[\"" + jsonSafe(gameVersion) + "\"]");
        if (notEmpty(loader) && !"minecraft".equals(loader)) params.put("loaders", "[\"" + jsonSafe(loader) + "\"]");
        JsonArray versions = mApiHandler.get("project/" + urlSegment(projectId) + "/version", params, JsonArray.class);
        if (versions == null) return null;
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) continue;
            String id = stringOr(element.getAsJsonObject(), "id", "");
            if (!id.isEmpty()) return id;
        }
        return null;
    }

    private String getProjectType(String projectId) {
        JsonObject project = mApiHandler.get("project/" + urlSegment(projectId), JsonObject.class);
        return project == null ? "mod" : stringOr(project, "project_type", "mod");
    }

    private String getProjectTitle(String projectId) {
        JsonObject project = mApiHandler.get("project/" + urlSegment(projectId), JsonObject.class);
        return project == null ? null : stringOr(project, "title", projectId);
    }

    private static String[] readRequiredDependencies(JsonArray deps) {
        ArrayList<String> result = new ArrayList<>();
        for (JsonElement element : deps) {
            if (!element.isJsonObject()) continue;
            JsonObject dep = element.getAsJsonObject();
            if (!"required".equals(stringOr(dep, "dependency_type", ""))) continue;
            String project = stringOr(dep, "project_id", "");
            String version = stringOr(dep, "version_id", "");
            if (!project.isEmpty()) result.add(project + "|" + version);
        }
        return result.toArray(new String[0]);
    }

    private static JsonObject choosePrimaryFile(JsonArray files) {
        if (files == null || files.size() == 0) return null;
        JsonObject first = null;
        for (JsonElement element : files) {
            if (!element.isJsonObject()) continue;
            JsonObject file = element.getAsJsonObject();
            if (first == null) first = file;
            if (file.has("primary") && !file.get("primary").isJsonNull() && file.get("primary").getAsBoolean()) return file;
        }
        return first;
    }

    private static JsonArray safeArray(JsonObject object, String member) {
        return object != null && object.has(member) && object.get(member).isJsonArray() ? object.getAsJsonArray(member) : new JsonArray();
    }

    private static String firstGameVersion(JsonObject version, String preferred) {
        JsonArray versions = safeArray(version, "game_versions");
        if (notEmpty(preferred)) for (JsonElement value : versions) if (preferred.equals(value.getAsString())) return preferred;
        return versions.size() == 0 ? "" : versions.get(0).getAsString();
    }

    private static String validProjectType(String type, boolean oldModpack) {
        if ("mod".equals(type) || "modpack".equals(type) || "resourcepack".equals(type) || "shader".equals(type)) return type;
        return oldModpack ? "modpack" : "mod";
    }

    private static boolean hasNonEmpty(JsonObject o, String key) { return o.has(key) && !o.get(key).isJsonNull() && !o.get(key).getAsString().isEmpty(); }
    private static String nullableString(JsonObject o, String key) { return hasNonEmpty(o, key) ? o.get(key).getAsString() : ""; }
    private static String stringOr(JsonObject o, String key, String fallback) { return hasNonEmpty(o, key) ? o.get(key).getAsString() : fallback; }
    private static boolean notEmpty(String s) { return s != null && !s.trim().isEmpty(); }
    private static String join(ArrayList<String> values, String separator) { StringBuilder b = new StringBuilder(); for (String s : values) { if (b.length() > 0) b.append(separator); b.append(s); } return b.toString(); }
    private static String jsonSafe(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String urlSegment(String id) {
        try { return URLEncoder.encode(id == null ? "" : id, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new IllegalStateException("UTF-8 is required", impossible); }
    }

    private static ModLoader createInfo(ModrinthIndex index) {
        if (index == null || index.dependencies == null) return null;
        Map<String, String> deps = index.dependencies;
        String mcVersion = deps.get("minecraft");
        if (mcVersion == null) return null;
        String loaderVersion;
        if ((loaderVersion = deps.get("forge")) != null) return new ModLoader(ModLoader.MOD_LOADER_FORGE, loaderVersion, mcVersion);
        if ((loaderVersion = deps.get("fabric-loader")) != null) return new ModLoader(ModLoader.MOD_LOADER_FABRIC, loaderVersion, mcVersion);
        if ((loaderVersion = deps.get("quilt-loader")) != null) return new ModLoader(ModLoader.MOD_LOADER_QUILT, loaderVersion, mcVersion);
        return null;
    }

    private ModLoader installMrpack(File mrpackFile, File destination) throws IOException {
        try (ZipFile zip = new ZipFile(mrpackFile)) {
            ModrinthIndex index = Tools.GLOBAL_GSON.fromJson(Tools.read(ZipUtils.getEntryStream(zip, "modrinth.index.json")), ModrinthIndex.class);
            if (index == null || index.files == null || index.formatVersion != 1 || !"minecraft".equals(index.game)) {
                throw new IOException("Invalid or unsupported Modrinth modpack index.");
            }
            ModDownloader downloader = new ModDownloader(destination);
            for (ModrinthIndex.ModrinthIndexFile file : index.files) {
                // A client installation must not install files explicitly marked unsupported on clients.
                if (file == null || (file.env != null && "unsupported".equalsIgnoreCase(file.env.client))) continue;
                if (file.hashes == null || file.hashes.sha1 == null || file.hashes.sha1.trim().isEmpty()
                        || file.downloads == null || file.downloads.length == 0) {
                    throw new IOException("A required modpack file is missing its download URL or SHA-1 hash.");
                }
                downloader.submitDownload(file.fileSize, file.path, file.hashes.sha1, file.downloads);
            }
            downloader.awaitFinish(new DownloaderProgressWrapper(R.string.modpack_download_downloading_mods, ProgressLayout.INSTALL_MODPACK));
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.modpack_download_applying_overrides, 1, 2);
            ZipUtils.zipExtract(zip, "overrides/", destination);
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 50, R.string.modpack_download_applying_overrides, 2, 2);
            ZipUtils.zipExtract(zip, "client-overrides/", destination);
            return createInfo(index);
        }
    }

    class ModrinthSearchResult extends SearchResult { int previousOffset; }
}
