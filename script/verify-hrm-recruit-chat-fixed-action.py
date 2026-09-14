import json
import sys

from playwright.sync_api import expect, sync_playwright

sys.stdout.reconfigure(encoding='utf-8')

BASE = 'http://127.0.0.1:5173'
GATEWAY = BASE + '/admin-api'
HR_USERNAME = 'qinchuan_gaofeng_1'
HR_PASSWORD = 'admin123'
HR_TENANT_ID = 2020


def login(request):
    response = request.post(
        GATEWAY + '/system/auth/login',
        headers={'tenant-id': str(HR_TENANT_ID), 'Content-Type': 'application/json'},
        data=json.dumps({
            'username': HR_USERNAME,
            'password': HR_PASSWORD,
            'tenantId': HR_TENANT_ID,
            'loginType': 'enterprise',
        }),
    )
    payload = response.json()
    if payload.get('code') != 0:
        raise AssertionError(payload)
    return payload['data']


def set_session(page, session):
    page.evaluate(
        '''session => {
          const app = document.querySelector('#app').__vue_app__;
          const access = app.config.globalProperties.$pinia._s.get('core-access');
          access.setAccessToken(session.accessToken);
          access.setRefreshToken(session.refreshToken);
          access.setTenantId(2020);
        }''',
        session,
    )


with sync_playwright() as playwright:
    browser = playwright.chromium.launch(headless=True)
    context = browser.new_context(viewport={'width': 1440, 'height': 1000})
    request = context.request
    page_errors = []
    page = context.new_page()
    page.on('pageerror', lambda error: page_errors.append(str(error)))
    session = login(request)
    page.goto(BASE + '/#/auth/login', wait_until='networkidle')
    set_session(page, session)
    page.evaluate("window.location.hash = '#/hrm/recruit/chat'")
    page.wait_for_load_state('networkidle')
    expect(page.locator('.chat-workspace')).to_be_visible(timeout=30000)
    table = page.locator('.conversation-table')
    action_heading = page.locator('.conversation-head .action-heading')
    action_cell = page.locator('.conversation-data-row .row-action').first
    expect(action_heading).to_have_count(1)
    expect(action_cell).to_be_visible(timeout=30000)
    table_box = table.bounding_box()
    before_heading = action_heading.bounding_box()
    before_action = action_cell.bounding_box()
    scroll_metrics = table.evaluate('(element) => ({clientWidth: element.clientWidth, scrollWidth: element.scrollWidth, scrollLeft: element.scrollLeft})')
    if scroll_metrics['scrollWidth'] <= scroll_metrics['clientWidth']:
        raise AssertionError({'stage': 'horizontal-overflow', 'metrics': scroll_metrics})
    table.evaluate('(element) => { element.scrollLeft = element.scrollWidth; }')
    page.wait_for_timeout(200)
    after_heading = action_heading.bounding_box()
    after_action = action_cell.bounding_box()
    if not table_box or not before_heading or not after_heading or not before_action or not after_action:
        raise AssertionError('操作列定位信息为空')
    viewport_width = page.evaluate('window.innerWidth')
    for name, box in [('操作表头', after_heading), ('操作按钮', after_action)]:
        if box['x'] < table_box['x'] or box['x'] + box['width'] > viewport_width:
            raise AssertionError({'stage': name, 'viewportWidth': viewport_width, 'box': box})
    if abs((after_heading['x'] + after_heading['width']) - (before_heading['x'] + before_heading['width'])) > 16 or abs((after_action['x'] + after_action['width']) - (before_action['x'] + before_action['width'])) > 16:
        raise AssertionError({
            'stage': 'sticky-position',
            'beforeHeading': before_heading,
            'afterHeading': after_heading,
            'beforeAction': before_action,
            'afterAction': after_action,
        })
    if page_errors:
        raise AssertionError({'pageErrors': page_errors})
    print('FIXED_ACTION_UI=PASS')
    print(json.dumps({'scrollMetrics': scroll_metrics, 'viewportWidth': viewport_width}, ensure_ascii=False))
    browser.close()
