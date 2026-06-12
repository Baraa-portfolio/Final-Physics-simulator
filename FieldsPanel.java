import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;

/**
 * FieldsPanel models a 2D particle acceleration chamber simulating kinematics 
 * within uniform Electric (E) or Magnetic (B) fields.
 *
 * Physics Mechanics (Electric Field):
 * F = q * E  (Vertical uniform deflection vector)
 * a = F / m  (Produces a classic parabolic trajectory path)
 *
 * Physics Mechanics (Magnetic Field):
 * F = q * (v × B)  (Lorentz Force: cross product is orthogonal to velocity)
 * a = F / m        (Produces uniform circular motion, where centripetal force yields: r = m*v / (|q|*B))
 */
public class FieldsPanel extends JPanel {

    // ── REFRESH & DIMENSIONAL CONSTANTS ─────────────────────────────────────
    private static final int    TICK_MS = 16;   // Animation update frequency (~60Hz execution rate)
    private static final double SCALE   = 60.0;  // Spatial conversion scalar: 60 pixels = 1.0 physical world meter

    // ── CONFIGURABLE INITIAL PARAMETERS ──────────────────────────────────────
    private double charge     = 1.0;   // Coulomb (C) parameter scales vector magnitudes and switches polarity signs
    private double mass       = 1.0;   // Mass (kg) provides physical inertial resistance to field acceleration
    private double fieldStr   = 2.0;   // Field strength metric representation: Electric E (N/C) or Magnetic B (T)
    private double initSpeed  = 3.0;   // Launch speed entering the horizontal center axis bounds (m/s)
    private boolean electricMode = true; // Selector driving active calculation state: true = E-field, false = B-field

    // ── LIVE RUNTIME STATE VARIABLES ──────────────────────────────────────────
    private double px, py;              // Physical cartesian position coordinates tracked from the canvas origin center
    private double vx, vy;              // Instantaneous directional velocity vectors (m/s)
    private boolean running = false;    // Safety gate tracking system thread timeline state flows

    // ── BACKGROUND TIMELINE LOOP & CANVAS COMPONENT HANDLES ──────────────────
    private Timer animTimer;
    private SimCanvas canvas;

    // ── INTERACTIVE SWING CONTROL HOOKS ──────────────────────────────────────
    private JSlider chargeSlider, massSlider, fieldSlider, speedSlider;
    private JTextField chargeField, massField, fieldField, speedField;
    private JLabel chargeLabel, massLabel, fieldLabel, speedLabel;
    private JLabel forceLabel, radiusLabel, accelLabel;
    private JButton startBtn;
    private JComboBox<String> modeBox;

