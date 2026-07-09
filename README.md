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
