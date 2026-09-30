package vn.ticketscenter.event.integration.storage;

import java.io.IOException;
import java.io.InputStream;

public interface ImageStorage {
    String store(InputStream content, String submittedName) throws IOException;
    void delete(String publicUrl) throws IOException;
    StoredImage open(String key) throws IOException;

    record StoredImage(InputStream content, String contentType, long length) {}
}
