import java.awt.*;
import javax.swing.*;

/**
 * CollisionsMomentumPanel models a 1D interactive sandbox simulating elastic and inelastic collisions.
 * Leverages standard momentum conservation models to calculate post-impact velocity variables.
 * * Physics Models (Elastic):
 * v1f = ((m1 - m2) * v1i + 2 * m2 * v2i) / (m1 + m2)
 * v2f = ((m2 - m1) * v2i + 2 * m1 * v1i) / (m1 + m2)
 * * Physics Models (Perfectly Inelastic):
 * vf  = (m1 * v1i + m2 * v2i) / (m1 + m2)
 * * Boundary Axioms: Total momentum (p) is universally conserved. 
 * Total Kinetic Energy (KE) is conserved strictly in elastic state modes.
 */
public class CollisionsMomentumPanel extends JPanel {

    // ── REFRESH & DIMENSIONAL CONSTANTS ─────────────────────────────────────
    private static final int    TICK_MS = 16;   // Animation update frequency (approx. 60Hz loop cycle)
    private static final double SCALE   = 40.0;  // Spatial tracking translation scalar: 40 pixels = 1 m/s velocity displacement
    private static final int    GROUND  = 260;  // Pixel Y-coordinate baseline mapping the floor path line

    // ── CONFIGURABLE INITIAL CONFIGURATIONS ──────────────────────────────────
    private double m1 = 2.0, m2 = 3.0;          // Masses assigned to Box 1 and Box 2 in kilograms (kg)
    private double v1i = 4.0, v2i = -2.0;       // Initial velocity settings in meters per second (m/s)
    private boolean elastic = true;             // Flag checking for elastic behavior vs perfectly inelastic binding

    // ── LIVE RUNTIME STATE VARIABLES ──────────────────────────────────────────
    private double x1, x2;                      // Instantaneous horizontal edge pixel coordinates on canvas
    private double vel1, vel2;                  // Current live velocity tracking registers (m/s)
    private boolean collided = false;           // State register blocking continuous collision triggers
    private boolean running  = false;           // Tracks active thread animation execution states

    // ── REALTIME TELEMETRY DATA REGISTERS ────────────────────────────────────
    private double p1, p2;                      // Object momentum states (kg·m/s)

    // ── BACKGROUND TIMELINE LOOP & CANVAS COMPONENT HANDLES ──────────────────
    private Timer animTimer;
    private SimCanvas canvas;

    // ── INTERACTIVE SWING CONTROL HOOKS ──────────────────────────────────────
    private JSlider m1Slider, m2Slider, v1Slider, v2Slider;
    private JTextField m1Field, m2Field, v1Field, v2Field;
    private JLabel m1Label, m2Label, v1Label, v2Label;
    private JLabel p1Label, p2Label, ptotalLabel, keLabel;
    private JButton startBtn;
    private JComboBox<String> typeBox;

