"""Prepare release assets after checking the APK manifest against the tag/build."""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
from pathlib import Path
from urllib.parse import quote


def prepare(apk, output, badging, tag, repository, notes, destination):
    version, code = output["versionName"], output["versionCode"]
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._+-]*", version) or type(code) is not int or code <= 0:
        raise ValueError("Invalid build version")
    if tag != "v" + version:
        raise ValueError("Release tag does not match build version")
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    if not package or package.groups() != ("com.v4ex.android", str(code), version):
        raise ValueError("APK manifest does not match build metadata")
    checksum = hashlib.sha256(apk.read_bytes()).hexdigest()
    filename = f"V4EX-{version}-{code}.apk"
    destination.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(apk, destination / filename)
    (destination / (filename + ".sha256")).write_text(checksum + "  " + filename + "\n")
    metadata = dict(versionCode=code, versionName=version, apkFileName=filename, notes=notes,
                    apkUrl=f"https://github.com/{repository}/releases/download/{quote(tag, safe='')}/{filename}",
                    sha256=checksum)
    (destination / "update.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2))
    return metadata


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--release", type=Path, required=True)
    parser.add_argument("--destination", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.apk, json.loads(args.metadata.read_text())["elements"][0],
            subprocess.check_output([args.aapt, "dump", "badging", str(args.apk)], text=True),
            args.tag, args.repository, json.loads(args.release.read_text())["body"], args.destination)
