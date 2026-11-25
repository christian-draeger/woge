#!/usr/bin/env python3
"""Align case 5/6 WebFlux events with TCP metadata from the same Linux loopback."""

import argparse
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ORIGIN = re.compile(r"Multipart TCK timeline ([\w-]+) \([^\n]*startedAt=([^\s)]+)\):")
EVENT = re.compile(r"^(\d+) us \[([\w-]+)-case-([567])\] \[[^\n]+?\] (.*)$", re.MULTILINE)
PORTS = re.compile(r"netty\.frame\.request peerPort=(\d+) localPort=(\d+)")
PACKET = re.compile(
    r"^(\d+\.\d+) IP 127\.0\.0\.1\.(\d+) > 127\.0\.0\.1\.(\d+): "
    r"Flags \[([^]]+)\],(.*)\blength (\d+)$"
)
EPOCH = datetime(1970, 1, 1, tzinfo=timezone.utc)


def read_timeline(path):
    if path.suffix == ".xml":
        root = ET.parse(path).getroot()
        return "\n".join(node.text or "" for node in root.iter("system-out"))
    return path.read_text()


def show_correlation(timeline, packets, capture_log):
    captured = re.search(r"^(\d+) packets captured$", capture_log, re.MULTILINE)
    if not captured or not 0 < int(captured[1]) < 10_000:
        raise ValueError("Capture was empty, incomplete or reached its packet limit")
    dropped = re.search(r"^(\d+) packets dropped by kernel$", capture_log, re.MULTILINE)
    if not dropped or int(dropped[1]):
        raise ValueError("Capture did not report zero kernel drops")

    starts = {origin[1]: datetime.fromisoformat(origin[2].replace("Z", "+00:00"))
              for origin in ORIGIN.finditer(timeline)}
    events = [(int(event[1]), event[2], event[3], event[4]) for event in EVENT.finditer(timeline)]
    for identifier, started_at in starts.items():
        related = [(micros, case, phase) for micros, run, case, phase in events
                   if run == identifier and case in ("5", "6")]
        flows = {case: (int(ports[1]), int(ports[2]))
                 for _, case, phase in related if (ports := PORTS.match(phase))}
        if "5" not in flows or not any(case == "6" for _, case, _ in related):
            continue

        # Instant precision in the timeline anchor is truncated to the microseconds used by the trace.
        elapsed = started_at - EPOCH
        origin_us = (elapsed.days * 86400 + elapsed.seconds) * 1_000_000 + elapsed.microseconds
        first_us = min(event[0] for event in related)
        last_us = max(event[0] for event in related)
        next_send = next((micros for micros, run, case, phase in events
                          if run == identifier and case == "7" and phase == "client.send.begin"), last_us + 1)
        rows = [(micros, f"case-{case} {phase}") for micros, case, phase in related]
        packet_count = 0
        for line in packets.splitlines():
            packet = PACKET.match(line)
            if packet is None:
                continue
            micros = int(Decimal(packet[1]) * 1_000_000) - origin_us
            if not first_us <= micros <= last_us or micros >= next_send:
                continue
            source, destination = int(packet[2]), int(packet[3])
            flow = (source, destination)
            if flow in flows.values():
                direction = "client->server"
            elif flow[::-1] in flows.values():
                direction = "server->client"
            else:
                continue
            fields = " ".join(re.findall(r"\b(?:seq|ack) [\d:]+", packet[5]))
            rows.append((micros, f"tcp {direction} {source}->{destination} flags={packet[4]} "
                                 f"{fields} bytes={packet[6]}"))
            packet_count += 1
        if not packet_count:
            raise ValueError("No TCP metadata matched the WebFlux flow and time window")
        print(f"Multipart TCK timeline {identifier}, request flows (client, server) {flows}")
        if "6" not in flows:
            print("Case 6 has no decoded header; packets can only be attributed to the case 5 connection.")
        for micros, row in sorted(rows):
            print(f"{micros} us {row}")
        return

    raise ValueError("Expected a WebFlux timeline with a clock anchor, case 5 ports and a case 6 attempt")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("timeline", type=Path, help="Gradle JUnit XML or a plain-text timeline")
    parser.add_argument("packets", type=Path, help="Loopback tcpdump stdout collected with -tt -S")
    parser.add_argument("capture_log", type=Path, help="Loopback tcpdump stderr with final capture counters")
    args = parser.parse_args()
    try:
        show_correlation(read_timeline(args.timeline), args.packets.read_text(), args.capture_log.read_text())
    except (ET.ParseError, OSError, ValueError) as failure:
        parser.exit(1, f"{failure}\n")
