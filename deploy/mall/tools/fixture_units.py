"""Read the WMS unit contract instead of inventing demo-only unit codes."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
CONTRACT = ROOT / ('txgy-module-wms/txgy-module-wms-api/src/main/java/cn/iocoder/'
                   'txgy/module/wms/api/integration/WmsUnitCodeDictionary.java')


def unit_codes():
    result = {}
    for declaration in re.findall(r'unit\(([^()]+)\)', CONTRACT.read_text(encoding='utf-8')):
        values = re.findall(r'"(?:[^"\\]|\\.)*"', declaration)
        if len(values) < 2:
            continue
        code, *aliases = [json.loads(value) for value in values]
        for alias in [code, *aliases]:
            result[alias.strip().replace(' ', '').lower()] = code
    if not result:
        raise ValueError('Cannot read WMS unit contract; do not silently invent codes')
    return result


def resolve_unit(value):
    key = (value or '').strip().replace(' ', '').lower()
    code = unit_codes().get(key)
    if code is None:
        raise ValueError(f'Unknown WMS demo unit: {value!r}')
    return code
