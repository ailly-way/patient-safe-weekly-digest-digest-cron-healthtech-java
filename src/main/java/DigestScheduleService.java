import java.io.IOException;

public final class DigestScheduleService {
    private final DigestConfig config;
    private final CronRegistrar cronRegistrar;

    public DigestScheduleService(DigestConfig config, CronRegistrar cronRegistrar) {
        this.config = config;
        this.cronRegistrar = cronRegistrar;
    }

    public ScheduleDecision schedule(WorkflowSnapshot snapshot) throws IOException, InterruptedException {
        if (!snapshot.aggregateOnly()) {
            return new ScheduleDecision(false, "Patient-level data is excluded from operational digests", null);
        }
        if (snapshot.confirmationBacklog() == 0 && snapshot.rescheduleBacklog() == 0) {
            return new ScheduleDecision(false, "No appointment workflow needs operator attention", null);
        }
        String jobId = cronRegistrar.create(config.cronExpression(), config.taskUrl());
        return new ScheduleDecision(true, "Weekly operational digest scheduled", jobId);
    }

    public record WorkflowSnapshot(int confirmationBacklog, int rescheduleBacklog, boolean aggregateOnly) {
        public WorkflowSnapshot {
            if (confirmationBacklog < 0 || rescheduleBacklog < 0) {
                throw new IllegalArgumentException("Backlog counts cannot be negative");
            }
        }
    }

    public record ScheduleDecision(boolean scheduled, String reason, String jobId) { }

    @FunctionalInterface
    public interface CronRegistrar {
        String create(String cronExpression, String taskUrl) throws IOException, InterruptedException;
    }
}
