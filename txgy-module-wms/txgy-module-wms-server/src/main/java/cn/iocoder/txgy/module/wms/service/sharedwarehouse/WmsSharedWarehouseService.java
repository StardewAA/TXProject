package cn.iocoder.txgy.module.wms.service.sharedwarehouse;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.wms.controller.admin.sharedwarehouse.vo.WmsSharedWarehousePageReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.sharedwarehouse.vo.WmsSharedWarehouseProductPageReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.sharedwarehouse.vo.WmsSharedWarehouseProductRespVO;
import cn.iocoder.txgy.module.wms.controller.admin.sharedwarehouse.vo.WmsSharedWarehouseRespVO;

import java.util.List;

/**
 * 共享仓库查询。实物仓与实物库存以 WMS 为准，商城和 ERP 只作为来源标记。
 */
public interface WmsSharedWarehouseService {

    PageResult<WmsSharedWarehouseRespVO> getSharedWarehousePage(WmsSharedWarehousePageReqVO pageReqVO);

    List<WmsSharedWarehouseRespVO> getSharedWarehouseList(WmsSharedWarehousePageReqVO pageReqVO);

    PageResult<WmsSharedWarehouseProductRespVO> getSharedWarehouseProductPage(
            WmsSharedWarehouseProductPageReqVO pageReqVO);

}
