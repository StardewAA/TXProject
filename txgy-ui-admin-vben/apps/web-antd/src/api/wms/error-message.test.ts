import { describe, expect, it } from 'vitest';
import { formatWmsErrorMessage } from './error-message';

describe('WMS 旧接口报错中文化', () => {
  it('将原材料仓和成品仓编码换成中文', () => {
    expect(formatWmsErrorMessage('采购入库申请无效：仓库类型必须为 RAW_MATERIAL'))
      .toBe('当前业务需要原材料仓，请选择匹配的业务仓库');
    expect(formatWmsErrorMessage('采购入库申请无效：仓库类型必须为 FINISHED_GOODS'))
      .toBe('当前业务需要成品仓，请选择匹配的业务仓库');
  });
  it('其他报错保持原文', () => {
    expect(formatWmsErrorMessage('仓库不存在')).toBe('仓库不存在');
  });
});
