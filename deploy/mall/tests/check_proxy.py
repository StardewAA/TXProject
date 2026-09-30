"""Run only on the isolated handoff network; no external services or writes."""
import json
import urllib.error
import urllib.request


for path in ('/admin-api/system/auth/login', '/app-api/mall/portal/products',
             '/admin-api/infra/file/4/get/picture.png', '/infra/ws'):
    request = urllib.request.Request('http://admin-check' + path, headers={
        'Authorization': 'Bearer fixture', 'tenant-id': '1'})
    with urllib.request.urlopen(request, timeout=10) as response:
        data = json.load(response)
        assert data == {'path': path, 'authorized': True, 'tenant': '1'}, data
    print('PASS proxy', path)

with urllib.request.urlopen('http://admin-check/not-a-real-page', timeout=10) as response:
    assert 'text/html' in response.headers.get('Content-Type', '')
    assert 'no-cache' in response.headers.get('Cache-Control', '')
print('PASS SPA fallback with revalidation')

try:
    urllib.request.urlopen('http://admin-check/assets/missing.js', timeout=10)
    raise AssertionError('Missing asset returned HTML/200')
except urllib.error.HTTPError as error:
    assert error.code == 404
print('PASS missing asset returns 404')
