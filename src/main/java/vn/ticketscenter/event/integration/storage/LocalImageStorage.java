package vn.ticketscenter.event.integration.storage;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class LocalImageStorage implements ImageStorage {
    public static final long DEFAULT_MAX_BYTES = 5L * 1024 * 1024;
    public static final long DEFAULT_MAX_PIXELS = 12_000_000;
    private static final String URL_PREFIX = "/api/event-images/";
    private static final Set<String> FORMATS = Set.of("png", "jpeg");

    private final Path directory;
    private final long maxBytes;
    private final long maxPixels;

    public LocalImageStorage(Path directory) {
        this(directory, DEFAULT_MAX_BYTES, DEFAULT_MAX_PIXELS);
    }

    public LocalImageStorage(Path directory, long maxBytes, long maxPixels) {
        this.directory = directory.toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        this.maxPixels = maxPixels;
    }

    @Override
    public String store(InputStream content, String submittedName) throws IOException {
        byte[] bytes = readBounded(content);
        ImageInfo image = inspect(bytes);
        Files.createDirectories(directory);
        String key = UUID.randomUUID() + "." + image.extension();
        Files.write(directory.resolve(key), bytes, StandardOpenOption.CREATE_NEW);
        return URL_PREFIX + key;
    }

    @Override
    public void delete(String publicUrl) throws IOException {
        if (publicUrl == null || !publicUrl.startsWith(URL_PREFIX)) return;
        String key = safeKey(publicUrl.substring(URL_PREFIX.length()));
        Files.deleteIfExists(directory.resolve(key));
    }

    @Override
    public StoredImage open(String key) throws IOException {
        String safe = safeKey(key);
        Path file = directory.resolve(safe);
        String type = safe.endsWith(".png") ? "image/png" : "image/jpeg";
        return new StoredImage(Files.newInputStream(file), type, Files.size(file));
    }

    private byte[] readBounded(InputStream content) throws IOException {
        if (content == null) throw new IllegalArgumentException("cover image is required");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        for (int count; (count = content.read(buffer)) >= 0; ) {
            total += count;
            if (total > maxBytes) throw new IllegalArgumentException("cover image exceeds size limit");
            output.write(buffer, 0, count);
        }
        if (total == 0) throw new IllegalArgumentException("cover image is empty");
        return output.toByteArray();
    }

    private ImageInfo inspect(byte[] bytes) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (stream == null) throw new IllegalArgumentException("cover image format is invalid");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException("cover image format is invalid");
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!FORMATS.contains(format)) throw new IllegalArgumentException("cover image must be PNG or JPEG");
                long pixels = Math.multiplyExact((long) reader.getWidth(0), (long) reader.getHeight(0));
                if (pixels <= 0 || pixels > maxPixels) throw new IllegalArgumentException("cover image dimensions exceed limit");
                if (reader.read(0) == null) throw new IllegalArgumentException("cover image cannot be decoded");
                return new ImageInfo("jpeg".equals(format) ? "jpg" : "png");
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("cover image dimensions exceed limit");
            } finally {
                reader.dispose();
            }
        }
    }

    private String safeKey(String key) {
        if (key == null || !key.matches("[0-9a-fA-F-]+\\.(png|jpg)")) {
            throw new IllegalArgumentException("invalid image key");
        }
        return key;
    }

    private record ImageInfo(String extension) {}
}
