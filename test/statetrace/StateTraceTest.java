package statetrace;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

public final class StateTraceTest {
    private StateTraceTest() { }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        check("DFA suffix menerima 1101 dan berakhir q2", () -> {
            Dfa.Result r = Dfa.parse(Samples.SUFFIX_01).simulate("1101");
            equal(true, r.accepted()); equal("q2", r.end()); equal(4, r.steps().size());
            equal(new Dfa.Step(3, "q0", "0", "q1"), r.steps().get(2));
        });
        check("DFA suffix menolak 1100", () -> equal(false, Dfa.parse(Samples.SUFFIX_01).simulate("1100").accepted()));
        check("String kosong memakai state awal", () -> {
            Dfa.Result r = Dfa.parse(Samples.EVEN_ONES).simulate("");
            equal(true, r.accepted()); equal("genap", r.end()); equal(0, r.steps().size());
            equal(false, Dfa.parse(Samples.SUFFIX_01).simulate("").accepted());
        });
        check("4094 string biner cocok dengan oracle dua bahasa", () -> {
            Dfa suffix = Dfa.parse(Samples.SUFFIX_01);
            Dfa even = Dfa.parse(Samples.EVEN_ONES);
            int count = 0;
            for (int length = 0; length <= 10; length++) {
                for (int number = 0; number < (1 << length); number++) {
                    String input = length == 0 ? "" : String.format("%" + length + "s", Integer.toBinaryString(number)).replace(' ', '0');
                    equal(input.endsWith("01"), suffix.simulate(input).accepted());
                    equal(input.chars().filter(c -> c == '1').count() % 2 == 0, even.simulate(input).accepted());
                    count += 2;
                }
            }
            equal(4094, count);
        });
        check("Simbol Unicode astral dihitung satu langkah", () -> {
            Dfa unicode = Dfa.parse("name=Emoji\nstates=a,b\nalphabet=😀\nstart=a\naccept=b\na,😀=b\nb,😀=a\n");
            equal(true, unicode.simulate("😀").accepted());
            equal(2, unicode.simulate("😀😀").steps().size());
            equal(false, unicode.simulate("😀😀").accepted());
        });
        check("Simbol asing menyertakan posisi", () -> rejects(() -> Dfa.parse(Samples.SUFFIX_01).simulate("10x"), "posisi 3"));
        check("Batas input 4096 diterapkan", () -> {
            Dfa dfa = Dfa.parse(Samples.EVEN_ONES);
            equal(4096, dfa.simulate("0".repeat(4096)).steps().size());
            rejects(() -> dfa.simulate("0".repeat(4097)), "4096");
        });
        check("Himpunan state penerima boleh kosong", () -> equal(false, Dfa.parse(Samples.EVEN_ONES.replace("accept=genap", "accept=")).simulate("").accepted()));
        check("State tak terjangkau dilaporkan tanpa menolak DFA", () -> {
            String source = Samples.EVEN_ONES.replace("states=genap,ganjil", "states=genap,ganjil,asing") + "asing,0=asing\nasing,1=asing\n";
            equal(List.of("asing"), List.copyOf(Dfa.parse(source).unreachable()));
        });
        check("Koleksi model dan hasil immutable", () -> {
            Dfa dfa = Dfa.parse(Samples.EVEN_ONES);
            unsupported(() -> dfa.states().add("new"));
            unsupported(() -> dfa.alphabet().add("2"));
            unsupported(() -> dfa.accepting().add("ganjil"));
            unsupported(() -> dfa.simulate("1").steps().clear());
        });
        check("Komentar, whitespace, BOM dan CRLF didukung", () -> {
            String source = "\ufeff\r\n# catatan\r\n" + Samples.SUFFIX_01.replace("=", " = ").replace("\n", "\r\n");
            equal(true, Dfa.parse(source).simulate("01").accepted());
        });
        check("Semua kolom wajib diperiksa", () -> {
            for (String field : List.of("name", "states", "alphabet", "start", "accept")) {
                String source = Samples.SUFFIX_01.lines().filter(line -> !line.startsWith(field + "=")).reduce("", (all, line) -> all + line + "\n");
                rejects(() -> Dfa.parse(source), "Kolom wajib");
            }
        });
        check("Kolom ganda dan tak dikenal ditolak", () -> {
            rejects(() -> Dfa.parse(Samples.SUFFIX_01 + "name=Lain\n"), "Kolom ganda");
            rejects(() -> Dfa.parse(Samples.SUFFIX_01 + "accpet=q2\n"), "tidak dikenal");
        });
        check("Nama state tidak sah, duplikat dan item kosong ditolak", () -> {
            for (String states : List.of("q0,q0,q2", "q0,,q2", "q0,1bad,q2", "q0,bad state,q2")) {
                rejects(() -> Dfa.parse(Samples.SUFFIX_01.replace("states=q0,q1,q2", "states=" + states)), null);
            }
        });
        check("Alfabet multikarakter, kosong, duplikat dan reserved ditolak", () -> {
            for (String alphabet : List.of("01,1", "", "0,0", "0,#", "0,=", "0, ", "0,\u0001")) {
                rejects(() -> Dfa.parse(Samples.SUFFIX_01.replace("alphabet=0,1", "alphabet=" + alphabet)), null);
            }
        });
        check("State awal dan penerima wajib terdaftar", () -> {
            rejects(() -> Dfa.parse(Samples.SUFFIX_01.replace("start=q0", "start=unknown")), "awal");
            rejects(() -> Dfa.parse(Samples.SUFFIX_01.replace("accept=q2", "accept=unknown")), "penerima");
        });
        check("Transisi ganda dan transisi hilang ditolak", () -> {
            rejects(() -> Dfa.parse(Samples.SUFFIX_01 + "q0,0=q2\n"), "ganda");
            rejects(() -> Dfa.parse(Samples.SUFFIX_01.replace("q2,1=q0\n", "")), "belum lengkap");
        });
        check("Asal, tujuan dan simbol transisi tidak terdaftar ditolak", () -> {
            rejects(() -> Dfa.parse(Samples.SUFFIX_01.replace("q0,0=q1", "unknown,0=q1")), "tidak terdaftar");
            rejects(() -> Dfa.parse(Samples.SUFFIX_01.replace("q0,0=q1", "q0,0=unknown")), "tidak terdaftar");
            rejects(() -> Dfa.parse(Samples.SUFFIX_01.replace("q0,0=q1", "q0,x=q1")), "Simbol transisi");
        });
        check("Format baris rusak dan NUL ditolak", () -> {
            for (String extra : List.of("no-equals", "name=a=b", "q0,0,1=q1", "\0")) rejects(() -> Dfa.parse(Samples.SUFFIX_01 + extra), null);
        });
        check("Batas ukuran definisi dan jumlah state/alfabet", () -> {
            rejects(() -> Dfa.parse("x".repeat(Dfa.MAX_BYTES + 1)), "65536");
            String states = String.join(",", java.util.stream.IntStream.range(0, 51).mapToObj(n -> "q" + n).toList());
            rejects(() -> Dfa.parse(Samples.SUFFIX_01.replace("states=q0,q1,q2", "states=" + states)), "1–50");
            rejects(() -> Dfa.parse(Samples.SUFFIX_01.replace("alphabet=0,1", "alphabet=a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q")), "1–16");
        });
        check("CSV mencakup state awal dan seluruh transisi", () -> {
            String csv = FilesIO.csv(Dfa.parse(Samples.SUFFIX_01).simulate("01"));
            equal(4L, csv.lines().count());
            require(csv.contains("0,\"\",\"\",\"q0\""));
            require(csv.endsWith("2,\"q1\",\"1\",\"q2\"\n"));
        });
        check("CSV meng-escape simbol tanda kutip", () -> {
            Dfa dfa = Dfa.parse("name=Quote\nstates=q\nalphabet=\"\nstart=q\naccept=q\nq,\"=q\n");
            require(FilesIO.csv(dfa.simulate("\"")).contains("1,\"q\",\"\"\"\",\"q\""));
        });
        Path temp = Files.createTempDirectory("statetrace-test-");
        try {
            check("IO UTF-8 round-trip dan berkas lama tidak ditimpa", () -> {
                Path file = temp.resolve("saved.dfa");
                FilesIO.saveDefinition(file, Samples.SUFFIX_01);
                equal(Samples.SUFFIX_01, FilesIO.readDefinition(file));
                ioReject(() -> FilesIO.saveDefinition(file, Samples.EVEN_ONES));
                equal(Samples.SUFFIX_01, FilesIO.readDefinition(file));
            });
            check("IO menolak UTF-8 rusak, file hilang dan terlalu besar", () -> {
                Path file = temp.resolve("bad.dfa");
                Files.write(file, new byte[] {(byte) 0xff});
                ioReject(() -> FilesIO.readDefinition(file));
                Files.write(file, new byte[Dfa.MAX_BYTES + 1]);
                ioReject(() -> FilesIO.readDefinition(file));
                ioReject(() -> FilesIO.readDefinition(temp.resolve("missing.dfa")));
            });
            check("CLI menerima, menolak, mengekspor dan menolak overwrite", () -> {
                Path file = temp.resolve("cli.dfa");
                FilesIO.saveDefinition(file, Samples.SUFFIX_01);
                Path csv = temp.resolve("cli.csv");
                equal(0, execute("run", file.toString(), "1101", "--csv", csv.toString()));
                equal(1, execute("run", file.toString(), "1100"));
                equal(2, execute("run", file.toString(), "01", "--csv", csv.toString()));
                equal(2, execute("run", file.toString(), "x"));
                equal(0, execute("check", file.toString()));
            });
            check("CLI help, versi, argumen salah dan mode tanpa layar", () -> {
                equal(0, execute("--help")); equal(0, execute("--version"));
                equal(2, execute("wrong")); equal(2, execute("run", "missing"));
                equal(2, execute());
            });
            check("Panel Swing menjalankan string hingga diterima", () -> edt(() -> {
                DfaView view = new DfaView();
                view.runButton.doClick();
                equal("DITERIMA", view.verdict.getText()); equal(5, view.trace.getRowCount());
                equal("q2", view.trace.getValueAt(4, 3));
                require(view.exportButton.isEnabled()); require(!view.nextButton.isEnabled());
                require(!view.table.isCellEditable(0, 0));
            }));
            check("Panel mode langkah tidak membocorkan hasil sebelum selesai", () -> edt(() -> {
                DfaView view = new DfaView();
                view.input.setText("01"); view.stepButton.doClick();
                equal("SEDANG BERJALAN", view.verdict.getText()); equal(1, view.trace.getRowCount());
                require(view.nextButton.isEnabled()); require(!view.exportButton.isEnabled());
                view.nextButton.doClick(); equal("SEDANG BERJALAN", view.verdict.getText());
                view.nextButton.doClick(); equal("DITERIMA", view.verdict.getText());
                equal(3, view.trace.getRowCount()); require(view.exportButton.isEnabled());
            }));
            check("Panel perubahan input membatalkan hasil lama", () -> edt(() -> {
                DfaView view = new DfaView(); view.runButton.doClick(); view.input.setText("1");
                equal(0, view.trace.getRowCount()); require(!view.exportButton.isEnabled());
                view.runButton.doClick(); equal("DITOLAK", view.verdict.getText());
            }));
            check("Panel perubahan definisi membatalkan mode langkah", () -> edt(() -> {
                DfaView view = new DfaView(); view.stepButton.doClick(); view.editor.append("# edited\n");
                require(!view.nextButton.isEnabled()); equal(0, view.trace.getRowCount());
            }));
            check("Panel menangani definisi salah dan simbol asing tanpa crash", () -> edt(() -> {
                DfaView view = new DfaView(); view.input.setText("x"); view.runButton.doClick();
                equal("MASUKAN TIDAK VALID", view.verdict.getText()); require(view.feedback.getText().contains("alfabet"));
                view.editor.setText("broken"); view.validateButton.doClick();
                equal("MASUKAN TIDAK VALID", view.verdict.getText()); require(!view.exportButton.isEnabled());
            }));
            check("Panel ganti contoh dan string kosong bekerja", () -> edt(() -> {
                DfaView view = new DfaView(); view.examples.setSelectedIndex(1); view.emptyButton.doClick();
                equal("DITERIMA", view.verdict.getText()); equal(1, view.trace.getRowCount());
                require(view.position.getText().contains("0/0"));
            }));
            check("Panel simpan/muat/ekspor dan blokir ekspor hasil parsial", () -> edt(() -> {
                DfaView view = new DfaView();
                Path file = temp.resolve("panel.dfa"); view.saveTo(file); view.loadFrom(file);
                view.stepButton.doClick(); ioReject(() -> view.exportTo(temp.resolve("partial.csv")));
                view.runButton.doClick(); Path csv = temp.resolve("panel.csv"); view.exportTo(csv);
                require(Files.readString(csv).contains("langkah,dari,simbol,ke"));
                ioReject(() -> view.exportTo(csv));
            }));
            check("Renderer menghasilkan panel PNG 1100×780", () -> {
                Path png = temp.resolve("panel.png"); Render.write(png);
                BufferedImage image = ImageIO.read(png.toFile());
                equal(1100, image.getWidth()); equal(780, image.getHeight());
                require(image.getRGB(30, 30) != image.getRGB(500, 500));
                ioReject(() -> Render.write(png));
            });
        } finally {
            try (Stream<Path> paths = Files.walk(temp)) {
                for (Path file : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
        System.out.println("Hasil: " + passed + " lulus, " + failed + " gagal.");
        if (failed != 0) System.exit(1);
    }

    private static int execute(String... args) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            return Main.execute(args, output, output);
        }
    }
    private static void check(String name, Checked action) {
        try { action.run(); passed++; System.out.println("OK " + name); }
        catch (Throwable error) { failed++; System.err.println("GAGAL " + name + ": " + error); error.printStackTrace(); }
    }
    private static void require(boolean value) { if (!value) throw new AssertionError("Syarat tidak terpenuhi."); }
    private static void equal(Object expected, Object actual) { if (!expected.equals(actual)) throw new AssertionError("Diharapkan " + expected + ", didapat " + actual); }
    private static void rejects(Checked action, String contains) throws Exception {
        try { action.run(); } catch (IllegalArgumentException error) {
            if (contains != null) require(error.getMessage().contains(contains)); return;
        }
        throw new AssertionError("Seharusnya IllegalArgumentException.");
    }
    private static void ioReject(Checked action) throws Exception {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Seharusnya IOException.");
    }
    private static void unsupported(Checked action) throws Exception {
        try { action.run(); } catch (UnsupportedOperationException expected) { return; }
        throw new AssertionError("Seharusnya immutable.");
    }
    private static void edt(Checked action) throws Exception {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { action.run(); } catch (Throwable error) { thrown.set(error); }
        });
        if (thrown.get() != null) throw new AssertionError(thrown.get());
    }
}
