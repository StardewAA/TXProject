"""Validate bundled credential wiring without printing values or making API calls."""
import json
import os
import re
from pathlib import Path


class ConfigurationError(ValueError):
    pass


def read_settings(path, allowed):
    result = {}
    try:
        lines = path.read_text(encoding='utf-8-sig').splitlines()
    except OSError:
        raise ConfigurationError(f'{path.name}: required configuration file unavailable') from None
    for number, line in enumerate(lines, 1):
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        match = re.fullmatch(r'([A-Z][A-Z0-9_]*)=(.*)', line)
        if not match or match[1] not in allowed or match[1] in result:
            raise ConfigurationError(f'{path.name}:{number}: unexpected or duplicate setting')
        value = match[2].strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        # Only literal values: do not evaluate shell commands or dotenv interpolation.
        if value and not re.fullmatch(r'[A-Za-z0-9_./+=-]+', value):
            raise ConfigurationError(f'{path.name}:{number}: only literal credential values are supported')
        result[match[1]] = value
    return result


def check(directory=Path('/config'), require_map=True, require_deepseek=True):
    entries = [('oss.env', ('OSS_ACCESS_KEY', 'OSS_ACCESS_SECRET'), True),
               ('deepseek.env', ('DEEPSEEK_API_KEY',), require_deepseek),
               ('pc.env', ('VITE_AMAP_KEY', 'VITE_AMAP_SECURITY_CODE'), require_map),
               ('h5.env', ('SHOPRO_AMAP_KEY', 'SHOPRO_AMAP_SECURITY_CODE'), require_map)]
    checked = []
    for name, names, required in entries:
        values = read_settings(directory / name, names)
        if required:
            for key in names:
                value = values.get(key, '')
                if len(value) < 12 or re.search(r'(?i)placeholder|change.?me|your[_-]|^sk-x+$|^x+$', value):
                    raise ConfigurationError(f'{name}: {key} is empty or a placeholder')
        checked.append({'file': name, 'configured': all(bool(values.get(key)) for key in names),
                        'required': required})
    return {'status': 'configuration-only-passed', 'credentialsPrinted': False,
            'externalApisCalled': False, 'checks': checked}


def main():
    try:
        result = check(require_map=os.getenv('REQUIRE_AMAP', 'true').lower() == 'true',
                       require_deepseek=os.getenv('REQUIRE_DEEPSEEK', 'true').lower() == 'true')
    except ConfigurationError as error:
        raise SystemExit(f'Configuration preflight failed: {error}') from None
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
