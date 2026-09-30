import sys
import re
from pathlib import Path
from playwright.sync_api import expect, sync_playwright

sys.stdout.reconfigure(encoding='utf-8')
workspace = Path(__file__).resolve().parents[2]
with sync_playwright() as playwright:
    browser = playwright.chromium.launch(headless=True)
    context = browser.new_context(storage_state=str(workspace / 'hrm_state.json'))
    page = context.new_page()
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    previous_check = (workspace / 'verify_candidate_education.py').read_text(encoding='utf-8')
    username = re.search(r'username.*?fill\(\x27([^\x27]+)', previous_check).group(1)
    password = re.search(r'password.*?fill\(\x27([^\x27]+)', previous_check).group(1)
    login = context.request.post('http://127.0.0.1:5173/admin-api/system/auth/login', headers={'tenant-id': '2020'}, data={'username': username, 'password': password, 'tenantId': 2020, 'loginType': 'enterprise'}).json()
    assert login['code'] == 0, login.get('msg')
    page.goto('http://127.0.0.1:5173/#/portal/recruit', wait_until='networkidle')
    result = page.evaluate('''async (session) => {
      const app = document.querySelector('#app').__vue_app__;
      const store = app.config.globalProperties.$pinia._s.get('core-access');
      store.setAccessToken(session.accessToken);
      store.setRefreshToken(session.refreshToken);
      const response = await fetch('/admin-api/system/auth/get-permission-info', {
        headers: { Authorization: `Bearer ${store.accessToken}`, 'tenant-id': '1' }
      });
      const data = await response.json();
      store.setTenantId(1);
      store.setVisitTenantId(1);
      return { code: data.code, msg: data.msg };
    }''', login['data'])
    assert result['code'] == 403, result
    print('MISMATCH_REPRODUCTION', result)
    page.goto('http://127.0.0.1:5173/#/auth/login', wait_until='networkidle')
    print('LOGIN_URL', page.url)
    expect(page.locator('input[name="username"]')).to_be_visible(timeout=15000)
    page.goto('http://127.0.0.1:5173/#/auth/register', wait_until='networkidle')
    expect(page.locator('input[name="username"]')).to_be_visible()
    page.goto('http://127.0.0.1:5173/#/auth/login', wait_until='networkidle')
    expect(page.locator('input[name="username"]')).to_be_visible()
    page.get_by_role('combobox').first.click()
    page.get_by_text('秦川化工产业园发展有限公司', exact=True).last.click()
    page.locator('input[name="username"]').fill(username)
    page.locator('input[name="password"]').fill(password)
    page.locator('button[aria-label="login"]').click()
    page.wait_for_url(lambda url: '/auth/' not in url, timeout=30000)
    page.wait_for_load_state('networkidle')
    session_check = page.evaluate('''async () => {
      const store = document.querySelector('#app').__vue_app__.config.globalProperties.$pinia._s.get('core-access');
      const response = await fetch('/admin-api/system/auth/get-permission-info', {
        headers: { Authorization: `Bearer ${store.accessToken}`, 'tenant-id': String(store.tenantId) }
      });
      return { code: (await response.json()).code, tenantId: store.tenantId, visitTenantId: store.visitTenantId };
    }''')
    assert session_check == {'code': 0, 'tenantId': 2020, 'visitTenantId': None}, session_check
    print('LOGIN_SESSION', session_check)
    recovery = page.evaluate('''async () => {
      const stores = document.querySelector('#app').__vue_app__.config.globalProperties.$pinia._s;
      const access = stores.get('core-access');
      try {
        const info = await stores.get('auth').fetchUserInfo();
        return Boolean(info.user);
      } finally {
        access.setTenantId(2020);
      }
    }''')
    assert recovery, 'Authenticated permission request must succeed'
    print('AUTHENTICATED_PERMISSION=PASS')
    print('ERRORS', errors)
    assert not errors
    print('AUTH_RECOVERY=PASS')
    browser.close()
