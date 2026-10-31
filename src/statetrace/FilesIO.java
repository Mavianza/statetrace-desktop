package statetrace;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class FilesIO {
    private FilesIO() { }

    public static String readDefinition(Path file) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("Definisi harus berupa berkas biasa.");
        byte[] bytes;
        try (InputStream stream = Files.newInputStream(file)) {
            bytes = stream.readNBytes(Dfa.MAX_BYTES + 1);
        }
        if (bytes.length > Dfa.MAX_BYTES) throw new IOException("Berkas melebihi 65536 byte.");
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    public static void saveDefinition(Path file, String definition) throws IOException {
        Dfa.parse(definition);
        writeNew(file, definition);
    }

    public static String csv(Dfa.Result result) {
        StringBuilder text = new StringBuilder("langkah,dari,simbol,ke\n");
        text.append("0,\"\",\"\",").append(quote(result.start())).append('\n');
        for (Dfa.Step step : result.steps()) {
            text.append(step.number()).append(',').append(quote(step.from())).append(',')
                    .append(quote(step.symbol())).append(',').append(quote(step.to())).append('\n');
        }
        return text.toString();
    }

    private static String quote(String text) { return "\"" + text.replace("\"", "\"\"") + "\""; }

    public static void writeNew(Path file, String text) throws IOException {
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException error) {
            throw new IOException("Tujuan sudah ada; pilih nama file baru: " + file, error);
        }
    }
}
