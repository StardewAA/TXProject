package cn.iocoder.txgy.module.crm.service.product;

import cn.iocoder.txgy.module.crm.controller.admin.product.vo.product.CrmProductWmsSyncRespVO;

/**
 * CRM 产品与 WMS 商品（进仓产品）对接的同步 Service 接口
 *
 * <p>只读取 WMS 商品与规格，写入 CRM 产品；不新增、修改、冻结、扣减或调整 WMS 库存。</p>
 *
 * @author OPENLAB BS
 */
public interface CrmProductWmsSyncService {

    /**
     * 将当前租户的 WMS 商品规格同步导入 CRM 产品管理
     *
     * <p>按 WMS 规格编号幂等：已同步的产品只更新名称、单位等 WMS 主数据字段；
     * 价格、分类、负责人、状态等人工维护字段不会被覆盖。</p>
     *
     * @return 同步结果
     */
    CrmProductWmsSyncRespVO syncFromWms();

}
