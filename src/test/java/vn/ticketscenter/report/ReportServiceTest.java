package vn.ticketscenter.report;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.report.repository.ReportRepository.ReportFilter;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportServiceTest {
    @Test
    void filterRequiresHalfOpenRangeAndBoundedPage() {
        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-01T00:00:00Z");

        assertEquals("paidAt", new ReportFilter(null, null, from, to, 1, 100, "paidAt").validate().sort());
        assertThrows(IllegalArgumentException.class,
                () -> new ReportFilter(null, null, to, from, 1, 20, "eventTitle").validate());
        assertThrows(IllegalArgumentException.class,
                () -> new ReportFilter(null, null, from, to, 0, 20, "eventTitle").validate());
        assertThrows(IllegalArgumentException.class,
                () -> new ReportFilter(null, null, from, to, 1, 101, "eventTitle").validate());
        assertThrows(IllegalArgumentException.class,
                () -> new ReportFilter(null, null, from, to, 1, 20, "drop table").validate());
    }

    @Test
    void managerFilterCannotSelectAnotherOrganization() {
        UUID allowed = UUID.randomUUID();
        UUID requested = UUID.randomUUID();

        assertThrows(SecurityException.class,
                () -> ReportFilter.scoped(allowed,
                        new ReportFilter(requested, null, Instant.EPOCH, Instant.EPOCH.plusSeconds(1), 1, 20, "eventTitle")));
    }
}
