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
}
