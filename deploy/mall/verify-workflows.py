"""Writes demo business data ONLY to the explicit isolated acceptance port.

Never run this against the normal deployment. No cloud uploads or real payments.
Orders/addresses remain in the disposable test database as acceptance evidence.
"""
import argparse
import json
import time
import urllib.parse
import urllib.request


class DemoClient:
    def __init__(self, origin):
        parsed = urllib.parse.urlsplit(origin)
        if parsed.hostname not in ('localhost', '127.0.0.1') or parsed.port != 24880:
            raise ValueError('Refusing writes: use isolated localhost:24880 only')
        self.origin = origin.rstrip('/')
        self.headers = {'Content-Type': 'application/json', 'terminal': '20'}

    def api(self, path, data=None, method=None):
        body = None if data is None else json.dumps(data).encode()
        request = urllib.request.Request(self.origin + '/app-api' + path, data=body,
                                         headers=self.headers, method=method)
        # Ignore host system proxies for this loopback-only test.
        with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(request, timeout=40) as response:
            result = json.load(response)
        if result.get('code') != 0:
            raise RuntimeError(f'{path}: code={result.get("code")}, {result.get("msg")}')
        return result.get('data')

    def login(self):
        login = self.api('/member/mall-auth/platform-login',
                         {'account': 'buyer2009', 'password': 'admin123'})
        assert str(login['tenantId']) == '2009', 'Unexpected demo tenant'
        self.headers.update({'Authorization': 'Bearer ' + login['accessToken'], 'tenant-id': '2009'})


def run(origin):
    client = DemoClient(origin)
    client.login()
    print('PASS demo member login (tenant 2009)', flush=True)
    products = client.api('/mall/portal/products?pageNo=1&pageSize=10&shopId=940501')
    assert products['list'], 'Seed shop has no products'
    detail = client.api('/mall/portal/products/' + str(products['list'][0]['spuId']))
    assert detail['saleable'] and detail['skus'], 'Seed product is not saleable'
    sku_id = detail['skus'][0]['skuId']
    address_id = client.api('/member/address/create', {
        'name': '部署验收测试', 'mobile': '13900000000', 'areaId': 110101,
        'detailAddress': '隔离测试地址，不发货', 'defaultStatus': False})
    assert address_id
    print('PASS manual address (no map credentials)', flush=True)
    cart_id = client.api('/trade/cart/add', {'skuId': sku_id, 'count': 1})
    cart = client.api('/trade/cart/list')
    assert any(str(item['id']) == str(cart_id) for item in cart['validList'])
    print('PASS tenant cart add/read', flush=True)
    payload = {'items': [{'skuId': sku_id, 'count': 1, 'cartId': cart_id}],
               'pointStatus': False, 'deliveryType': 1, 'addressId': address_id,
               'remark': 'isolated deployment acceptance; mock payment only'}
    params = urllib.parse.urlencode({'items[0].skuId': sku_id, 'items[0].count': 1,
                                    'pointStatus': 'false', 'deliveryType': 1, 'addressId': address_id})
    settlement = client.api('/trade/order/settlement?' + params)
    assert settlement, 'No settlement result'
    print('PASS order settlement', flush=True)
    first = client.api('/trade/order/create', payload)
    first_id = first['id']
    client.api('/trade/order/cancel?id=' + str(first_id), method='DELETE')
    cancelled = client.api('/trade/order/get-detail?id=' + str(first_id))
    assert cancelled['status'] == 40, cancelled['status']
    print('PASS create/cancel unpaid order', flush=True)
    payload['items'][0].pop('cartId')
    second = client.api('/trade/order/create', payload)
    second_id, pay_id = second['id'], second['payOrderId']
    pay_order = client.api('/pay/order/get?id=' + str(pay_id))
    channels = client.api('/pay/channel/get-enable-code-list?appId=' + str(pay_order['appId']))
    assert 'mock' in channels, 'Mock payment is not enabled; never substitute a real channel'
    client.api('/pay/order/submit', {'id': pay_id, 'channelCode': 'mock', 'channelExtras': {}})
    paid = None
    for _ in range(15):
        paid = client.api('/trade/order/get-detail?id=' + str(second_id) + '&sync=true')
        if paid['status'] == 10 and paid.get('payStatus'):
            break
        time.sleep(2)
    assert paid['status'] == 10 and paid.get('payStatus'), 'Payment/order status did not synchronize'
    print('PASS mock payment and paid-order synchronization', flush=True)
    after_sale_id = client.api('/trade/after-sale/create', {
        'orderItemId': paid['items'][0]['id'], 'way': 10, 'refundPrice': 1,
        'applyReason': '隔离验收', 'applyDescription': '仅测试申请及取消，不执行退款', 'applyPicUrls': []})
    after_sale = client.api('/trade/after-sale/get?id=' + str(after_sale_id))
    assert after_sale and str(after_sale['id']) == str(after_sale_id)
    client.api('/trade/after-sale/cancel?id=' + str(after_sale_id), method='DELETE')
    print('PASS after-sale apply/read/cancel', flush=True)
    print(json.dumps({'status': 'passed', 'scope': 'isolated-demo-api-workflows',
                      'cancelledOrderId': first_id, 'paidOrderId': second_id, 'afterSaleId': after_sale_id,
                      'notVerified': ['Physical delivery/receipt', 'Refund completion',
                                      'Browser interactions', 'Android device', 'OSS upload']}, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--origin', default='http://127.0.0.1:24880')
    parser.add_argument('--allow-demo-writes', action='store_true')
    arguments = parser.parse_args()
    if not arguments.allow_demo_writes:
        parser.error('--allow-demo-writes is required; this creates disposable demo records')
    run(arguments.origin)
