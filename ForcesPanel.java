import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * ForcesPanel represents a single screen inside our simulation software. 
 * By extending JPanel, it acts as a modular graphical container that can be dynamically
 * swapped in and out of the application window (MainFrame).
 * * It couples real-time 1D kinematics simulations with modern reactive UI design.
 */
public class ForcesPanel extends JPanel {

    // ── PHYSICS & RENDERING CONSTANTS ──────────────────────────────────────────
    
    // Theoretical acceleration due to gravity on Earth's surface (m/s²). Used to compute weight and normal forces.
    private static final double GRAVITY = 9.81;
    
    // Pixel conversion multiplier. Every 1 meter of physical world distance equals 40 pixels on your display.
    private static final double SCALE   = 40.0;
    
    // The target hardware refresh rate interval (16 milliseconds ≈ 62.5 updates per second) for smooth animation.
    private static final int    TICK_MS = 16;

    // ── MUTABLE USER PARAMETERS (SIMULATION INPUTS) ───────────────────────────
    
    private double mass    = 5.0;   // Mass of the block object measured in kilograms (kg)
    private double applied = 20.0;  // Constant pushing force manually exerted on the object in Newtons (N)
    private double mu      = 0.3;   // Coefficient of kinetic/static friction (unitless ratio)

    // ── LIVE KINEMATIC STATE VARIABLES (SIMULATION OUTPUTS) ────────────────────
    
    private double posX = 0, velX = 0; // Tracks instantaneous physical location (m) and velocity (m/s) along a 1D vector
    private double simTime = 0;        // Keeps track of the total accumulated elapsed flight time (seconds)
    private boolean running = false;   // Stateful tracking flag; checks if user is running or actively interacting with configuration

    // ── COMPONENT HOOKS & STRUCTURAL LAYOUT ────────────────────────────────────
    
    private Timer animTimer;   // Swing core animation clock. Emits standard actionable events sequentially on fixed intervals.
    private SimCanvas canvas;   // Explicit nested custom draw panel tailored exclusively for managing specialized graphic models.

    // ── SWING UI CONTROL SYSTEM REFERENCE HOOKS ────────────────────────────────
    
    private JSlider massSlider, forceSlider, muSlider;      // Range sliders giving immediate manual tactile access
    private JTextField massField, forceField, muField;    // Numerical alternative string-bound formatting panels
    private JLabel massLabel, forceLabel, muLabel;          // Accompanying semantic descriptive textual headers
    private JLabel accelLabel, velLabel, posLabel, frictionLabel; // Real-time updated numerical data readout telemetry fields
    private JButton startBtn;                               // Execution controller trigger loop switch button

