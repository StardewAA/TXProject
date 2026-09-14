package cn.iocoder.txgy.module.crm.service.quotation;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.crm.controller.admin.quotation.vo.quotation.CrmQuotationPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.quotation.vo.quotation.CrmQuotationSaveReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.quotation.vo.quotation.CrmQuotationTransferReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.quotation.CrmQuotationDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.quotation.CrmQuotationProductDO;
import jakarta.validation.Valid;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;

/**
 * CRM 报价单 Service 接口
 *
 * 关联链路：客户(Customer) -> 商机(Business) -> 报价(Quotation) -> 合同(Contract)
 *
 * @author 防腐保温项目组
 */
public interface CrmQuotationService {

    /**
     * 创建报价单
     *
     * @param createReqVO 创建信息
     * @param userId      用户编号
     * @return 报价编号
     */
    Long createQuotation(@Valid CrmQuotationSaveReqVO createReqVO, Long userId);

    /**
     * 更新报价单
     *
     * @param updateReqVO 更新信息
     * @param userId      用户编号
     */
    void updateQuotation(@Valid CrmQuotationSaveReqVO updateReqVO, Long userId);

    /**
     * 删除报价单
     *
     * @param id 编号
     */
    void deleteQuotation(Long id);

    /**
     * 报价单转移
     *
     * @param reqVO  请求
     * @param userId 用户编号
     */
    void transferQuotation(CrmQuotationTransferReqVO reqVO, Long userId);

    /**
     * 发起报价单审批流程
     *
     * @param id     报价编号
     * @param userId 用户编号
     */
    void submitQuotation(Long id, Long userId);

    /**
     * 更新报价单流程审批结果
     *
     * @param id        报价编号
     * @param bpmResult BPM 审批结果
     */
    void updateQuotationAuditStatus(Long id, Integer bpmResult);

    /**
     * 获得报价单
     *
     * @param id 编号
     * @return 报价单
     */
    CrmQuotationDO getQuotation(Long id);

    /**
     * 校验报价单是否合法
     *
     * @param id 编号
     * @return 报价单
     */
    CrmQuotationDO validateQuotation(Long id);

    /**
     * 获得报价单列表
     *
     * @param ids 编号
     * @return 报价单列表
     */
    List<CrmQuotationDO> getQuotationList(Collection<Long> ids);

    /**
     * 获得报价单 Map
     *
     * @param ids 编号
     * @return 报价单 Map
     */
    default Map<Long, CrmQuotationDO> getQuotationMap(Collection<Long> ids) {
        return convertMap(getQuotationList(ids), CrmQuotationDO::getId);
    }

    /**
     * 获得报价单分页
     *
     * 数据权限：基于 {@link CrmQuotationDO} 读取
     *
     * @param pageReqVO 分页查询
     * @param userId    用户编号
     * @return 报价单分页
     */
    PageResult<CrmQuotationDO> getQuotationPage(CrmQuotationPageReqVO pageReqVO, Long userId);

    /**
     * 根据客户编号，获取关联客户的报价单数量
     *
     * @param customerId 客户编号
     * @return 数量
     */
    Long getQuotationCountByCustomerId(Long customerId);

    /**
     * 根据商机编号，获取关联商机的报价单数量
     *
     * @param businessId 商机编号
     * @return 数量
     */
    Long getQuotationCountByBusinessId(Long businessId);

    /**
     * 根据报价编号，获得报价单的产品列表
     *
     * @param quotationId 报价编号
     * @return 产品列表
     */
    List<CrmQuotationProductDO> getQuotationProductListByQuotationId(Long quotationId);

    /**
     * 报价单转为合同：由审批通过的报价单生成合同草稿
     *
     * @param id     报价编号
     * @param userId 用户编号
     * @return 生成的合同编号
     */
    Long createContractByQuotation(Long id, Long userId);

}
