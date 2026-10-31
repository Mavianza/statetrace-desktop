package statetrace;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/** Panel GUI yang juga dapat diuji/dirender tanpa membuat jendela native. */
public final class DfaView extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final Color NAVY = new Color(19, 37, 63);
    private static final Color BLUE = new Color(37, 99, 235);
    private static final Color GREEN = new Color(4, 120, 87);
    private static final Color RED = new Color(180, 35, 24);
    private static final Color MUTED = new Color(82, 100, 120);
    private static final Color BACKGROUND = new Color(242, 245, 250);

    final JTextArea editor = new JTextArea();
    final JTextField input = new JTextField("1101");
    final JComboBox<String> examples = new JComboBox<>(new String[] {"Berakhiran 01", "Jumlah 1 genap"});
    final JButton runButton = button("Jalankan semua", true);
    final JButton stepButton = button("Mulai per langkah", false);
    final JButton nextButton = button("Langkah berikutnya", false);
    final JButton emptyButton = button("String kosong (ε)", false);
    final JButton validateButton = button("Validasi DFA", false);
    final JButton exportButton = button("Ekspor jejak CSV", false);
    final JLabel verdict = new JLabel("SIAP MENCOBA");
    final JLabel position = new JLabel("Jalankan string atau mulai per langkah.");
    final JTextArea feedback = new JTextArea(2, 20);
    final JTextArea summary = new JTextArea(3, 20);
    final TraceModel trace = new TraceModel();
    final JTable table = new JTable(trace);
    private Dfa.Result result;
    private int cursor;

    public DfaView() {
        super(new BorderLayout(0, 16));
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("DfaView harus dibuat di EDT.");
        setBackground(BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(18, 20, 14, 20));
        setPreferredSize(new Dimension(1100, 780));

        JPanel header = new JPanel(new GridLayout(2, 1, 0, 3));
        header.setBackground(NAVY);
        header.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        JLabel title = new JLabel("StateTrace");
        title.setFont(font(Font.BOLD, 28));
        title.setForeground(Color.WHITE);
        JLabel subtitle = new JLabel("Lab DFA  /  definisi → string → jejak transisi");
        subtitle.setFont(font(Font.PLAIN, 14));
        subtitle.setForeground(new Color(199, 217, 241));
        header.add(title);
        header.add(subtitle);
        add(header, BorderLayout.NORTH);

        JPanel definition = card(new BorderLayout(0, 12));
        JPanel definitionTop = new JPanel(new BorderLayout(0, 8));
        definitionTop.setOpaque(false);
        definitionTop.add(label("01   DEFINISI AUTOMATA", 14, true), BorderLayout.NORTH);
        examples.setFont(font(Font.PLAIN, 13));
        examples.setPreferredSize(new Dimension(250, 32));
        JButton open = button("Buka .dfa", false);
        JPanel choose = new JPanel(new BorderLayout(8, 0));
        choose.setOpaque(false);
        choose.add(examples, BorderLayout.CENTER);
        choose.add(open, BorderLayout.EAST);
        definitionTop.add(choose, BorderLayout.CENTER);
        definition.add(definitionTop, BorderLayout.NORTH);

        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        editor.setForeground(NAVY);
        editor.setBackground(new Color(248, 250, 253));
        editor.setTabSize(4);
        editor.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        definition.add(new JScrollPane(editor), BorderLayout.CENTER);
        JButton save = button("Simpan .dfa", false);
        JPanel definitionActions = new JPanel(new GridLayout(1, 2, 8, 0));
        definitionActions.setOpaque(false);
        definitionActions.add(validateButton);
        definitionActions.add(save);
        definition.add(definitionActions, BorderLayout.SOUTH);

        JPanel simulation = card(new BorderLayout(0, 12));
        JPanel control = new JPanel(new BorderLayout(0, 10));
        control.setOpaque(false);
        control.add(label("02   COBA STRING", 14, true), BorderLayout.NORTH);
        JPanel fields = new JPanel(new BorderLayout(0, 10));
        fields.setOpaque(false);
        input.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 22));
        input.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(190, 204, 222)), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        fields.add(input, BorderLayout.NORTH);
        JPanel actions = new JPanel(new GridLayout(2, 2, 8, 8));
        actions.setOpaque(false);
        actions.add(runButton);
        actions.add(stepButton);
        actions.add(nextButton);
        actions.add(emptyButton);
        fields.add(actions, BorderLayout.CENTER);
        control.add(fields, BorderLayout.CENTER);
        summary.setEditable(false);
        summary.setLineWrap(true);
        summary.setWrapStyleWord(true);
        summary.setFont(font(Font.PLAIN, 12));
        summary.setForeground(MUTED);
        summary.setOpaque(false);
        control.add(summary, BorderLayout.SOUTH);
        simulation.add(control, BorderLayout.NORTH);

        JPanel traceArea = new JPanel(new BorderLayout(0, 10));
        traceArea.setOpaque(false);
        JPanel resultHeader = new JPanel(new GridLayout(2, 1, 0, 3));
        resultHeader.setOpaque(false);
        verdict.setFont(font(Font.BOLD, 25));
        verdict.setForeground(MUTED);
        position.setFont(font(Font.PLAIN, 13));
        position.setForeground(MUTED);
        resultHeader.add(verdict);
        resultHeader.add(position);
        traceArea.add(resultHeader, BorderLayout.NORTH);
        table.setFont(font(Font.PLAIN, 14));
        table.setRowHeight(29);
        table.setFillsViewportHeight(true);
        table.setGridColor(new Color(224, 231, 239));
        table.setSelectionBackground(new Color(221, 235, 255));
        table.setSelectionForeground(NAVY);
        table.getTableHeader().setFont(font(Font.BOLD, 12));
        DefaultTableCellRenderer centered = new DefaultTableCellRenderer();
        centered.setHorizontalAlignment(SwingConstants.CENTER);
        table.setDefaultRenderer(Object.class, centered);
        table.setDefaultRenderer(Integer.class, centered);
        table.getColumnModel().getColumn(0).setMaxWidth(85);
        JScrollPane traceScroll = new JScrollPane(table);
        traceScroll.setColumnHeaderView(table.getTableHeader());
        traceArea.add(traceScroll, BorderLayout.CENTER);
        simulation.add(traceArea, BorderLayout.CENTER);

        JPanel traceActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        traceActions.setOpaque(false);
        traceActions.add(exportButton);
        simulation.add(traceActions, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, definition, simulation);
        split.setBorder(null);
        split.setOpaque(false);
        split.setDividerSize(12);
        split.setResizeWeight(0.42);
        split.setDividerLocation(430);
        definition.setMinimumSize(new Dimension(330, 200));
        simulation.setMinimumSize(new Dimension(450, 200));
        add(split, BorderLayout.CENTER);

        feedback.setEditable(false);
        feedback.setLineWrap(true);
        feedback.setWrapStyleWord(true);
        feedback.setFont(font(Font.PLAIN, 12));
        feedback.setBackground(new Color(228, 235, 245));
        feedback.setForeground(MUTED);
        feedback.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
        add(feedback, BorderLayout.SOUTH);

        DocumentListener changes = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { invalidateRun(); }
            @Override public void removeUpdate(DocumentEvent event) { invalidateRun(); }
            @Override public void changedUpdate(DocumentEvent event) { invalidateRun(); }
        };
        editor.getDocument().addDocumentListener(changes);
        input.getDocument().addDocumentListener(changes);
        examples.addActionListener(event -> editor.setText(examples.getSelectedIndex() == 0 ? Samples.SUFFIX_01 : Samples.EVEN_ONES));
        runButton.addActionListener(event -> runAll());
        input.addActionListener(event -> runAll());
        stepButton.addActionListener(event -> beginSteps());
        nextButton.addActionListener(event -> advance());
        emptyButton.addActionListener(event -> { input.setText(""); runAll(); });
        validateButton.addActionListener(event -> validateDefinition());
        open.addActionListener(event -> chooseOpen());
        save.addActionListener(event -> chooseSave(false));
        exportButton.addActionListener(event -> chooseSave(true));
        editor.setText(Samples.SUFFIX_01);
        editor.setCaretPosition(0);
        validateDefinition();
        note("Siap. String kosong diuji lewat tombol ε. Langkah 0 adalah state awal, bukan transisi ε.", false);
    }

    private static Font font(int style, int size) { return new Font(Font.SANS_SERIF, style, size); }
    private static JLabel label(String text, int size, boolean bold) {
        JLabel label = new JLabel(text);
        label.setFont(font(bold ? Font.BOLD : Font.PLAIN, size));
        label.setForeground(NAVY);
        return label;
    }
    private static JPanel card(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(217, 225, 235)), BorderFactory.createEmptyBorder(16, 16, 16, 16)));
        return panel;
    }
    private static JButton button(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(font(Font.BOLD, 12));
        button.setForeground(primary ? Color.WHITE : NAVY);
        button.setBackground(primary ? BLUE : new Color(233, 239, 247));
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        return button;
    }
    private void note(String text, boolean error) {
        feedback.setForeground(error ? RED : MUTED);
        feedback.setText(text);
    }

    private void invalidateRun() {
        result = null;
        cursor = 0;
        trace.setRowCount(0);
        nextButton.setEnabled(false);
        exportButton.setEnabled(false);
        verdict.setText("BELUM DIJALANKAN");
        verdict.setForeground(MUTED);
        position.setText("Masukan berubah; jalankan ulang untuk melihat hasil.");
        summary.setText("");
        note("Definisi/input diubah. Jejak sebelumnya telah dibersihkan.", false);
    }

    private Dfa compile() {
        Dfa dfa = Dfa.parse(editor.getText());
        summary.setText("DFA: " + dfa.name() + "\nState awal: " + dfa.start() + "  |  State penerima: " + (dfa.accepting().isEmpty() ? "tidak ada" : String.join(", ", dfa.accepting()))
                + "\n" + dfa.states().size() + " state  ·  Alfabet: " + String.join(", ", dfa.alphabet()));
        return dfa;
    }

    void validateDefinition() {
        try {
            Dfa dfa = compile();
            note("DFA valid dan transisi lengkap." + (dfa.unreachable().isEmpty() ? " Semua state terjangkau." : " State tidak terjangkau: " + String.join(", ", dfa.unreachable()) + "."), false);
        } catch (IllegalArgumentException error) { showInvalid(error); }
    }

    private boolean prepare() {
        invalidateRun();
        try {
            result = compile().simulate(input.getText());
            trace.addRow(new Object[] {0, "—", "—", result.start()});
            cursor = 0;
            refresh();
            return true;
        } catch (IllegalArgumentException error) {
            showInvalid(error);
            return false;
        }
    }

    void runAll() {
        if (!prepare()) return;
        while (cursor < result.steps().size()) appendNext();
        refresh();
    }
    void beginSteps() { prepare(); }
    void advance() {
        if (result == null || cursor >= result.steps().size()) return;
        appendNext();
        refresh();
    }
    private void appendNext() {
        Dfa.Step step = result.steps().get(cursor++);
        trace.addRow(new Object[] {step.number(), step.from(), step.symbol(), step.to()});
    }
    private void refresh() {
        boolean done = cursor == result.steps().size();
        String state = cursor == 0 ? result.start() : result.steps().get(cursor - 1).to();
        verdict.setText(done ? (result.accepted() ? "DITERIMA" : "DITOLAK") : "SEDANG BERJALAN");
        verdict.setForeground(done ? (result.accepted() ? GREEN : RED) : BLUE);
        position.setText("State aktif: " + state + "  |  Simbol dibaca: " + cursor + "/" + result.steps().size());
        nextButton.setEnabled(!done);
        exportButton.setEnabled(done);
        if (trace.getRowCount() > 0) {
            int row = trace.getRowCount() - 1;
            table.setRowSelectionInterval(row, row);
            table.scrollRectToVisible(table.getCellRect(row, 0, true));
        }
        note(done ? "Selesai. " + (result.accepted() ? "State akhir termasuk state penerima." : "State akhir bukan state penerima.") + " Jejak siap diekspor sebagai CSV."
                : "Klik Langkah berikutnya untuk membaca satu simbol. Hasil akhir belum ditentukan.", false);
    }
    private void showInvalid(IllegalArgumentException error) {
        result = null;
        trace.setRowCount(0);
        nextButton.setEnabled(false);
        exportButton.setEnabled(false);
        verdict.setText("MASUKAN TIDAK VALID");
        verdict.setForeground(RED);
        position.setText("Perbaiki definisi DFA atau string input.");
        note(error.getMessage(), true);
    }

    void loadFrom(Path file) throws IOException {
        String source = FilesIO.readDefinition(file);
        Dfa.parse(source);
        editor.setText(source);
        editor.setCaretPosition(0);
        validateDefinition();
    }
    void saveTo(Path file) throws IOException { FilesIO.saveDefinition(file, editor.getText()); }
    void exportTo(Path file) throws IOException {
        if (result == null || cursor != result.steps().size()) throw new IOException("Jalankan simulasi sampai selesai sebelum mengekspor.");
        FilesIO.writeNew(file, FilesIO.csv(result));
    }
    private void chooseOpen() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Definisi DFA UTF-8", "dfa", "txt"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try { loadFrom(chooser.getSelectedFile().toPath()); }
        catch (IOException | IllegalArgumentException error) { note("Buka gagal: " + error.getMessage(), true); }
    }
    private void chooseSave(boolean csv) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(csv ? "jejak.csv" : "automata.dfa"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Path file = chooser.getSelectedFile().toPath();
            if (csv) exportTo(file); else saveTo(file);
            note("Tersimpan: " + file, false);
        } catch (IOException | IllegalArgumentException error) {
            note("Simpan gagal (file yang sudah ada tidak ditimpa): " + error.getMessage(), true);
        }
    }

    static final class TraceModel extends DefaultTableModel {
        private static final long serialVersionUID = 1L;
        TraceModel() { super(new Object[] {"Langkah", "Dari", "Simbol", "Ke"}, 0); }
        @Override public boolean isCellEditable(int row, int column) { return false; }
        @Override public Class<?> getColumnClass(int column) { return column == 0 ? Integer.class : String.class; }
    }
}