    /**
     * Main UI Assembly Panel Constructor. Maps child panels, borders, action events, 
     * and initializes physics data loops.
     */
    public FieldsPanel(MainFrame frame) {
        // Enforce BorderLayout with uniform spacing gaps between tracking layout panels
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── NORTH SECTION: Module Title Display Banner ───────────────────────
        JLabel title = new JLabel("Magnetic / Electric Fields (Grade 12)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        add(title, BorderLayout.NORTH);

        // ── CENTER SECTION: Vector Deflection Particle Canvas Layer ──────────
        canvas = new SimCanvas();
        canvas.setPreferredSize(new Dimension(540, 400));
        canvas.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        add(canvas, BorderLayout.CENTER);

        // ── EAST SECTION: Sidebar Simulation Controller Column ────────────────
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        controls.setPreferredSize(new Dimension(190, 0));

        // ── PROPERTY CONTROLLER: ACTIVE ACCELERATION FIELD MODE SELECTOR ─────
        modeBox = new JComboBox<>(new String[]{"Electric Field (E)", "Magnetic Field (B)"});
        modeBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        modeBox.setAlignmentX(LEFT_ALIGNMENT);
        modeBox.addActionListener(e -> {
            electricMode = modeBox.getSelectedIndex() == 0;
            fieldLabel.setText(electricMode ? "E field: " + (int)fieldStr + " N/C" : "B field: " + (int)fieldStr + " T");
            if (!running) reset(); // Reset coordinate paths instantly to reflect updated field configurations
        });

        // ── PROPERTY CONTROLLER: PARTICLE ELECTRIC CHARGE CONTEXT (q) ────────
        chargeLabel  = new JLabel("Charge: +1 C");
        chargeSlider = new JSlider(-5, 5, 1); // Selectable data range constrained between bounds: [-5C to +5C]
        chargeField  = new JTextField("1", 4);
        chargeSlider.addChangeListener(e -> {
            charge = chargeSlider.getValue();
            chargeLabel.setText("Charge: " + (charge >= 0 ? "+" : "") + (int)charge + " C");
            chargeField.setText(String.valueOf((int) charge));
            if (!running) reset();
        });
        chargeField.addActionListener(e -> {
            try {
                int val = (int) Math.max(-5, Math.min(5, Double.parseDouble(chargeField.getText().trim())));
                chargeSlider.setValue(val);
            } catch (NumberFormatException ignored) {
                chargeField.setText(String.valueOf((int) charge));
            }
        });

        // ── PROPERTY CONTROLLER: INERTIAL MASS METRIC CONTEXT (m) ───────────
        massLabel  = new JLabel("Mass: 1 kg");
        massSlider = new JSlider(1, 10, 1);  // Enforces positive bounds constraints: [1kg to 10kg]
        massField  = new JTextField("1", 4);
        massSlider.addChangeListener(e -> {
            mass = massSlider.getValue();
            massLabel.setText("Mass: " + (int) mass + " kg");
            massField.setText(String.valueOf((int) mass));
            if (!running) reset();
        });
        massField.addActionListener(e -> {
            try {
                int val = (int) Math.max(1, Math.min(10, Double.parseDouble(massField.getText().trim())));
                massSlider.setValue(val);
            } catch (NumberFormatException ignored) {
                massField.setText(String.valueOf((int) mass));
            }
        });

        // ── PROPERTY CONTROLLER: GRADUATED FIELD INTENSITY COEFFICIENT (E/B) ─
        fieldLabel  = new JLabel("E field: 2 N/C");
        fieldSlider = new JSlider(1, 10, 2);
        fieldField  = new JTextField("2", 4);
        fieldSlider.addChangeListener(e -> {
            fieldStr = fieldSlider.getValue();
            fieldLabel.setText((electricMode ? "E field: " : "B field: ") + (int)fieldStr + (electricMode ? " N/C" : " T"));
            fieldField.setText(String.valueOf((int) fieldStr));
            if (!running) reset();
        });
        fieldField.addActionListener(e -> {
            try {
                int val = (int) Math.max(1, Math.min(10, Double.parseDouble(fieldField.getText().trim())));
                fieldSlider.setValue(val);
            } catch (NumberFormatException ignored) {
                fieldField.setText(String.valueOf((int) fieldStr));
            }
        });

        // ── PROPERTY CONTROLLER: ORIENTATION INJECTION ACCELERATION (v0) ─────
        speedLabel  = new JLabel("Init Speed: 3 m/s");
        speedSlider = new JSlider(1, 10, 3);
        speedField  = new JTextField("3", 4);
        speedSlider.addChangeListener(e -> {
            initSpeed = speedSlider.getValue();
            speedLabel.setText("Init Speed: " + (int) initSpeed + " m/s");
            speedField.setText(String.valueOf((int) initSpeed));
            if (!running) reset();
        });
        speedField.addActionListener(e -> {
            try {
                int val = (int) Math.max(1, Math.min(10, Double.parseDouble(speedField.getText().trim())));
                speedSlider.setValue(val);
            } catch (NumberFormatException ignored) {
                speedField.setText(String.valueOf((int) initSpeed));
            }
        });

        // Instantiate live telemetry data registers
        forceLabel  = new JLabel("Force:  0.00 N");
        accelLabel  = new JLabel("Accel:  0.00 m/s²");
        radiusLabel = new JLabel("Radius: - m");

        startBtn = new JButton("Start");
        startBtn.addActionListener(e -> toggleSim());
        JButton resetBtn = new JButton("Reset");
        resetBtn.addActionListener(e -> reset());

        // Assemble control widgets onto side column structure layout sequentially
        controls.add(new JLabel("Field type:"));
        controls.add(modeBox);
        controls.add(Box.createVerticalStrut(8));
        controls.add(chargeLabel);   controls.add(chargeSlider);  controls.add(labeledField("Enter charge:", chargeField));
        controls.add(Box.createVerticalStrut(4));
        controls.add(massLabel);     controls.add(massSlider);    controls.add(labeledField("Enter mass:", massField));
        controls.add(Box.createVerticalStrut(4));
        controls.add(fieldLabel);    controls.add(fieldSlider);   controls.add(labeledField("Enter field:", fieldField));
        controls.add(Box.createVerticalStrut(4));
        controls.add(speedLabel);    controls.add(speedSlider);   controls.add(labeledField("Enter speed:", speedField));
        controls.add(Box.createVerticalStrut(10));
        controls.add(forceLabel);    controls.add(Box.createVerticalStrut(3));
        controls.add(accelLabel);    controls.add(Box.createVerticalStrut(3));
        controls.add(radiusLabel);
        controls.add(Box.createVerticalStrut(10));
        controls.add(startBtn);      controls.add(Box.createVerticalStrut(4));
        controls.add(resetBtn);
        
        add(controls, BorderLayout.EAST);

        // ── SOUTH SECTION: Main Hub Navigation Core Hook Banner ───────────────
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton back = new JButton("Back");
        back.addActionListener(e -> { 
            animTimer.stop(); // Stops thread clock loops to block memory leaks or background drifting
            frame.showConceptSelection("Grade 12"); 
        });
        south.add(back);
        add(south, BorderLayout.SOUTH);

        animTimer = new Timer(TICK_MS, e -> tick());
        reset(); // Force core variable calibrations on construction initialization
    }

    /**
     * UI component helper. Groups an identification title string flush next
     * to its target numerical text parameter entry field.
     */
    private JPanel labeledField(String label, JTextField field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        row.setOpaque(false);
        row.add(new JLabel(label));
        row.add(field);
        return row;
    }

    /**
     * Halts standard ticker cycles, restores initial parameters, 
     * flushes out historical display traces, and resets instrumentation panels.
     */
    private void reset() {
        animTimer.stop();
        running = false;
        
        // Horizontal distribution offsets: places node on left margin edge line (-3.0 meters away from center origin)
        px = -3.0; 
        py = 0.0;
        
        // Inject velocity purely onto the horizontal vector plane axis
        vx = initSpeed; 
        vy = 0.0;
        
        startBtn.setText("Start");
        if (canvas != null) canvas.clearTrail();
        updateReadouts();
        if (canvas != null) canvas.repaint();
    }

    /**
     * Bridges manual user click events to target thread rendering clock actions.
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
     * Handles 2D integration steps evaluating active electric acceleration or perpendicular magnetic vector changes.
     */
    private void tick() {
        double dt = TICK_MS / 1000.0; // Converts tick index gaps to real elapsed time fractions
        double ax = 0, ay = 0;

        if (electricMode) {
            // Uniform Electric Field Vector (Pointing vertically upwards along y-axis)
            // Mathematical Expression: F_y = q * E
            double F = charge * fieldStr;
            ay = F / mass; // Newton's Second Law: a = F / m
            ax = 0;        // Pure vertical acceleration
        } else {
            // Uniform Magnetic Field Vector pointing straight into the display panel plane (z = -1)
            // Mathematical Expression (Cross Product Formulation): F = q * (v × B)
            // Given parameters: v = (vx, vy, 0), B = (0, 0, -B)
            // Matrix Expansion yields: v × B = (vy*(-B) - 0, 0 - vx*(-B), 0) = (-vy*B, vx*B, 0)
            double Fx = charge * (-vy * fieldStr);
            double Fy = charge * (vx * fieldStr);
            ax = Fx / mass;
            ay = Fy / mass;
        }

        // Apply classic Euler-Cromer Numerical Integration approximations
        vx += ax * dt;
        vy += ay * dt;
        px += vx * dt;
        py += vy * dt;

        updateReadouts();
        canvas.repaint();
    }

    /**
     * Solves field equations and translates numerical metrics onto side labels.
     */
    private void updateReadouts() {
        double speed = Math.hypot(vx, vy);
        double force, accel;

        if (electricMode) {
            force = Math.abs(charge * fieldStr);
            accel = force / mass;
            forceLabel .setText(String.format("Force:  %.2f N",    force));
            accelLabel .setText(String.format("Accel:  %.2f m/s²", accel));
            radiusLabel.setText("Radius: N/A");
        } else {
            // Lorentz equation resolution for circular trajectories
            force = Math.abs(charge) * speed * fieldStr;
            accel = force / mass;
            // Radius of path expression: r = m*v / (|q|*B)
            double radius = (Math.abs(charge) * fieldStr > 0) ? (mass * speed) / (Math.abs(charge) * fieldStr) : 0;
            
            forceLabel .setText(String.format("Force:  %.2f N",    force));
            accelLabel .setText(String.format("Accel:  %.2f m/s²", accel));
            radiusLabel.setText(Math.abs(charge) * fieldStr > 0 ? String.format("Radius: %.2f m", radius) : "Radius: ∞");
        }
    }

    // ── GRAPHICS ENGINE COORDINATE LAYER CONTAINER ───────────────────────────

    private class SimCanvas extends JPanel {

        // Array queue collecting historical step coordinate points for path trailing
        private final java.util.List<Point2D.Double> trail = new java.util.ArrayList<>();
        private double lastPx = Double.NaN;

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g); // Flush previous animation frames out of memory buffer
            
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int W  = getWidth();
            int H  = getHeight();
            int cx = W / 2; // Midpoint translation coordinates defining 0,0 world origin
            int cy = H / 2;

            // Append live coordinates onto trailing log files while simulation loops run
            if (running && (Double.isNaN(lastPx) || lastPx != px)) {
                trail.add(new Point2D.Double(px, py));
                lastPx = px;
                if (trail.size() > 1500) trail.remove(0); // Cap footprint trace sizes to protect system RAM bounds
            }

            // ── RENDER ACCELERATION FIELD FIELD BACKGROUND MATRIX ────────────
            if (electricMode) {
                // Generate uniform horizontal dashed vector grid indicating upward acceleration vectors
                g2.setColor(new Color(200, 220, 255));
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4, 8}, 0));
                for (int y = 20; y < H; y += 40) {
                    g2.drawLine(0, y, W, y);
                    // Overlay micro structural directional arrows pointing vertically up
                    for (int x = 30; x < W; x += 60) {
                        g2.setStroke(new BasicStroke(1f));
                        g2.setColor(new Color(150, 180, 230));
                        g2.drawLine(x, y + 6, x, y - 6);
                        g2.drawLine(x, y - 6, x - 3, y - 2);
                        g2.drawLine(x, y - 6, x + 3, y - 2);
                    }
                }
                g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g2.setColor(new Color(100, 130, 200));
                g2.drawString("E field ↑  (" + (int)fieldStr + " N/C)", 6, H - 6);
            } else {
                // Generate cross-product grid indices marking vector forces going straight into display plane
                g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
                g2.setColor(new Color(180, 215, 180));
                for (int y = 25; y < H; y += 40) {
                    for (int x = 20; x < W; x += 40) {
                        g2.drawString("×", x - 4, y + 4); // Traditional 'X' vector layout signifying vector inward flow
                    }
                }
                g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g2.setColor(new Color(80, 150, 80));
                g2.drawString("B field × into screen (" + (int)fieldStr + " T)", 6, H - 6);
            }

            // ── RENDER GRAPH ORIGIN BOUNDARY CORNER MARKS ────────────────────
            g2.setColor(new Color(225, 225, 225));
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(cx, 0, cx, H);
            g2.drawLine(0, cy, W, cy);

            // ── RENDER TRAVEL PATH TRACE FOOTPRINTS ──────────────────────────
            g2.setColor(new Color(255, 90, 90, 180)); // Semi-transparent crimson trace string line
            g2.setStroke(new BasicStroke(2.0f));
            for (int i = 1; i < trail.size(); i++) {
                Point2D.Double a = trail.get(i - 1), b = trail.get(i);
                int ax2 = cx + (int)(a.x * SCALE);
                int ay2 = cy - (int)(a.y * SCALE); // Flips standard coordinate layout sign to reflect true cartesian upward bounds
                int bx2 = cx + (int)(b.x * SCALE);
                int by2 = cy - (int)(b.y * SCALE);
                g2.drawLine(ax2, ay2, bx2, by2);
            }

            // ── RENDER CHARGED PARTICLE SPHERE ASSEMBLY ──────────────────────
            int screenX = cx + (int)(px * SCALE);
            int screenY = cy - (int)(py * SCALE);
            int radius  = 9;

            // Modulate color profile based on target polarity metrics: Positive = Deep Red, Negative = Cobalt Blue
            g2.setColor(charge >= 0 ? new Color(220, 60, 60) : new Color(55, 95, 215));
            g2.fillOval(screenX - radius, screenY - radius, radius * 2, radius * 2);
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(screenX - radius, screenY - radius, radius * 2, radius * 2);

            // Stamp symbol polarity text character over target center mass
            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            g2.setColor(Color.WHITE);
            String signSymbol = (charge > 0) ? "+" : (charge < 0 ? "−" : "0");
            int textOffset = (charge < 0) ? 3 : 4; // Visual alignment adjustments for the minus glyph character
            g2.drawString(signSymbol, screenX - textOffset, screenY + 4);

            // ── RENDER DYNAMIC SPEED ARROW VECTOR ATTACHMENTS ───────────────
            double totalVelocityMagnitude = Math.hypot(vx, vy);
            if (totalVelocityMagnitude > 0.01) {
                int arrowFixedPixelLength = 35;
                int vex = screenX + (int)(vx / totalVelocityMagnitude * arrowFixedPixelLength);
                int vey = screenY - (int)(vy / totalVelocityMagnitude * arrowFixedPixelLength);
                
                g2.setColor(Color.ORANGE);
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(screenX, screenY, vex, vey); // Base connector stem line
                
                // Angle back-trace calculations establishing trailing wing points
                double arrowHeadAngle = Math.atan2(vey - screenY, vex - screenX);
                g2.drawLine(vex, vey, vex - (int)(8 * Math.cos(arrowHeadAngle - 0.4)), vey - (int)(8 * Math.sin(arrowHeadAngle - 0.4)));
                g2.drawLine(vex, vey, vex - (int)(8 * Math.cos(arrowHeadAngle + 0.4)), vey - (int)(8 * Math.sin(arrowHeadAngle + 0.4)));
            }

            // ── RENDER VISUAL INDEX LEGEND TOP CORNER BOX ────────────────────
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(new Color(220, 60, 60));  g2.drawString("● Positive Charge (+q)", W - 135, 18);
            g2.setColor(new Color(55, 95, 215));  g2.drawString("● Negative Charge (−q)", W - 135, 32);
            g2.setColor(Color.ORANGE);             g2.drawString("→ Velocity Vector (v)", W - 135, 46);
        }

        /**
         * Clears all trace coordinates out of memory files upon manual user reset clicks.
         */
        void clearTrail() { 
            trail.clear(); 
            lastPx = Double.NaN; 
        }
    }
}
