#!/usr/bin/env python3
"""Small UIAutomator assertion/tap helper for the emulator smoke test."""

import re
import sys
import xml.etree.ElementTree as ET


operation, path, needle = sys.argv[1:4]
nodes = ET.parse(path).iter("node")
matches = [node for node in nodes if
           (node.get("resource-id", "").endswith(needle) if operation == "tap-id"
            else needle in node.get("text", "") or needle in node.get("content-desc", ""))]
if operation == "contains":
    sys.exit(0 if matches else 1)
if operation in ("tap", "tap-id") and matches:
    bounds = matches[0].get("bounds", "")
    coords = [int(value) for value in re.findall(r"\d+", bounds)]
    if len(coords) == 4:
        print(f"input tap {(coords[0] + coords[2]) // 2} {(coords[1] + coords[3]) // 2}")
        sys.exit(0)
sys.exit(1)
