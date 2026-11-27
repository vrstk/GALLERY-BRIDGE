"""Write and verify synthetic SharedPreferences used by the signed upgrade test."""

import argparse
import hashlib
import json
import xml.etree.ElementTree as ET
from pathlib import Path

CATALOG_URL = "https://example.com/gallery-bridge-upgrade"
MODEL = "Модель для проверки обновления"
TITLE = "Галерея для проверки обновления"
DATE = "2026-11-16"
TARGET = "Mail WebDAV"
ROOT = "Models/Upgrade QA"
SENTINEL = "GALLERY_BRIDGE_UPGRADE_PREVIOUS_PRESERVED"
UPDATED_AT = 1_795_000_000_000

CATALOG = [{
    "url": CATALOG_URL, "model": MODEL, "title": TITLE, "date": DATE,
    "updatedAt": UPDATED_AT, "target": TARGET, "total": 4, "completed": 1,
    "outcome": "Передача остановлена", "type": "photo",
}]
HISTORY = [{
    "id": 32032, "updatedAt": UPDATED_AT, "model": MODEL, "title": "Предыдущая передача",
    "date": DATE, "target": TARGET, "total": 2, "uploaded": 1,
    "skipped": 1, "failed": 0, "outcome": "Завершено",
}]
QUEUE = {
    "id": 32033, "model": MODEL, "title": TITLE, "date": DATE,
    "target": TARGET, "root": ROOT, "account": "qa@example.com", "sourceUrl": CATALOG_URL,
    "entries": [
        {
            "url": f"{CATALOG_URL}/{index}.jpg", "name": f"{index}.jpg",
            "kind": "PHOTO", "source": "upgrade-fixture", "state": state,
            "failureReason": "Требуется проверка назначения" if state == "BLOCKED" else "",
            "contentSha256": hashlib.sha256(f"upgrade fixture {index}".encode()).hexdigest()
            if state in ("DONE", "WRITING") else "",
            "galleryUrl": CATALOG_URL, "galleryModel": MODEL, "galleryTitle": TITLE, "galleryDate": DATE,
        }
        for index, state in enumerate(("DONE", "PENDING", "BLOCKED", "WRITING"), start=1)
    ],
}
TRANSFER_STATUS = "Передача выполняется в фоне"
RECOVERED_STATUS = "Передача прервана системой"

SETTINGS = {"model": MODEL, "dark": False, "mailRoot": ROOT, "transferTarget": TARGET}
DATASETS = {
    "gallery_bridge_catalog_v1.xml": ("galleries", CATALOG),
    "gallery_bridge_history.xml": ("records", HISTORY),
    "gallery_bridge_queue.xml": ("snapshot", QUEUE),
}


def write_fixtures(directory):
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "upgrade-sentinel.txt").write_text(SENTINEL, encoding="utf-8")
    settings = ET.Element("map")
    for name, value in SETTINGS.items():
        if isinstance(value, bool):
            ET.SubElement(settings, "boolean", name=name, value=str(value).lower())
        else:
            ET.SubElement(settings, "string", name=name).text = value
    ET.ElementTree(settings).write(directory / "settings.xml", encoding="utf-8", xml_declaration=True)
    transfer = ET.Element("map")
    ET.SubElement(transfer, "boolean", name="running", value="true")
    ET.SubElement(transfer, "string", name="status").text = TRANSFER_STATUS
    ET.SubElement(transfer, "long", name="revision", value=str(UPDATED_AT))
    ET.ElementTree(transfer).write(directory / "transfer_state.xml", encoding="utf-8", xml_declaration=True)

    for filename, (name, data) in DATASETS.items():
        prefs = ET.Element("map")
        ET.SubElement(prefs, "string", name=name).text = json.dumps(data, ensure_ascii=False)
        ET.ElementTree(prefs).write(directory / filename, encoding="utf-8", xml_declaration=True)


def verify_fixtures(directory, after_launch):
    def check(actual, expected, label):
        if actual != expected:
            raise SystemExit(f"Upgrade data mismatch: {label}")
        print(f"Verified: {label}")

    def read_prefs(filename):
        root = ET.parse(directory / filename).getroot()
        if root.tag != "map":
            raise SystemExit(f"Unexpected SharedPreferences format: {filename}")
        return root

    check((directory / "upgrade-sentinel.txt").read_text(encoding="utf-8"), SENTINEL, "app file")
    settings = read_prefs("settings.xml")
    for name, value in SETTINGS.items():
        if isinstance(value, bool):
            actual = settings.find(f"./boolean[@name='{name}']")
            check(actual.get("value") if actual is not None else None, str(value).lower(), name)
        else:
            check(settings.findtext(f"./string[@name='{name}']"), value, name)
    for filename, (name, data) in DATASETS.items():
        stored = read_prefs(filename).findtext(f"./string[@name='{name}']")
        check(json.loads(stored) if stored else None, data, filename)

    transfer = read_prefs("transfer_state.xml")
    running = transfer.find("./boolean[@name='running']")
    check(running.get("value") if running is not None else None,
          "false" if after_launch else "true", "transfer running flag")
    check(transfer.findtext("./string[@name='status']"),
          RECOVERED_STATUS if after_launch else TRANSFER_STATUS, "transfer status")
    revision = transfer.find("./long[@name='revision']")
    revision_value = int(revision.get("value")) if revision is not None else 0
    if after_launch:
        check(revision_value > UPDATED_AT, True, "transfer recovery revision")
    else:
        check(revision_value, UPDATED_AT, "transfer revision")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("write", "verify"))
    parser.add_argument("directory", type=Path)
    parser.add_argument("--after-launch", action="store_true")
    args = parser.parse_args()
    if args.action == "write":
        write_fixtures(args.directory)
    else:
        verify_fixtures(args.directory, args.after_launch)
