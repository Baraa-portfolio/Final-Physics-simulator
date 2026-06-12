import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

// KinematicsPanel is itself a JPanel — the entire simulation screen is one panel
// added into MainFrame's content area via BorderLayout.
public class KinematicsPanel extends JPanel {

    // ── Constants ─────────────────────────────────────────────────────────────

    // Acceleration due to gravity in m/s². Used every tick to pull the ball down.
    private static final double GRAVITY = 9.81;

    // How many pixels represent one metre on screen.
    // Converts world units (metres) into screen units (pixels) when drawing.
    private static final double SCALE = 8.0;

    // How often the simulation updates, in milliseconds.
    // 16ms ≈ 60 updates per second, which looks smooth on screen.
    private static final int TICK_MS = 16;

    // ── Physics state ─────────────────────────────────────────────────────────

    // The two user-controlled inputs. These are the starting conditions.
    private double v0       = 20.0;  // initial launch speed (m/s)
    private double angleDeg = 45.0;  // launch angle (degrees)

    // Tracks how many seconds have passed since launch.
    // Used for the time readout and to prevent the ball stopping on the first tick.
    private double simTime = 0.0;

    // Whether the ball is currently in the air.
    // Controls whether the angle arrow is shown and whether sliders trigger a reset.
    private boolean launched = false;

    // The ball's current position in world coordinates (metres).
    // ballX = horizontal distance from origin, ballY = height above ground.
    private double ballX, ballY;

    // The ball's current velocity components (metres per second).
    // ballVx never changes (no horizontal force). ballVy decreases each tick due to gravity.
    private double ballVx, ballVy;

    // Stores past pixel positions of the ball so the trail arc can be drawn.
    // A new Point is added every tick. Cleared on reset or new launch.
    private final List<Point> trail = new ArrayList<>();

    // ── Swing components ──────────────────────────────────────────────────────

    // javax.swing.Timer — fires an ActionEvent every TICK_MS milliseconds.
    // Each event calls tick(), which advances the physics and redraws the canvas.
    private Timer animTimer;

    // JSlider lets the user drag to pick a value within a range.
    private JSlider speedSlider, angleSlider;

    // JTextField lets the user type in a value directly (alternative to the slider).
    private JTextField speedField, angleField;

    // JLabel displays read-only text. Used for slider headers and live readouts.
    private JLabel speedLabel, angleLabel;
    private JLabel timeLabel, rangeLabel, heightLabel;

    // JButton triggers an action when clicked.
    private JButton launchBtn;

    // Our custom inner panel that handles all drawing. See SimCanvas below.
    private SimCanvas canvas;

    // ── Constructor ───────────────────────────────────────────────────────────

