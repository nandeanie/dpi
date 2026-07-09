package com.dpiengine.service;

import com.dpiengine.model.ProcessingStatistics;

import java.time.Duration;

/**
 * Prints a formatted summary of a completed run: packet and byte counts, protocol breakdown,
 * classification hit rates, and how long the run took.
 */
public final class ReportPrinter {

    private static final int WIDTH = 56;

    public void print(ProcessingStatistics stats, Duration elapsed) {
        printBar();
        printTitle("DPI Engine - Processing Report");
        printBar();
        printRow("Total packets read", format(stats.totalPacketsRead()));
        printRow("Total bytes read", format(stats.totalBytesRead()));
        printRow("TCP packets", format(stats.tcpPackets()));
        printRow("UDP packets", format(stats.udpPackets()));
        printRow("Other packets", format(stats.otherPackets()));
        printRow("SNI matches (TLS)", format(stats.sniMatchedPackets()));
        printRow("Host matches (HTTP)", format(stats.httpHostMatchedPackets()));
        printRow("Packets blocked", format(stats.blockedPackets()));
        printRow("Packets passed", format(stats.passedPackets()));
        printRow("Elapsed time", formatDuration(elapsed));
        if (elapsed.toMillis() > 0) {
            double packetsPerSecond = stats.totalPacketsRead() / (elapsed.toMillis() / 1000.0);
            printRow("Throughput", String.format("%.0f packets/sec", packetsPerSecond));
        }
        printBar();
    }

    private void printBar() {
        System.out.println("=".repeat(WIDTH));
    }

    private void printTitle(String title) {
        int padding = Math.max(0, (WIDTH - title.length()) / 2);
        System.out.println(" ".repeat(padding) + title);
    }

    private void printRow(String label, String value) {
        System.out.printf("%-30s %25s%n", label, value);
    }

    private String format(long value) {
        return String.format("%,d", value);
    }

    private String formatDuration(Duration elapsed) {
        long millis = elapsed.toMillis();
        return String.format("%d.%03ds", millis / 1000, millis % 1000);
    }
}
