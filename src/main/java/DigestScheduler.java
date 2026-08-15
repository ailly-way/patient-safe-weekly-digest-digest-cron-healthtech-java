public final class DigestScheduler {
    private DigestScheduler() { }

    public static void main(String[] args) throws Exception {
        DigestConfig config = DigestConfig.fromEnvironment();
        InfraiCronClient infrai = new InfraiCronClient(config);
        DigestScheduleService service = new DigestScheduleService(config, infrai::create);

        int confirmations = integerArg(args, 0, 12);
        int reschedules = integerArg(args, 1, 3);
        var snapshot = new DigestScheduleService.WorkflowSnapshot(confirmations, reschedules, true);
        var decision = service.schedule(snapshot);
        System.out.println(decision.reason());
        if (decision.scheduled()) System.out.println("job_id=" + decision.jobId());
    }

    private static int integerArg(String[] args, int index, int fallback) {
        return args.length > index ? Integer.parseInt(args[index]) : fallback;
    }
}
