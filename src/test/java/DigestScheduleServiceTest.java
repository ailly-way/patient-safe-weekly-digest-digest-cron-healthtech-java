import java.util.concurrent.atomic.AtomicInteger;

public final class DigestScheduleServiceTest {
    public static void main(String[] args) throws Exception {
        DigestConfig config = new DigestConfig("test-key", "https://clinic.example/digests/weekly",
                "0 8 * * 1", java.time.Duration.ofSeconds(1), 1);
        AtomicInteger calls = new AtomicInteger();
        DigestScheduleService service = new DigestScheduleService(config, (cron, task) -> {
            calls.incrementAndGet();
            assertEquals("0 8 * * 1", cron);
            assertEquals("https://clinic.example/digests/weekly", task);
            return "job-health-42";
        });

        var unsafe = service.schedule(new DigestScheduleService.WorkflowSnapshot(8, 2, false));
        assertFalse(unsafe.scheduled(), "patient-level input must be rejected");
        assertEquals(0, calls.get());

        var quiet = service.schedule(new DigestScheduleService.WorkflowSnapshot(0, 0, true));
        assertFalse(quiet.scheduled(), "an empty workflow must not create a schedule");
        assertEquals(0, calls.get());

        var actionable = service.schedule(new DigestScheduleService.WorkflowSnapshot(8, 2, true));
        assertTrue(actionable.scheduled(), "aggregate backlog must create a schedule");
        assertEquals("job-health-42", actionable.jobId());
        assertEquals(1, calls.get());
        System.out.println("PASS: patient-safe scheduling policy");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        assertTrue(!value, message);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + ", got " + actual);
    }
}