    // The constructor builds the entire UI and wires up all the event listeners.
    // frame is passed in so the Back button can navigate to the concept selection screen.
    public KinematicsPanel(MainFrame frame) {

        // BorderLayout divides this panel into 5 zones: NORTH, SOUTH, EAST, WEST, CENTER.
        // The gaps (8, 8) add spacing between zones.
        setLayout(new BorderLayout(8, 8));

        // Adds padding around the inside edge of this panel (top, left, bottom, right).
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── NORTH: title label ────────────────────────────────────────────────
        JLabel title = new JLabel("Kinematics - Projectile Motion");
        // deriveFont creates a modified copy of the label's current font — bold, size 16.
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        add(title, BorderLayout.NORTH);

        // ── CENTER: the drawing canvas ────────────────────────────────────────
        canvas = new SimCanvas();
        // setPreferredSize tells the layout manager how big this component wants to be.
        // The actual size may differ depending on the window size.
        canvas.setPreferredSize(new Dimension(600, 380));
        // Draws a 1-pixel gray line border around the canvas panel.
        canvas.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        add(canvas, BorderLayout.CENTER);

        // ── EAST: controls panel ──────────────────────────────────────────────
        // A plain JPanel to hold all the sliders, fields, and buttons.
        JPanel controls = new JPanel();
        // BoxLayout stacks children top-to-bottom (Y_AXIS).
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        // Left padding only — creates a gap between the canvas and the controls.
        controls.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        controls.setPreferredSize(new Dimension(190, 0));

        // ── Speed slider + text field ─────────────────────────────────────────
        speedLabel  = new JLabel("Speed: 20 m/s");
        // JSlider(min, max, initialValue)
        speedSlider = new JSlider(1, 60, 20);
        // JTextField(defaultText, columns) — columns is a hint for preferred width.
        speedField  = new JTextField("20", 5);

        // ChangeListener fires whenever the slider value changes (while dragging too).
        speedSlider.addChangeListener(e -> {
            v0 = speedSlider.getValue();
            speedLabel.setText("Speed: " + (int) v0 + " m/s");
            // Keep the text field in sync with the slider.
            speedField.setText(String.valueOf((int) v0));
            // If the ball isn't in the air, reset the canvas to show the new trajectory.
            if (!launched) reset();
        });

        // ActionListener on a JTextField fires when the user presses Enter.
        speedField.addActionListener(e -> {
            try {
                // Clamp the typed value to the slider's valid range, then update the slider.
                // Updating the slider triggers its ChangeListener above, keeping everything in sync.
                int val = (int) Math.max(1, Math.min(60, Double.parseDouble(speedField.getText())));
                speedSlider.setValue(val);
            } catch (NumberFormatException ignored) {
                // If the user typed something that isn't a number, just ignore it.
            }
        });

        // ── Angle slider + text field ─────────────────────────────────────────
        angleLabel  = new JLabel("Angle: 45°");//lable
        angleSlider = new JSlider(0, 90, 45);//creates the slider, with the min max and start
        angleField  = new JTextField("45", 5);//this creates the editable textfield

        angleSlider.addChangeListener(e -> {
            angleDeg = angleSlider.getValue();
            angleLabel.setText("Angle: " + (int) angleDeg + "°");//change the label
            angleField.setText(String.valueOf((int) angleDeg));//update the written thing
            if (!launched) reset();//if hasnt been launched yet it will update the arrow
        });

        angleField.addActionListener(e -> { //once the 
            try {
                int val = (int) Math.max(0, Math.min(90, Double.parseDouble(angleField.getText())));
                angleSlider.setValue(val);
            } catch (NumberFormatException ignored) {}
        });

        // ── Readout labels ────────────────────────────────────────────────────
        // These are updated every tick by updateLabels() to show live physics values.
        timeLabel   = new JLabel("Time:   0.00 s");
        rangeLabel  = new JLabel("Range:  0.00 m");
        heightLabel = new JLabel("Height: 0.00 m");

        // ── Buttons ───────────────────────────────────────────────────────────
        launchBtn = new JButton("Launch");
        // addActionListener wires the button click to the toggleLaunch method.
        launchBtn.addActionListener(e -> toggleLaunch());

        JButton resetBtn = new JButton("Reset");
        resetBtn.addActionListener(e -> reset());

        // ── Add everything to the controls panel in order (top to bottom) ─────
        controls.add(speedLabel);
        controls.add(speedSlider);
        // labeledField() returns a small JPanel containing a label and text field side by side.
        controls.add(labeledField("Enter speed:", speedField));
        // Box.createVerticalStrut adds an invisible fixed-height gap between components.
        controls.add(Box.createVerticalStrut(8));
        controls.add(angleLabel);
        controls.add(angleSlider);
        controls.add(labeledField("Enter angle:", angleField));
        controls.add(Box.createVerticalStrut(12));
        controls.add(timeLabel);
        controls.add(Box.createVerticalStrut(4));
        controls.add(rangeLabel);
        controls.add(Box.createVerticalStrut(4));
        controls.add(heightLabel);
        controls.add(Box.createVerticalStrut(12));
        controls.add(launchBtn);
        controls.add(Box.createVerticalStrut(4));
        controls.add(resetBtn);

        add(controls, BorderLayout.EAST);

        // ── SOUTH: back button ────────────────────────────────────────────────
        // FlowLayout arranges children left-to-right in a row.
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton back = new JButton("Back");
        back.addActionListener(e -> {
            animTimer.stop(); // Always stop the timer before navigating away.
            frame.showConceptSelection("Grade 11");
        });
        south.add(back);
        add(south, BorderLayout.SOUTH);

        // ── Timer setup ───────────────────────────────────────────────────────
        // Creates the timer but does NOT start it yet. It starts only when Launch is clicked.
        // Every TICK_MS milliseconds it calls tick() via the lambda.
        animTimer = new Timer(TICK_MS, e -> tick());

        // Set everything to its initial state before the user does anything.
        reset();
    }

    // ── Helper: labeledField ──────────────────────────────────────────────────

    // Builds and returns a small JPanel with a label and text field side by side.
    // FlowLayout(LEFT, 2, 0) aligns children left with 2px horizontal gap, 0px vertical gap.
    // setOpaque(false) makes the panel background transparent so the parent's color shows through.
    private JPanel labeledField(String label, JTextField field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        row.setOpaque(false);
        row.add(new JLabel(label));
        row.add(field);
        return row;
    }

