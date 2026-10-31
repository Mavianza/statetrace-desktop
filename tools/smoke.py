"""Uji JAR lewat proses Java nyata; tidak memerlukan layar atau paket Python tambahan."""

import csv
import io
import json
from pathlib import Path
import random
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "dist" / "statetrace.jar"


def invoke(*args):
    result = subprocess.run(
        ["java", "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8", "-jar", str(JAR), *map(str, args)],
        cwd=ROOT, text=True, encoding="utf-8", capture_output=True, timeout=15,
    )
    assert "Exception in thread" not in result.stderr, result.stderr
    return result


def main():
    assert JAR.is_file(), "Bangun JAR terlebih dahulu: java Build.java"
    with zipfile.ZipFile(JAR) as archive:
        assert archive.testzip() is None
        assert b"Main-Class: statetrace.Main" in archive.read("META-INF/MANIFEST.MF")
        for name in archive.namelist():
            if name.endswith(".class"):
                data = archive.read(name)
                assert data[:4] == b"\xca\xfe\xba\xbe" and int.from_bytes(data[6:8], "big") == 61, name

    randomizer = random.Random(20261101)
    cases = ["", "0", "1", "01", "10", "1101", "001", "1010"]
    cases += ["".join(randomizer.choice("01") for _ in range(randomizer.randrange(1, 28))) for _ in range(24)]
    checked = 0
    for file, oracle in [("ends-01.dfa", lambda text: text.endswith("01")), ("even-ones.dfa", lambda text: text.count("1") % 2 == 0)]:
        definition = ROOT / "examples" / file
        for text in cases:
            result = invoke("run", definition, text)
            expected = oracle(text)
            assert result.returncode == (0 if expected else 1), (file, text, result.stderr)
            assert ("DITERIMA" if expected else "DITOLAK") in result.stdout
            checked += 1

    with tempfile.TemporaryDirectory(prefix="statetrace-smoke-") as temporary:
        temp = Path(temporary)
        report = temp / "trace.csv"
        definition = ROOT / "examples" / "ends-01.dfa"
        exported = invoke("run", definition, "1101", "--csv", report)
        assert exported.returncode == 0
        rows = list(csv.reader(io.StringIO(report.read_text(encoding="utf-8"))))
        assert rows[0] == ["langkah", "dari", "simbol", "ke"]
        assert len(rows) == 6 and rows[-1] == ["4", "q1", "1", "q2"]
        before = report.read_bytes()
        assert invoke("run", definition, "01", "--csv", report).returncode == 2
        assert report.read_bytes() == before
        assert invoke("run", definition, "x").returncode == 2
        assert invoke("check", definition).returncode == 0
        assert invoke("check", temp / "missing.dfa").returncode == 2

        emoji = temp / "emoji.dfa"
        emoji.write_text("name=Emoji\nstates=a,b\nalphabet=😀\nstart=a\naccept=b\na,😀=b\nb,😀=a\n", encoding="utf-8")
        assert invoke("run", emoji, "😀").returncode == 0
        assert invoke("run", emoji, "😀😀").returncode == 1
        invalid = temp / "invalid.dfa"
        invalid.write_text(definition.read_text().replace("q0,0=q1\n", ""), encoding="utf-8")
        assert invoke("check", invalid).returncode == 2
        assert invoke().returncode == 2  # Error lingkungan GUI tanpa layar ditangani.

    print(json.dumps({"binary_language_cases": checked, "integration_checks": 9, "status": "passed", "jar_class_version": 61}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
