package Main.Java;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Single-JAR bootstrap:
 * - Shows a small launcher window
 * - Optionally checks GitHub Releases for an updated .jar
 * - If update chosen: downloads new jar, schedules replace+restart, exits
 * - Otherwise launches TBCGUI
 *
 * IMPORTANT:
 * 1) You must publish your updates as GitHub Releases with a .jar asset attached.
 * 2) CURRENT_VERSION must be updated whenever you ship a new release.
 */
public final class TBCBootstrap {

    // ----------------------------
    // CHANGE THESE 3 VALUES
    // ----------------------------

    // 1) Your app's current version (bump this each release)
    private static final String CURRENT_VERSION = "0.1.0";

    // 2) Your GitHub owner/user
    private static final String GITHUB_OWNER = "Colbcuh";

    // 3) Your GitHub repo name
    private static final String GITHUB_REPO = "TheBraveCompanion";

    // ----------------------------
    // UI THEME (matches TBC vibe)
    // ----------------------------
    private static final Color BG_DARKEST = new Color(32, 34, 37);
    private static final Color BG_DARK    = new Color(44, 47, 51);
    private static final Color BG_MID     = new Color(54, 57, 63);
    private static final Color ACCENT     = new Color(40, 130, 210);
    private static final Color TEXT       = new Color(235, 235, 235);
    private static final Color MUTED      = new Color(170, 170, 170);

    private static final int CORNER_RADIUS = 18;

    // Ensures launcher doesn't "flash" too fast
    private static final int MIN_VISIBLE_MS = 1200;

    // GitHub Releases API
    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";

    private TBCBootstrap() {}

    public static void main(String[] args) {
        // Start crash logging as early as possible
        CrashLogger.init("TBC");

        SwingUtilities.invokeLater(() -> {
            LauncherWindow win = new LauncherWindow();
            win.setVisible(true);

            long start = System.currentTimeMillis();

            SwingWorker<UpdateInfo, String> worker = new SwingWorker<UpdateInfo, String>() {
                @Override
                protected UpdateInfo doInBackground() {
                    try {
                        publish("Starting…");

                        // If you have AppPaths, ensure folders here (safe if it already exists)
                        try {
                            // If your AppPaths uses %APPDATA%\TBC, this is where you'd ensure dirs.
                            // Example (if methods exist):
                            // AppPaths.appDir().mkdirs();
                            // AppPaths.logsDir().mkdirs();
                            // AppPaths.cacheDir().mkdirs();
                        } catch (Throwable t) {
                            // Don’t fail startup if paths aren’t available
                            CrashLogger.log("AppPaths init warning", t);
                        }

                        publish("Checking for updates…");

                        // Only attempt update check if we are running from a .jar
                        File self = getRunningJarFile();
                        if (self == null) {
                            publish("Running in IDE (no update check).");
                            return UpdateInfo.none();
                        }

                        UpdateInfo info = checkGithubLatestRelease();
                        if (info.hasUpdate) {
                            publish("Update found: " + info.latestVersion);
                        } else {
                            publish("Up to date.");
                        }
                        return info;

                    } catch (Throwable t) {
                        CrashLogger.log("Bootstrap update check failed", t);
                        publish("Update check failed (continuing).");
                        return UpdateInfo.none();
                    }
                }

                @Override
                protected void process(List<String> chunks) {
                    if (chunks != null && !chunks.isEmpty()) {
                        win.setStatus(chunks.get(chunks.size() - 1));
                    }
                }

                @Override
                protected void done() {
                    int remaining = (int) Math.max(0, MIN_VISIBLE_MS - (System.currentTimeMillis() - start));

                    Timer timer = new Timer(remaining, e -> {
                        ((Timer) e.getSource()).stop();

                        UpdateInfo info = UpdateInfo.none();
                        try {
                            info = get();
                        } catch (Throwable t) {
                            CrashLogger.log("Bootstrap get() failed", t);
                        }

                        // If update exists, prompt the user
                        if (info != null && info.hasUpdate && info.jarUrl != null) {
                            int choice = JOptionPane.showConfirmDialog(
                                    win,
                                    "A new version is available:\n\n" +
                                            "Current: " + CURRENT_VERSION + "\n" +
                                            "Latest:  " + info.latestVersion + "\n\n" +
                                            "Would you like to update now?",
                                    "Update Available",
                                    JOptionPane.YES_NO_OPTION,
                                    JOptionPane.QUESTION_MESSAGE
                            );

                            if (choice == JOptionPane.YES_OPTION) {
                                runUpdateFlow(win, info);
                                return; // update flow will exit or continue
                            }
                        }

                        // No update (or user declined) -> launch app
                        win.setStatus("Launching TBC…");
                        launchMainGUI();

                        // Close launcher shortly after launch request
                        Timer close = new Timer(300, e2 -> {
                            ((Timer) e2.getSource()).stop();
                            win.dispose();
                        });
                        close.setRepeats(false);
                        close.start();
                    });

                    timer.setRepeats(false);
                    timer.start();
                }
            };

            worker.execute();
        });
    }

