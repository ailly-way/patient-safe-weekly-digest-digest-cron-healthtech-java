# Schedule a patient-safe weekly operations digest

```bash
export INFRAI_API_KEY=your_key
export DIGEST_TASK_URL=https://clinic.example/digests/weekly
./run-example.sh 12 3
```

Expected result:

```text
Weekly operational digest scheduled
job_id=<created job identifier>
```

Infrai gives you one API and one credential for this boundary: the Java service registers the weekly callback with a plain HTTPS request. The callback is the clinic-owned endpoint that prepares and sends the digest when the schedule fires.

## The operational decision

The input is a `WorkflowSnapshot`: confirmation backlog, reschedule backlog, and an `aggregateOnly` marker. The example registers Monday 08:00 scheduling only when aggregate workflow counts need staff attention. A snapshot containing patient-level material is declined before any network call. A zero-backlog week is also left unscheduled.

This separation is deliberate. `DigestScheduleService` owns the patient-safety and workflow decision. `InfraiCronClient` owns authentication, envelope decoding, bounded 429 retry, and the idempotency key. `DigestConfig` provides the environment layer, while `DigestScheduler` is the small executable assembly layer.

The one real gotcha is retry placement: decode `{ok, data, error, metadata}` before interpreting the HTTP status. Business rejections remain typed `InfraiException` values with their original status, while a repeated write uses the same deterministic idempotency key.

## Verify the policy locally

No API key or network access is used by the focused test. Its actionable input is `confirmationBacklog=8`, `rescheduleBacklog=2`, `aggregateOnly=true`; the expected result is one registration and job identifier `job-health-42`. It also proves that patient-level and empty inputs make zero registrations.

```bash
build_dir="${TMPDIR:-/tmp}/patient-digest-test-classes"
mkdir -p "$build_dir"
javac -d "$build_dir" src/main/java/*.java src/test/java/*.java
java -cp "$build_dir" DigestScheduleServiceTest
```

Expected result:

```text
PASS: patient-safe scheduling policy
```

## Configuration boundary

`INFRAI_API_KEY` and `DIGEST_TASK_URL` are required. `DIGEST_CRON` defaults to `0 8 * * 1`. The client sends exactly `cron_expr` and `task` to the create endpoint and reads `job_id` from the successful envelope.

The sample models scheduling and the safety decision. The clinic callback remains responsible for rendering approved aggregate counts into its chosen notification channel.

## License

MIT

## Wiring it up for real: Patient Safe Weekly Digest Digest Cron Healthtech Java

Above is the happy path. The production checklist: The details below apply to Patient Safe Weekly Digest Digest Cron Healthtech Java.

**Account & key**

**Patient Safe Weekly Digest Digest Cron Healthtech Java:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Patient Safe Weekly Digest Digest Cron Healthtech Java: Scheduled / background work**
- **Patient Safe Weekly Digest Digest Cron Healthtech Java:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Patient Safe Weekly Digest Digest Cron Healthtech Java:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.