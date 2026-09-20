import { beforeEach, describe, expect, it, vi } from 'vitest';

const { deleteRequest } = vi.hoisted(() => ({ deleteRequest: vi.fn() }));
vi.mock('#/api/request', () => ({ requestClient: { delete: deleteRequest } }));

import { deleteSpu } from './index';

describe('后台商品删除请求版本保护', () => {
  beforeEach(() => { deleteRequest.mockReset(); });

  it('删除请求必须将当前内容版本与商品编号共同作为query参数传递', async () => {
    deleteRequest.mockResolvedValue(true);
    await expect(deleteSpu(8, 3)).resolves.toBe(true);
    expect(deleteRequest).toHaveBeenCalledExactlyOnceWith('/product/spu/delete', {
      params: { id: 8, expectedVersion: 3 },
    });
  });

  it('合法历史版本0不能被丢弃', async () => {
    deleteRequest.mockResolvedValue(true);
    await deleteSpu(8, 0);
    expect(deleteRequest).toHaveBeenCalledWith('/product/spu/delete', {
      params: { id: 8, expectedVersion: 0 },
    });
  });

  it('缺失、负数、小数以及字符串版本不得发出删除请求', () => {
    for (const version of [undefined, null, -1, 0.5, Number.NaN, '3', Number.MAX_SAFE_INTEGER + 1]) {
      expect(() => deleteSpu(8, version as number)).toThrow('内容版本无效');
    }
    expect(deleteRequest).not.toHaveBeenCalled();
  });

  it('非法商品编号不得发送删除请求', () => {
    for (const id of [0, -1, 1.2, Number.NaN, '8']) {
      expect(() => deleteSpu(id as number, 3)).toThrow('商品编号');
    }
    expect(deleteRequest).not.toHaveBeenCalled();
  });

  it('服务端拒绝旧版本后不自动重试或去掉版本', async () => {
    deleteRequest.mockRejectedValue(new Error('商品版本已变化'));
    await expect(deleteSpu(8, 2)).rejects.toThrow('商品版本已变化');
    expect(deleteRequest).toHaveBeenCalledTimes(1);
  });
});
