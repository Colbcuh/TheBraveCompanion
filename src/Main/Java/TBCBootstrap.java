package Main.Java;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.nio.file.*;
import java.util.Locale;

public final class TBCBootstrap {

    // ====== CONFIG YOU MUST SET ======
    // GitHub repo for Releases
    private static final String GITHUB_OWNER = "Colbcuh";
    private static final String GITHUB_REPO  = "TheBraveCompanion";

    // Your current version (bump when you publish a new release)
    private static final String APP_VERSION_FALLBACK = "0.1.0";
    // =================================

    // Theme
    private static final Color BG_DARKEST = new Color(32, 34, 37);
    private static final Color BG_DARK    = new Color(44, 47, 51);
    private static final Color BG_MID     = new Color(54, 57, 63);
    private static final Color ACCENT     = new Color(40, 130, 210);
    private static final Color TEXT       = new Color(235, 235, 235);
    private static final Color MUTED      = new Color(170, 170, 170);

    private static final int CORNER_RADIUS = 18;

    private TBCBootstrap() {}

    public static void main(String[] args) {
        // Updater mode (runs from a temp copy so it can replace the original jar)
        if (args != null && args.length > 0 && "--updater".equalsIgnoreCase(args[0])) {
            runUpdaterMode(args);
            return;
        }

        CrashLogger.init("TBC");
        AppPaths.ensureDirs();

        SwingUtilities.invokeLater(() -> {
            LauncherWindow win = new LauncherWindow();
            win.setVisible(true);

            SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {

                boolean launchedUpdaterOrNewJar = false;

                @Override
                protected Void doInBackground() {
                    try {
                        publish("Preparing…");

                        // If not running from a jar (e.g., in IntelliJ), skip update
                        Path currentJar = getRunningJarPathOrNull();
                        if (currentJar == null) {
                            publish("Launching…");
                            return null;
                        }

                        publish("Checking for updates…");

                        String currentVersion = VersionUtil.currentVersion(APP_VERSION_FALLBACK);

                        UpdateService.LatestRelease latest;
                        try {
                            latest = UpdateService.fetchLatestRelease(GITHUB_OWNER, GITHUB_REPO);
                        } catch (Throwable t) {
                            // Network / GitHub error: just launch
                            publish("Launching…");
                            return null;
                        }

                        if (!VersionUtil.isNewer(latest.version, currentVersion)) {
                            publish("Up to date. Launching…");
                            return null;
                        }

                        // Ask the user
                        final boolean[] yes = new boolean[]{false};
                        SwingUtilities.invokeAndWait(() -> {
                            int choice = JOptionPane.showConfirmDialog(
                                    win,
                                    "Update available:\n\nCurrent: " + currentVersion +
                                            "\nLatest: " + latest.version +
                                            "\n\nInstall update now?",
                                    "Update Available",
                                    JOptionPane.YES_NO_OPTION,
                                    JOptionPane.QUESTION_MESSAGE
                            );
                            yes[0] = (choice == JOptionPane.YES_OPTION);
                        });

                        if (!yes[0]) {
                            publish("Launching…");
                            return null;
                        }

                        publish("Downloading update…");

                        // Download next to existing jar if possible
                        Path jarDir = currentJar.getParent();
                        String targetName = currentJar.getFileName().toString();
                        Path newJar = jarDir.resolve(targetName + ".new");

                        try {
                            UpdateService.downloadTo(newJar, latest.jarUrl, text -> publish(text));
                        } catch (Throwable t) {
                            // Fallback: download to AppData and launch that (no replacement)
                            Path fallback = Paths.get(AppPaths.appDir().getAbsolutePath(),
                                    "TBC-" + latest.version + ".jar");

                            UpdateService.downloadTo(fallback, latest.jarUrl, text -> publish(text));

                            publish("Launching new version…");
                            launchJar(fallback);
                            launchedUpdaterOrNewJar = true;
                            return null;
                        }

                        publish("Installing update…");

                        // Copy THIS jar to a temp updater copy so it can replace the original.
                        Path updaterJar = Paths.get(AppPaths.cacheDir().getAbsolutePath(), "updater.jar");
                        Files.copy(currentJar, updaterJar, StandardCopyOption.REPLACE_EXISTING);

                        // Launch updater copy in --updater mode, passing target and new paths.
                        launchUpdater(updaterJar, currentJar, newJar);
                        launchedUpdaterOrNewJar = true;

                    } catch (Throwable t) {
                        publish("Launching…");
                    }
                    return null;
                }

                @Override
                protected void process(java.util.List<String> chunks) {
                    if (!chunks.isEmpty()) {
                        win.setStatus(chunks.get(chunks.size() - 1));
                    }
                }

                @Override
                protected void done() {
                    // If we launched updater/new jar, exit so file locks are released
                    if (launchedUpdaterOrNewJar) {
                        win.dispose();
                        System.exit(0);
                        return;
                    }

                    // Otherwise launch normal app
                    win.setStatus("Launching…");
                    TBCGUI.launch();
                    win.dispose();
                }
            };

            worker.execute();
        });
    }

