package cn.iocoder.txgy.module.erp.service.logistics;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsDetailRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsEventRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsEventPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsEventPageRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsExceptionHandleReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsNodeReceiveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsPageRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsReceiptReceiveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsTaskReceiveReqVO;

public interface ErpLogisticsService {

    ErpLogisticsEventRespVO receiveTask(ErpLogisticsTaskReceiveReqVO reqVO);

    ErpLogisticsEventRespVO receiveNode(ErpLogisticsNodeReceiveReqVO reqVO);

    ErpLogisticsEventRespVO receiveReceipt(ErpLogisticsReceiptReceiveReqVO reqVO);

    PageResult<ErpLogisticsPageRespVO> getLogisticsPage(ErpLogisticsPageReqVO reqVO);

    ErpLogisticsDetailRespVO getLogisticsDetail(Long id);

    ErpLogisticsDetailRespVO getLogisticsByOrder(String sourceOrderType, String sourceOrderNo);

    ErpLogisticsEventRespVO getEventResult(String requestId);

    PageResult<ErpLogisticsEventPageRespVO> getEventPage(ErpLogisticsEventPageReqVO reqVO);

    ErpLogisticsEventRespVO retryPendingEvent(Long id);

    void handleException(ErpLogisticsExceptionHandleReqVO reqVO);

}
