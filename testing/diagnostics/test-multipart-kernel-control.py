import runpy
import unittest
from pathlib import Path

MODULE = runpy.run_path(str(Path(__file__).with_name("multipart-kernel-control.py")))


class MetadataTest(unittest.TestCase):
    def test_keeps_only_allowlisted_text_fields(self):
        source = (
            "1795563631.123456 IP 127.0.0.1.47132 > 127.0.0.1.45114: "
            "Flags [P.], seq 736:902, ack 1358, win 512, options [nop], "
            "untrusted diagnostic words, length 166"
        )
        self.assertEqual(
            MODULE["packet_metadata"](source),
            "1795563631.123456 IP 127.0.0.1.47132 > 127.0.0.1.45114: "
            "Flags [P.], seq 736:902, ack 1358, length 166",
        )

    def test_drops_other_lines_ports_and_addresses(self):
        row = "1795563631.123456 IP 127.0.0.1.47132 > 127.0.0.1.45114: Flags [.], ack 1358, length 0"
        for line in (
            "unformatted payload",
            row.replace("47132", "80"),
            row.replace("127.0.0.1.45114", "192.0.2.1.45114"),
            row.replace("[.]", "[unexpected]"),
        ):
            with self.subTest(line=line):
                self.assertIsNone(MODULE["packet_metadata"](line))


class HostOverlapTest(unittest.TestCase):
    def test_requires_each_peer_to_run_while_webflux_is_active(self):
        overlap = MODULE["overlapping_webflux_peers"]
        windows = {
            "woge-spring-webflux": {"start": 2.0, "end": 5.0},
            "woge-spring-mvc": {"start": 1.0, "end": 3.0},
            "woge-ktor": {"start": 5.0, "end": 6.0},
        }
        self.assertEqual(overlap(windows), ["woge-spring-mvc"])
        windows["woge-ktor"]["start"] = 4.0
        self.assertEqual(overlap(windows), ["woge-spring-mvc", "woge-ktor"])
        windows.pop("woge-spring-webflux")
        self.assertEqual(overlap(windows), [])


if __name__ == "__main__":
    unittest.main()
