#!/usr/bin/env python3
"""
Simple load test for the /score endpoint. Pure standard library so there's nothing to
install. Fires N requests across C worker threads and reports throughput and latency
percentiles - these are the numbers that go on the resume.

    python3 loadtest/loadtest.py --requests 5000 --concurrency 50

Point it somewhere else with --url http://localhost:8080/score
"""

import argparse
import json
import random
import statistics
import threading
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

COUNTRIES = ["US", "US", "US", "GB", "CA", "RU", "NG"]
MERCHANTS = ["Amazon", "Walmart", "Steam", "Uber", "Apple"]


def random_payload():
    fraud = random.random() < 0.1
    return {
        "cardId": f"card-{random.randint(1, 50)}",
        "amount": random.randint(1000, 9000) if fraud else random.randint(5, 200),
        "merchant": random.choice(MERCHANTS),
        "country": random.choice(COUNTRIES[5:]) if fraud else "US",
    }


def one_request(url):
    body = json.dumps(random_payload()).encode()
    req = urllib.request.Request(url, data=body, headers={"Content-Type": "application/json"})
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            resp.read()
            ok = resp.status < 400
    except Exception:
        ok = False
    return (time.perf_counter() - start) * 1000.0, ok  # latency in ms


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="http://localhost:8080/score")
    ap.add_argument("--requests", type=int, default=2000)
    ap.add_argument("--concurrency", type=int, default=25)
    args = ap.parse_args()

    print(f"firing {args.requests} requests at {args.url} "
          f"with {args.concurrency} workers...")

    latencies = []
    ok_count = 0
    lock = threading.Lock()

    def task():
        nonlocal ok_count
        latency, ok = one_request(args.url)
        with lock:
            latencies.append(latency)
            if ok:
                ok_count += 1

    wall_start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        for _ in range(args.requests):
            pool.submit(task)
    wall = time.perf_counter() - wall_start

    latencies.sort()

    def pct(p):
        if not latencies:
            return 0.0
        return latencies[min(len(latencies) - 1, int(len(latencies) * p))]

    print("\n--- results ---")
    print(f"requests:     {args.requests}  (ok: {ok_count})")
    print(f"wall time:    {wall:.2f} s")
    print(f"throughput:   {args.requests / wall:.0f} req/s")
    if latencies:
        print(f"latency mean: {statistics.mean(latencies):.1f} ms")
        print(f"latency p50:  {pct(0.50):.1f} ms")
        print(f"latency p95:  {pct(0.95):.1f} ms")
        print(f"latency p99:  {pct(0.99):.1f} ms")


if __name__ == "__main__":
    main()
