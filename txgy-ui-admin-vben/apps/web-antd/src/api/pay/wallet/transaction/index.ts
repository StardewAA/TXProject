import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace WalletTransactionApi {
  /** 钱包交易流水信息 */
  export interface Transaction {
    id: number;
    walletId: number;
    /** 业务分类：1 充值、2 充值退款、3 支付、4 支付退款、5 更新余额、6 转账 */
    bizType: number;
    title: string;
    price: number;
    balance: number;
    createTime: string;
  }

  /** 钱包流水分页查询参数（与后端 PayWalletTransactionPageReqVO 对应） */
  export interface TransactionPageReqVO extends PageParam {
    walletId?: number;
    userId?: number;
    userType?: number;
  }
}

/** 查询钱包交易流水列表 */
export function getTransactionPage(
  params: WalletTransactionApi.TransactionPageReqVO,
) {
  return requestClient.get<PageResult<WalletTransactionApi.Transaction>>(
    '/pay/wallet-transaction/page',
    {
      params,
    },
  );
}