    /**
     * Panel Initialization. Handles child component staging, layout construction, 
     * border padding, and core event registration routines.
     */
    public CollisionsMomentumPanel(MainFrame frame) {
        // Apply BorderLayout with an 8-pixel uniform padding gap between active frame regions
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── NORTH SECTION: Module Title Display Banner ───────────────────────
        JLabel title = new JLabel("Collisions & Momentum (Grade 12)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        add(title, BorderLayout.NORTH);

        // ── CENTER SECTION: Vector Kinematics Canvas Overlay ─────────────────
        canvas = new SimCanvas();
        canvas.setPreferredSize(new Dimension(560, 300));
        canvas.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        add(canvas, BorderLayout.CENTER);

        // ── EAST SECTION: Sidebar Simulation Controller Column ────────────────
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        controls.setPreferredSize(new Dimension(190, 0));

        // ── PROPERTY CONTROLLER: COLLISION TYPE SELECTION BOX ────────────────
        typeBox = new JComboBox<>(new String[]{"Elastic", "Perfectly Inelastic"});
        typeBox.addActionListener(e -> {
            elastic = typeBox.getSelectedIndex() == 0;
            if (!running) reset(); // Instantly apply physics configurations to calculations pipeline
        });
        typeBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        typeBox.setAlignmentX(LEFT_ALIGNMENT);

        // ── PROPERTY CONTROLLER: MASS ONE CONTROL CONTEXT (m1) ────────────────
        m1Label  = new JLabel("Mass 1: 2 kg");
        m1Slider = new JSlider(1, 10, 2); // Boundaries restricted inside bounds: [1kg to 10kg]
        m1Field  = new JTextField("2", 4);
        m1Slider.addChangeListener(e -> {
            m1 = m1Slider.getValue();
            m1Label.setText("Mass 1: " + (int) m1 + " kg");
            m1Field.setText(String.valueOf((int) m1));
            if (!running) reset();
        });
        m1Field.addActionListener(e -> {
            try {
                int val = (int) Math.max(1, Math.min(10, Double.parseDouble(m1Field.getText().trim())));
                m1Slider.setValue(val);
            } catch (NumberFormatException ignored) {
                m1Field.setText(String.valueOf((int) m1));
            }
        });

        // ── PROPERTY CONTROLLER: MASS TWO CONTROL CONTEXT (m2) ────────────────
        m2Label  = new JLabel("Mass 2: 3 kg");
        m2Slider = new JSlider(1, 10, 3);
        m2Field  = new JTextField("3", 4);
        m2Slider.addChangeListener(e -> {
            m2 = m2Slider.getValue();
            m2Label.setText("Mass 2: " + (int) m2 + " kg");
            m2Field.setText(String.valueOf((int) m2));
            if (!running) reset();
        });
        m2Field.addActionListener(e -> {
            try {
                int val = (int) Math.max(1, Math.min(10, Double.parseDouble(m2Field.getText().trim())));
                m2Slider.setValue(val);
            } catch (NumberFormatException ignored) {
                m2Field.setText(String.valueOf((int) m2));
            }
        });

        // ── PROPERTY CONTROLLER: INITIAL VELOCITY ONE REGISTRY (v1) ──────────
        v1Label  = new JLabel("v1: 4 m/s →");
        v1Slider = new JSlider(-8, 8, 4); // Velocity domain boundaries cap out at [-8 m/s to +8 m/s]
        v1Field  = new JTextField("4", 4);
        v1Slider.addChangeListener(e -> {
            v1i = v1Slider.getValue();
            v1Label.setText("v1: " + (int) v1i + " m/s " + dirArrow(v1i));
            v1Field.setText(String.valueOf((int) v1i));
            if (!running) reset();
        });
        v1Field.addActionListener(e -> {
            try {
                int val = (int) Math.max(-8, Math.min(8, Double.parseDouble(v1Field.getText().trim())));
                v1Slider.setValue(val);
            } catch (NumberFormatException ignored) {
                v1Field.setText(String.valueOf((int) v1i));
            }
        });

        // ── PROPERTY CONTROLLER: INITIAL VELOCITY TWO REGISTRY (v2) ──────────
        v2Label  = new JLabel("v2: -2 m/s ←");
        v2Slider = new JSlider(-8, 8, -2);
        v2Field  = new JTextField("-2", 4);
        v2Slider.addChangeListener(e -> {
            v2i = v2Slider.getValue();
            v2Label.setText("v2: " + (int) v2i + " m/s " + dirArrow(v2i));
            v2Field.setText(String.valueOf((int) v2i));
            if (!running) reset();
        });
        v2Field.addActionListener(e -> {
            try {
                int val = (int) Math.max(-8, Math.min(8, Double.parseDouble(v2Field.getText().trim())));
                v2Slider.setValue(val);
            } catch (NumberFormatException ignored) {
                v2Field.setText(String.valueOf((int) v2i));
            }
        });

        // Instantiate live instrumentation telemetry metrics labels
        p1Label     = new JLabel("p1:      0.00 kg·m/s");
        p2Label     = new JLabel("p2:      0.00 kg·m/s");
        ptotalLabel = new JLabel("p total: 0.00 kg·m/s");
        keLabel     = new JLabel("KE:      0.00 J");

        startBtn = new JButton("Start");
        startBtn.addActionListener(e -> toggleSim());
        JButton resetBtn = new JButton("Reset");
        resetBtn.addActionListener(e -> reset());

        // Construct sidebar column interface assembly top-to-bottom
        controls.add(new JLabel("Collision type:"));
        controls.add(typeBox);
        controls.add(Box.createVerticalStrut(8));
        controls.add(m1Label);      controls.add(m1Slider);     controls.add(labeledField("Enter m1:", m1Field));
        controls.add(Box.createVerticalStrut(4));
        controls.add(m2Label);      controls.add(m2Slider);     controls.add(labeledField("Enter m2:", m2Field));
        controls.add(Box.createVerticalStrut(4));
        controls.add(v1Label);      controls.add(v1Slider);     controls.add(labeledField("Enter v1:", v1Field));
        controls.add(Box.createVerticalStrut(4));
        controls.add(v2Label);      controls.add(v2Slider);     controls.add(labeledField("Enter v2:", v2Field));
        controls.add(Box.createVerticalStrut(10));
        controls.add(p1Label);      controls.add(Box.createVerticalStrut(2));
        controls.add(p2Label);      controls.add(Box.createVerticalStrut(2));
        controls.add(ptotalLabel);  controls.add(Box.createVerticalStrut(2));
        controls.add(keLabel);
        controls.add(Box.createVerticalStrut(10));
        controls.add(startBtn);     controls.add(Box.createVerticalStrut(4));
        controls.add(resetBtn);
        
        add(controls, BorderLayout.EAST);

        // ── SOUTH SECTION: Core Hub Frame Navigation Footer Panel ─────────────────
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton back = new JButton("Back");
        back.addActionListener(e -> { 
            animTimer.stop(); // Safe timer teardown preventing background memory leaks
            frame.showConceptSelection("Grade 12"); 
        });
        south.add(back);
        add(south, BorderLayout.SOUTH);

        animTimer = new Timer(TICK_MS, e -> tick());
        reset(); // Force structural initializations on launch parameters
    }

    /**
     * UI component assembly layout row builder. Packs a descriptive parameter string
     * context label right next to its corresponding data entry text field container.
     */
    private JPanel labeledField(String label, JTextField field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        row.setOpaque(false);
        row.add(new JLabel(label));
        row.add(field);
        return row;
    }

    /**
     * Converts a velocity value into a quick string indicator for labels.
     */
    private String dirArrow(double v) {
        if (v > 0) return "→";
        if (v < 0) return "←";
        return "•";
    }

    /**
     * Terminates loops, structural positions are dropped back to start configurations, 
     * flushes state variables, and repaints calculations data clean.
     */
    private void reset() {
        animTimer.stop();
        running  = false;
        collided = false;
        vel1 = v1i;
        vel2 = v2i;
        
        // Horizontal distribution layout balancing: Box 1 left side, Box 2 right side
        x1 = 80;
        x2 = 380;
        
        startBtn.setText("Start");
        updateReadouts();
        canvas.repaint();
    }

    /**
     * Coordinates background running thread timelines with the active multi-state UI button tags.
     */
    private void toggleSim() {
        if (!running) {
            running = true;
            startBtn.setText("Pause");
            animTimer.start();
        } else if (animTimer.isRunning()) {
            animTimer.stop();
            startBtn.setText("Resume");
        } else {
            animTimer.start();
            startBtn.setText("Pause");
        }
    }

    /**
     * CORE CALCULATION LOOP TICKER ENGINE (Executes systematically every 16ms)
     * Handles position translation step shifts, boundary tracking, and elastic/inelastic collisions.
     */
    private void tick() {
        double dt   = TICK_MS / 1000.0; // Normalizes tracking clock index segments into real seconds
        int    box1W = boxWidth(m1);
        int    box2W = boxWidth(m2);

        // Displace position states based on current velocity vectors (scaled from physical meters to pixels)
        x1 += vel1 * SCALE * dt;
        x2 += vel2 * SCALE * dt;

        // ── CRITICAL INTERSECTION EDGE DETECTION FILTER ───────────────────────
        // Trigger condition: Right baseline edge of Box 1 intersects or passes the Left boundary line of Box 2
        if (!collided && (x1 + box1W >= x2)) {
            collided = true;
            
            // Forces mass positions to snap exactly flush together to stop box overlapping errors at high speeds
            double overlap = (x1 + box1W) - x2;
            x1 -= overlap / 2.0;
            x2 += overlap / 2.0;

            if (elastic) {
                // Apply 1D Elastic Collision Momentum Calculus models
                double nv1 = ((m1 - m2) * vel1 + 2 * m2 * vel2) / (m1 + m2);
                double nv2 = ((m2 - m1) * vel2 + 2 * m1 * vel1) / (m1 + m2);
                vel1 = nv1;
                vel2 = nv2;
            } else {
                // Apply Perfectly Inelastic Collision Momentum Calculus models (Objects lock together)
                double vf = (m1 * vel1 + m2 * vel2) / (m1 + m2);
                vel1 = vf;
                vel2 = vf;
            }
        }

        updateReadouts();
        canvas.repaint();
    }

    /**
     * Solves linear tracking systems and formats calculated variables into user labels.
     */
    private void updateReadouts() {
        p1 = m1 * vel1;
        p2 = m2 * vel2;
        // Mechanical system kinetic expression computation: KE = 0.5 * m * v²
        double ke = (0.5 * m1 * vel1 * vel1) + (0.5 * m2 * vel2 * vel2);
        
        p1Label    .setText(String.format("p1:      %.2f kg·m/s", p1));
        p2Label    .setText(String.format("p2:      %.2f kg·m/s", p2));
        ptotalLabel.setText(String.format("p total: %.2f kg·m/s", p1 + p2));
        keLabel    .setText(String.format("KE:      %.2f J",       ke));
    }

    /**
     * Custom sizing ratio formula. Scales box visual footprints dynamically on the canvas
     * relative to their mass parameters so users can visually pick up on weight disparities.
     */
    private int boxWidth(double mass) { 
        return 20 + (int)(mass * 8); 
    }

    // ── GRAPHICS ENGINE COORDINATE LAYER CONTAINER ───────────────────────────

    private class SimCanvas extends JPanel {

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g); // Flush display buffer memory frames
            
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int W       = getWidth();
            int groundY = GROUND;

            // ── RENDER FLOOR PLAN TRACK ──────────────────────────────────────
            g2.setColor(Color.DARK_GRAY);
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(0, groundY, W, groundY);

            // Compute structural rendering geometries
            int b1W  = boxWidth(m1);
            int b2W  = boxWidth(m2);
            int boxH = 40; // Fixed physical mass object pixel height baseline
            int boxY = groundY - boxH;

            int bx1 = (int) x1;
            int bx2 = (int) x2;

            // ── RENDER MASS VECTOR OBJECT COMPONENT ONE (Blue Box) ───────────
            g2.setColor(new Color(100, 149, 237)); // Soft Cornflower Blue
            g2.fillRect(bx1, boxY, b1W, boxH);
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRect(bx1, boxY, b1W, boxH);
            
            // Draw Mass labels aligned cleanly in the box center
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2.setColor(Color.WHITE);
            drawCenteredString(g2, (int)m1 + "kg", bx1, boxY, b1W, boxH);

            // ── RENDER MASS VECTOR OBJECT COMPONENT TWO (Orange Box) ─────────
            g2.setColor(new Color(230, 140, 40)); // Safety Orange
            g2.fillRect(bx2, boxY, b2W, boxH);
            g2.setColor(Color.BLACK);
            g2.drawRect(bx2, boxY, b2W, boxH);
            g2.setColor(Color.WHITE);
            drawCenteredString(g2, (int)m2 + "kg", bx2, boxY, b2W, boxH);

            // ── DYNAMIC DIRECTIONAL VECTOR ARROW GENERATION LAYERS ────────────
            drawVelArrow(g2, bx1, b1W, boxY, boxH, vel1, new Color(100, 149, 237));
            
            // In an inelastic collision, the boxes fuse together. 
            // Only render one shared combined velocity arrow from Box 1 to avoid arrow drawing artifacts.
            if (!collided || elastic) {
                drawVelArrow(g2, bx2, b2W, boxY, boxH, vel2, new Color(230, 140, 40));
            }

            // ── VISUAL IMPACT DECORATION ACCENT: COLLISION FLASH ─────────────
            if (collided && (x2 - (x1 + b1W) <= 5)) {
                g2.setColor(new Color(255, 80, 80, 140)); // Translucent Crimson impact highlight circle overlay
                int flashX = (bx1 + b1W + bx2) / 2;
                g2.fillOval(flashX - 15, boxY - 10, 30, 30);
            }

            // ── SUB-TEXT VALUE ATTACHMENTS: LIVE SPEED READOUTS UNDER CARTS ──
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(new Color(100, 149, 237));
            g2.drawString(String.format("v=%.1f m/s", vel1), bx1, groundY + 14);
            g2.setColor(new Color(200, 110, 20));
            g2.drawString(String.format("v=%.1f m/s", vel2), bx2, groundY + 14);
        }

