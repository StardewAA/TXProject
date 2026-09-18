import json
import sys
import time
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


def read_payload(response):
    try:
        payload = response.json()
    except Exception as error:
        raise AssertionError(f'HTTP {response.status}: {response.text()}') from error
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


def register_personal(request, username, nickname):
    response = request.post(
        GATEWAY + '/system/auth/register',
        headers={'Content-Type': 'application/json'},
        data=json.dumps({
            'username': username,
            'nickname': nickname,
            'password': PASSWORD,
            'registerType': 'personal',
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


def find_conversation(conversations, conversation_id):
    return next(item for item in conversations if item['id'] == conversation_id)


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


def main():
    run_id = str(int(time.time()))
    records = []
    page_errors = []
    first_candidate_session = None

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        context = browser.new_context(viewport={'width': 1440, 'height': 1000})
        request = context.request
        hr_session = login(request, HR_USERNAME, HR_PASSWORD, HR_TENANT_ID, 'enterprise')

        for index in range(1, 6):
            username = f'hrmtest{run_id}{index}'
            nickname = f'批量求职者{index}'
            mobile = f'139{run_id[-7:]}{index}'
            candidate_session = register_personal(request, username, nickname)
            create_profile(request, candidate_session, nickname, index)
            if first_candidate_session is None:
                first_candidate_session = candidate_session
            opened = request_json(
                request,
                candidate_session,
                'POST',
                '/hrm/portal/recruit/chat/conversation/open',
                PERSONAL_TENANT_ID,
                {'postId': POST_ID},
            )
            if opened['status'] != 0:
                raise AssertionError(opened)

            conversation_id = opened['id']
            request_json(
                request,
                candidate_session,
                'POST',
                '/hrm/portal/recruit/chat/message/send',
                PERSONAL_TENANT_ID,
                {
                    'conversationId': conversation_id,
                    'contentType': 1,
                    'content': f'您好，我是{nickname}，对这个岗位很感兴趣，想进一步了解岗位要求。',
                },
            )
            waiting = find_conversation(
                request_json(
                    request,
                    candidate_session,
                    'GET',
                    '/hrm/portal/recruit/chat/conversation/list',
                    PERSONAL_TENANT_ID,
                ),
                conversation_id,
            )
            if waiting['status'] != 1 or waiting['applyQualified']:
                raise AssertionError({'stage': 'candidate-started', 'record': waiting})

            hr_waiting = find_conversation(
                request_json(
                    request,
                    hr_session,
                    'GET',
                    '/hrm/recruit/chat/conversation/list',
                    HR_TENANT_ID,
                ),
                conversation_id,
            )
            if hr_waiting['status'] != 1 or not hr_waiting['lastContent']:
                raise AssertionError({'stage': 'hr-inbox', 'record': hr_waiting})

            request_json(
                request,
                hr_session,
                'POST',
                '/hrm/recruit/chat/message/send',
                HR_TENANT_ID,
                {
                    'conversationId': conversation_id,
                    'contentType': 1,
                    'content': f'您好，{nickname}，感谢关注。可以继续沟通，合适后提交简历。',
                },
            )
            qualified = find_conversation(
                request_json(
                    request,
                    candidate_session,
                    'GET',
                    '/hrm/portal/recruit/chat/conversation/list',
                    PERSONAL_TENANT_ID,
                ),
                conversation_id,
            )
            if qualified['status'] != 2 or not qualified['applyQualified']:
                raise AssertionError({'stage': 'hr-replied', 'record': qualified})
            if qualified['applicationStatus'] != 'READY_TO_SUBMIT':
                raise AssertionError({'stage': 'ready-to-submit', 'record': qualified})

            application_id = request_json(
                request,
                candidate_session,
                'POST',
                '/hrm/portal/recruit/application/create',
                PERSONAL_TENANT_ID,
                {
                    'postId': POST_ID,
                    'name': nickname,
                    'mobile': mobile,
                    'sex': 1 if index % 2 else 2,
                    'age': 24 + index,
                    'workTime': index,
                    'education': 5,
                    'graduateSchool': f'批量验收大学{index}',
                    'latestWorkPlace': f'批量验收公司{index}',
                    'resumeFileName': f'{nickname}-简历.pdf',
                    'resumeFileUrl': f'https://example.com/hrm-batch-{run_id}-{index}.pdf',
                    'sourceRemark': 'HRM 招聘沟通批量验收数据',
                },
            )
            submitted = find_conversation(
                request_json(
                    request,
                    candidate_session,
                    'GET',
                    '/hrm/portal/recruit/chat/conversation/list',
                    PERSONAL_TENANT_ID,
                ),
                conversation_id,
            )
            if submitted['applicationStatus'] != 'SUBMITTED' or submitted['applicationId'] != application_id:
                raise AssertionError({'stage': 'submitted', 'record': submitted})

            hr_submitted = find_conversation(
                request_json(
                    request,
                    hr_session,
                    'GET',
                    '/hrm/recruit/chat/conversation/list',
                    HR_TENANT_ID,
                ),
                conversation_id,
            )
            if hr_submitted['candidateId'] is None or hr_submitted['applicationId'] != application_id:
                raise AssertionError({'stage': 'hr-submitted', 'record': hr_submitted})

            candidate_page = request_json(
                request,
                hr_session,
                'GET',
                '/hrm/recruit/candidate/page',
                HR_TENANT_ID,
                params={'pageNo': 1, 'pageSize': 100, 'postId': POST_ID, 'search': mobile},
            )
            candidate = next(
                item for item in candidate_page['list']
                if item.get('mobile') == mobile[:3] + '****' + mobile[-4:]
            )
            if candidate['applicationId'] != application_id:
                raise AssertionError({'stage': 'candidate-list', 'record': candidate})

            record = {
                'username': username,
                'password': PASSWORD,
                'nickname': nickname,
                'mobile': mobile,
                'conversationId': conversation_id,
                'applicationId': application_id,
                'candidateId': candidate['id'],
            }
            records.append(record)
            print('PASS', json.dumps(record, ensure_ascii=False))

        page = context.new_page()
        page.on('pageerror', lambda error: page_errors.append(str(error)))
        page.goto(BASE + '/#/auth/login', wait_until='networkidle')
        print('RECON', (page.locator('body').inner_text() or '').splitlines()[0:3])
        set_session(page, first_candidate_session, PERSONAL_TENANT_ID)
        page.evaluate("window.location.hash = '#/portal/recruit?section=messages&postId=' + '" + str(POST_ID) + "'")
        page.wait_for_load_state('networkidle')
        expect(page.locator('.message-page')).to_be_visible(timeout=30000)
        expect(page.locator('.contact-status', has_text='招聘方已回复').first).to_be_visible()
        expect(page.locator('.contact-application-status', has_text='已投递').first).to_be_visible()
        print('PERSONAL_UI=PASS')

        enterprise_page = context.new_page()
        enterprise_page.on('pageerror', lambda error: page_errors.append(str(error)))
        enterprise_page.goto(BASE + '/#/auth/login', wait_until='networkidle')
        set_session(enterprise_page, hr_session, HR_TENANT_ID)
        enterprise_page.evaluate("window.location.hash = '#/hrm/recruit/chat'")
        enterprise_page.wait_for_load_state('networkidle')
        expect(enterprise_page.locator('.chat-workspace')).to_be_visible(timeout=30000)
        for record in records:
            expect(enterprise_page.get_by_text(record['nickname'], exact=True).first).to_be_visible(timeout=30000)
        print('ENTERPRISE_CHAT_UI=PASS count=5')

        if page_errors:
            raise AssertionError({'pageErrors': page_errors})
        browser.close()

    print('BATCH_CHAT_TEST=PASS')
    print(json.dumps({'postId': POST_ID, 'hrTenantId': HR_TENANT_ID, 'records': records}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
