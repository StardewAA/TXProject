package cn.iocoder.txgy.module.erp.service.purchase;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inquiry.*;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.*;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.List;

public interface ErpInquiryService {
    Long createInquiry(ErpInquirySaveReqVO reqVO);
    Long createInquiryFromRequest(Long requestId);
    void updateInquiry(ErpInquirySaveReqVO reqVO);
    void deleteInquiry(List<Long> ids);
    void submitInquiry(Long id, Long userId);
    /** 反审批询价单（审批中 / 已审批未发布 → 草稿），并联动取消 BPM 在途流程实例 */
    void reverseApproveInquiry(Long id, Long userId, String reason);
    void updateInquiryAuditStatus(Long id, Integer bpmStatus);
    void publishInquiry(Long id);
    void closeInquiry(Long id);
    Long saveQuote(ErpInquiryQuoteSaveReqVO reqVO);
    void saveAwardSelection(ErpAwardSelectionReqVO reqVO);
    void submitAward(Long id, Long userId);
    /** 反审批定标（审批中 / 已定标未生成订单 → 待提交定标），并联动取消 BPM 在途流程实例 */
    void reverseApproveAward(Long id, Long userId, String reason);
    void updateAwardAuditStatus(Long id, Integer bpmStatus);
    List<Long> confirmAward(Long id);
    ErpInquiryDO getInquiry(Long id);
    PageResult<ErpInquiryDO> getInquiryPage(ErpInquiryPageReqVO reqVO);
    List<ErpInquiryItemDO> getItemList(Collection<Long> inquiryIds);
    List<ErpInquiryQuoteDO> getQuoteList(Long inquiryId);

    /**
     * 批量取每张询价单的最低报价合计（最优报价）：一次查询后在内存分组，避免列表页 N+1 查询。
     *
     * @param inquiryIds 询价单编号集合
     * @return key = 询价单编号，value = 最低报价合计；无报价或金额为空的单据不返回
     */
    Map<Long, BigDecimal> getCheapestQuoteMap(Collection<Long> inquiryIds);
    List<ErpInquiryQuoteItemDO> getQuoteItemList(Collection<Long> quoteIds);

    /**
     * 获得当前登录账号绑定的供应商编号
     *
     * @return 供应商编号；内部账号（未绑定供应商）返回 null
     */
    Long getLoginSupplierId();

    /**
     * 保存询价单的受邀供应商名单（全量覆盖）
     *
     * @param inquiryId   询价单编号
     * @param supplierIds 邀请的供应商编号；为空表示不限受邀范围
     */
    void saveInvitedSuppliers(Long inquiryId, List<Long> supplierIds);

    /**
     * 获得询价单的受邀供应商关系
     *
     * @param inquiryId 询价单编号
     * @return 受邀供应商关系列表
     */
    List<ErpInquirySupplierDO> getInvitedSupplierList(Long inquiryId);

    /**
     * 校验供应商是否受邀参与询价单（宽松）。
     *
     * <p>没有配置任何受邀供应商的询价单视为「不限受邀范围」，直接放行，
     * 用于兼容改造前由内部采购员代录报价的历史数据。</p>
     *
     * @param inquiryId  询价单编号
     * @param supplierId 供应商编号
     */
    void validateSupplierInvited(Long inquiryId, Long supplierId);

    /**
     * 校验询价单是否送达指定供应商（严格）。
     *
     * <p>必须存在受邀记录，否则抛「无权查看」。用于供应商身份的可见性与报价准入，
     * 避免供应商看到/报价未发送给自己的询价单。</p>
     *
     * @param inquiryId  询价单编号
     * @param supplierId 供应商编号
     */
    void validateSupplierVisible(Long inquiryId, Long supplierId);
}
