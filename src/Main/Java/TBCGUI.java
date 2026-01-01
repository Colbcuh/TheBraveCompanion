package Main.Java;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.RadialGradientPaint;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class TBCGUI {

    // Colors
    private static final Color BG_DARKEST = new Color(32, 34, 37);
    private static final Color BG_DARK    = new Color(44, 47, 51);
    private static final Color BG_MID     = new Color(54, 57, 63);

    private static final Color ACCENT       = new Color(40, 130, 210);
    private static final Color TEXT_PRIMARY = new Color(235, 235, 235);
    private static final Color TEXT_MUTED   = new Color(170, 170, 170);
    private static final Color TEXT_LIGHT   = new Color(200, 200, 200);
    private static final Color CLOSE_RED    = new Color(237, 66, 69);

    private static final int CORNER_RADIUS = 18;

    // Sidebar: 0 = at very top
    private static final int TAB_TOP_PADDING = 0;

    // Left name column width
    private static final int LEFT_COLUMN_WIDTH = 280;

    // Name field sizing
    private static final int NAME_FIELD_WIDTH  = 154;
    private static final int NAME_FIELD_HEIGHT = 20;
    private static final int NAME_ROW_HEIGHT   = 30;

    // Random Order box sizing
    private static final int OUTPUT_WIDTH  = 190;
    private static final int OUTPUT_HEIGHT = 420;

    /**
     * Dev entrypoint (running from IntelliJ).
     * In release, your JAR Main-Class should be TBCBootstrap.
     */
    public static void main(String[] args) {
        // Errors-only logging (safe if bootstrap already initialized)
        CrashLogger.init("TBC");
        launch();
    }

    /**
     * Public entrypoint for the bootstrap/launcher to start the UI.
     */
    public static void launch() {
        SwingUtilities.invokeLater(TBCGUI::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame();
        frame.setUndecorated(true);

        // Slightly larger default than before to fit UB better (no scrollbars)
        frame.setSize(1100, 720);
        frame.setLocationRelativeTo(null);

        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                frame.setShape(new RoundRectangle2D.Double(
                        0, 0, frame.getWidth(), frame.getHeight(),
                        CORNER_RADIUS, CORNER_RADIUS
                ));
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_DARKEST);
        root.setBorder(new EmptyBorder(6, 6, 6, 6));
        frame.setContentPane(root);

        root.add(createTitleBar(frame), BorderLayout.NORTH);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG_DARK);
        root.add(main, BorderLayout.CENTER);

        // Shared roster between tabs
        RosterModel rosterModel = new RosterModel();

        JPanel contentHolder = new JPanel(new CardLayout());
        contentHolder.setBackground(BG_DARK);

        UB ubPanel = new UB(rosterModel);
        JPanel crtPanel = createCRTPanel(rosterModel, ubPanel);

        contentHolder.add(crtPanel, "crt");
        contentHolder.add(ubPanel, "ub");

        main.add(createSideBar(contentHolder), BorderLayout.WEST);
        main.add(contentHolder, BorderLayout.CENTER);

        ((CardLayout) contentHolder.getLayout()).show(contentHolder, "crt");

        frame.setVisible(true);
    }

    // ---------------------------------------------------------
    // TITLE BAR
    // ---------------------------------------------------------

    private static JPanel createTitleBar(JFrame frame) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BG_DARKEST);
        bar.setBorder(new EmptyBorder(4, 10, 4, 10));

        JLabel title = new JLabel("TBC");
        title.setForeground(TEXT_PRIMARY);
        title.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 14));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(title);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);
        right.add(new TitleBtn(TitleBtn.Type.MINIMIZE, frame));
        right.add(new TitleBtn(TitleBtn.Type.MAXIMIZE, frame));
        right.add(new TitleBtn(TitleBtn.Type.CLOSE, frame));

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);

        final Point[] offset = {null};
        bar.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { offset[0] = e.getPoint(); }
        });
        bar.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                Point p = e.getLocationOnScreen();
                frame.setLocation(p.x - offset[0].x, p.y - offset[0].y);
            }
        });

        return bar;
    }

    private static class TitleBtn extends JButton {
        enum Type { MINIMIZE, MAXIMIZE, CLOSE }
        private final Type type;
        private final JFrame frame;
        private boolean hover = false;
        private boolean pressed = false;

        public TitleBtn(Type type, JFrame frame) {
            this.type = type;
            this.frame = frame;

            setPreferredSize(new Dimension(34, 22));
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e){ hover = true; repaint(); }
                @Override public void mouseExited (MouseEvent e){ hover = false; pressed=false; repaint(); }
                @Override public void mousePressed(MouseEvent e){ pressed = true; repaint(); }
                @Override public void mouseReleased(MouseEvent e){
                    pressed = false; repaint();
                    if (contains(e.getPoint())) perform();
                }
            });
        }

        private void perform() {
            switch(type){
                case MINIMIZE: frame.setState(Frame.ICONIFIED); break;
                case MAXIMIZE:
                    if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH)
                        frame.setExtendedState(Frame.NORMAL);
                    else frame.setExtendedState(Frame.MAXIMIZED_BOTH);
                    break;
                case CLOSE: System.exit(0); break;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w=getWidth(), h=getHeight();
            if(hover || pressed){
                Color bg = (type == Type.CLOSE)
                        ? (pressed ? CLOSE_RED.darker() : CLOSE_RED)
                        : (pressed ? BG_MID.darker() : BG_MID);
                g2.setColor(bg);
                g2.fillRoundRect(0,0,w,h,8,8);
            }

            g2.setColor(hover||pressed ? TEXT_PRIMARY : TEXT_MUTED);
            g2.setStroke(new BasicStroke(1.7f));

            int cx=w/2, cy=h/2;

            switch(type){
                case MINIMIZE: g2.drawLine(cx-6, cy+2, cx+6, cy+2); break;
                case MAXIMIZE: g2.drawRect(cx-5, cy-5, 10,10); break;
                case CLOSE:
                    g2.drawLine(cx-6, cy-6, cx+6, cy+6);
                    g2.drawLine(cx-6, cy+6, cx+6, cy-6);
                    break;
            }

            g2.dispose();
        }
    }

    // ---------------------------------------------------------
    // LEFT SIDEBAR
    // ---------------------------------------------------------

    private static JPanel createSideBar(JPanel contentHolder) {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(BG_DARKEST);
        side.setPreferredSize(new Dimension(75, 0));

        JPanel tabsColumn = new JPanel();
        tabsColumn.setOpaque(false);
        tabsColumn.setLayout(new BoxLayout(tabsColumn, BoxLayout.Y_AXIS));
        tabsColumn.setBorder(new EmptyBorder(TAB_TOP_PADDING, 10, 10, 10));

        CardLayout cl = (CardLayout) contentHolder.getLayout();

        final SideTab crtTab = new SideTab("CRT");
        final SideTab ubTab  = new SideTab("UB");

        crtTab.setSelected(true);
        ubTab.setSelected(false);

        crtTab.setOnClick(() -> {
            cl.show(contentHolder, "crt");
            crtTab.setSelected(true);
            ubTab.setSelected(false);
        });

        ubTab.setOnClick(() -> {
            cl.show(contentHolder, "ub");
            ubTab.setSelected(true);
            crtTab.setSelected(false);
        });

        crtTab.setAlignmentX(Component.CENTER_ALIGNMENT);
        ubTab.setAlignmentX(Component.CENTER_ALIGNMENT);

        tabsColumn.add(Box.createVerticalStrut(-6));
        tabsColumn.add(crtTab);
        tabsColumn.add(Box.createVerticalStrut(10));
        tabsColumn.add(ubTab);
        tabsColumn.add(Box.createVerticalGlue());

        side.add(tabsColumn, BorderLayout.CENTER);

        JLabel discord = new JLabel();
        discord.setHorizontalAlignment(SwingConstants.CENTER);
        discord.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        try {
            BufferedImage img = ImageIO.read(TBCGUI.class.getResource("/discord_logo.png"));
            if (img != null) {
                Image scaled = img.getScaledInstance(48, 48, Image.SCALE_SMOOTH);
                discord.setIcon(new ImageIcon(scaled));
            } else {
                discord.setText("D");
                discord.setForeground(TEXT_MUTED);
            }
        } catch (Exception e) {
            discord.setText("D");
            discord.setForeground(TEXT_MUTED);
        }

        discord.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new URI("https://discord.gg/JTWBydQmMP"));
                } catch (Exception ex) {
                    CrashLogger.log("Failed to open Discord link", ex);
                }
            }
        });

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(new EmptyBorder(0, 0, 10, 0));
        bottom.add(discord, BorderLayout.SOUTH);

        side.add(bottom, BorderLayout.SOUTH);

        return side;
    }

    private static class SideTab extends JComponent {
        private boolean hover = false, pressed = false, selected = false;
        private final String text;
        private Runnable onClick;

        public SideTab(String text) {
            this.text = text;
            setPreferredSize(new Dimension(48, 48));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e){ hover = true; repaint(); }
                @Override public void mouseExited (MouseEvent e){ hover = false; pressed = false; repaint(); }
                @Override public void mousePressed(MouseEvent e){ pressed = true; repaint(); }
                @Override public void mouseReleased(MouseEvent e){
                    pressed = false; repaint();
                    if (contains(e.getPoint()) && onClick != null) onClick.run();
                }
            });
        }

        public void setOnClick(Runnable r) { this.onClick = r; }
        public void setSelected(boolean s) { this.selected = s; repaint(); }

        @Override
        public boolean contains(int x, int y) {
            int size = Math.min(getWidth(), getHeight());
            int d = size - 8;
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int dx = x - cx;
            int dy = y - cy;
            int radius = d / 2;
            return dx * dx + dy * dy <= radius * radius;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int size = Math.min(getWidth(), getHeight());
            int d = size - 8;
            int x = (getWidth() - d) / 2;
            int y = (getHeight() - d) / 2;

            Color inner = BG_MID;
            Color outer = BG_DARK;

            if (hover && !pressed) {
                inner = inner.brighter();
            } else if (pressed) {
                inner = inner.darker();
                outer = outer.darker();
            }

            RadialGradientPaint paint = new RadialGradientPaint(
                    new Point2D.Float(getWidth() / 2f, getHeight() / 2f),
                    d / 2f,
                    new float[]{0f, 1f},
                    new Color[]{inner, outer}
            );
            g2.setPaint(paint);
            g2.fillOval(x, y, d, d);

            if (!selected) {
                g2.setColor(new Color(255, 255, 255, 40));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(x, y, d, d);
            } else {
                g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 70));
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(x - 1, y - 1, d + 2, d + 2);
            }

            String label = text.toUpperCase();
            Font f = new Font("Segoe UI Semibold", Font.PLAIN, 14);
            g2.setFont(f);

            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(label);
            int textX = getWidth() / 2 - textWidth / 2;
            int textY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();

            g2.setColor(new Color(0, 0, 0, 150));
            g2.drawString(label, textX + 1, textY + 1);

            g2.setColor(Color.WHITE);
            g2.drawString(label, textX, textY);

            g2.dispose();
        }
    }

    // ---------------------------------------------------------
    // CRT PANEL
    // ---------------------------------------------------------

    private static JPanel createCRTPanel(RosterModel rosterModel, UB ubPanel) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(20,20,20,20));

        JPanel left = new JPanel();
        left.setBackground(BG_DARK);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setPreferredSize(new Dimension(LEFT_COLUMN_WIDTH, 0));
        left.setMinimumSize(new Dimension(LEFT_COLUMN_WIDTH, 0));

        JLabel namesLabel = new JLabel("Enter names:");
        namesLabel.setForeground(TEXT_PRIMARY);
        namesLabel.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 14));
        left.add(namesLabel);
        left.add(Box.createVerticalStrut(8));

        JTextField[] nameFields = new JTextField[5];

        for (int i = 0; i < 5; i++) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);

            JLabel lbl = new JLabel("Name " + (i + 1) + ": ");
            lbl.setForeground(TEXT_PRIMARY);
            lbl.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 13));

            RoundedTextField tf = new RoundedTextField();
            tf.setPreferredSize(new Dimension(NAME_FIELD_WIDTH, NAME_FIELD_HEIGHT));
            tf.setMinimumSize(new Dimension(NAME_FIELD_WIDTH, NAME_FIELD_HEIGHT));
            tf.setMaximumSize(new Dimension(NAME_FIELD_WIDTH, NAME_FIELD_HEIGHT));

            nameFields[i] = tf;

            row.add(lbl, BorderLayout.WEST);
            row.add(tf, BorderLayout.CENTER);

            row.setPreferredSize(new Dimension(Integer.MAX_VALUE, NAME_ROW_HEIGHT));
            row.setMinimumSize(new Dimension(0, NAME_ROW_HEIGHT));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, NAME_ROW_HEIGHT));

            left.add(row);
            left.add(Box.createVerticalStrut(10));
        }

        JPanel right = new JPanel();
        right.setBackground(BG_DARK);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBorder(new EmptyBorder(0,20,0,0));

        JLabel orderLabel = new JLabel("Random Order");
        orderLabel.setForeground(TEXT_PRIMARY);
        orderLabel.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 14));
        orderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        RoundedTextArea outputArea = new RoundedTextArea();
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        outputArea.setForeground(TEXT_LIGHT);
        outputArea.setFont(new Font("Consolas", Font.PLAIN, 18));

        JPanel outputWrapper = new JPanel(new BorderLayout()) {
            @Override public Dimension getPreferredSize() { return new Dimension(OUTPUT_WIDTH, OUTPUT_HEIGHT); }
            @Override public Dimension getMaximumSize() { return new Dimension(OUTPUT_WIDTH, Integer.MAX_VALUE); }
        };
        outputWrapper.setOpaque(false);
        outputWrapper.add(outputArea, BorderLayout.CENTER);
        outputWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        right.add(orderLabel);
        right.add(Box.createVerticalStrut(8));
        right.add(outputWrapper);

        RoundedButton generateBtn = new RoundedButton("Generate");
        generateBtn.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 15));
        generateBtn.setForeground(TEXT_PRIMARY);
        generateBtn.setBackground(ACCENT);
        generateBtn.setBorder(BorderFactory.createEmptyBorder(8,20,8,20));
        generateBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        generateBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e){ generateBtn.setBackground(ACCENT.brighter()); }
            @Override public void mouseExited (MouseEvent e){ generateBtn.setBackground(ACCENT); }
        });

        generateBtn.addActionListener(e -> {
            List<String> players = new ArrayList<>();
            for (JTextField tf : nameFields) {
                String text = tf.getText().trim();
                if (!text.isEmpty()) players.add(text);
            }
            if (players.size() < 2) {
                outputArea.setText("Enter at least two names.");
                return;
            }

            rosterModel.setPlayers(players);
            ubPanel.refreshFromRoster();

            outputArea.setText(buildRandomOrder(players, 5));
        });

        JPanel bottom = new JPanel();
        bottom.setBackground(BG_DARK);
        bottom.setBorder(new EmptyBorder(16,0,0,0));
        bottom.add(generateBtn);

        panel.add(left, BorderLayout.WEST);
        panel.add(right, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    private static String buildRandomOrder(List<String> players, int lines){
        List<String> list = new ArrayList<>(new LinkedHashSet<>(players));
        Collections.shuffle(list);
        StringBuilder sb = new StringBuilder();
        for(int i=0;i<lines;i++){
            sb.append(list.get(i % list.size()))
                    .append(" > ")
                    .append(list.get((i+1) % list.size()))
                    .append("\n");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------
    // ROUNDED BUTTON / FIELDS
    // ---------------------------------------------------------

    private static class RoundedButton extends JButton {
        private final int arc = 18;

        public RoundedButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0, 0, 0, 80));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.dispose();
        }
    }

    private static class RoundedTextField extends JTextField {
        private final int arc = 16;

        public RoundedTextField() {
            setOpaque(false);
            setBackground(BG_MID);
            setCaretColor(Color.WHITE);
            setForeground(TEXT_LIGHT);
            setFont(new Font("Segoe UI", Font.PLAIN, 12));
            setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BG_MID);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0, 0, 0, 80));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.dispose();
        }
    }

    private static class RoundedTextArea extends JTextArea {
        private final int arc = 16;

        public RoundedTextArea() {
            setOpaque(false);
            setBackground(BG_MID);
            setBorder(BorderFactory.createEmptyBorder(6,10,6,10));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BG_MID);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0, 0, 0, 80));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.dispose();
        }
    }
}
