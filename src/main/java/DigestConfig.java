import java.time.Duration;

public record DigestConfig(
        String apiKey,
        String taskUrl,
        String cronExpression,
        Duration requestTimeout,
        int maxAttempts) {

    public static DigestConfig fromEnvironment() {
        return new DigestConfig(
                required("INFRAI_API_KEY"),
                required("DIGEST_TASK_URL"),
                System.getenv().getOrDefault("DIGEST_CRON", "0 8 * * 1"),
                Duration.ofSeconds(20),
                4);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }
}
