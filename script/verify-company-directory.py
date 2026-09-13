import json
import sys
from pathlib import Path

from playwright.sync_api import expect, sync_playwright

sys.stdout.reconfigure(encoding='utf-8')
workspace = Path(__file__).resolve().parents[2]
base_url = 'http://127.0.0.1:5173'

with sync_playwright() as playwright:
    browser = playwright.chromium.launch(headless=True)
    context = browser.new_context(storage_state=str(workspace / 'hrm_state.json'), viewport={'width': 1600, 'height': 1100})
    page = context.new_page()
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.goto(base_url + '/#/portal/recruit?section=companies', wait_until='networkidle')
    print('INITIAL_URL', page.url)
    if '/auth/' in page.url:
        raise RuntimeError('Existing session expired; authenticated page verification required')
    directory = page.locator('.company-directory')
    expect(directory.locator('.company-result-count')).to_have_text('共 25 家公司', timeout=60000)
    expect(directory.locator('.company-card')).to_have_count(12)
    page.screenshot(path=str(workspace / '公司页验收.png'), full_page=True)
    directory.get_by_role('group', name='招聘状态').get_by_role('button', name='正在招聘', exact=True).click()
    expect(directory.locator('.company-card')).to_have_count(2)
    expect(directory.locator('.company-result-count')).to_have_text('共 2 家公司')
    directory.get_by_role('button', name='清空筛选条件', exact=True).click()
    source_posts = []
    source_page = 1
    while True:
        response = context.request.get(base_url + f'/admin-api/hrm/portal/recruit/post/page?pageNo={source_page}&pageSize=100').json()
        assert response['code'] == 0
        source_posts.extend(response['data']['list'])
        if len(source_posts) >= response['data']['total']:
            break
        source_page += 1
    sample = next(post for post in source_posts if post.get('areaName') and post.get('postTypeName'))
    sample_city = sample['areaName'].split()[1] if len(sample['areaName'].split()) > 1 else sample['areaName'].split()[0]
    sample_type = sample['postTypeName']
    matching_tenants = {post['tenantId'] for post in source_posts if sample_city in post.get('areaName', '') and post.get('postTypeName') == sample_type}
    city_group = directory.get_by_role('group', name='招聘地点')
    city_group.get_by_role('button', name=sample_city, exact=True).click()
    directory.get_by_role('group', name='职位类型').get_by_role('button', name=sample_type, exact=True).click()
    expect(directory.locator('.company-result-count')).to_have_text(f'共 {len(matching_tenants)} 家公司')
    directory.locator('.company-card-main').click()
    filtered_dialog = page.get_by_role('dialog')
    for detail in filtered_dialog.locator('.company-detail-post small').all_text_contents():
        assert sample_city in detail and sample_type in detail, detail
    filtered_dialog.locator('.ant-modal-close').click()
    directory.get_by_role('button', name='清空筛选条件', exact=True).click()
    search = directory.get_by_role('searchbox', name='搜索职位或公司')
    search.fill('秦川')
    search.press('Enter')
    expect(directory.locator('.company-card')).to_have_count(1)
    expect(directory.locator('.company-name')).to_have_text('秦川化工产业园发展有限公司')
    directory.locator('.company-card-main').click()
    dialog = page.get_by_role('dialog')
    expect(dialog.locator('.company-detail-post')).to_have_count(6)
    expect(dialog.locator('.company-detail-caption')).to_contain_text('201 个')
    dialog.locator('.ant-modal-close').click()
    search.fill('不存在的公司XYZ987654')
    search.press('Enter')
    expect(directory.get_by_role('heading', name='暂无符合条件的公司')).to_be_visible()
    directory.locator('.company-filters').get_by_role('button', name='清空筛选条件', exact=True).click()
    directory.locator('.ant-pagination-item-3').click()
    expect(directory.locator('.company-card')).to_have_count(1)
    directory.get_by_role('group', name='招聘状态').get_by_role('button', name='暂无公开职位', exact=True).click()
    expect(directory.locator('.company-result-count')).to_have_text('共 23 家公司')
    directory.locator('.company-card-main').first.click()
    expect(dialog.get_by_role('heading', name='这家公司暂无公开职位')).to_be_visible()
    dialog.locator('.ant-modal-close').click()
    directory.get_by_role('button', name='清空筛选条件', exact=True).click()
    page.set_viewport_size({'width': 390, 'height': 844})
    page.screenshot(path=str(workspace / '公司页手机验收.png'), full_page=True)
    assert directory.evaluate('(element) => element.scrollWidth <= element.clientWidth'), 'Directory overflows on mobile'
    page.set_viewport_size({'width': 1600, 'height': 1100})
    directory.locator('.company-hot-post').first.click()
    page.wait_for_url('**/portal/recruit/detail?id=*')
    page.wait_for_load_state('networkidle')
    print('DETAIL_URL', page.url)
    print('PAGE_ERRORS', json.dumps(errors, ensure_ascii=False))
    assert not errors, errors
    print('COMPANY_DIRECTORY_VERIFICATION=PASS')
    browser.close()