    // ==============================
    // Updater mode (runs from updater.jar copy)
    // ==============================
    private static void runUpdaterMode(String[] args) {
        // args:
        // 0 --updater
        // 1 targetJarPath
        // 2 newJarPath
        if (args.length < 3) return;

        final Path target = Paths.get(args[1]);
        final Path newJar  = Paths.get(args[2]);

        SwingUtilities.invokeLater(() -> {
            UpdaterWindow win = new UpdaterWindow();
            win.setVisible(true);

            SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
                @Override
                protected Void doInBackground() {
                    publish("Waiting for app to close…");

                    // Wait for the target jar to become replaceable
                    boolean replaced = tryReplaceWithRetries(target, newJar, 60, 250);

                    if (!replaced) {
                        publish("Could not replace in folder. Installing to AppData…");
                        try {
                            AppPaths.ensureDirs();
                            Path appJar = Paths.get(AppPaths.appDir().getAbsolutePath(), "TBC-latest.jar");
                            safeMove(newJar, appJar);
                            publish("Launching…");
                            launchJar(appJar);
                            return null;
                        } catch (Throwable t) {
                            publish("Update failed.");
                            return null;
                        }
                    }

                    publish("Launching updated version…");
                    launchJar(target);
                    return null;
                }

                @Override
                protected void process(java.util.List<String> chunks) {
                    if (!chunks.isEmpty()) win.setStatus(chunks.get(chunks.size() - 1));
                }

                @Override
                protected void done() {
                    win.dispose();
                    System.exit(0);
                }
            };

            worker.execute();
        });
    }

    private static boolean tryReplaceWithRetries(Path target, Path newJar, int attempts, int sleepMs) {
        for (int i = 0; i < attempts; i++) {
            try {
                Files.move(newJar, target, StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (Exception ignored) {
                try { Thread.sleep(sleepMs); } catch (InterruptedException ignored2) {}
            }
        }
        return false;
    }

    private static void safeMove(Path from, Path to) throws Exception {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
            try { Files.deleteIfExists(from); } catch (Exception ignored) {}
        }
    }

    private static void launchUpdater(Path updaterJar, Path targetJar, Path newJar) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                javawPath(),
                "-jar",
                updaterJar.toAbsolutePath().toString(),
                "--updater",
                targetJar.toAbsolutePath().toString(),
                newJar.toAbsolutePath().toString()
        );
        pb.directory(updaterJar.getParent().toFile());
        pb.start();
    }

    private static void launchJar(Path jar) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                javawPath(),
                "-jar",
                jar.toAbsolutePath().toString()
        );
        pb.directory(jar.getParent().toFile());
        pb.start();
    }

    private static String javawPath() {
        String javaHome = System.getProperty("java.home");
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

        if (windows) {
            File javaw = new File(javaHome, "bin\\javaw.exe");
            if (javaw.exists()) return javaw.getAbsolutePath();
        }

        File java = new File(javaHome, "bin\\java.exe");
        if (java.exists()) return java.getAbsolutePath();

        return windows ? "javaw" : "java";
    }

    private static Path getRunningJarPathOrNull() {
        try {
            URL url = TBCBootstrap.class.getProtectionDomain().getCodeSource().getLocation();
            if (url == null) return null;

            Path p = Paths.get(url.toURI());
            String s = p.toString().toLowerCase(Locale.ROOT);
            if (!s.endsWith(".jar")) return null;

            return p.toAbsolutePath();
        } catch (Exception ignored) {
            return null;
        }
    }

    // ==============================
    // UI windows
    // ==============================

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

            JPanel top = new JPanel(new BorderLayout(14, 0));
            top.setOpaque(false);

            JLabel logo = new JLabel();
            logo.setPreferredSize(new Dimension(96, 96));
            logo.setHorizontalAlignment(SwingConstants.CENTER);

            try {
                URL u = TBCBootstrap.class.getResource("/tbc_logo.png");
                if (u != null) {
                    BufferedImage img = ImageIO.read(u);
                    logo.setIcon(new ImageIcon(img.getScaledInstance(96, 96, Image.SCALE_SMOOTH)));
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

    private static final class UpdaterWindow extends JFrame {
        private final JLabel statusLabel;

        UpdaterWindow() {
            super("Updating TBC");
            setUndecorated(true);
            setSize(520, 220);
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

            JLabel title = new JLabel("Updating…");
            title.setForeground(TEXT);
            title.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 20));
            card.add(title, BorderLayout.NORTH);

            JPanel center = new JPanel();
            center.setOpaque(false);
            center.setBorder(new EmptyBorder(12, 0, 0, 0));
            center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

            JProgressBar bar = new JProgressBar();
            bar.setIndeterminate(true);
            bar.setBorderPainted(false);
            bar.setForeground(ACCENT);
            bar.setBackground(BG_MID);
            bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));

            statusLabel = new JLabel("Installing update…");
            statusLabel.setForeground(MUTED);
            statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            statusLabel.setBorder(new EmptyBorder(12, 2, 0, 0));

            center.add(bar);
            center.add(statusLabel);
            card.add(center, BorderLayout.CENTER);
        }

        void setStatus(String text) {
            statusLabel.setText(text);
        }
    }
}
