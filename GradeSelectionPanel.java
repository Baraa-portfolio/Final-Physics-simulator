import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class GradeSelectionPanel extends JPanel {
    public GradeSelectionPanel(MainFrame frame) {
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 0;
        gbc.gridy = 0;

        JLabel title = new JLabel("Select Grade");
        title.setFont(title.getFont().deriveFont(24f));
        add(title, gbc);

        gbc.gridy++;
        JButton grade11 = new JButton("Grade 11");
        grade11.addActionListener(e -> frame.showConceptSelection("Grade 11"));
        add(grade11, gbc);

        gbc.gridy++;
        JButton grade12 = new JButton("Grade 12");
        grade12.addActionListener(e -> frame.showConceptSelection("Grade 12"));
        add(grade12, gbc);
    }
}
