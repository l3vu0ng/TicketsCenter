package vn.ticketscenter.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.ticketscenter.event.integration.storage.LocalImageStorage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalImageStorageTest {
    @TempDir Path directory;

    @Test
    void acceptsDecodedPngAndRejectsFakeOrOversizedImages() throws Exception {
        LocalImageStorage storage = new LocalImageStorage(directory, 1024, 100);
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);

        String url = storage.store(new ByteArrayInputStream(bytes.toByteArray()), "photo.jpg");

        assertTrue(url.matches("/api/event-images/[0-9a-f-]+\\.png"));
        assertThrows(IllegalArgumentException.class,
                () -> storage.store(new ByteArrayInputStream("<svg/>".getBytes()), "cover.png"));
        assertThrows(IllegalArgumentException.class,
                () -> new LocalImageStorage(directory, 4, 100)
                        .store(new ByteArrayInputStream(bytes.toByteArray()), "cover.png"));
    }
}
