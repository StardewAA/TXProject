"""Stage the public fixture images or verify their published OSS bytes.

No upload, deletion, credential access or database mutation is performed.
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import ssl
import urllib.error
import urllib.parse
import urllib.request


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['stage', 'verify'])
    parser.add_argument('--manifest', type=Path, default=Path(__file__).resolve().parent.parent / 'public-assets.json')
    parser.add_argument('--origin', default=os.environ.get('OSS_PUBLIC_ORIGIN'))
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding='utf-8'))
    assets = manifest['assets']
    assert assets, 'Public image manifest is empty'
    assert len({a['objectKey'] for a in assets}) == len(assets), 'Duplicate object key'
    for asset in assets:
        key = PurePosixPath(asset['objectKey'])
        assert str(key).startswith('mall-demo/v1/product-images/'), 'Unexpected public image prefix'
        assert not key.is_absolute() and '..' not in key.parts and '\\' not in str(key), 'Invalid object key'
        assert asset['contentType'].startswith('image/'), 'Only public product images are allowed'
    if args.action == 'stage':
        root = Path(__file__).resolve().parents[3]
        image_root = (root / 'txgy-mall-web/public/product-images').resolve()
        stage = args.manifest.resolve().parent / '.assets'
        for asset in assets:
            source = (root / asset['source']).resolve()
            assert source.is_relative_to(image_root), 'Source outside public product images'
            assert source.stat().st_size == asset['size'] and digest(source) == asset['sha256'], 'Source image changed: ' + asset['source']
            target = stage / asset['objectKey']
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
        print(f'Staged {len(assets)} public images in {stage}. No OSS upload performed.')
        return

    origin = (args.origin or manifest['origin']).rstrip('/')
    parsed = urllib.parse.urlsplit(origin)
    assert parsed.scheme == 'https' and parsed.hostname and not parsed.username and not parsed.password, 'An HTTPS public origin is required'
    assert not parsed.query and not parsed.fragment and parsed.path in ('', '/'), 'Use an origin without path, signature or query'

    def verify(asset):
        try:
            url = origin + '/' + urllib.parse.quote(asset['objectKey'], safe='/')
            with urllib.request.urlopen(url, timeout=20) as response:
                assert response.url.startswith('https://'), 'Insecure redirect'
                assert response.headers.get('Content-Type', '').startswith('image/'), 'Response is not an image'
                content = response.read(asset['size'] + 1)
                assert len(content) == asset['size'], 'File size differs'
                assert hashlib.sha256(content).hexdigest() == asset['sha256'], 'SHA-256 differs'
            return None
        except (OSError, AssertionError, ValueError) as error:
            # No credentials or signed URLs are included in output.
            reason = type(error).__name__
            if isinstance(error, urllib.error.HTTPError):
                reason += f' HTTP {error.code}'
            elif isinstance(getattr(error, 'reason', None), ssl.SSLError):
                reason = 'TLS handshake failed'
            return f'{asset["objectKey"]}: {reason}'

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        failures = [result for result in pool.map(verify, assets) if result]
    if failures:
        print('\n'.join(failures[:8]))
        raise SystemExit(f'OSS image verification failed: {len(failures)}/{len(assets)}. '
                         'The maintainer must publish the manifest images and verify HTTPS access before distribution.')
    print(f'OSS images verified: {len(assets)} objects; size, SHA-256 and HTTPS readability match.')


if __name__ == '__main__':
    main()