    /**
     * Component Constructor. Instantiates and configures layout rules, sets border styles, 
     * builds individual nested panels, wires listeners, and locks components in a structural hierarchy.
     */
    public ForcesPanel(MainFrame frame) {
        // BorderLayout slices this container into five operational spaces: North, South, East, West, Center.
        // (8, 8) declares a constant 8-pixel horizontal and vertical buffer gap between adjacent sections.
        setLayout(new BorderLayout(8, 8));
        
        // Formulates an invisible protective 10-pixel structural margin lining the inside frame of this panel.
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── REGION: NORTH (Header Title) ──────────────────────────────────────
        JLabel title = new JLabel("Forces - Friction & Newton's 2nd Law");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f)); // Copies standard font layout but enforces BOLD and 16pt scaling.
        add(title, BorderLayout.NORTH); // Pins header securely to the very top edge.

        // ── REGION: CENTER (The Graphical Display Engine) ──────────────────────
        canvas = new SimCanvas();
        canvas.setPreferredSize(new Dimension(600, 300)); // Relays suggested geometric baseline constraints to layout managers.
        canvas.setBorder(BorderFactory.createLineBorder(Color.GRAY)); // Outlines drawing panel with a solid neutral boundary line.
        add(canvas, BorderLayout.CENTER); // Center fills all remaining spatial area not claimed by top, bottom, or sides.

        // ── REGION: EAST (Control Inputs and Real-Time Readouts Panel) ─────────
        JPanel controls = new JPanel();
        // BoxLayout.Y_AXIS strictly constructs components sequentially stacked straight down vertically (top-to-bottom).
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0)); // Padding buffer ensures the canvas line does not merge into sliders.
        controls.setPreferredSize(new Dimension(190, 0)); // Hardcaps width at 190 pixels, height expands fluidly to fill vertical layout.

        // ── CONTROLLER BLOCK: OBJECT MASS (kg) ──────────────────────────────────
        massLabel  = new JLabel("Mass: 5 kg");
        massSlider = new JSlider(1, 20, 5); // Bounds values strictly between [1, 20], setting cursor default point to 5.
        massField  = new JTextField("5", 5);
        
        // Fires dynamically whenever the slider cursor moves (by mouse drag or programmatic injection).
        massSlider.addChangeListener(e -> {
            mass = massSlider.getValue(); // Resolves integer state to sync backend numerical primitive data type.
            massLabel.setText("Mass: " + (int) mass + " kg"); // Formats textual information readout view.
            massField.setText(String.valueOf((int) mass));   // Explicit string transformation forces input field match.
            if (!running) reset(); // Triggers reset state update to instantly recalibrate static weights on the graphics panel.
        });
        
        // Input text fields utilize ActionListeners which execute ONLY when the user targets the box and clicks 'Enter'.
        massField.addActionListener(e -> applyMassField());
        // FocusListener catches focus context loss (e.g., typing a number, then clicking away onto an entirely different button).
        massField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { applyMassField(); }
        });

        // ── CONTROLLER BLOCK: APPLIED FORCE (N) ────────────────────────────────
        forceLabel  = new JLabel("Applied Force: 20 N");
        forceSlider = new JSlider(-100, 100, 20); // Extends into negative fields; negative forces signify pulling left instead of pushing right.
        forceField  = new JTextField("20", 5);
        forceSlider.addChangeListener(e -> {
            applied = forceSlider.getValue();
            forceLabel.setText("Applied Force: " + (int) applied + " N");
            forceField.setText(String.valueOf((int) applied));
            if (!running) reset();
        });
        forceField.addActionListener(e -> applyForceField());
        forceField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { applyForceField(); }
        });

        // ── CONTROLLER BLOCK: FRICTIONAL COEFFICIENT (mu) ─────────────────────
        muLabel  = new JLabel("Friction (mu): 0.30");
        // NOTE: JSlider operates exclusively with integers! To parse a decimal fractional value (0.00 to 1.00), 
        // we must abstract the slider logic by multiplying and tracking integers from 0 to 100.
        muSlider = new JSlider(0, 100, 30); 
        muField  = new JTextField("0.30", 5);
        muSlider.addChangeListener(e -> {
            mu = muSlider.getValue() / 100.0; // Deconstructs raw slider values back down to real physical scales.
            muLabel.setText(String.format("Friction (mu): %.2f", mu)); // Forces continuous uniform double decimal presentation rules.
            muField.setText(String.format("%.2f", mu));
            if (!running) reset();
        });
        muField.addActionListener(e -> applyMuField());
        muField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { applyMuField(); }
        });

        // Instantiate live telemetry data reporting tags. 
        accelLabel    = new JLabel("Acceleration: 0.00 m/s²");
        velLabel      = new JLabel("Velocity:      0.00 m/s");
        posLabel      = new JLabel("Position:      0.00 m");
        frictionLabel = new JLabel("Friction F:   0.00 N");

        startBtn = new JButton("Start");
        startBtn.addActionListener(e -> toggleSim()); // Binds user interaction handler to manage ongoing clock loops.
        JButton resetBtn = new JButton("Reset");
        resetBtn.addActionListener(e -> reset());

        // Construct layout by physically stacking child objects top-to-bottom inside the control layout frame.
        controls.add(massLabel); controls.add(massSlider); controls.add(labeledField("Enter mass:", massField));
        controls.add(Box.createVerticalStrut(6)); // Rigid un-scannable vertical spacing padding buffer (6 pixels).
        controls.add(forceLabel); controls.add(forceSlider); controls.add(labeledField("Enter force:", forceField));
        controls.add(Box.createVerticalStrut(6));
        controls.add(muLabel);    controls.add(muSlider);    controls.add(labeledField("Enter mu:", muField));
        controls.add(Box.createVerticalStrut(12));
        controls.add(accelLabel); controls.add(Box.createVerticalStrut(3));
        controls.add(velLabel);   controls.add(Box.createVerticalStrut(3));
        controls.add(posLabel);   controls.add(Box.createVerticalStrut(3));
        controls.add(frictionLabel);
        controls.add(Box.createVerticalStrut(12));
        controls.add(startBtn);   controls.add(Box.createVerticalStrut(4));
        controls.add(resetBtn);
        
        add(controls, BorderLayout.EAST); // Locks structural layout panel into place on the right hand side.

        // ── REGION: SOUTH (Navigational Panel) ─────────────────────────────────
        // FlowLayout places elements left-to-right natively. LEFT constraint forces alignment flush against the left boundary.
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton back = new JButton("Back");
        back.addActionListener(e -> { 
            animTimer.stop(); // SAFETY FAILSAFE: Crucial to isolate/kill running background threads to avoid background memory leaks!
            frame.showConceptSelection("Grade 11"); // Signals parent frame to pop container view out of frame stacks.
        });
        south.add(back);
        add(south, BorderLayout.SOUTH);

        // Instantiates loop system using fixed intervals. Every trigger step execution executes the 'tick()' method strategy.
        animTimer = new Timer(TICK_MS, e -> tick());
        reset(); // Bootstraps starting physics states to clean initial configurations.
    }

    /**
     * Structural layout utility factory. Returns an encapsulated side-by-side component structure row.
     */
    private JPanel labeledField(String label, JTextField field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        row.setOpaque(false); // Translucent rendering configuration ensures underlying container structural colors mask gracefully.
        row.add(new JLabel(label));
        row.add(field);
        return row;
    }

    // ── TEXT VALIDATION CONTROLLER SUBORDINATES ─────────────────────────────────
    // These methods parse, evaluate, sanitize, clamp, and apply data entered manually into input boxes.
    
    private void applyMassField() {
        try {
            // Parses entered string data, safely stripping whitespace using .trim().
            // Math.max/min locks manual inputs strictly inside the safe slider array boundaries [1 to 20 kg].
            int val = (int) Math.max(1, Math.min(20, Double.parseDouble(massField.getText().trim())));
            massSlider.setValue(val); // Updating slider automatically hooks right into ChangeListener loop above.
        } catch (NumberFormatException ignored) {
            massField.setText(String.valueOf((int) mass)); // Soft fail validation fallback; replaces bad user typo entries with last working value.
        }
    }

    private void applyForceField() {
        try {
            int val = (int) Math.max(-100, Math.min(100, Double.parseDouble(forceField.getText().trim())));
            forceSlider.setValue(val);
        } catch (NumberFormatException ignored) {
            forceField.setText(String.valueOf((int) applied));
        }
    }

    private void applyMuField() {
        try {
            // Because our UI slider is map scaled up by 100x to maintain precision tracking,
            // we have to multiply the user typed data entry to convert back safely into integer bounds.
            int val = (int) Math.max(0, Math.min(100, Double.parseDouble(muField.getText().trim()) * 100));
            muSlider.setValue(val);
        } catch (NumberFormatException ignored) {
            muField.setText(String.format("%.2f", mu));
        }
    }

    /**
     * Manages operational state machines for the interactive execution context panel.
     * Maps across three core logical timeline phases: Stop -> Active -> Paused -> Active.
     */
    private void toggleSim() {
        if (!running) {
            running = true;
            startBtn.setText("Pause");
            animTimer.start(); // Mounts loop execution calls.
        } else if (animTimer.isRunning()) {
            animTimer.stop(); // Halts incremental ticks while keeping current state records alive.
            startBtn.setText("Resume");
        } else {
            animTimer.start();
            startBtn.setText("Pause");
        }
    }

    /**
     * Wipes physics engines vectors, flushes current time states back down to ground zero, 
     * kills operational processing intervals, and instructs UI elements to drop clean values.
     */
    private void reset() {
        animTimer.stop();
        running = false;
        posX = 0; velX = 0; simTime = 0;
        startBtn.setText("Start");
        updateLabels(0, 0);
        canvas.repaint(); // Forces full UI paint cycles to reposition our structural graphical blocks.
    }

    /**
     * THE MAIN CORE PHYSICS INTERACTION LOOP ENGINE (Fired every 16ms)
     * This logic relies on classic Newtonian Mechanics and evaluates systems using Euler-Cromer Integration methods.
     */
    private void tick() {
        // Translates loop refresh ticks back down into decimal components scaled in seconds.
        double dt = TICK_MS / 1000.0; // 16ms -> 0.016 seconds
        
        // Compute Maximum Potential Frictional Threshold Magnitude: f_k = \mu \cdot m \cdot g
        double friction = mu * mass * GRAVITY;
        double fnet;

        // Dynamic System Analysis Evaluation:
        // Friction MUST always act against the direction of relative travel motion!
        if (velX > 0) {
            // Object moving right: Friction acts left (subtracted)
            fnet = applied - friction;
        } else if (velX < 0) {
            // Object moving left: Friction acts right (added)
            fnet = applied + friction;
        } else {
            // STATIC SYSTEM EVALUATION CONDITION (Object is sitting perfectly still, velX == 0)
            // Does the active vector push exceed static frictional breakout restrictions?
            if (Math.abs(applied) > friction) {
                // Outward driving push breaks friction barriers:
                // Newton's 2nd Law takes over. Subtract friction from the driving vector path direction.
                fnet = applied - Math.signum(applied) * friction;
            } else {
                // Pushing force is insufficient to break static friction barriers. Net Force drops immediately to zero!
                fnet = 0;
            }
        }

        // Apply Newton's 2nd Law of Motion: a = F_net / m
        double accel = fnet / mass;
        
        // Integrate instantaneous linear acceleration component values forward into step velocity: v = v + a * dt
        velX += accel * dt;

        // CRITICAL FAILSAFE STOP PROTECTION CHECK:
        // If an object slows down to almost zero due to friction, and the applied driving forces are too weak 
        // to keep it moving, friction shouldn't push the box backward! It must come to a dead stop.
        if (Math.abs(velX) < 0.01 && Math.abs(applied) < friction) {
            velX = 0;
        }

        // Integrate current physical location coordinates forward using linear mapping vectors: x = x + v * dt
        posX += velX * dt;
        simTime += dt; // Accumulate running clock metrics

        updateLabels(accel, friction); // Flush numeric telemetry output directly into interface text fields.
        canvas.repaint(); // Re-index graphical positions and trigger redraws.
    }

    /**
     * Formats real-time updates for telemetry labels using standardized formatting conventions.
     */
    private void updateLabels(double accel, double friction) {
        accelLabel   .setText(String.format("Acceleration: %.2f m/s²", accel));
        velLabel     .setText(String.format("Velocity:      %.2f m/s",  velX));
        posLabel     .setText(String.format("Position:      %.2f m",    posX));
        frictionLabel.setText(String.format("Friction F:   %.2f N",    friction));
    }

    /**
     * Specialized private custom canvas panel designed to handle full graphical displays.
     * Operates closely within outer-class scopes for quick, natural access to inner parameters.
     */
    private class SimCanvas extends JPanel {
        
        /**
         * Overrides Java Swing's internal layout hooks. Executed asynchronously whenever system updates 
         * request panel paint changes or when `.repaint()` is invoked manually.
         */
        @Override
        protected void paintComponent(Graphics g) {
            // Mandatory internal rule: clear previous frame graphics buffers to prevent screen smearing artifacts!
            super.paintComponent(g);
            
            // Unpacks standard baseline functional Graphics rendering properties into a Graphics2D pipeline
            // to access line weight stroke controls and antialiasing logic filters.
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Fetch working dimensions dynamically; this tracks and handles window scaling gracefully.
            int W = getWidth(), H = getHeight(), groundY = H - 60, originX = 40;

            // ── DRAW ENVIRONMENT: GROUND LINE ───────────────────────────────────
            g2.setColor(Color.DARK_GRAY);
            g2.setStroke(new BasicStroke(2f)); // Renders line at 2 pixels wide.
            g2.drawLine(0, groundY, W, groundY); // Pin track coordinates across left to right boundaries.

            // ── DRAW MEASUREMENT SCALE TICKS (Every 5 Meters) ───────────────────
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(Color.GRAY);
            int step = (int)(5 * SCALE); // Calculates specific distance buffers mapped to scale rules (5m * 40px/m = 200px)
            for (int px = originX, m = 0; px < W; px += step, m += 5) {
                g2.drawLine(px, groundY, px, groundY + 4); // Drop small vertical line notches beneath track
                g2.drawString(m + "m", px - 5, groundY + 15); // Draw numerical metric tags
            }

            // ── OBJECT MODEL COORDINATE CALCULATION ─────────────────────────────
            int boxW = 50, boxH = 40; // Declare spatial pixel size scales for physical target mass blocks.
            // Map instantaneous real world location variables into real coordinate space tracks on screen.
            int boxX = originX + (int)(posX * SCALE);
            int boxY = groundY - boxH; // Subtracts height parameters to draw the block sitting *on top* of the floor.

            // ── RENDER SIMULATED BOX OBJECT ─────────────────────────────────────
            g2.setColor(new Color(100, 149, 237)); // Cornflower Blue filling color.
            g2.fillRect(boxX, boxY, boxW, boxH);
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRect(boxX, boxY, boxW, boxH); // Render crisp black border frame outline wrap.

            // Center object mass value text string accurately inside box layout matrix
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2.setColor(Color.WHITE);
            String mStr = (int) mass + " kg";
            FontMetrics fm = g2.getFontMetrics(); // Measures precise width profile footprint dimensions of active fonts.
            g2.drawString(mStr, boxX + (boxW - fm.stringWidth(mStr)) / 2, boxY + boxH / 2 + 4);

            // Establish core focal nexus anchor centers for anchoring vector tracking arrows
            int centerX = boxX + boxW / 2, centerY = boxY + boxH / 2;

            // ── VECTOR ARROW GENERATION: APPLIED FORCE ─────────────────────────
            if (applied != 0) {
                // Dynamically scale arrow line length based on force magnitude, capped at a maximum of 120 pixels.
                int arrowLen = Math.min((int)(Math.abs(applied) * 2), 120);
                if (applied > 0) {
                    // Pull vector direction extending rightwards
                    drawArrow(g2, centerX + boxW / 2, centerY, centerX + boxW / 2 + arrowLen, centerY, Color.RED);
                    g2.setColor(Color.RED);
                    g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                    g2.drawString("F=" + (int)applied + "N", centerX + boxW / 2 + 4, centerY - 5);
                } else {
                    // Pull vector direction extending leftwards
                    drawArrow(g2, centerX - boxW / 2, centerY, centerX - boxW / 2 - arrowLen, centerY, Color.RED);
                    g2.setColor(Color.RED);
                    g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                    g2.drawString("F=" + (int)applied + "N", centerX - boxW / 2 - arrowLen, centerY - 5);
                }
            }

            // ── VECTOR ARROW GENERATION: FRICTION ──────────────────────────────
            double frictionForce = mu * mass * GRAVITY;
            // Determine active counter direction path profiles based on instant velocity.
            double frictionDir = (velX != 0) ? -Math.signum(velX) : -Math.signum(applied);
            
            // Only draw a friction arrow if the object is moving, or if an applied force is being actively resisted.
            if (velX != 0 || Math.abs(applied) > frictionForce) {
                int fLen = Math.min((int)(frictionForce * 2), 80);
                if (frictionDir > 0) {
                    drawArrow(g2, centerX + boxW / 2, centerY, centerX + boxW / 2 + fLen, centerY, Color.ORANGE);
                    g2.setColor(Color.ORANGE);
                    g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                    g2.drawString("f", centerX + boxW / 2 + fLen + 2, centerY - 5);
                } else {
                    drawArrow(g2, centerX - boxW / 2, centerY, centerX - boxW / 2 - fLen, centerY, Color.ORANGE);
                    g2.setColor(Color.ORANGE);
                    g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                    g2.drawString("f", centerX - boxW / 2 - fLen - 12, centerY - 5);
                }
            }

            // ── VECTOR ARROW GENERATION: NORMAL FORCE (N) ──────────────────────
            int nLen = Math.min((int)(mass * GRAVITY * 1.5), 80);
            drawArrow(g2, centerX, boxY, centerX, boxY - nLen, Color.GREEN.darker()); // Points upward from the top of the box.
            g2.setColor(Color.GREEN.darker());
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.drawString("N", centerX + 4, boxY - nLen - 3);

            // ── VECTOR ARROW GENERATION: WEIGHT GRAVITATIONAL FORCE (W) ────────
            drawArrow(g2, centerX, groundY, centerX, groundY + 40, Color.GRAY); // Points straight down from the base.
            g2.setColor(Color.GRAY);
            g2.drawString("W", centerX + 4, groundY + 38);

            // ── GRAPHICAL SIMULATION KEY LEGEND BOX ────────────────────────────
            int lx = W - 110, ly = 12;
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(Color.RED);            g2.drawString("→ Applied (F)", lx, ly);
            g2.setColor(Color.ORANGE);         g2.drawString("← Friction (f)", lx, ly + 14);
            g2.setColor(Color.GREEN.darker()); g2.drawString("↑ Normal (N)", lx, ly + 28);
            g2.setColor(Color.GRAY);           g2.drawString("↓ Weight (W)", lx, ly + 42);
        }

        /**
         * Mathematical rendering utility to draw vector direction line pointers on screen.
         * Leverages polar trigonometry math coordinate tracking points to calculate arrow head wings.
         */
        private void drawArrow(Graphics2D g2, int x1, int y1, int x2, int y2, Color color) {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(x1, y1, x2, y2); // Draw the main arrow shaft line.
            
            // Math.atan2 parses absolute directional delta configurations to discover the line's angle of incidence in radians.
            double angle = Math.atan2(y2 - y1, x2 - x1);
            
            // Renders arrow tip wing flairs offset symmetrically from line pointer headers using radial translations (0.4 radians).
            g2.drawLine(x2, y2, x2 - (int)(10 * Math.cos(angle - 0.4)), y2 - (int)(10 * Math.sin(angle - 0.4)));
            g2.drawLine(x2, y2, x2 - (int)(10 * Math.cos(angle + 0.4)), y2 - (int)(10 * Math.sin(angle + 0.4)));
        }
    }
}
