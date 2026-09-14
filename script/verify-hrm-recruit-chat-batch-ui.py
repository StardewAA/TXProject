import json
import sys
from urllib.parse import urlencode

from playwright.sync_api import expect, sync_playwright

sys.stdout.reconfigure(encoding='utf-8')

BASE = 'http://127.0.0.1:5173'
GATEWAY = BASE + '/admin-api'
POST_ID = 2021191
HR_USERNAME = 'qinchuan_gaofeng_1'
HR_PASSWORD = 'admin123'
HR_TENANT_ID = 2020
PERSONAL_TENANT_ID = 0
PASSWORD = 'hrmTest1234'
TEST_USERS = [
    ('hrmtest17892769861', '批量求职者1'),
    ('hrmtest17892769862', '批量求职者2'),
    ('hrmtest17892769863', '批量求职者3'),
    ('hrmtest17892769864', '批量求职者4'),
    ('hrmtest17892769865', '批量求职者5'),
]


def read_payload(response):
    payload = response.json()
    if payload.get('code') != 0:
        raise AssertionError({'status': response.status, 'payload': payload})
    return payload.get('data')


def login(request, username, password, tenant_id, login_type):
    response = request.post(
        GATEWAY + '/system/auth/login',
        headers={'tenant-id': str(tenant_id), 'Content-Type': 'application/json'},
        data=json.dumps({
            'username': username,
            'password': password,
            'tenantId': tenant_id,
            'loginType': login_type,
        }),
    )
    return read_payload(response)


def request_json(request, session, method, path, tenant_id, body=None, params=None):
    url = GATEWAY + path
    if params:
        url += '?' + urlencode(params)
    headers = {
        'Authorization': 'Bearer ' + session['accessToken'],
        'tenant-id': str(tenant_id),
    }
    if body is not None:
        headers['Content-Type'] = 'application/json'
    response = request.fetch(
        url,
        method=method,
        headers=headers,
        data=json.dumps(body) if body is not None else None,
    )
    return read_payload(response)


def set_session(page, session, tenant_id):
    page.evaluate(
        '''([session, tenantId]) => {
          const app = document.querySelector('#app').__vue_app__;
          const access = app.config.globalProperties.$pinia._s.get('core-access');
          access.setAccessToken(session.accessToken);
          access.setRefreshToken(session.refreshToken);
          access.setTenantId(tenantId);
        }''',
        [session, tenant_id],
    )


def create_profile(request, session, nickname, index):
    return request_json(
        request,
        session,
        'POST',
        '/hrm/portal/resume/create',
        PERSONAL_TENANT_ID,
        {
            'title': '求职资料（系统）',
            'content': json.dumps({
                'name': nickname,
                'gender': 1 if index % 2 else 2,
                'birthMonth': f'199{index}-0{index}-01',
                'city': '西安',
                'education': '本科',
                'educationStart': '2016-09',
                'educationEnd': '2020-06',
                'school': f'批量验收大学{index}',
                'major': '工程管理',
                'schoolType': '全日制',
                'identity': 'worker',
                'employmentStatus': '在职-月内到岗',
                'workStart': f'202{index}-07',
                'jobStart': f'202{index}-07',
                'company': f'批量验收公司{index}',
                'strengths': '用于 HRM 招聘沟通页面验收的测试求职资料。',
                'postTypeId': 1,
                'postTypeName': '工程管理',
                'completedAt': '2026-09-13T00:00:00.000Z',
            }, ensure_ascii=False),
            'remark': 'HRM 招聘沟通页面验收资料',
        },
    )


def main():
    page_errors = []
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        context = browser.new_context(viewport={'width': 1440, 'height': 1000})
        request = context.request
        candidate_sessions = []
        for index, (username, nickname) in enumerate(TEST_USERS, start=1):
            session = login(request, username, PASSWORD, PERSONAL_TENANT_ID, 'personal')
            create_profile(request, session, nickname, index)
            candidate_sessions.append(session)
            print('PROFILE_READY', username)

        personal_page = context.new_page()
        personal_page.on('pageerror', lambda error: page_errors.append(str(error)))
        personal_page.goto(BASE + '/#/auth/login', wait_until='networkidle')
        set_session(personal_page, candidate_sessions[0], PERSONAL_TENANT_ID)
        personal_page.evaluate("window.location.hash = '#/portal/recruit?section=messages&postId=' + '" + str(POST_ID) + "'")
        personal_page.wait_for_load_state('networkidle')
        expect(personal_page.locator('.message-page')).to_be_visible(timeout=30000)
        expect(personal_page.locator('.contact-status', has_text='招聘方已回复').first).to_be_visible()
        expect(personal_page.locator('.contact-application-status', has_text='已投递').first).to_be_visible()
        print('PERSONAL_MESSAGES_UI=PASS')

        hr_session = login(request, HR_USERNAME, HR_PASSWORD, HR_TENANT_ID, 'enterprise')
        enterprise_page = context.new_page()
        enterprise_page.on('pageerror', lambda error: page_errors.append(str(error)))
        enterprise_page.goto(BASE + '/#/auth/login', wait_until='networkidle')
        set_session(enterprise_page, hr_session, HR_TENANT_ID)
        enterprise_page.evaluate("window.location.hash = '#/hrm/recruit/chat'")
        enterprise_page.wait_for_load_state('networkidle')
        expect(enterprise_page.locator('.chat-workspace')).to_be_visible(timeout=30000)
        for _, nickname in TEST_USERS:
            expect(enterprise_page.get_by_text(nickname, exact=True).first).to_be_visible(timeout=30000)
        print('ENTERPRISE_CHAT_UI=PASS count=5')

        if page_errors:
            raise AssertionError({'pageErrors': page_errors})
        browser.close()

    print('BATCH_CHAT_UI_TEST=PASS')


if __name__ == '__main__':
    main()
