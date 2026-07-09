# DPI Packet Analyzer 

It reads a `.pcap` capture,
classifies each flow (TLS SNI, HTTP Host header, or well-known port), applies configurable
block rules, and writes the filtered capture back out. Ships two interchangeable engines:

- **Single-threaded** — direct, sequential port of the original simple engine.
- **Multi-threaded** — the original's Reader → Load-Balancer → Fast-Path → Writer pipeline,
  rebuilt on `ExecutorService` and `BlockingQueue` instead of raw threads.

Both produce identical classification results; only the scheduling differs.
