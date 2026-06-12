import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * EnergyWorkPanel represents a 1D vertical drop simulation panel with inelastic ground impacts.
 * Extends JPanel to plug cleanly into our primary card-based frame manager (MainFrame).
 * * Core Physics Logic:
 * PE = m * g * h              (Potential Energy depends on instantaneous height)
 * KE = 0.5 * m * v^2          (Kinetic Energy depends on instantaneous velocity)
 * Total E = PE_initial        (Conserved strictly while falling; no air friction included)
 * v_final = -v_incident * e   (Newtonian Restitution manages velocity dampening on bounces)
 */
public class EnergyWorkPanel extends JPanel {

    // ── PHYSICS & TIMING CONSTANTS ──────────────────────────────────────────
    
    // Constant acceleration due to gravity on Earth (m/s²). Driving term behind change in downward velocity.
    private static final double GRAVITY = 9.81;
    
    // Refresh cycle delay interval (16ms ≈ 60 frames per second display update frequency).
    private static final int    TICK_MS = 16;

    // ── CONFIGURABLE PHYSICAL INPUT VARIABLES ─────────────────────────────────
    
    private double mass      = 2.0;   // Object mass in kilograms (kg). Scales both absolute kinetic and potential energy values.
    private double startH    = 10.0;  // Initial drop elevation measured in meters (m). Establishes total system energy limits.

    // ── MUTABLE KINEMATIC STATE VARIABLES ─────────────────────────────────────
    
    private double posY    = 0;   // Instantaneous height relative to ground baseline (meters).
    private double velY    = 0;   // Instantaneous vertical speed vector component (m/s). Downward vector path is tracked positively.
    private double posX    = 0;   // Visual horizontal offset displacement (pixels) used purely to sweep the ball left-to-right across screen.
    private boolean running  = false; // State flag confirming the animation timeline loop is actively processing clock cycles.
    private boolean dropped  = false; // State flag locking user modification inputs once the ball has been released from its initial drop state.

    // ── ANIMATION & GRAPHICS ENGINE HOOKS ────────────────────────────────────
    
    private Timer animTimer;   // Main UI execution clock. Emits standard ActionEvents sequentially to step through calculations.
    private SimCanvas canvas;   // Custom inner drawing board layer optimized for custom geometric graphics pipeline overrides.

    private double restitution = 0.8;  // Coefficient of Restitution (e). Defines fractional velocity retained after collision [0.0 = splat, 1.0 = ideal elastic].

    // ── SWING GRAPHICAL COMPONENT HANDLES ────────────────────────────────────
    
    private JSlider massSlider, heightSlider, bounceSlider;      // Tactical configuration input controllers
    private JTextField massField, heightField, bounceField;    // Numerical alternative string-bound formatting inputs
    private JLabel  massLabel, heightLabel, bounceLabel;          // Context labels for our input sliders
    private JLabel  peLabel, keLabel, totalLabel, velLabel;       // Live updating instrumentation readouts (Telemetry)
    private JButton startBtn;                                     // Multi-state program execution switch ("Drop"/"Pause"/"Resume")

