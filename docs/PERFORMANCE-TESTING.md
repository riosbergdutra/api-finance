# Backend performance test procedure

## Purpose and safety

This procedure measures the service without mutating financial data. The included k6 script defaults to the read-only readiness endpoint. Do not point it at production. Run first against a disposable staging environment with synthetic users/data, then increase load gradually. Authenticated business endpoints may be tested only after a dedicated read-only route and synthetic account are selected.

## Prerequisites

- k6 installed locally or available in the test runner.
- A staging backend reachable through its normal ingress.
- Monitoring enabled for backend, PostgreSQL, Redis, JVM and Nginx.
- A baseline snapshot of CPU/RAM, Hikari pool, Redis memory/latency, PostgreSQL connections/locks and request metrics.
- A written test window and a stop contact.

## Smoke test

```bash
k6 run performance/k6/readiness.js
```

Set the target explicitly:

```bash
BASE_URL=https://staging.example.invalid k6 run performance/k6/readiness.js
```

The script calls only `GET /actuator/health/readiness` by default. If the endpoint is not exposed through the staging ingress, use a deliberately configured read-only route with `READINESS_PATH`. Do not change the script to call POST/PUT/PATCH/DELETE for a load test.

## Gradual load profile

```bash
BASE_URL=https://staging.example.invalid \
VUS=5 DURATION=2m \
k6 run performance/k6/readiness.js
```

Start at 1–5 virtual users, then 10, 25, 50 and 100 only while error rate, p95 latency and infrastructure headroom remain healthy. Increase duration after concurrency is stable. Stop immediately on sustained 5xx, connection exhaustion, database lock growth, Redis errors, elevated memory/GC, or any unexpected data mutation.

The script exposes conservative initial thresholds (p95 under 1s and failed checks under 1%). These are test guardrails, not promised production SLOs; tune them after agreeing on product SLOs and endpoint semantics.

## What to record

For each run record commit SHA, environment, date, endpoint, test-data profile, virtual users, duration, request count, p50/p95/p99, error rate, throughput, CPU/RAM, JVM heap/GC, Hikari active/idle/pending, PostgreSQL active connections/locks/slow queries, Redis ops/latency/memory and WebSocket connection count. Keep raw reports access-controlled and scrub personal data.

## Workload matrix

1. Readiness smoke: 1 VU, 30 seconds.
2. Read-only API baseline: 1–5 VUs, 2 minutes.
3. Step test: 10/25/50/100 VUs, 2 minutes each, with recovery intervals.
4. Soak: 25–50% of the highest stable concurrency, 30–60 minutes.
5. WebSocket soak: separate test with controlled connections and heartbeat; monitor Redis command volume and close/revalidation behavior.
6. Failure test: only in isolated staging; simulate Redis/Keycloak interruption and verify bounded failures/recovery.

## Interpretation and tuning

- If Hikari pending connections rise, inspect query duration and transaction boundaries before increasing pool size. Pool capacity is multiplied by backend replica count and must remain below PostgreSQL's connection budget.
- If Redis latency/command rate rises with WebSocket count, profile session revalidation. Do not introduce pipelining/batching until key serialization, atomicity, timeout and failure semantics are covered by integration tests.
- If p95 rises while DB is healthy, inspect application CPU, thread pools, GC and downstream HTTP calls.
- If p95 rises with DB locks/slow queries, capture `EXPLAIN (ANALYZE, BUFFERS)` on sanitized representative data and optimize the query/index only after validating the plan.
- Do not publish a maximum-user claim from a single run. Repeat tests and document the tested hardware, deployment topology and confidence limits.
