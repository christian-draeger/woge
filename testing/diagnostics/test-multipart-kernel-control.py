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


if __name__ == "__main__":
    unittest.main()
