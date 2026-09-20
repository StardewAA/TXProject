package cn.iocoder.txgy.module.erp.service.purchase;

import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inquiry.ErpInquiryComparisonRespVO;

/**
 * ERP 询价横向比价 Service
 *
 * <p>把同一物料下所有供应商的报价并列铺开，给出加权评分、排名、最优推荐与价格异常标记。
 * 补上原实现「只能把报价读出来、比价靠肉眼挑下拉框」的缺口。</p>
 *
 * @author OPENLAB BS
 */
public interface ErpInquiryComparisonService {

    /**
     * 获得询价单的物料维度横向比价。
     *
     * @param inquiryId 询价单编号
     * @return 比价结果；询价单不存在时返回 null
     */
    ErpInquiryComparisonRespVO getComparison(Long inquiryId);

}
