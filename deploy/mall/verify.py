"""Container smoke check. Login and public reads only; does not create orders."""
import json
import argparse
import os
import time
import urllib.error
import urllib.request

GATEWAY = os.environ.get('VERIFY_GATEWAY', 'http://gateway:48080')


def get(url, payload=None, headers=None):
    request = urllib.request.Request(url, data=None if payload is None else json.dumps(payload).encode(),
                                     headers={'Content-Type': 'application/json', 'terminal': '20', **(headers or {})})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read(), response.headers.get('Content-Type', '')


def api(path, payload=None, headers=None, origin=None):
    body, _ = get((origin or GATEWAY) + path, payload, headers)
    result = json.loads(body)
    if result.get('code') != 0:
        raise RuntimeError(f'{path}: business code {result.get("code")}; check service logs')
    return result.get('data')


def check(include_oss=True):
    for service in ('mall-pc', 'mall-h5', 'admin'):
        body, typ = get('http://' + service + '/')
        assert 'text/html' in typ and b'<html' in body.lower(), f'{service}: missing HTML'
    page = api('/app-api/mall/portal/products?pageNo=1&pageSize=10')
    assert page['total'] > 0 and page['list'], 'Public catalogue is empty'
    product = api('/app-api/mall/portal/products/' + str(page['list'][0]['spuId']))
    assert product['saleable'] and product['skus'], 'Product has no saleable SKUs'
    pic = product['picUrl']
    assert pic.startswith('https://'), 'Product image must use the public OSS HTTPS URL'
    if include_oss:
        body, typ = get(pic)
        assert typ.startswith('image/') and len(body) > 100, 'OSS picture unavailable; verify published demo assets'
    login = api('/app-api/member/mall-auth/platform-login', {'account':'buyer01', 'password':'admin123'})
    assert str(login['tenantId']) == '1', 'Legacy tenant 1 buyer account mapping is missing'
    token = login['accessToken']
    session = api('/app-api/member/platform-account/session', headers={
        'Authorization':'Bearer ' + token, 'tenant-id':str(login['tenantId'])})
    assert session['platformAccountId'], 'Buyer platform identity unavailable'
    other = api('/app-api/member/mall-auth/platform-login', {'account':'buyer2009', 'password':'admin123'})
    assert str(other['tenantId']) == '2009', 'Tenant 2009 buyer account mapping is missing'
    # Go through the browser-facing proxy, not just gateway: a healthy index.html
    # must not hide a missing /admin-api Nginx route.
    admin = api('/admin-api/system/auth/login', {'username':'admin', 'password':'admin123'},
                {'tenant-id':'1'}, origin='http://admin')
    assert admin['accessToken'], 'Admin login failed'
    for frontend in ('mall-pc', 'mall-h5'):
        proxied = api('/app-api/mall/portal/products?pageNo=1&pageSize=1', origin='http://' + frontend)
        assert proxied['list'], f'{frontend}: same-origin API proxy failed'
    not_verified = ['Order/payment/after-sale workflows', 'New uploads and OSS promotion/retries',
                    'Amap browser SDK and live AI customer replies',
                    'Browser interaction and Android device']
    if not include_oss:
        not_verified.insert(0, 'OSS HTTPS reads')
    print(json.dumps({'status':'passed' if include_oss else 'local-only-passed',
        'scope':'startup-smoke-only',
        'ossVerified':include_oss, 'publicProducts':page['total'], 'checks':[
        'PC/H5/admin HTML', 'public catalogue and SKU', 'public OSS product image',
        'tenant 1 and 2009 buyer login, platform identity', 'admin same-origin password login',
        'PC/H5 same-origin API proxy'] if include_oss else [
        'PC/H5/admin HTML', 'public catalogue and SKU',
        'tenant 1 and 2009 buyer login, platform identity', 'admin same-origin password login',
        'PC/H5 same-origin API proxy'],
        'notVerified':not_verified}, ensure_ascii=False))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--local-only', action='store_true', help='Skip OSS reads; NOT full cloud acceptance')
    args = parser.parse_args()
    for attempt in range(12):
        try:
            check(include_oss=not args.local_only)
            return
        except (OSError, ValueError, KeyError, AssertionError, RuntimeError) as error:
            if attempt == 11:
                raise SystemExit(f'Mall smoke check failed: {type(error).__name__}: {error}')
            time.sleep(5)


if __name__ == '__main__':
    main()