        /**
         * Aligns bounding box metrics to center string text perfectly inside 
         * canvas objects using baseline geometry calculations.
         */
        private void drawCenteredString(Graphics2D g2, String s, int x, int y, int w, int h) {
            FontMetrics fm = g2.getFontMetrics();
            int cx = x + (w - fm.stringWidth(s)) / 2;
            int cy = y + (h / 2) + (fm.getAscent() / 2) - 2;
            g2.drawString(s, cx, cy);
        }

        /**
         * Draws dynamic velocity vectors pointing out from the side of the box carts.
         * Arrow length scales dynamically with velocity magnitude.
         */
        private void drawVelArrow(Graphics2D g2, int bx, int bw, int by, int bh, double vel, Color color) {
            if (Math.abs(vel) < 0.05) return; // Ignore drawing tiny noise velocity arrows if the cart is at rest
            
            int cy   = by + bh / 2;
            int len  = Math.min((int)(Math.abs(vel) * 12), 80); // Scales length cleanly; caps arrow length at an 80-pixel limit
            int startX, endX;
            
            if (vel > 0) {
                startX = bx + bw; // Projects outward from right-hand edge wall
                endX   = startX + len;
            } else {
                startX = bx;      // Projects outward from left-hand edge wall
                endX   = startX - len;
            }
            
            g2.setColor(color.darker());
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(startX, cy, endX, cy); // Draws main shaft line
            
            // Formulates back-tracing arrowhead wings
            double angle = Math.atan2(0, endX - startX);
            g2.drawLine(endX, cy, endX - (int)(10 * Math.cos(angle - 0.4)), cy - (int)(10 * Math.sin(angle - 0.4)));
            g2.drawLine(endX, cy, endX - (int)(10 * Math.cos(angle + 0.4)), cy - (int)(10 * Math.sin(angle + 0.4)));
        }
    }
}
