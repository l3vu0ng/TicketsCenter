package vn.ticketscenter.event.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.event.integration.storage.LocalImageStorage;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

@WebServlet(name = "EventImageServlet", urlPatterns = "/api/event-images/*")
public final class EventImageServlet extends HttpServlet {
    private final LocalImageStorage storage = new LocalImageStorage(Path.of(
            AppConfig.get(AppConfig.Keys.EVENT_IMAGE_DIRECTORY, "./data/event-images")));

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String key = request.getPathInfo() == null ? "" : request.getPathInfo().replaceFirst("^/", "");
        try {
            var image = storage.open(key);
            response.setStatus(200);
            response.setContentType(image.contentType());
            response.setContentLengthLong(image.length());
            response.setHeader("Cache-Control", "public,max-age=31536000,immutable");
            try (var content = image.content()) { content.transferTo(response.getOutputStream()); }
        } catch (NoSuchFileException exception) {
            HttpResponses.error(response, 404, "NOT_FOUND", "Ảnh không tồn tại");
        } catch (IllegalArgumentException exception) {
            HttpResponses.error(response, 400, "VALIDATION_FAILED", exception.getMessage());
        }
    }
}
