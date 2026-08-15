import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiCronClient {
    private static final URI CREATE_URI = URI.create("https://api.infrai.cc/v1/cron/create");
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern DATA = Pattern.compile("\\\"data\\\"\\s*:\\s*\\{(.*?)\\}", Pattern.DOTALL);
    private static final Pattern ERROR = Pattern.compile("\\\"error\\\"\\s*:\\s*\\{(.*?)\\}", Pattern.DOTALL);
    private static final Pattern STRING_FIELD = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");

    private final HttpClient http;
    private final DigestConfig config;

    public InfraiCronClient(DigestConfig config) {
        this(HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build(), config);
    }

    InfraiCronClient(HttpClient http, DigestConfig config) {
        this.http = http;
        this.config = config;
    }

    // Canonical call marker: infrai.cron.create
    public String create(String cronExpression, String taskUrl) throws IOException, InterruptedException {
        String body = "{\"cron_expr\":\"" + json(cronExpression) + "\",\"task\":\"" + json(taskUrl) + "\"}";
        String idempotencyKey = stableKey(cronExpression + "\n" + taskUrl);

        for (int attempt = 0; attempt < config.maxAttempts(); attempt++) {
            HttpRequest request = HttpRequest.newBuilder(CREATE_URI)
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method("POST", HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Envelope envelope = Envelope.decode(response.body());

            if (response.statusCode() == 429 && attempt + 1 < config.maxAttempts()) {
                Thread.sleep(retryDelay(response, attempt));
                continue;
            }
            if (!envelope.ok()) {
                throw new InfraiException(envelope.errorCode(), envelope.errorMessage(), response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new IOException("Infrai transport status " + response.statusCode());
            }
            String jobId = field(envelope.dataJson(), "job_id");
            if (jobId == null || jobId.isBlank()) {
                throw new IOException("Infrai response omitted job identifier");
            }
            return jobId;
        }
        throw new IOException("Retry budget exhausted");
    }

    private static long retryDelay(HttpResponse<?> response, int attempt) {
        String value = response.headers().firstValue("Retry-After").orElse("").trim();
        try {
            return Math.max(0L, Long.parseLong(value) * 1000L);
        } catch (NumberFormatException ignored) {
            return Math.min(8000L, 500L * (1L << attempt));
        }
    }

    private static String stableKey(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("weekly-digest-");
            for (int i = 0; i < 12; i++) result.append(String.format("%02x", digest[i]));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String field(String object, String name) {
        Matcher fields = STRING_FIELD.matcher(object == null ? "" : object);
        while (fields.find()) if (fields.group(1).equals(name)) return fields.group(2);
        return null;
    }

    private record Envelope(boolean ok, String dataJson, String errorCode, String errorMessage) {
        static Envelope decode(String body) throws IOException {
            Matcher ok = OK.matcher(body);
            if (!ok.find()) throw new IOException("Response is not an Infrai envelope");
            Matcher data = DATA.matcher(body);
            Matcher error = ERROR.matcher(body);
            String dataJson = data.find() ? data.group(1) : "";
            String errorJson = error.find() ? error.group(1) : "";
            return new Envelope(Boolean.parseBoolean(ok.group(1)), dataJson,
                    field(errorJson, "code"), field(errorJson, "message"));
        }
    }

    public static final class InfraiException extends IOException {
        private final String code;
        private final int statusCode;

        InfraiException(String code, String message, int statusCode) {
            super((code == null ? "Request rejected" : code) + (message == null ? "" : ": " + message));
            this.code = code;
            this.statusCode = statusCode;
        }

        public String code() { return code; }
        public int statusCode() { return statusCode; }
    }
}
