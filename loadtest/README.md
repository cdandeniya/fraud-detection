# Load test

Hammer the `/score` endpoint and print throughput + latency percentiles. Standard library
only, nothing to install.

```bash
# start the app first (docker compose up -d db redis kafka model, then run the app), then:
python3 loadtest/loadtest.py --requests 5000 --concurrency 50
```

Turn the simulator off (`fraud.simulator.enabled=false`) while load testing so you're only
measuring the requests you're sending. Copy the throughput and p95 numbers into the resume
bullets in `BUILD_PLAN.md`.
