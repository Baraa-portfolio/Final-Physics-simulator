import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class ConceptSelectionPanel extends JPanel {
    private final String grade;

    public ConceptSelectionPanel(MainFrame frame, String gradeLabel) {
        this.grade = gradeLabel;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel header = new JLabel(grade + " - Select a Concept");
        header.setFont(header.getFont().deriveFont(20f));
        add(header, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 2, 10, 10));

        if ("Grade 11".equals(grade)) {
            JButton kinematics = new JButton("Kinematics");
            kinematics.addActionListener(e -> frame.showKinematics());
            grid.add(kinematics);

            JButton forces = new JButton("Forces");
            forces.addActionListener(e -> frame.showForces());
            grid.add(forces);

            JButton energyWork = new JButton("Energy & Work");
            energyWork.addActionListener(e -> frame.showEnergyWork());
            grid.add(energyWork);
        } else if ("Grade 12".equals(grade)) {
            JButton circular = new JButton("Circular Motion");
            circular.addActionListener(e -> frame.showCircularMotion());
            grid.add(circular);

            JButton collisions = new JButton("Collisions & Momentum");
            collisions.addActionListener(e -> frame.showCollisionsMomentum());
            grid.add(collisions);

            JButton fields = new JButton("Magnetic / Electric Fields");
            fields.addActionListener(e -> frame.showFields());
            grid.add(fields);
        }

        add(grid, BorderLayout.CENTER);

        JButton back = new JButton("Back");
        back.addActionListener(e -> frame.showGradeSelection());
        JPanel south = new JPanel(new BorderLayout());
        south.add(back, BorderLayout.WEST);
        add(south, BorderLayout.SOUTH);
    }
}
