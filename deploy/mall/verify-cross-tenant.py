"""Disposable cross-tenant order regressions. Writes ONLY localhost:24880 test deployment."""
import argparse
import importlib.util
import json
import time
from pathlib import Path

spec = importlib.util.spec_from_file_location('workflows', Path(__file__).with_name('verify-workflows.py'))
workflows = importlib.util.module_from_spec(spec)
spec.loader.exec_module(workflows)


def check_denied(client, path, data=None, method=None):
    try:
        client.api(path, data, method)
    except RuntimeError as error:
        # Business not-found and denied are valid; an internal error is not proof of ACL.
        assert 'code=500,' not in str(error), str(error)
        return
    raise AssertionError('Other buyer unexpectedly accessed ' + path)


def run(completed_order_id=None):
    client = workflows.DemoClient('http://127.0.0.1:24880')
    client.login()
    other = workflows.DemoClient('http://127.0.0.1:24880')
    login = other.api('/member/mall-auth/platform-login', {'account': 'buyer2001', 'password': 'admin123'})
    other.headers.update({'Authorization': 'Bearer ' + login['accessToken'], 'tenant-id': str(login['tenantId'])})
    if completed_order_id:
        order = client.api('/trade/order/get-detail?id=' + str(completed_order_id))
        assert order['status'] == 30, 'Review fixture must already be completed in isolated DB'
        item_id = order['items'][0]['id']
        review = {'orderItemId': item_id, 'descriptionScores': 5, 'benefitScores': 5,
                  'content': '隔离跨租户评价回归', 'anonymous': False, 'picUrls': []}
        check_denied(other, '/trade/order/item/create-comment', review)
        comment_id = client.api('/trade/order/item/create-comment', review)
        refreshed = client.api('/trade/order/get-detail?id=' + str(completed_order_id))
        assert comment_id and refreshed['commentStatus'] and refreshed['items'][0]['commentStatus']
        print('PASS cross-tenant completed-order text review; other buyer denied; status persisted')
        return
    products = client.api('/mall/portal/products?pageNo=1&pageSize=20')['list']
    product = next(p for p in products if int(p['tenantId']) != 2009)
    detail = client.api('/mall/portal/products/' + str(product['spuId']))
    sku = detail['skus'][0]['skuId']
    address = client.api('/member/address/create', {'name': '隔离跨租户验收', 'mobile': '13900000000',
        'areaId': 110101, 'detailAddress': '隔离库，不发货', 'defaultStatus': False})

    def submit(suffix):
        cart_id = client.api('/trade/platform-cart/add', {'skuId': sku, 'quantity': 1})
        request = {'cartItemIds': [cart_id], 'addressId': address, 'idempotencyKey': 'acceptance-cross-' + str(time.time_ns()) + suffix}
        preview = client.api('/trade/platform-checkout/preview', {'cartItemIds': [cart_id], 'addressId': address})
        assert preview['validationPassed'] and preview['submittable']
        result = client.api('/trade/platform-checkout/submit', request)
        assert result['aggregateStatus'] == 'ALL_UNPAID' and not result['planningOnly'] and result['payable']
        assert len(result['childOrders']) == 1 and result['platformPayOrderId']
        again = client.api('/trade/platform-checkout/submit', request)
        assert again['checkoutNo'] == result['checkoutNo']
        assert again['childOrders'][0]['orderId'] == result['childOrders'][0]['orderId']
        return result

    first = submit('cancel')
    order_id = first['childOrders'][0]['orderId']
    order = client.api('/trade/order/get-detail?id=' + str(order_id))
    assert order['id'] == order_id and order['items']
    item = client.api('/trade/order/item/get?id=' + str(order['items'][0]['id']))
    assert item['id'] == order['items'][0]['id']
    check_denied(other, '/trade/order/cancel?id=' + str(order_id), method='DELETE')
    check_denied(other, '/trade/order/item/get?id=' + str(item['id']))
    client.api('/trade/order/cancel?id=' + str(order_id), method='DELETE')
    assert client.api('/trade/order/get-detail?id=' + str(order_id))['status'] == 40
    pay = client.api('/pay/order/get?id=' + str(first['platformPayOrderId']))
    assert pay['status'] == 30
    print('PASS cross-tenant submit/idempotent retry/item read/cancel/payment close; other buyer denied', flush=True)

    second = submit('pay')
    paid_id = second['childOrders'][0]['orderId']
    pay_id = second['platformPayOrderId']
    pay = client.api('/pay/order/get?id=' + str(pay_id))
    assert 'mock' in client.api('/pay/channel/get-enable-code-list?appId=' + str(pay['appId']))
    client.api('/pay/order/submit', {'id': pay_id, 'channelCode': 'mock', 'channelExtras': {}})
    for _ in range(20):
        order = client.api('/trade/order/get-detail?id=' + str(paid_id) + '&sync=true')
        if order['status'] == 10 and order.get('payStatus'): break
        time.sleep(1)
    assert order['status'] == 10 and order.get('payStatus')
    check_denied(client, '/trade/order/cancel?id=' + str(paid_id), method='DELETE')
    print('PASS aggregate mock payment and paid-order cancellation refusal', flush=True)
    print(json.dumps({'cancelledOrderId': order_id, 'paidOrderId': paid_id,
                      'sellerTenantId': second['childOrders'][0]['tenantId'],
                      'reviewRequiresCompletedFixture': True, 'spuId': product['spuId']}, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--allow-demo-writes', action='store_true')
    parser.add_argument('--completed-order-id', type=int)
    args = parser.parse_args()
    if not args.allow_demo_writes: parser.error('--allow-demo-writes is required')
    run(args.completed_order_id)
