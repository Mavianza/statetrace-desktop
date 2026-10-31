package statetrace;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Pratinjau panel Swing nyata; tidak membuat/memotret jendela native. */
public final class Render {
    private Render() { }

    public static void write(Path destination) throws IOException {
        BufferedImage[] image = new BufferedImage[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                DfaView view = new DfaView();
                view.runAll();
                view.setSize(1100, 780);
                layout(view);
                layout(view);
                image[0] = new BufferedImage(1100, 780, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image[0].createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    view.printAll(graphics);
                } finally { graphics.dispose(); }
            });
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Rendering dibatalkan.", error);
        } catch (InvocationTargetException error) {
            throw new IOException("Rendering panel gagal: " + error.getCause().getMessage(), error);
        }
        try (OutputStream stream = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            if (!ImageIO.write(image[0], "png", stream)) throw new IOException("Encoder PNG tidak tersedia.");
        }
    }

    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container container) layout(container);
    }
}