    /**
     * Primary Component Constructor. Handles layout rule management, container margins, 
     * structural component stacking, and event listener registration.
     */
    public EnergyWorkPanel(MainFrame frame) {
        // Enforces BorderLayout. (8,8) establishes explicit padding between layout regions.
        setLayout(new BorderLayout(8, 8));
        
        // Generates an external 10-pixel transparent cushioning margin lining the interior perimeter of the panel.
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── NORTH REGION: Header Title ────────────────────────────────────────
        JLabel title = new JLabel("Energy & Work - Kinetic vs Potential Energy");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f)); // Forces 16pt scaling and BOLD decoration while copying existing fonts.
        add(title, BorderLayout.NORTH);

        // ── CENTER REGION: Mathematical Graphic Output Display ──────────────────
        canvas = new SimCanvas();
        canvas.setPreferredSize(new Dimension(600, 360)); // Sets baseline dimensions requested from window container.
        canvas.setBorder(BorderFactory.createLineBorder(Color.GRAY)); // Outlines the simulator canvas with a clear border line.
        add(canvas, BorderLayout.CENTER);

        // ── EAST REGION: Dedicated User Controls Sidebar Layout ─────────────────
        JPanel controls = new JPanel();
        // BoxLayout.Y_AXIS stacks sub-components rigidly in a single straight vertical row from top-to-bottom.
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0)); // Prevents the sidebar elements from brushing right against the canvas wall.
        controls.setPreferredSize(new Dimension(190, 0)); // Hardcaps absolute lateral column spacing at 190 pixels width.

        // ── SYSTEM COMPONENT CONTROL MODULE: OBJECT MASS (kg) ────────────────────
        massLabel  = new JLabel("Mass: 2 kg");
        massSlider = new JSlider(1, 20, 2); // Valid slider range span [1kg to 20kg], initial selector notch set at 2.
        massField  = new JTextField("2", 5);

        // Listens live to slider cursor movements (both via mouse-drag inputs and indirect programmatic injections).
        massSlider.addChangeListener(e -> {
            mass = massSlider.getValue(); // Pulls raw integer from slider state to sync native physics fields.
            massLabel.setText("Mass: " + (int) mass + " kg"); // Syncs structural display labels text.
            massField.setText(String.valueOf((int) mass));   // Forces synchronization with companion text input fields.
            if (!dropped) reset(); // Automatically recalibrates potential energy bars before execution if ball is still at rest.
        });
        
        // ActionListeners on text fields run exclusively when a user enters text into the box and hits 'Enter'.
        ActionListener massAction = e -> applyMassField();
        massField.addActionListener(massAction);
        // FocusListener captures context changes (e.g., modifying typed text, then clicking away onto another button).
        massField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { applyMassField(); }
        });

        // ── SYSTEM COMPONENT CONTROL MODULE: INITIAL ELEVATION HEIGHT (m) ───────
        heightLabel  = new JLabel("Start Height: 10 m");
        heightSlider = new JSlider(1, 30, 10); // Physical height map tracking limit bounds restricted across [1m to 30m].
        heightField  = new JTextField("10", 5);

        heightSlider.addChangeListener(e -> {
            startH = heightSlider.getValue();
            heightLabel.setText("Start Height: " + (int) startH + " m");
            heightField.setText(String.valueOf((int) startH));
            if (!dropped) reset(); // Moves the ball to its new starting height position on the coordinate graph immediately.
        });
        ActionListener heightAction = e -> applyHeightField();
        heightField.addActionListener(heightAction);
        heightField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { applyHeightField(); }
        });

        // ── SYSTEM COMPONENT CONTROL MODULE: COEFFICIENT OF RESTITUTION (0-1) ────
        bounceLabel  = new JLabel("Bounciness: 0.80");
        // NOTE: JSlider components accept only integers! To capture double fractional values safely, 
        // we scale up our range mapping variables by 100x internally, evaluating bounds from 0 to 100.
        bounceSlider = new JSlider(0, 100, 80); 
        bounceField  = new JTextField("0.80", 5);

        bounceSlider.addChangeListener(e -> {
            restitution = bounceSlider.getValue() / 100.0; // Converts 100x slider integers down into 0.00-1.00 precision ratios.
            bounceLabel.setText(String.format("Bounciness: %.2f", restitution)); // Forces clean decimal layout representations.
            bounceField.setText(String.format("%.2f", restitution));
        });
        ActionListener bounceAction = e -> applyBounceField();
        bounceField.addActionListener(bounceAction);
        bounceField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { applyBounceField(); }
        });

        // Instantiate live telemetry data reporting tags. Updates systematically via math ticker loop.
        peLabel    = new JLabel("PE: 0.00 J");
        keLabel    = new JLabel("KE: 0.00 J");
        totalLabel = new JLabel("Total: 0.00 J");
        velLabel   = new JLabel("Speed: 0.00 m/s");

        startBtn = new JButton("Drop");
        startBtn.addActionListener(e -> toggleSim()); // Wires execution controls to the clock management state switch.

        JButton resetBtn = new JButton("Reset");
        resetBtn.addActionListener(e -> reset());

        // Assemble control sidebar layout by dropping items sequentially top-to-bottom.
        controls.add(massLabel); controls.add(massSlider); controls.add(labeledField("Enter mass (1-20):", massField));
        controls.add(Box.createVerticalStrut(6)); // Fixed vertical un-scannable spacer buffers (6 pixels).
        controls.add(heightLabel); controls.add(heightSlider); controls.add(labeledField("Enter height (1-30):", heightField));
        controls.add(Box.createVerticalStrut(6));
        controls.add(bounceLabel); controls.add(bounceSlider); controls.add(labeledField("Enter bounce (0-1):", bounceField));
        controls.add(Box.createVerticalStrut(12));
        controls.add(peLabel);     controls.add(Box.createVerticalStrut(3));
        controls.add(keLabel);     controls.add(Box.createVerticalStrut(3));
        controls.add(totalLabel);  controls.add(Box.createVerticalStrut(3));
        controls.add(velLabel);
        controls.add(Box.createVerticalStrut(12));
        controls.add(startBtn);    controls.add(Box.createVerticalStrut(4));
        controls.add(resetBtn);
        
        add(controls, BorderLayout.EAST); // Places control sidebar into structural layout frame on the right side.

        // ── SOUTH REGION: Navigation Controls Footer Panel ─────────────────────
        // FlowLayout handles items left-to-right natively. FlowLayout.LEFT forces leftmost layout packing alignment.
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton back = new JButton("Back");
        back.addActionListener(e -> { 
            animTimer.stop(); // CRITICAL MEMORY FAILSAFE: Always explicitly stop timers when navigating away to prevent background leakage!
            frame.showConceptSelection("Grade 11"); 
        });
        south.add(back);
        add(south, BorderLayout.SOUTH);

        // Sets up loop system linking targeted ticks directly with the core physics tick() handler method strategy.
        animTimer = new Timer(TICK_MS, e -> tick());
        reset(); // Bootstrap initial environment physics attributes cleanly.
    }

    // ── TEXT VALIDATION CONTROLLER SUBORDINATES ─────────────────────────────────
    // These methods parse, evaluate, sanitize, clamp, and apply data entered manually into input boxes.

    private void applyMassField() {
        try {
            // Extracts raw string data, cleanly stripping spaces using .trim().
            // Math.max/min locks manual inputs strictly inside the safe slider array boundaries [1 to 20 kg].
            int val = (int) Math.max(1, Math.min(20, Double.parseDouble(massField.getText().trim())));
            massSlider.setValue(val); // Updating slider values automatically invokes the ChangeListener event.
        } catch (NumberFormatException ignored) {
            massField.setText(String.valueOf((int) mass)); // Graceful fallback protection; substitutes broken typos with last known functional value.
        }
    }

    private void applyHeightField() {
        try {
            int val = (int) Math.max(1, Math.min(30, Double.parseDouble(heightField.getText().trim())));
            heightSlider.setValue(val);
        } catch (NumberFormatException ignored) {
            heightField.setText(String.valueOf((int) startH));
        }
    }

    private void applyBounceField() {
        try {
            // Parses decimal values directly across valid mathematical coefficient parameters [0.0 to 1.0]
            double val = Math.max(0.0, Math.min(1.0, Double.parseDouble(bounceField.getText().trim())));
            bounceSlider.setValue((int)(val * 100)); // Scales internal slider integer track context up by 100x.
        } catch (NumberFormatException ignored) {
            bounceField.setText(String.format("%.2f", restitution));
        }
    }

    /**
     * Component layout row design factory utility. Merges inline text descriptions 
     * alongside text fields tightly within a shared single-row sub-container framework.
     */
    private JPanel labeledField(String label, JTextField field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        row.setOpaque(false); // Translucent rendering allows background colors of master container panels to mask through.
        row.add(new JLabel(label));
        row.add(field);
        return row;
    }

    // ── PHYSICS & TIMELINE STATE CONTROLLERS ───────────────────────────────────

    /**
     * Manages running states and button text changes for the interactive simulation loop timeline.
     * Steps structurally through: Initial Drop -> Running -> Paused -> Running.
     */
    private void toggleSim() {
        if (!dropped) {
            // INITIALIZATION CONTEXT: First execution launch click
            dropped = true;
            running = true;
            posY = startH; // Places target ball object at designated start height boundaries.
            velY = 0;      // Starts perfectly at rest (Initial $v = 0$).
            posX = 0;      // Resets structural horizontal sweep positions back to left border margins.
            startBtn.setText("Pause");
            animTimer.start(); // Mounts thread updates to feed ticks to math loop handlers.
        } else if (animTimer.isRunning()) {
            // PAUSE ACTION: Freezes loop tracking while preserving historical variables state records.
            animTimer.stop();
            startBtn.setText("Resume");
        } else {
            // RESUME ACTION: Re-engages animation timers safely.
            animTimer.start();
            startBtn.setText("Pause");
        }
    }

    /**
     * Wipes ongoing calculations, clears live runtime position paths, sets state 
     * flags to false, flushes instrumentation readouts, and requests graphical clean updates.
     */
    private void reset() {
        animTimer.stop();
        dropped = false;
        running = false;
        posY    = startH; // Resets ball elevation to starting position parameters.
        velY    = 0;
        posX    = 0;
        startBtn.setText("Drop");
        updateLabels(); // Forces telemetry fields back to clean theoretical starting baselines.
        canvas.repaint(); // Requests complete UI paint sweep pass to move the visual model components back up to the top.
    }

    /**
     * CORE PHYSICAL COMPUTATION CLOCK TICK TIMELINE STRATEGY (Fires every 16ms)
     * Models kinematics via standard symplectic Euler integration steps.
     */
    private void tick() {
        // Formulates execution interval step variables scaled in seconds (16ms = 0.016s).
        double dt = TICK_MS / 1000.0;
        
        // Step 1: Compute instantaneous velocity acceleration updates. $v_f = v_i + a \cdot dt$
        // Gravity acts in a downward-positive vector notation context here.
        velY += GRAVITY * dt;   
        
        // Step 2: Compute change in spatial height locations. $h_f = h_i - v \cdot dt$
        // Upward elevation values decrease as object vector paths accelerate downwards toward zero.
        posY -= velY * dt;      
        
        // Step 3: Sweep object along horizontal layout paths steadily over time for aesthetic presentation.
        posX += 80 * dt;        

        // INELASTIC GROUND BOUNDARY COLLISION CONDITION EVALUATION:
        if (posY <= 0) {
            posY = 0; // Hard clamps position coordinate fields straight to zero to prevent underground clipping.
            
            // Apply Newtonian Restitution model: Instantaneously invert direction vector and dampen speed.
            velY = -velY * restitution;  
            
            // LOW KINETIC ENERGY THRESHOLD CHECK (Failsafe dead-stop breakout trigger):
            // Prevents microscopic bouncing calculations from running infinitely. If velocity falls beneath 0.2 m/s,
            // the object is brought to a complete rest on the floor.
            if (Math.abs(velY) < 0.2) {
                velY = 0;
                animTimer.stop(); // Terminates background animation ticks.
                dropped = false;  // Unlocks input configurations layout system sliders.
                startBtn.setText("Drop");
            }
        }

        updateLabels(); // Directs telemetry tags to grab newly indexed numbers.
        canvas.repaint(); // Flags graphics canvas layer to evaluate layout maps and handle visual redraw cycles.
    }

    /**
     * Evaluates instant physics properties using work-energy equations 
     * and displays formatted values on text views.
     */
    private void updateLabels() {
        // $PE = m \cdot g \cdot h$ (Clamped at 0 to avoid negative results during precision roundings)
        double pe    = mass * GRAVITY * Math.max(posY, 0);
        
        // $KE = 0.5 \cdot m \cdot v^2$
        double ke    = 0.5 * mass * velY * velY;
        
        // Theoretical Total energy cap established by initial boundary inputs. $E_{\text{total}} = m \cdot g \cdot h_{\text{start}}$
        double total = mass * GRAVITY * startH;
        
        peLabel   .setText(String.format("PE: %.2f J",    pe));
        keLabel   .setText(String.format("KE: %.2f J",    ke));
        totalLabel.setText(String.format("Total: %.2f J", total));
        velLabel  .setText(String.format("Speed: %.2f m/s", Math.abs(velY))); // Shows absolute speed scalar notation.
    }

    // ── GRAPHICS ENGINE RENDERING PANEL LAYER ───────────────────────────────────

    /**
     * Custom drawing panel specialized in mapping vector quantities, plotting 
     * coordinate tracking markers, and drawing the dynamic bar charts.
     */
    private class SimCanvas extends JPanel {

        /**
         * Core UI paint handler. Triggered internally by system events or manually via `.repaint()`.
         */
        @Override
        protected void paintComponent(Graphics g) {
            // Mandatory internal rule: clear previous frame graphics buffers to prevent screen smearing artifacts!
            super.paintComponent(g);
            
            // Cast standard Graphics reference handles over to complex Graphics2D modules 
            // to access anti-aliasing text features and layout stroke line weight filters.
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int W       = getWidth();
            int H       = getHeight();
            int groundY = H - 40; // Ground platform horizontal position line axis set 40 pixels up from the panel floor.
            int originX = 50;    // Sets the zero marker coordinate position point for the height scale vertical line.

            // Pixel conversion scale mapping factor: Maps 32 total physical vertical meters cleanly into display bounds.
            double scaleY = (groundY - 20) / 32.0;

            // ── RENDER SIMULATED SYSTEM ENVIRONMENT BASE: GROUND LINE ───────────
            g2.setColor(Color.DARK_GRAY);
            g2.setStroke(new BasicStroke(2f)); // Draws line 2 pixels wide.
            g2.drawLine(0, groundY, W, groundY);

            // ── RENDER MEASUREMENT TICK MARKERS (Every 5 Meters along Y-axis) ───
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(Color.GRAY);
            for (int m = 0; m <= 30; m += 5) {
                int py = groundY - (int)(m * scaleY); // Calculates pixel position matching target meter heights.
                g2.drawLine(originX - 4, py, originX, py); // Draws small indicator notches.
                g2.drawString(m + "m", originX - 24, py + 4); // Prints text value alignment guides next to marks.
            }
            // Vertical structural line trace marking the height reference axis tracker
            g2.drawLine(originX, 0, originX, groundY);

            // ── RENDER HORIZONTAL DASHED LINE: DECLARED LAUNCH DROPPED LEVEL ────
            int startPY = groundY - (int)(startH * scaleY);
            g2.setColor(Color.LIGHT_GRAY);
            // Configures standard rendering stroke engines to track custom dashed dash patterns [4 pixels solid, 4 pixels gap].
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                    0, new float[]{4, 4}, 0));
            g2.drawLine(originX, startPY, W, startPY);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.drawString("start h = " + (int) startH + " m", originX + 4, startPY - 3);

            // ── OBJECT DRAW: SIMULATED MOVING SPHERE BALL MODEL ──────────────────
            int ballR  = 12; // Spatial pixel radius boundary size dimensions for our object circle tracker.
            int ballPX = originX + 20 + (int) posX;
            int ballPY = groundY - (int)(posY * scaleY) - ballR; // Subtracts radius factors so height variables correspond to the circle's base edge.
            
            // Right-side screen safety lock constraint; prevents the tracking ball from flying out of window visibility ranges.
            if (ballPX > W - ballR) ballPX = W - ballR;

            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(new Color(100, 149, 237)); // Cornflower Blue fill layout selection.
            g2.fillOval(ballPX - ballR, ballPY, ballR * 2, ballR * 2);
            g2.setColor(Color.BLACK);
            g2.drawOval(ballPX - ballR, ballPY, ballR * 2, ballR * 2); // Outlines clean black perimeter frame.

            // ── DYNAMIC BAR GRAPHICAL COLUMN CHARTS GENERATION SUBROUTINE ────────
            drawEnergyBars(g2, W, groundY, scaleY);
        }

        /**
         * Plots real-time energy conservation column metrics inside the graphics window container.
         * Dynamically tracks changes in Kinetic and Potential ratios as energy forms shift back and forth.
         */
        private void drawEnergyBars(Graphics2D g2, int W, int groundY, double scaleY) {
            double pe    = mass * GRAVITY * Math.max(posY, 0);
            double ke    = 0.5 * mass * velY * velY;
            double total = mass * GRAVITY * startH;
            if (total == 0) return; // Core safety divide-by-zero check if starting parameters drop to null.

            int barX  = W - 130;  // Anchors sidebar coordinate alignment position relative to changing panel widths.
            int barY  = 20;
            int barW  = 30;       // Column thickness pixel widths.
            int maxH  = groundY - 40; // Hardcaps bar charts max scaling height to prevent overlap into floor lines.

            // ── DRAW COLUMN ONE: POTENTIAL ENERGY (PE - Blue) ───────────────────
            int peH = (int)(pe / total * maxH); // Extracts the percentage slice representation out of global energy capacities.
            g2.setColor(new Color(70, 130, 210)); // Soft Steel Blue fill selection.
            g2.fillRect(barX, barY + maxH - peH, barW, peH); // Fills from bottom of graph up to height limits.
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(barX, barY, barW, maxH); // Outlines constant background reference frame box.
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(new Color(70, 130, 210));
            g2.drawString("PE", barX + 7, barY + maxH + 13);

            // ── DRAW COLUMN TWO: KINETIC ENERGY (KE - Orange) ───────────────────
            int keH = (int)(ke / total * maxH);
            g2.setColor(new Color(230, 140, 40)); // Vibrant Ochre Orange fill selection.
            g2.fillRect(barX + 45, barY + maxH - keH, barW, keH); // Shifts column 45 pixels over to sit cleanly next to PE bar.
            g2.setColor(Color.BLACK);
            g2.drawRect(barX + 45, barY, barW, maxH);
            g2.setColor(new Color(230, 140, 40));
            g2.drawString("KE", barX + 52, barY + maxH + 13);

            // ── CHART HEADER TAGS ───────────────────────────────────────────────
            g2.setColor(Color.DARK_GRAY);
            g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2.drawString("Energy", barX + 8, barY - 4);
        }
    }
}
