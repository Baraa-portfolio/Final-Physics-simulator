import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;

/**
 * CircularMotionPanel models a 2D interactive simulator exploring uniform circular motion.
 * Extends JPanel to integrate seamlessly into a parent card-based frame configuration context.
 * * Core Physics Logic:
 * ω = v / r                 (Angular velocity tracks radians traveled per second)
 * a_c = v² / r              (Centripetal acceleration points orthogonally toward the rotation axis)
 * F_c = m * a_c             (Centripetal force pulls the mass toward the orbital node center)
 * T = 2π * r / v            (Period tracks duration in seconds required to complete one full revolution)
 */
public class CircularMotionPanel extends JPanel {

    // ── REFRESH CYCLE CONSTANT ──────────────────────────────────────────────
    // Refresh cycle delay interval (16ms ≈ 60 frames per second display update frequency).
    private static final int TICK_MS = 16;

    // ── CONFIGURABLE PHYSICAL INPUT FIELDS ────────────────────────────────────
    // NOTE: For simple spatial conversion engineering, a 1-to-1 mapping scale is used: 1 pixel = 1 meter.
    private double radius = 100.0;  // Orbital tracking path radius length (meters/pixels).
    private double speed  = 80.0;   // Instantaneous tangential linear velocity magnitude (meters per second).
    private double mass   = 2.0;    // System object mass in kilograms (kg). Scales absolute centripetal force parameters.

    // ── SYSTEM SYSTEM STATE FIELD VARIABLES ───────────────────────────────────
    private double angle  = 0.0;    // Instantaneous angular positioning parameter measured along the arc path (radians).
    private boolean running = false; // Internal execution state verification flag checking if animation loops are active.

    // ── GRAPHICS TIMELINE & CONTAINER COMPONENT LEGS ─────────────────────────
    private Timer animTimer;        // UI background update thread engine. Fires standard ActionEvents sequentially.
    private SimCanvas canvas;       // Custom graphics coordinate plane tracking window override layer.

    // ── SWING OBJECT INPUT HANDLES ───────────────────────────────────────────
    private JSlider radiusSlider, speedSlider, massSlider;      // Physical state modulation sliders
    private JTextField radiusField, speedField, massField;    // Numerical alternative text inputs
    private JLabel radiusLabel, speedLabel, massLabel;          // Structural label fields mapping parameter values
    private JLabel accelLabel, forceLabel, periodLabel, omegaLabel; // Readout instrumentation telemetry modules
    private JButton startBtn;                                   // Primary multi-state loop execution switch

    /**
     * Component Constructor. Handles layout rule implementation, layout spacing padding rules,
     * container stacking, and event listener registration routines.
     */
    public CircularMotionPanel(MainFrame frame) {
        // Enforces BorderLayout. (8,8) establishes explicit padding between layout regions.
        setLayout(new BorderLayout(8, 8));
        // Generates an external 10-pixel transparent cushioning margin lining the interior perimeter of the panel.
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── NORTH REGION: Section Header ─────────────────────────────────────
        JLabel title = new JLabel("Circular Motion (Grade 12)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f)); // Forces 16pt scaling and BOLD decoration.
        add(title, BorderLayout.NORTH);

        // ── CENTER REGION: 2D Graphics Canvas Coordinate Space ────────────────
        canvas = new SimCanvas();
        canvas.setPreferredSize(new Dimension(560, 400));
        canvas.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        add(canvas, BorderLayout.CENTER);

        // ── EAST REGION: Dedicated User Controls Sidebar Column Layout ─────────
        JPanel controls = new JPanel();
        // BoxLayout.Y_AXIS stacks elements rigidly top-to-bottom in a single vertical column block.
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0)); // Prevents sidebar bounding issues against the canvas wall.
        controls.setPreferredSize(new Dimension(190, 0)); // Hardcaps absolute column thickness spacing parameters at 190 pixels width.

        // ── CONTROL PIPELINE MODULE: RADIUS VARIATION (meters) ────────────────
        radiusLabel  = new JLabel("Radius: 100 m");
        radiusSlider = new JSlider(20, 180, 100); // Valid path mapping scope parameters restricted between [20m to 180m].
        radiusField  = new JTextField("100", 5);
        
