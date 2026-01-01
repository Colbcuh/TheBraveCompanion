package Main.Java;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.List;

public final class TBCBootstrap {

    private static final Color BG_DARKEST = new Color(32, 34, 37);
    private static final Color BG_DARK    = new Color(44, 47, 51);
    private static final Color BG_MID     = new Color(54, 57, 63);
    private static final Color ACCENT     = new Color(40, 130, 210);
    private static final Color TEXT       = new Color(235, 235, 235);
    private static final Color MUTED      = new Color(170, 170, 170);

    private static final int CORNER_RADIUS = 18;

    // Make it visible long enough to read (adjust if you want longer)
    private static final int MIN_VISIBLE_MS = 2500;

    // If the GUI never appears, stop waiting after this long
    private static final int GUI_APPEAR_TIMEOUT_MS = 8000;

    private TBCBootstrap() {}

    public static void main(String[] args) {
        CrashLogger.init("TBC");

        SwingUtilities.invokeLater(() -> {
            LauncherWindow win = new LauncherWindow();
            win.setVisible(true);

            // Give Swing a brief moment to paint the window before doing work
            new Timer(120, e -> {
                ((Timer) e.getSource()).stop();
                startWorker(win);
            }).start();
        });
    }

    private static void startWorker(LauncherWindow win) {
        final long start = System.currentTimeMillis();

        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() {
                try {
                    publish("Starting…");

                    // Ensure folders exist
                    AppPaths.baseDir().mkdirs();
                    AppPaths.appDir().mkdirs();
                    AppPaths.cacheDir().mkdirs();
                    AppPaths.logsDir().mkdirs();

                    publish("Preparing resources…");
                    Thread.sleep(250);

                    publish("Launching TBC…");
                    Thread.sleep(250);

                } catch (Throwable t) {
                    CrashLogger.log("Bootstrap preflight failed", t);
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    win.setStatus(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                long elapsed = System.currentTimeMillis() - start;
                int remaining = (int) Math.max(0, MIN_VISIBLE_MS - elapsed);

                // Wait until min visible time has passed, THEN launch GUI and wait until it appears
                Timer afterMin = new Timer(remaining, e -> {
                    ((Timer) e.getSource()).stop();

                    // Request the GUI start
                    TBCGUI.launch();

                    // Now keep the loader up until the GUI is actually visible
                    waitForGuiThenClose(win);

                });
                afterMin.setRepeats(false);
                afterMin.start();
            }
        };

        worker.execute();
    }

    private static void waitForGuiThenClose(LauncherWindow win) {
        final long waitStart = System.currentTimeMillis();

        Timer poll = new Timer(60, null);
        poll.addActionListener(e -> {
            boolean guiVisible = false;

            for (Frame f : Frame.getFrames()) {
                if (f != null && f.isVisible() && f != win) {
                    guiVisible = true;
                    break;
                }
            }

            long waited = System.currentTimeMillis() - waitStart;

            if (guiVisible) {
                poll.stop();
                win.dispose();
                return;
            }

            if (waited >= GUI_APPEAR_TIMEOUT_MS) {
                poll.stop();
                win.setStatus("Still starting… (check logs if it hangs)");
                // Leave window open a bit longer so the user can read the message
                new Timer(1500, e2 -> {
                    ((Timer) e2.getSource()).stop();
                    win.dispose();
                }).start();
            }
        });
        poll.start();
    }

    // -------------------------------------------------------------------------
    // Launcher Window
    // -------------------------------------------------------------------------
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
                URL url = TBCBootstrap.class.getResource("/tbc_logo.png");
                if (url != null) {
                    BufferedImage img = ImageIO.read(url);
                    Image scaled = img.getScaledInstance(96, 96, Image.SCALE_SMOOTH);
                    logo.setIcon(new ImageIcon(scaled));
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
}
