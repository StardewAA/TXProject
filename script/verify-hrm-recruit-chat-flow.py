import json
import sys
from urllib.parse import urlencode

from playwright.sync_api import expect, sync_playwright

sys.stdout.reconfigure(encoding='utf-8')

BASE = 'http://127.0.0.1:5173'
GATEWAY = BASE + '/admin-api'
POST_ID = 2021191
CANDIDATE_MOBILE = '13800002103'
CANDIDATE_MOBILE_MASKED = '138****2103'


def login(request, username, password, tenant_id, login_type='enterprise'):
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
    payload = response.json()
    assert payload['code'] == 0, payload
    return payload['data']


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
    payload = response.json()
    assert payload['code'] == 0, {'status': response.status, 'payload': payload}
    return payload['data']


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


with sync_playwright() as playwright:
    browser = playwright.chromium.launch(headless=True)
    context = browser.new_context(viewport={'width': 1440, 'height': 1000})
    request = context.request
    errors = []

    candidate_session = login(request, 'admin123456', 'admin123456', 0, 'personal')
    enterprise_session = login(request, 'qinchuan_gaofeng_1', 'admin123', 2020)

    opened = request_json(
        request,
        candidate_session,
        'POST',
        '/hrm/portal/recruit/chat/conversation/open',
        0,
        {'postId': POST_ID},
    )
    assert opened['postId'] == POST_ID and opened['status'] == 0, opened
    conversation_id = opened['id']
    print('OPENED', conversation_id, opened['status'])

    request_json(
        request,
        candidate_session,
        'POST',
        '/hrm/portal/recruit/chat/message/send',
        0,
        {
            'conversationId': conversation_id,
            'contentType': 1,
            'content': '您好，我对这个岗位很感兴趣，想进一步了解岗位要求。',
        },
    )
    waiting = next(item for item in request_json(
        request,
        candidate_session,
        'GET',
        '/hrm/portal/recruit/chat/conversation/list',
        0,
    ) if item['id'] == conversation_id)
    assert waiting['status'] == 1 and not waiting['applyQualified'], waiting
    print('CANDIDATE_STARTED', waiting['status'])

    hr_list = request_json(
        request,
        enterprise_session,
        'GET',
        '/hrm/recruit/chat/conversation/list',
        2020,
    )
    hr_conversation = next(item for item in hr_list if item['id'] == conversation_id)
    assert hr_conversation['status'] == 1, hr_conversation
    request_json(
        request,
        enterprise_session,
        'POST',
        '/hrm/recruit/chat/message/send',
        2020,
        {
            'conversationId': conversation_id,
            'contentType': 1,
            'content': '您好，感谢关注。可以继续沟通，合适后提交简历。',
        },
    )
    qualified = next(item for item in request_json(
        request,
        candidate_session,
        'GET',
        '/hrm/portal/recruit/chat/conversation/list',
        0,
    ) if item['id'] == conversation_id)
    assert qualified['status'] == 2
    assert qualified['applyQualified']
    assert qualified['applicationStatus'] == 'READY_TO_SUBMIT'
    print('HR_REPLIED', qualified['status'], qualified['applicationStatus'])

    application_id = request_json(
        request,
        candidate_session,
        'POST',
        '/hrm/portal/recruit/application/create',
        0,
        {
            'postId': POST_ID,
            'name': '跨租户流程验收候选人',
            'mobile': CANDIDATE_MOBILE,
            'sex': 1,
            'age': 28,
            'education': 5,
            'graduateSchool': '验收大学',
            'latestWorkPlace': '验收公司',
            'resumeFileName': '验收简历.pdf',
            'resumeFileUrl': 'https://example.com/hrm-recruit-flow-resume.pdf',
            'sourceRemark': 'HRM 沟通投递链路验收',
        },
    )
    submitted = next(item for item in request_json(
        request,
        candidate_session,
        'GET',
        '/hrm/portal/recruit/chat/conversation/list',
        0,
    ) if item['id'] == conversation_id)
    assert submitted['status'] == 2
    assert submitted['applicationStatus'] == 'SUBMITTED'
    assert submitted['applicationId'] == application_id
    print('RESUME_SUBMITTED', application_id, submitted['applicationStatus'])

    hr_after_submit = next(item for item in request_json(
        request,
        enterprise_session,
        'GET',
        '/hrm/recruit/chat/conversation/list',
        2020,
    ) if item['id'] == conversation_id)
    assert hr_after_submit['candidateId'] and hr_after_submit['applicationId'] == application_id
    candidates = request_json(
        request,
        enterprise_session,
        'GET',
        '/hrm/recruit/candidate/page',
        2020,
        params={'pageNo': 1, 'pageSize': 100, 'postId': POST_ID, 'search': CANDIDATE_MOBILE},
    )
    candidate = next(item for item in candidates['list'] if item['mobile'] == CANDIDATE_MOBILE_MASKED)
    assert candidate['applicationId'] == application_id
    print('CANDIDATE_VISIBLE', candidate['id'], candidate['applicationId'])

    page = context.new_page()
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.goto(BASE + '/#/auth/login', wait_until='networkidle')
    set_session(page, candidate_session, 0)
    page.evaluate("window.location.hash = '#/portal/recruit?section=messages&postId=' + '" + str(POST_ID) + "'")
    page.wait_for_load_state('networkidle')
    expect(page.locator('.message-page')).to_be_visible(timeout=30000)
    expect(page.locator('.contact-status', has_text='招聘方已回复').first).to_be_visible()
    expect(page.locator('.contact-application-status', has_text='已投递').first).to_be_visible()
    print('PERSONAL_UI=PASS')

    page2 = context.new_page()
    page2.on('pageerror', lambda error: errors.append(str(error)))
    page2.goto(BASE + '/#/auth/login', wait_until='networkidle')
    set_session(page2, enterprise_session, 2020)
    page2.evaluate("window.location.hash = '#/hrm/recruit/chat'")
    page2.wait_for_load_state('networkidle')
    expect(page2.locator('.chat-workspace')).to_be_visible(timeout=30000)
    for column in ['名字', '应聘职位', '性别', '年龄', '学历', '毕业院校', '发来的消息', '操作']:
        expect(page2.get_by_text(column, exact=True)).to_be_visible()
    expect(page2.get_by_text('已投递', exact=True).first).to_be_visible()
    print('ENTERPRISE_CHAT_UI=PASS')

    page2.evaluate("window.location.hash = '#/hrm/recruit/candidate'")
    page2.wait_for_load_state('networkidle')
    expect(page2.get_by_text(CANDIDATE_MOBILE_MASKED, exact=True)).to_be_visible(timeout=30000)
    print('ENTERPRISE_CANDIDATE_UI=PASS')
    print('PAGE_ERRORS', errors)
    assert not errors
    browser.close()
