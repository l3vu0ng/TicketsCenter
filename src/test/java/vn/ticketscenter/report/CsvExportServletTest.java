package vn.ticketscenter.report;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.report.controller.CsvExportServlet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvExportServletTest {
    @Test
    void quotesCsvAndNeutralizesExcelFormulaText() {
        assertEquals("\"' =HYPERLINK(\"\"https://example.test\"\")\"",
                CsvExportServlet.textCell(" =HYPERLINK(\"https://example.test\")"));
        assertEquals("\"Sự kiện \"\"Việt\"\"\n2026\"", CsvExportServlet.textCell("Sự kiện \"Việt\"\n2026"));
        assertEquals("120000", CsvExportServlet.numberCell("120000"));
    }

    @Test
    void controlPrefixCannotHideFormula() {
        assertEquals("\"'\t+SUM(1,1)\"", CsvExportServlet.textCell("\t+SUM(1,1)"));
        assertEquals("\"'-1+2\"", CsvExportServlet.textCell("-1+2"));
        assertEquals("\"'@cmd\"", CsvExportServlet.textCell("@cmd"));
    }
}
