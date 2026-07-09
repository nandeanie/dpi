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
names below refers to *packet parsing* (Ethernet/IP/TCP/UDP), not to sockets.


## Package structure

```
com.dpiengine
├── model        FiveTuple, ParsedPacket, RawPacket, Flow, AppType, PcapGlobalHeader,
│                 PcapPacketHeader, ProcessingStatistics
├── protocol      PacketParser (Ethernet/IPv4/TCP/UDP), TlsSniExtractor, HttpHostExtractor,
│                 AppTypeClassifier
├── io            PcapFileReader, PcapFileWriter
├── service       BlockRuleSet (rule manager), FlowTable (connection tracker), ReportPrinter
├── pipeline      PipelineMessage, LoadBalancerWorker, FastPathWorker, OutputWriterWorker,
│                 DpiPipeline (orchestrator)
├── config        EngineOptions, CliArgumentParser
├── exception     PcapFormatException, PacketParseException, EngineConfigurationException
├── util          NetworkByteReader, IpAddressFormatter
├── DpiEngineSingleThreaded   (entry point, sequential engine)
├── DpiEngineMultiThreaded    (entry point, concurrent pipeline)
└── ConsoleApp                (entry point, interactive menu-driven console)
```

`client` and `server` packages from a typical assignment template are intentionally absent —
there is no client-server boundary in this project to put them around. If a real network layer
is ever wanted (e.g. a long-running DPI service that accepts capture uploads over TCP), it
would sit cleanly *outside* this structure, calling into `pipeline.DpiPipeline` the same way
`DpiEngineMultiThreaded.main` does now.

## Class diagram (text)

```
FiveTuple ──────────────┐
                         │ (flowKey)
RawPacket ── PacketParser ──▶ ParsedPacket
                                  │
                    ┌─────────────┼──────────────┐
                    ▼             ▼              ▼
            TlsSniExtractor  HttpHostExtractor  AppTypeClassifier
                    │             │              │
                    └──────┬──────┴──────────────┘
                           ▼
                         Flow  ◀── FlowTable (per FastPath)
                           │
                           ▼
                     BlockRuleSet.evaluate
                           │
                 ┌─────────┴─────────┐
                 ▼                   ▼
        PcapFileWriter        ProcessingStatistics

Pipeline wiring (multi-threaded mode):

PcapFileReader ─▶ PacketParser ─▶ [LB input queues]
                                        │  hash(FiveTuple) % loadBalancerCount
                                        ▼
                              LoadBalancerWorker (× N)
                                        │  hash(FiveTuple) % fastPathsPerLB
                                        ▼
                               FastPathWorker (× N×M)
                          [own FlowTable, shared BlockRuleSet]
                                        │
                                        ▼
                              [shared output queue]
                                        │
                                        ▼
                             OutputWriterWorker ─▶ PcapFileWriter
```

## Sequence of communication (single request through the pipeline)

```
Main            Reader          LoadBalancer      FastPath        Writer
 │  run()         │                  │                │              │
 │───────────────▶│                  │                │              │
 │            readNextPacket()       │                │              │
 │            parse -> ParsedPacket  │                │              │
 │            put(msg) on LB queue ─▶│                │              │
 │                                   │ take()         │              │
 │                                hash -> pick FP     │              │
 │                                put(msg) on FP ────▶│              │
 │                                   │                │ take()       │
 │                                   │            classify (SNI/Host) │
 │                                   │            evaluate block rule │
 │                                   │            [not blocked] put ─▶│
 │                                   │                │           write │
 │  (loop until EOF)                 │                │              │
 │  put(poison) on every LB queue ──▶│                │              │
 │                                forward poison ────▶│              │
 │                                   │ returns        │ returns      │
 │  await all LB futures             │                │              │
 │  await all FastPath futures                        │              │
 │  put(poison) on output queue ─────────────────────────────────────▶│
 │  await writer future                                                │ returns
 │  print report                                                       │
```

## How to build

Two ways — pick whichever you have available.

### Option A — plain `javac` (no Maven required)

```bash
./build.sh
```

Compiles everything into `./out`.

### Option B — Maven

```bash
mvn -q package
```

Produces `target/dpi-packet-analyzer.jar` with a runnable manifest (defaults to the
interactive console app).

## How to run

```bash
# Single-threaded engine
./run-single.sh --in sample.pcap --out filtered.pcap \
  --block-app YOUTUBE --block-domain doubleclick --block-ip 93.184.216.34

# Multi-threaded pipeline (2 load balancers x 2 fast paths = 4 workers)
./run-multithreaded.sh --in sample.pcap --out filtered.pcap \
  --block-app YOUTUBE --load-balancers 2 --fast-paths-per-lb 2

# Interactive console menu (prompts for everything, validates input)
./run-console.sh
```

Or, after a Maven build:

```bash
java -jar target/dpi-packet-analyzer.jar
java -cp target/dpi-packet-analyzer.jar com.dpiengine.DpiEngineMultiThreaded --in a.pcap --out b.pcap
```

### CLI flags

| Flag                     | Required | Meaning                                                |
|--------------------------|:--------:|---------------------------------------------------------|
| `--in <file>`            | yes      | input `.pcap` path                                     |
| `--out <file>`           | yes      | output `.pcap` path (filtered capture)                 |
| `--block-app <NAME>`     | no       | repeatable; blocks an `AppType` (e.g. `YOUTUBE`, `TIKTOK`) |
| `--block-domain <text>`  | no       | repeatable; blocks any hostname containing this substring |
| `--block-ip <a.b.c.d>`   | no       | repeatable; blocks a specific IPv4 address              |
| `--load-balancers <n>`   | no       | multi-threaded only, default 2                          |
| `--fast-paths-per-lb <n>`| no       | multi-threaded only, default 2                          |

## Networking / robustness notes

- **Byte order**: the PCAP global header's magic number is checked to auto-detect
  little-endian vs. byte-swapped captures; every per-packet header is then read with the
  matching `ByteOrder`. Header *field* bytes inside Ethernet/IP/TCP/UDP are always big-endian
  (network byte order), independent of the file's own byte order.
- **Truncated/corrupt files**: a file too short for even the global header, a packet header
  claiming more bytes than remain in the file, or a body cut off mid-read all raise
  `PcapFormatException` with a specific message rather than throwing a raw, confusing I/O
  exception or silently producing garbage.
- **Malformed individual packets** (short frames, bad IHL, truncated TCP header) are caught
  per-packet and skipped, so one bad frame in a multi-million-packet capture doesn't abort the
  whole run.
- **Resource safety**: `PcapFileReader` and `PcapFileWriter` are both `Closeable` and opened in
  try-with-resources everywhere, so file handles are released even if a parse error or
  interrupt happens mid-run.
- **Backpressure**: every queue in the pipeline is a bounded `ArrayBlockingQueue`. If the
  writer falls behind, `put()` blocks upstream naturally — no unbounded memory growth from a
