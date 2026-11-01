import datetime as dt
import json
import os
import pathlib
import tempfile
import unittest

import fetch_sachet as fs

FIX = pathlib.Path(__file__).parent / "fixtures"
NOW = dt.datetime(2026, 10, 27, 12, 0, tzinfo=fs.IST)


def fake_fetch(url: str) -> bytes:
    name = url.rsplit("/", 1)[-1]
    if url.endswith("feed"):
        name = "rss.xml"
    p = FIX / name
    if not p.exists():
        raise IOError("404")
    return p.read_bytes()


class RelayTest(unittest.TestCase):
    def setUp(self):
        self.per, self.errors = fs.build(["https://example.test/feed"], fake_fetch, NOW)

    def test_district_name_match_and_level(self):
        ganjam = self.per["Ganjam"]
        self.assertEqual(len(ganjam), 1)
        a = ganjam[0]
        self.assertEqual(a["level"], "red")  # "Red alert" in headline beats severity=Severe
        self.assertEqual(a["agency"], "IMD Bhubaneswar")
        self.assertIn("ଅତି ପ୍ରବଳ", a["originalText"])  # official Odia text kept
        self.assertEqual(a["validUntil"], "2026-10-28T05:00:00+05:30")
        self.assertIn(a["id"], [x["id"] for x in self.per["Puri"]])

    def test_polygon_match(self):
        # Polygon covers Cuttack, Jajpur, Kendrapara, Jagatsinghpur, Dhenkanal HQs.
        for d in ("Cuttack", "Kendrapara", "Jagatsinghpur"):
            self.assertTrue(any(a["id"] == "sachet-1004" for a in self.per[d]), d)
        self.assertFalse(any(a["id"] == "sachet-1004" for a in self.per["Ganjam"]))
        self.assertEqual(self.per["Cuttack"][0]["level"], "red")  # Extreme severity

    def test_other_states_and_expired_dropped(self):
        ids = {a["id"] for alerts in self.per.values() for a in alerts}
        self.assertNotIn("sachet-1002", ids)
        self.assertNotIn("sachet-1003", ids)

    def test_errors_reported_not_fatal(self):
        self.assertEqual(len(self.errors), 1)
        self.assertIn("missing.xml", self.errors[0])

    def test_write_site_schema(self):
        with tempfile.TemporaryDirectory() as d:
            fs.write_site(d, self.per, NOW, self.errors)
            files = os.listdir(os.path.join(d, "alerts"))
            self.assertEqual(len(files), 31)  # 30 districts + index
            doc = json.loads(pathlib.Path(d, "alerts", "Ganjam.json").read_text(encoding="utf-8"))
            self.assertEqual(doc["district"], "Ganjam")
            self.assertEqual(doc["fetchedAt"], "2026-10-27T12:00:00+05:30")
            self.assertEqual(set(doc["alerts"][0]) - {"agency"},
                             {"id", "source", "level", "headline", "issuedAt", "validUntil", "originalText", "simplifiedOdia"})
            empty = json.loads(pathlib.Path(d, "alerts", "Sundargarh.json").read_text(encoding="utf-8"))
            self.assertEqual(empty["alerts"], [])

    def test_state_wide_alert_goes_to_all(self):
        self.assertEqual(fs.match_districts("Whole of Odisha", []), set(fs.DISTRICTS))
        self.assertEqual(fs.match_districts("Keonjhar and Baleswar", []), {"Kendujhar", "Balasore"})
        # "puri" must not match inside other words
        self.assertEqual(fs.match_districts("Jaipur, Rajasthan", []), set())

    def test_all_feeds_failing_writes_unavailable_once(self):
        with tempfile.TemporaryDirectory() as d:
            import unittest.mock as um
            with um.patch.object(fs, "http_get", side_effect=IOError("down")):
                self.assertEqual(fs.main(["--out", d, "--feed", "x"]), 0)
                doc = json.loads(pathlib.Path(d, "alerts", "Puri.json").read_text(encoding="utf-8"))
                self.assertEqual(doc["status"], "source_unavailable")
                before = pathlib.Path(d, "alerts", "index.json").read_text()
                self.assertEqual(fs.main(["--out", d, "--feed", "x"]), 0)  # keeps old files
                self.assertEqual(before, pathlib.Path(d, "alerts", "index.json").read_text())

    def test_all_feeds_failing_returns_error(self):
        def boom(url):
            raise IOError("down")
        per, errors = fs.build(["a", "b"], boom, NOW)
        self.assertEqual(len(errors), 2)


if __name__ == "__main__":
    unittest.main()
