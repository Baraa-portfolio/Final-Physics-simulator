import java.awt.BorderLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class MainFrame extends JFrame {
    // Central place to swap panels
    public MainFrame() {
        setTitle("Physics Simulator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Start with grade selection
        setContentPanel(new GradeSelectionPanel(this));
    }

    public void setContentPanel(JPanel panel) {
        getContentPane().removeAll();
        getContentPane().add(panel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    // Helpers to navigate
    public void showGradeSelection() {
        setContentPanel(new GradeSelectionPanel(this));
    }

    public void showConceptSelection(String gradeLabel) {
        setContentPanel(new ConceptSelectionPanel(this, gradeLabel));
    }

    // Route to concept panels
    public void showKinematics() {
        setContentPanel(new KinematicsPanel(this));
    }

    public void showForces() {
        setContentPanel(new ForcesPanel(this));
    }

    public void showEnergyWork() {
        setContentPanel(new EnergyWorkPanel(this));
    }

    public void showCircularMotion() {
        setContentPanel(new CircularMotionPanel(this));
    }

    public void showCollisionsMomentum() {
        setContentPanel(new CollisionsMomentumPanel(this));
    }

    public void showFields() {
        setContentPanel(new FieldsPanel(this));
    }
}
