package statetrace;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public final class Main {
    private Main() { }
    private static final String HELP = """
            StateTrace 1.0.0 — simulator DFA dengan GUI dan jejak transisi

            java -jar dist/statetrace.jar                        Buka GUI
            java -jar dist/statetrace.jar check model.dfa         Validasi DFA
            java -jar dist/statetrace.jar run model.dfa STRING    Simulasi
            java -jar dist/statetrace.jar run model.dfa STRING --csv jejak.csv
            java -jar dist/statetrace.jar render pratinjau.png    Render panel demo

            STRING kosong ditulis sebagai "". Batas input: 4096 simbol Unicode.
            Berkas output yang sudah ada tidak ditimpa.
            Exit: 0 diterima/valid, 1 ditolak, 2 masukan/IO/lingkungan bermasalah.
            """;

    public static void main(String[] args) {
        int code = execute(args, System.out, System.err);
        if (code != 0) System.exit(code);
    }

    static int execute(String[] args, PrintStream out, PrintStream err) {
        try {
            if (args.length == 0) {
                if (GraphicsEnvironment.isHeadless()) throw new IllegalArgumentException("Layar desktop tidak tersedia. Gunakan perintah check/run untuk mode CLI.");
                SwingUtilities.invokeLater(() -> {
                    JFrame frame = new JFrame("StateTrace — Lab DFA");
                    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    frame.setContentPane(new DfaView());
                    frame.pack();
                    frame.setMinimumSize(new Dimension(900, 680));
                    frame.setLocationRelativeTo(null);
                    frame.setVisible(true);
                });
                return 0;
            }
            if (args.length == 1 && args[0].equals("--help")) { out.print(HELP); return 0; }
            if (args.length == 1 && args[0].equals("--version")) { out.println("StateTrace 1.0.0"); return 0; }
            if (args.length == 2 && args[0].equals("render")) {
                Render.write(Path.of(args[1]));
                out.println("Pratinjau panel Swing tersimpan: " + args[1]);
                return 0;
            }
            if (args.length == 2 && args[0].equals("check")) {
                Dfa dfa = Dfa.parse(FilesIO.readDefinition(Path.of(args[1])));
                out.printf("DFA valid: %s; %d state; %d simbol; %d transisi.%n", dfa.name(), dfa.states().size(), dfa.alphabet().size(), dfa.states().size() * dfa.alphabet().size());
                if (!dfa.unreachable().isEmpty()) out.println("State tidak terjangkau: " + String.join(", ", dfa.unreachable()));
                return 0;
            }
            if ((args.length == 3 || (args.length == 5 && args[3].equals("--csv"))) && args[0].equals("run")) {
                Dfa.Result result = Dfa.parse(FilesIO.readDefinition(Path.of(args[1]))).simulate(args[2]);
                if (args.length == 5) FilesIO.writeNew(Path.of(args[4]), FilesIO.csv(result));
                out.println("0: " + result.start() + " (state awal)");
                for (Dfa.Step step : result.steps()) out.printf("%d: %s --[%s]--> %s%n", step.number(), step.from(), step.symbol(), step.to());
                out.println((result.accepted() ? "DITERIMA" : "DITOLAK") + "; state akhir: " + result.end());
                return result.accepted() ? 0 : 1;
            }
            throw new IllegalArgumentException("Argumen tidak sesuai. Gunakan --help.");
        } catch (IllegalArgumentException | IOException error) {
            err.println("Gagal: " + error.getMessage());
            return 2;
        }
    }
}