        radiusSlider.addChangeListener(e -> {
            radius = radiusSlider.getValue(); // Pulls slider integer to synchronize internal physics variable states.
            radiusLabel.setText("Radius: " + (int) radius + " m");
            radiusField.setText(String.valueOf((int) radius));
            updateReadouts(); // Instantaneously updates calculus predictions down the telemetry pipeline.
            canvas.repaint();  // Requests vector line redraw tracking adjustments while paused.
        });
        radiusField.addActionListener(e -> {
            try {
                // Extracts input parameters, clamping ranges safely between [20 to 180].
                int val = (int) Math.max(20, Math.min(180, Double.parseDouble(radiusField.getText().trim())));
                radiusSlider.setValue(val);
            } catch (NumberFormatException ignored) {
                radiusField.setText(String.valueOf((int) radius)); // Reverts structural errors back to current field records.
            }
        });

        // ── CONTROL PIPELINE MODULE: TANGENTIAL VELOCITY SPEED (m/s) ───────────
        speedLabel  = new JLabel("Speed: 80 m/s");
        speedSlider = new JSlider(10, 300, 80); // Speed variables path tracking limits span across [10 m/s to 300 m/s].
        speedField  = new JTextField("80", 5);
        
        speedSlider.addChangeListener(e -> {
            speed = speedSlider.getValue();
            speedLabel.setText("Speed: " + (int) speed + " m/s");
            speedField.setText(String.valueOf((int) speed));
            updateReadouts();
        });
        speedField.addActionListener(e -> {
            try {
                int val = (int) Math.max(10, Math.min(300, Double.parseDouble(speedField.getText().trim())));
                speedSlider.setValue(val);
            } catch (NumberFormatException ignored) {
                speedField.setText(String.valueOf((int) speed));
            }
        });

        // ── CONTROL PIPELINE MODULE: MASS PARAMETERS (kg) ────────────────────
        massLabel  = new JLabel("Mass: 2 kg");
        massSlider = new JSlider(1, 20, 2); // Structural mass variations restricted inside [1kg to 20kg].
        massField  = new JTextField("2", 5);
        
        massSlider.addChangeListener(e -> {
            mass = massSlider.getValue();
            massLabel.setText("Mass: " + (int) mass + " kg");
            massField.setText(String.valueOf((int) mass));
            updateReadouts();
        });
        massField.addActionListener(e -> {
            try {
                int val = (int) Math.max(1, Math.min(20, Double.parseDouble(massField.getText().trim())));
                massSlider.setValue(val);
            } catch (NumberFormatException ignored) {
                massField.setText(String.valueOf((int) mass));
            }
        });

        // Instantiate live telemetry data reporting tags. Updates systematically via calculations ticker loop.
        accelLabel  = new JLabel("Accel:  0.00 m/s²");
        forceLabel  = new JLabel("Force:  0.00 N");
        periodLabel = new JLabel("Period: 0.00 s");
        omegaLabel  = new JLabel("ω:      0.00 rad/s");

        startBtn = new JButton("Start");
        startBtn.addActionListener(e -> toggleSim());

        JButton resetBtn = new JButton("Reset");
        resetBtn.addActionListener(e -> reset());

        // Assemble control sidebar layout by dropping items sequentially top-to-bottom.
        controls.add(radiusLabel); controls.add(radiusSlider); controls.add(labeledField("Enter radius:", radiusField));
        controls.add(Box.createVerticalStrut(6)); // Fixed un-scannable vertical gap spacer buffers (6 pixels).
        controls.add(speedLabel);  controls.add(speedSlider);  controls.add(labeledField("Enter speed:", speedField));
        controls.add(Box.createVerticalStrut(6));
        controls.add(massLabel);   controls.add(massSlider);   controls.add(labeledField("Enter mass:", massField));
        controls.add(Box.createVerticalStrut(12));
        controls.add(accelLabel);  controls.add(Box.createVerticalStrut(3));
        controls.add(forceLabel);  controls.add(Box.createVerticalStrut(3));
        controls.add(periodLabel); controls.add(Box.createVerticalStrut(3));
        controls.add(omegaLabel);
        controls.add(Box.createVerticalStrut(12));
        controls.add(startBtn);    controls.add(Box.createVerticalStrut(4));
        controls.add(resetBtn);
        
        add(controls, BorderLayout.EAST); // Locks control panel box setup straight into standard layout right margins.

        // ── SOUTH REGION: Master Navigation Footer Controls ───────────────────
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton back = new JButton("Back");
        back.addActionListener(e -> { 
            animTimer.stop(); // CRITICAL MEMORY FAILSAFE: Always explicitly stop background timers when navigating contexts to stop memory leaks!
            frame.showConceptSelection("Grade 12"); 
        });
        south.add(back);
        add(south, BorderLayout.SOUTH);

