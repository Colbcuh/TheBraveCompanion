package Main.Java;

import com.google.gson.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class UpdateService {

    private static final Gson GSON = new Gson();

    private UpdateService() {}

    public interface Progress {
        void onStatus(String text);
    }

    public static final class LatestRelease {
        public final String version;
        public final String jarUrl;
        public final String assetName;

        public LatestRelease(String version, String jarUrl, String assetName) {
            this.version = version;
            this.jarUrl = jarUrl;
            this.assetName = assetName;
        }
    }

    // GitHub API: /repos/{owner}/{repo}/releases/latest
    public static LatestRelease fetchLatestRelease(String owner, String repo) throws IOException {
        String api = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";
        String json = httpGet(new URL(api));

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        String tag = root.has("tag_name") ? root.get("tag_name").getAsString() : null;
        if (tag == null || tag.trim().isEmpty()) {
            throw new IOException("No tag_name found in latest release.");
        }
        String version = VersionUtil.normalize(tag);

        if (!root.has("assets") || !root.get("assets").isJsonArray()) {
            throw new IOException("No assets array found in latest release.");
        }

        JsonArray assets = root.getAsJsonArray("assets");

        // Pick the first asset that ends with .jar
        for (JsonElement el : assets) {
            JsonObject a = el.getAsJsonObject();
            String name = a.has("name") ? a.get("name").getAsString() : "";
            String url = a.has("browser_download_url") ? a.get("browser_download_url").getAsString() : "";

            if (name.toLowerCase().endsWith(".jar") && url.startsWith("http")) {
                return new LatestRelease(version, url, name);
            }
        }

        throw new IOException("No .jar asset found in latest release assets.");
    }

    public static Path downloadTo(Path dest, String url, Progress progress) throws IOException {
        if (progress != null) progress.onStatus("Downloading update…");

        Files.createDirectories(dest.getParent());
        Path tmp = Paths.get(dest.toString() + ".part");

        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(10_000);
        con.setReadTimeout(30_000);
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", "TBC-Updater");

        int code = con.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code + " downloading " + url);

        try (InputStream in = new BufferedInputStream(con.getInputStream());
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(
                     tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {

            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
        }

        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    private static String httpGet(URL url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setConnectTimeout(10_000);
        con.setReadTimeout(20_000);
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", "TBC-Updater");

        int code = con.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code + " for " + url);

        StringBuilder sb = new StringBuilder(8192);
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