    // ------------------------------------------------------------
    // UPDATE FLOW (download -> schedule replace -> exit)
    // ------------------------------------------------------------
    private static void runUpdateFlow(LauncherWindow win, UpdateInfo info) {
        SwingWorker<Boolean, String> updater = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    File self = getRunningJarFile();
                    if (self == null) {
                        publish("Cannot self-update (not running from a jar).");
                        return false;
                    }

                    File dir = self.getParentFile();
                    if (dir == null) dir = new File(".");

                    File newJar = new File(dir, self.getName() + ".new");

                    publish("Downloading update…");
                    downloadWithProgress(info.jarUrl, newJar, pct -> publish("Downloading update… " + pct + "%"));

                    if (!newJar.exists() || newJar.length() < 1024) {
                        publish("Download failed (file too small).");
                        return false;
                    }

                    publish("Applying update…");
                    scheduleReplaceAndRestart(self, newJar);

                    publish("Restarting…");
                    return true;

                } catch (Throwable t) {
                    CrashLogger.log("Update flow failed", t);
                    publish("Update failed (continuing).");
                    return false;
                }
            }

            @Override
            protected void process(List<String> chunks) {
                if (chunks != null && !chunks.isEmpty()) {
                    win.setStatus(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                boolean ok = false;
                try { ok = get(); } catch (Throwable ignored) {}

                if (ok) {
                    // Exit so the jar is no longer locked; the .cmd will move it and restart.
                    win.dispose();
                    System.exit(0);
                } else {
                    // If update failed, just launch normally.
                    win.setStatus("Launching TBC…");
                    launchMainGUI();
                    Timer close = new Timer(300, e2 -> {
                        ((Timer) e2.getSource()).stop();
                        win.dispose();
                    });
                    close.setRepeats(false);
                    close.start();
                }
            }
        };

        updater.execute();
    }

    /**
     * Schedules jar replacement + restart on Windows by creating a temporary .cmd file.
     * This is required because a running jar cannot overwrite itself.
     */
    private static void scheduleReplaceAndRestart(File currentJar, File newJar) throws IOException {
        String javaExe = findJavaExe();

        // Helper script in same directory so it can access the jars reliably
        File dir = currentJar.getParentFile();
        if (dir == null) dir = new File(".");

        File script = new File(dir, "tbc_update.cmd");

        String currentPath = currentJar.getAbsolutePath();
        String newPath = newJar.getAbsolutePath();

        // Loop until move succeeds (jar must be unlocked after we exit)
        String cmd =
                "@echo off\r\n" +
                        "setlocal\r\n" +
                        "set CUR=\"" + escapeForCmd(currentPath) + "\"\r\n" +
                        "set NEW=\"" + escapeForCmd(newPath) + "\"\r\n" +
                        "set JAVA=\"" + escapeForCmd(javaExe) + "\"\r\n" +
                        "\r\n" +
                        ":loop\r\n" +
                        "ping 127.0.0.1 -n 2 >nul\r\n" +
                        "move /y %NEW% %CUR% >nul 2>&1\r\n" +
                        "if errorlevel 1 goto loop\r\n" +
                        "start \"\" %JAVA% -jar %CUR%\r\n" +
                        "del \"%~f0\" >nul 2>&1\r\n" +
                        "endlocal\r\n";

        Files.write(script.toPath(), cmd.getBytes(StandardCharsets.UTF_8));

        // Run the script minimized in the background
        new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", "/min", script.getAbsolutePath())
                .directory(dir)
                .start();
    }

    private static String escapeForCmd(String s) {
        // Basic safety: cmd variable quoting already used, so escape quotes
        return s.replace("\"", "\"\"");
    }

    private static String findJavaExe() {
        // Prefer javaw for GUI apps
        String home = System.getProperty("java.home");
        File javaw = new File(home, "bin\\javaw.exe");
        if (javaw.exists()) return javaw.getAbsolutePath();

        File java = new File(home, "bin\\java.exe");
        if (java.exists()) return java.getAbsolutePath();

        // Fallback to PATH
        return "javaw";
    }

    // ------------------------------------------------------------
    // GITHUB UPDATE CHECK (Releases/latest)
    // ------------------------------------------------------------

    private static UpdateInfo checkGithubLatestRelease() throws IOException {
        // Minimal JSON parsing (no gson required here)
        String json = httpGetText(new URL(LATEST_RELEASE_API));

        String tag = extractJsonString(json, "\"tag_name\"");
        if (tag == null || tag.trim().isEmpty()) return UpdateInfo.none();

        // Normalize tag like "v1.2.3" -> "1.2.3"
        String latest = tag.trim();
        if (latest.startsWith("v") || latest.startsWith("V")) latest = latest.substring(1);

        if (!isNewer(latest, CURRENT_VERSION)) return UpdateInfo.none();

        // Find first asset that ends with ".jar" (and not "sources"/"javadoc")
        String jarUrl = extractFirstJarDownloadUrl(json);
        if (jarUrl == null) return UpdateInfo.none();

        UpdateInfo info = new UpdateInfo();
        info.hasUpdate = true;
        info.latestVersion = latest;
        info.jarUrl = jarUrl;
        return info;
    }

    private static boolean isNewer(String latest, String current) {
        // If your versions are not strictly numeric (e.g. beta tags), use simple inequality:
        if (latest.equalsIgnoreCase(current)) return false;

        // Try numeric compare: "1.2.3" vs "1.2.10"
        try {
            int[] a = parseVer(latest);
            int[] b = parseVer(current);
            for (int i = 0; i < Math.max(a.length, b.length); i++) {
                int ai = i < a.length ? a[i] : 0;
                int bi = i < b.length ? b[i] : 0;
                if (ai != bi) return ai > bi;
            }
            return false;
        } catch (Throwable ignored) {
            // Fallback
            return true;
        }
    }

    private static int[] parseVer(String v) {
        String[] parts = v.replaceAll("[^0-9.]", "").split("\\.");
        List<Integer> nums = new ArrayList<>();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            nums.add(Integer.parseInt(p));
        }
        int[] out = new int[nums.size()];
        for (int i = 0; i < nums.size(); i++) out[i] = nums.get(i);
        return out;
    }

    private static String extractFirstJarDownloadUrl(String json) {
        // We look for: "browser_download_url":"...something.jar"
        int idx = 0;
        while (true) {
            int k = json.indexOf("\"browser_download_url\"", idx);
            if (k < 0) return null;

            int colon = json.indexOf(':', k);
            if (colon < 0) return null;

            int q1 = json.indexOf('"', colon + 1);
            int q2 = json.indexOf('"', q1 + 1);
            if (q1 < 0 || q2 < 0) return null;

            String url = json.substring(q1 + 1, q2);

            String lower = url.toLowerCase();
            if (lower.endsWith(".jar") && !lower.contains("sources") && !lower.contains("javadoc")) {
                return url;
            }

            idx = q2 + 1;
        }
    }

    private static String extractJsonString(String json, String key) {
        // key example: "\"tag_name\""
        int k = json.indexOf(key);
        if (k < 0) return null;

        int colon = json.indexOf(':', k);
        if (colon < 0) return null;

        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return null;

        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;

        return json.substring(q1 + 1, q2);
    }

    private static String httpGetText(URL url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setConnectTimeout(10_000);
        con.setReadTimeout(20_000);
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", "TBC/" + CURRENT_VERSION);

        int code = con.getResponseCode();
        InputStream in = (code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream();
        if (in == null) throw new IOException("HTTP " + code + " for " + url);

        ByteArrayOutputStream bout = new ByteArrayOutputStream(8192);
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) >= 0) bout.write(buf, 0, r);

        String text = new String(bout.toByteArray(), StandardCharsets.UTF_8);
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " for " + url + "\n" + text);
        return text;
    }

    private interface ProgressCb { void onProgress(int percent); }

    private static void downloadWithProgress(String urlStr, File dest, ProgressCb cb) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(urlStr).openConnection();
        con.setConnectTimeout(10_000);
        con.setReadTimeout(60_000);
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", "TBC/" + CURRENT_VERSION);

        int code = con.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " for " + urlStr);

        int len = con.getContentLength();

        try (InputStream in = con.getInputStream();
             OutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {

            byte[] buf = new byte[8192];
            long total = 0;
            int lastPct = -1;

            int r;
            while ((r = in.read(buf)) >= 0) {
                out.write(buf, 0, r);
                total += r;

                if (len > 0) {
                    int pct = (int) Math.min(100, (total * 100L) / len);
                    if (pct != lastPct) {
                        lastPct = pct;
                        if (cb != null) cb.onProgress(pct);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------
    // LAUNCH GUI (supports either TBCGUI.launch() or TBCGUI.main())
    // ------------------------------------------------------------
    private static void launchMainGUI() {
        try {
            // Prefer: public static void launch()
            try {
                TBCGUI.class.getMethod("launch").invoke(null);
                return;
            } catch (NoSuchMethodException ignored) {
                // fallback
            }
            // Fallback: call main
            TBCGUI.main(new String[0]);

        } catch (Throwable t) {
            CrashLogger.log("Failed to launch GUI", t);
            JOptionPane.showMessageDialog(
                    null,
                    "Failed to launch TBC.\n\n" + t.getMessage(),
                    "Launch Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private static File getRunningJarFile() {
        try {
            File f = new File(TBCBootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) return f;
        } catch (Throwable ignored) {}
        return null;
    }

    // ------------------------------------------------------------
    // Small launcher window
    // ------------------------------------------------------------
    private static final class LauncherWindow extends JFrame {
        private final JLabel statusLabel;

        LauncherWindow() {
            super("TBC Launcher");
            setUndecorated(true);
            setSize(520, 320);
            setLocationRelativeTo(null);
            setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));

            JPanel root = new JPanel(new BorderLayout());
            root.setBackground(BG_DARKEST);
            root.setBorder(new EmptyBorder(16, 16, 16, 16));
            setContentPane(root);

            JPanel card = new JPanel(new BorderLayout());
            card.setBackground(BG_DARK);
            card.setBorder(new EmptyBorder(18, 18, 18, 18));
            root.add(card, BorderLayout.CENTER);

            // Top: Logo + Title
            JPanel top = new JPanel(new BorderLayout(14, 0));
            top.setOpaque(false);

            JLabel logo = new JLabel();
            logo.setPreferredSize(new Dimension(96, 96));
            logo.setHorizontalAlignment(SwingConstants.CENTER);

            // Optional: src/Main/Resources/tbc_logo.png
            try {
                URL url = TBCBootstrap.class.getResource("/tbc_logo.png");
                if (url != null) {
                    BufferedImage img = ImageIO.read(url);
                    if (img != null) {
                        Image scaled = img.getScaledInstance(96, 96, Image.SCALE_SMOOTH);
                        logo.setIcon(new ImageIcon(scaled));
                    }
                }
            } catch (Exception ignored) {}

            JPanel titleBox = new JPanel();
            titleBox.setOpaque(false);
            titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

            JLabel title = new JLabel("TBC");
            title.setForeground(TEXT);
            title.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 28));

            JLabel subtitle = new JLabel("Preparing application…");
            subtitle.setForeground(MUTED);
            subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));

            titleBox.add(title);
            titleBox.add(Box.createVerticalStrut(6));
            titleBox.add(subtitle);

            top.add(logo, BorderLayout.WEST);
            top.add(titleBox, BorderLayout.CENTER);

            card.add(top, BorderLayout.NORTH);

            // Center: Progress
            JPanel center = new JPanel();
            center.setOpaque(false);
            center.setBorder(new EmptyBorder(18, 0, 0, 0));
            center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

            JProgressBar bar = new JProgressBar();
            bar.setIndeterminate(true);
            bar.setBorderPainted(false);
            bar.setForeground(ACCENT);
            bar.setBackground(BG_MID);
            bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));

            statusLabel = new JLabel("Starting…");
            statusLabel.setForeground(TEXT);
            statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            statusLabel.setBorder(new EmptyBorder(14, 2, 0, 0));

            center.add(bar);
            center.add(statusLabel);

            card.add(center, BorderLayout.CENTER);
        }

        void setStatus(String text) {
            statusLabel.setText(text);
        }
    }

    private static final class UpdateInfo {
        boolean hasUpdate;
        String latestVersion;
        String jarUrl;

        static UpdateInfo none() {
            UpdateInfo i = new UpdateInfo();
            i.hasUpdate = false;
            return i;
        }
    }
}
