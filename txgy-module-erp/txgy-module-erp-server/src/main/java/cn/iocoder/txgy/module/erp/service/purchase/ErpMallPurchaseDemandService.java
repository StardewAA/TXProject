package cn.iocoder.txgy.module.erp.service.purchase;

import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.mall.MallPurchaseDemandCancelReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.mall.MallPurchaseDemandReceiveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.mall.MallPurchaseDemandRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.mall.MallPurchaseDemandResultReqVO;
import jakarta.validation.Valid;

/**
 * Service for mall-originated shortage procurement demands.
 */
public interface ErpMallPurchaseDemandService {

    MallPurchaseDemandRespVO receiveDemand(@Valid MallPurchaseDemandReceiveReqVO reqVO);

    MallPurchaseDemandRespVO getDemand(String erpRequestNo);

    MallPurchaseDemandRespVO requestCancel(String erpRequestNo, @Valid MallPurchaseDemandCancelReqVO reqVO);

    MallPurchaseDemandRespVO updateResult(String erpRequestNo, @Valid MallPurchaseDemandResultReqVO reqVO);

}