    // ── toggleLaunch ─────────────────────────────────────────────────────────

    // Called when the Launch/Pause/Resume button is clicked.
    // Cycles through three states: not launched → launched (running) → paused → running again.
    private void toggleLaunch() {
        if (!launched) {
            // Convert angle from degrees to radians — Math.cos/sin require radians.
            double rad = Math.toRadians(angleDeg);
            // Split initial speed into horizontal and vertical components using trig.
            // ballVx = v0 * cos(θ),  ballVy = v0 * sin(θ)
            ballVx = v0 * Math.cos(rad);
            ballVy = v0 * Math.sin(rad);
            ballX = 0; ballY = 0;  // start at the origin
            simTime = 0;
            trail.clear();         // remove any previous arc
            launched = true;
            launchBtn.setText("Pause");
            animTimer.start();     // begin firing tick() every TICK_MS ms
        } else if (animTimer.isRunning()) {
            animTimer.stop();      // freeze the simulation
            launchBtn.setText("Resume");
        } else {
            animTimer.start();     // unfreeze
            launchBtn.setText("Pause");
        }
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    // Returns the simulation to its starting state without changing slider values.
    private void reset() {
        animTimer.stop();
        launched = false;
        simTime  = 0;
        ballX = 0; ballY = 0;
        // Pre-calculate velocity components so the angle arrow draws correctly on load.
        double rad = Math.toRadians(angleDeg);
        ballVx = v0 * Math.cos(rad);
        ballVy = v0 * Math.sin(rad);
        trail.clear();
        launchBtn.setText("Launch");
        updateLabels();
        // repaint() tells Swing to call paintComponent() on the canvas on the next frame.
        canvas.repaint();
    }

    // ── tick ──────────────────────────────────────────────────────────────────

    // Called automatically every TICK_MS milliseconds by the Timer while running.
    // Advances the physics by one small time step (dt) and redraws.
    private void tick() {
        // Convert the tick interval from milliseconds to seconds for the physics formulas.
        double dt = TICK_MS / 1000.0;  // = 0.016 seconds

        // Apply gravity: pull vertical velocity downward each tick (Euler integration).
        ballVy -= GRAVITY * dt;

        // Move the ball by its current velocity × time step.
        ballX  += ballVx * dt;  // horizontal: constant, no forces
        ballY  += ballVy * dt;  // vertical: changes because ballVy is decreasing

        simTime += dt;

        // Save the current position as a pixel coordinate for the trail.
        trail.add(new Point(toPixelX(ballX), toPixelY(ballY)));

        // Landing check: stop when the ball hits the ground.
        // simTime > 0.05 gives it a brief grace period so it doesn't stop on frame 1
        // (since the ball starts at ballY = 0, which would immediately trigger this).
        if (ballY <= 0 && simTime > 0.05) {
            ballY = 0;
            animTimer.stop();
            launched = false;
            launchBtn.setText("Launch");
        }

        updateLabels();
        canvas.repaint(); // trigger a redraw after every physics update
    }

    // ── updateLabels ──────────────────────────────────────────────────────────

    // Refreshes the three live readout JLabels with current physics values.
    // String.format("%.2f", value) formats a double to 2 decimal places.
    // Math.max(..., 0) prevents showing negative values when the ball clips below ground.
    private void updateLabels() {
        timeLabel  .setText(String.format("Time:   %.2f s", simTime));
        rangeLabel .setText(String.format("Range:  %.2f m", Math.max(ballX, 0)));
        heightLabel.setText(String.format("Height: %.2f m", Math.max(ballY, 0)));
    }

    // ── Coordinate converters ─────────────────────────────────────────────────

    // Converts a world x position (metres) to a pixel x position on the canvas.
    // Offset by 30 pixels from the left edge to leave room for the y-axis labels.
    private int toPixelX(double wx) {
        return 30 + (int)(wx * SCALE);
    }

    // Converts a world y position (metres) to a pixel y position on the canvas.
    // Swing's y axis is flipped — y=0 is at the TOP and increases downward.
    // So we subtract from groundY to flip it: higher world y = lower pixel y = higher on screen.
    private int toPixelY(double wy) {
        int groundY = canvas.getHeight() - 30;
        return groundY - (int)(wy * SCALE);
    }

    // ── SimCanvas (inner class) ───────────────────────────────────────────────

    // A private inner class that extends JPanel purely to override paintComponent.
    // Being an inner class means it can directly access all of KinematicsPanel's fields
    // (trail, ballX, ballY, angleDeg, launched, etc.) without needing them passed in.
    private class SimCanvas extends JPanel {

        @Override
        protected void paintComponent(Graphics g) {
            // Always call super first — clears the panel to its background color.
            // Skipping this causes old frames to stack up (smearing effect).
            super.paintComponent(g);

            // The Graphics object Swing provides is always actually a Graphics2D at runtime.
            // Casting gives access to advanced features: setStroke, setRenderingHint, etc.
            Graphics2D g2 = (Graphics2D) g;

            // Turn on antialiasing — smooths out jagged edges on circles and diagonal lines.
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Get the current pixel dimensions of this canvas panel.
            // These can change if the window is resized, so we read them fresh each paint call.
            int W = getWidth(), H = getHeight();

            // The ground is 30 pixels up from the bottom — leaves room for x-axis labels.
            int groundY = H - 30;

            // ── Ground line ───────────────────────────────────────────────────
            // setColor affects everything drawn after it until changed again.
            g2.setColor(Color.DARK_GRAY);
            // setStroke sets line thickness. BasicStroke(2f) = 2 pixels wide.
            g2.setStroke(new BasicStroke(2f));
            // drawLine(x1, y1, x2, y2) — draws from left edge to right edge at groundY.
            g2.drawLine(0, groundY, W, groundY);

            // ── Axis tick marks and labels ────────────────────────────────────
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(Color.GRAY);

            // step = how many pixels apart each 10-metre tick mark should be.
            int step = (int)(10 * SCALE); // 10m * 8px/m = 80px

            // X axis: walk from left to right, drawing a tick and label every 10 metres.
            for (int px = 30, m = 0; px < W; px += step, m += 10) {
                // Short vertical line downward from the ground line (the tick mark).
                g2.drawLine(px, groundY, px, groundY + 4);
                // Label below the tick. drawString x,y is the bottom-left of the text.
                g2.drawString(m + "m", px - 6, groundY + 14);
            }

            // Y axis: walk upward from the ground, drawing a tick and label every 10 metres.
            // py decreases (goes up on screen) because Swing's y is flipped.
            for (int py = groundY, m = 0; py > 10; py -= step, m += 10) {
                // Short horizontal line to the left of the y-axis origin.
                g2.drawLine(26, py, 30, py);
                // Skip the 0m label to avoid clutter at the origin.
                if (m > 0) g2.drawString(m + "", 2, py + 4);
            }

            // ── Trail ─────────────────────────────────────────────────────────
            g2.setColor(Color.LIGHT_GRAY);
            g2.setStroke(new BasicStroke(1f));

            // Draw a line segment between every consecutive pair of stored trail points.
            // This produces the smooth parabolic arc behind the ball.
            for (int i = 1; i < trail.size(); i++) {
                Point a = trail.get(i - 1), b = trail.get(i);
                g2.drawLine(a.x, a.y, b.x, b.y);
            }

            // ── Ball ──────────────────────────────────────────────────────────
            // Convert ball's world position to pixel position.
            // Math.max(ballY, 0) clamps it to the ground so it doesn't draw below groundY.
            int bx = toPixelX(ballX), by = toPixelY(Math.max(ballY, 0));

            // fillOval(x, y, width, height) — x,y is the TOP-LEFT of the bounding box.
            // Subtract half the diameter (8) to centre the circle on bx, by.
            g2.setColor(Color.BLUE);
            g2.fillOval(bx - 8, by - 8, 16, 16);
            // Draw a black outline on top of the filled circle.
            g2.setColor(Color.BLACK);
            g2.drawOval(bx - 8, by - 8, 16, 16);

            // ── Angle arrow (shown only before launch) ────────────────────────
            if (!launched) {
                g2.setColor(Color.RED);
                g2.setStroke(new BasicStroke(2f));

                // Convert the angle to radians for Math.cos / Math.sin.
                double rad = Math.toRadians(angleDeg);

                // Origin point: the ball's starting pixel position (world 0, 0).
                int ox = toPixelX(0), oy = toPixelY(0);

                // Tip of the arrow: 50 pixels away from origin in the launch direction.
                // cos(θ) gives the horizontal component, sin(θ) gives vertical.
                // ty subtracts because Swing's y is flipped — sin positive = upward on screen = smaller y.
                int tx = ox + (int)(50 * Math.cos(rad));
                int ty = oy - (int)(50 * Math.sin(rad));

                g2.drawLine(ox, oy, tx, ty);

                // Draw the angle value as a string near the arrow tip.
                g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
                g2.drawString((int)angleDeg + "°", ox + 18, oy - 6);
            }
        }
    }
}