        // Map timing loops directly to execute standard calculations updates inside tick() methods.
        animTimer = new Timer(TICK_MS, e -> tick());
        updateReadouts(); // Pre-populate calculation instrumentation telemetry metrics views.
    }

    /**
     * Component layout row design factory utility. Merges text labels 
     * alongside text fields within a shared single-row frame container.
     */
    private JPanel labeledField(String label, JTextField field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        row.setOpaque(false); // Translucent background allows master panel theme properties to show through cleanly.
        row.add(new JLabel(label));
        row.add(field);
        return row;
    }

    /**
     * Manages running states and button text changes for the interactive simulation loop timeline.
     * Transitions structurally across: Not Running -> Running -> Paused -> Running.
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
     * Wipes active background loops, forces angular path trackers back to zero point coordinates, 
     * re-syncs instrumentation telemetry view panels, and requests a canvas update pass.
     */
    private void reset() {
        animTimer.stop();
        running = false;
        angle   = 0; // Moves angular position trackers back to a standard 0.0 radian entry alignment point.
        startBtn.setText("Start");
        updateReadouts();
        canvas.repaint(); // Redraws components to display the ball back at its starting baseline configuration.
    }

    /**
     * CALCULATION CLOCK UPDATE HEARTBEAT (Fires sequentially every 16ms)
     * Handles orbital accumulation and angular boundary stabilization routines.
     */
    private void tick() {
        // Step 1: Compute angular velocity profiles via instant linear variables. ω = v / r
        double omega = speed / radius;
        // Step 2: Formulate fractional tracking clock intervals step indices (16ms = 0.016 seconds).
        double dt    = TICK_MS / 1000.0;
        
        // Step 3: Compute localized arc position shifts. Δθ = ω * dt
        angle += omega * dt;
        
        // Step 4: Circular range boundary normalization wrap loop. 
        // Keeps angle scalars clean and within standard trigonometric boundaries [0 to 2π].
        if (angle > 2 * Math.PI) {
            angle -= 2 * Math.PI;
        }

        updateReadouts(); // Pulls fresh numeric computations down to the telemetry layout engine views.
        canvas.repaint();  // Requests a UI execution cycle sweep pass to paint vectors onto screen pixels.
    }

    /**
     * Evaluates active system properties using vector motion calculus models, 
     * formatting and populating findings straight onto data panel labels.
     */
    private void updateReadouts() {
        double omega  = speed / radius;
        double accel  = (speed * speed) / radius; // Centripetal Acceleration formula: a_c = v² / r
        double force  = mass * accel;             // Centripetal Force expression: F_c = m * v² / r
        double period = (2 * Math.PI * radius) / speed; // Total duration path trace metric: T = 2πr / v
        
        accelLabel .setText(String.format("Accel:  %.2f m/s²", accel));
        forceLabel .setText(String.format("Force:  %.2f N",    force));
        periodLabel.setText(String.format("Period: %.2f s",    period));
        omegaLabel .setText(String.format("ω:      %.2f rad/s", omega));
    }

    // ── GRAPHICS ENGINE DRAW LAYER INTERIOR PANEL ──────────────────────────────

    /**
     * Inner graphics class specialized in managing coordinate vector offsets, 
     * plotting trajectory track rings, and rendering active dynamic vector orientation arrows.
     */
    private class SimCanvas extends JPanel {

        /**
         * System drawing engine handler override pipeline method.
         */
        @Override
        protected void paintComponent(Graphics g) {
            // Mandatory internal execution rule: flush previous frame trails to prevent pixel smearing!
            super.paintComponent(g);
            
            // Cast standard Graphics reference handles over to complex Graphics2D modules 
            // to access line weight stroke variables and text anti-aliasing features.
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int W  = getWidth();
            int H  = getHeight();
            int cx = W / 2; // Establishes center pivot horizontal anchor axis metrics based on canvas dimensions.
            int cy = H / 2; // Establishes center pivot vertical anchor axis metrics.
            int r  = (int) radius;

            // ── DRAW VISUAL TRAJECTORY TRACK RING: DASHED ORBITAL CIRCLING ──────
            g2.setColor(Color.LIGHT_GRAY);
            // Configures paint engine stroke patterns to map custom dashed line profiles [6px line, 4px blank gap].
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                    0, new float[]{6, 4}, 0));
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);

            // ── DRAW FIXED CENTER ORBITAL ORIGIN NODE ───────────────────────────
            g2.setColor(Color.DARK_GRAY);
            g2.fillOval(cx - 4, cy - 4, 8, 8); // Plots tiny 8-pixel master control reference anchor dot.

            // ── COMPUTE VECTOR TRIGONOMETRIC POSITION DISPLACEMENTS ─────────────
            // NOTE: Computer pixel coordinate systems track y-axes downward positively. 
            // To maintain standard polar coordinate mechanics, invert the vertical offset calculation with a subtraction operator.
            int ballX = cx + (int)(r * Math.cos(angle));
            int ballY = cy - (int)(r * Math.sin(angle));  

            // ── DRAW RADIAL REFERENCE LINK LINE ─────────────────────────────────
            g2.setColor(Color.GRAY);
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(cx, cy, ballX, ballY); // Links center structural core straight to orbital mass path center.

            // Radius notation data tracking print label overlay
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(Color.GRAY);
            g2.drawString("r = " + (int) radius + " m", cx + 4, cy - 4);

            // ── DRAW VECTOR COMPONENT ARROW ONE: CENTRIPETAL FORCE (Fc - Red) ───
            // Centripetal vectors pull orthogonally inward along the path radius directly towards the focal core center node.
            double fx = cx - ballX;
            double fy = cy - ballY;
            double fmag = Math.sqrt(fx * fx + fy * fy);
            int arrowLen = 40; // Sets uniform pixel length dimension rules for visualization mapping vectors.
            
            // Evaluates directional vector path offsets to accurately position arrow tip parameters.
            int fax = ballX + (int)(fx / fmag * arrowLen);
            int fay = ballY + (int)(fy / fmag * arrowLen);
            drawArrow(g2, ballX, ballY, fax, fay, Color.RED);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(Color.RED);
            g2.drawString("Fc", fax + 3, fay - 3);

            // ── DRAW VECTOR COMPONENT ARROW TWO: TANGENTIAL VELOCITY (v - Cyan) ──
            // Tangential velocity operates perfectly perpendicular relative to the centripetal orientation vector path.
            // Calculated via derivative transformations of spatial position arrays: x' = -sin(θ), y' = cos(θ).
            double vx = -Math.sin(angle);   
            double vy =  Math.cos(angle);
            int vLen = 50; 
            
            int vax = ballX + (int)(vx * vLen);
            int vay = ballY - (int)(vy * vLen); // Applies the screen transformation vertical reflection inversion filter.
            drawArrow(g2, ballX, ballY, vax, vay, Color.CYAN.darker());
            g2.setColor(Color.CYAN.darker());
            g2.drawString("v", vax + 3, vay - 3);

            // ── DRAW MAIN ORBITING MASS SATELLITE OBJECT SPHERE ─────────────────
            int ballR = 10; // Mass tracking object radius footprint thickness parameters.
            g2.setColor(new Color(100, 149, 237)); // Soft Cornflower Blue internal skin mask layout selection.
            g2.fillOval(ballX - ballR, ballY - ballR, ballR * 2, ballR * 2);
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(ballX - ballR, ballY - ballR, ballR * 2, ballR * 2); // Outlines sharp perimeter mask frame lines.

            // ── DRAW GRAPH KEY LEGEND MAP OVERLAYS ──────────────────────────────
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(Color.RED);          g2.drawString("→ Centripetal Force (Fc)", 8, H - 30);
            g2.setColor(Color.CYAN.darker()); g2.drawString("→ Velocity (v)", 8, H - 16);
        }

        /**
         * Modular geometric utility calculation framework designed to generate 
         * sharp directional arrow indicators utilizing vector angular offsets.
         */
        private void drawArrow(Graphics2D g2, int x1, int y1, int x2, int y2, Color color) {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(x1, y1, x2, y2); // Draws core structural vector path shaft line.
            
            // Computes inverse displacement angles to anchor geometric arrowhead wings accurately.
            double angle = Math.atan2(y2 - y1, x2 - x1);
            // Arrow wing configuration layout transformations tracking backwards from peak coordinates (x2, y2) at a 0.4 radian offset.
            g2.drawLine(x2, y2, x2 - (int)(10 * Math.cos(angle - 0.4)), y2 - (int)(10 * Math.sin(angle - 0.4)));
            g2.drawLine(x2, y2, x2 - (int)(10 * Math.cos(angle + 0.4)), y2 - (int)(10 * Math.sin(angle + 0.4)));
        }
    }
}
