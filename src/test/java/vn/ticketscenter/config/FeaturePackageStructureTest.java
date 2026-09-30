package vn.ticketscenter.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeaturePackageStructureTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/vn/ticketscenter");

    @Test
    void productionPackagesAreFeatureBased() throws Exception {
        try (var paths = Files.list(SOURCE_ROOT)) {
            Set<String> packages = paths.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());

            assertEquals(Set.of(
                    "admin", "audit", "config", "event", "fulfillment",
                    "identity", "order", "payment", "settlement", "ticketing"
            ), packages);
        }

        try (var paths = Files.walk(SOURCE_ROOT)) {
            assertTrue(paths.noneMatch(path -> path.getFileName().toString().equals(".gitkeep")));
        }
    }

    @Test
    void webServletUrlPatternsAreUnique() throws Exception {
        java.util.Map<String, String> mappedPatterns = new java.util.HashMap<>();
        java.util.regex.Pattern servletPattern = java.util.regex.Pattern.compile("@WebServlet\\s*\\((.*?)\\)", java.util.regex.Pattern.DOTALL);
        java.util.regex.Pattern urlPatternRegex = java.util.regex.Pattern.compile("\"(/[^\"]*)\"");

        try (var paths = Files.walk(SOURCE_ROOT)) {
            java.util.List<Path> javaFiles = paths.filter(p -> p.toString().endsWith(".java")).toList();
            for (Path file : javaFiles) {
                String content = Files.readString(file);
                java.util.regex.Matcher matcher = servletPattern.matcher(content);
                if (matcher.find()) {
                    String annotationBody = matcher.group(1);
                    java.util.regex.Matcher urlMatcher = urlPatternRegex.matcher(annotationBody);
                    while (urlMatcher.find()) {
                        String url = urlMatcher.group(1);
                        String previous = mappedPatterns.put(url, file.getFileName().toString());
                        org.junit.jupiter.api.Assertions.assertNull(previous, () -> String.format(
                                "Duplicate URL pattern '%s' found in %s and %s (violates Tomcat servlet mapping constraints)",
                                url, previous, file.getFileName()
                        ));
                    }
                }
            }
        }
        org.junit.jupiter.api.Assertions.assertFalse(mappedPatterns.isEmpty(), "Expected to find @WebServlet mappings");
    }
}
