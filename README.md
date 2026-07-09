# DPI Packet Analyzer 

It reads a `.pcap` capture,
classifies each flow (TLS SNI, HTTP Host header, or well-known port), applies configurable
block rules, and writes the filtered capture back out. Ships two interchangeable engines:

- **Single-threaded** — direct, sequential port of the original simple engine.
- **Multi-threaded** — the original's Reader → Load-Balancer → Fast-Path → Writer pipeline,
  rebuilt on `ExecutorService` and `BlockingQueue` instead of raw threads.

Both produce identical classification results; only the scheduling differs.

## Important note on "client-server"

The original project is **not** a networked client-server application — there is no socket,
no `accept()`/`connect()`, nothing on the wire. It is a single-process pipeline that reads a
PCAP file, fans work out across worker threads connected by in-memory queues, and writes a
PCAP file. This port preserves that architecture faithfully rather than inventing a network
protocol that never existed in the source project. The "network" terminology in the class
