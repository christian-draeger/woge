"""Fast wire-reader regression tests; no Docker, server, browser or Node involved."""

import json
from pathlib import Path
import struct
import unittest

from verify import baseline_frames


class DeploymentProbeTest(unittest.TestCase):
    def setUp(self):
        golden = Path(__file__).resolve().parents[2] / (
            "modules/woge-protocol/src/test/resources/fixtures/patch-stream-v1.hex"
        )
        self.wire = bytes.fromhex(golden.read_text().strip())

    def test_supported_old_golden_is_a_complete_replace_response(self):
        frames = baseline_frames(self.wire)
        self.assertEqual("replace", frames[0][1]["operation"])
        self.assertEqual("epoch-a", frames[0][1]["epoch"])
        self.assertEqual(2, frames[-1][0])

    def test_unknown_version_or_new_frame_kind_fails_old_reader(self):
        with self.assertRaises(AssertionError):
            baseline_frames(self.wire[:4] + b"\x02" + self.wire[5:])
        with self.assertRaises(AssertionError):
            baseline_frames(self.wire[:5] + b"\x04" + self.wire[6:])

    def test_truncated_or_missing_completion_never_means_success(self):
        with self.assertRaises((AssertionError, json.JSONDecodeError, struct.error)):
            baseline_frames(self.wire[:-1])
        kind, content, metadata, payload = struct.unpack_from(">BBII", self.wire, 5)
        self.assertEqual(1, kind)
        with self.assertRaises(AssertionError):
            baseline_frames(self.wire[:15 + content + metadata + payload])

    def test_terminal_count_must_match(self):
        corrupted = self.wire.replace(b'{"patches":1}', b'{"patches":9}')
        self.assertNotEqual(self.wire, corrupted)
        with self.assertRaises(AssertionError):
            baseline_frames(corrupted)


if __name__ == "__main__":
    unittest.main()
