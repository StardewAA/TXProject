package cn.iocoder.txgy.module.crm.service.business;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.crm.controller.admin.business.vo.business.CrmBusinessLoseReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.business.vo.business.CrmBusinessPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.business.vo.business.CrmBusinessSaveReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.business.vo.business.CrmBusinessTransferReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.business.vo.business.CrmBusinessUpdateStateReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.business.vo.business.CrmBusinessUpdateStatusReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.business.vo.business.CrmBusinessWinReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.statistics.vo.funnel.CrmStatisticsFunnelReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.business.CrmBusinessDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.business.CrmBusinessProductDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.business.CrmBusinessStatusDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.contact.CrmContactDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.txgy.module.crm.enums.business.CrmBusinessEndStatusEnum;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;

/**
 * 商机 Service 接口
 *
 * @author ljlleo
 */
public interface CrmBusinessService {

    /**
     * 创建商机
     *
     * @param createReqVO 创建信息
     * @param userId      用户编号
     * @return 编号
     */
    Long createBusiness(@Valid CrmBusinessSaveReqVO createReqVO, Long userId);

    /**
     * 更新商机
     *
     * @param updateReqVO 更新信息
     */
    void updateBusiness(@Valid CrmBusinessSaveReqVO updateReqVO);

    /**
     * 更新商机相关跟进信息
     *
     * @param id                 编号
     * @param contactTime        实际跟进时间（本次实际联系客户的时间）
     * @param contactNextTime    下次联系时间
     * @param contactLastContent 最后联系内容
     */
    void updateBusinessFollowUp(Long id, LocalDateTime contactTime, LocalDateTime contactNextTime, String contactLastContent);

    /**
     * 更新商机的下次联系时间
     *
     * @param ids             编号数组
     * @param contactNextTime 下次联系时间
     */
    void updateBusinessContactNextTime(Collection<Long> ids, LocalDateTime contactNextTime);

    /**
     * 更新商机的状态
     *
     * <p>标准化后仅用于兼容历史调用：按旧状态名称映射为新的「商机阶段」；
     * 赢单/输单必须使用 {@link #winBusiness} / {@link #loseBusiness}。
     *
     * @param reqVO 更新请求
     */
    void updateBusinessStatus(CrmBusinessUpdateStatusReqVO reqVO);

    /**
     * 推进商机阶段（同步派生「商机状态组」与「预估赢单率」）
     *
     * @param id    商机编号
     * @param stage 商机阶段：1商机待评估 2初步接洽 3需求确认 4方案评估 5商务谈判
     */
    void updateBusinessStage(Long id, Integer stage);

    /**
     * 商机赢单
     *
     * <p>签约日期、签约金额必填；成功后状态组=已赢单、预估赢单率=100，关键字段只读。
     *
     * @param reqVO  赢单请求
     * @param userId 操作用户编号
     */
    void winBusiness(@Valid CrmBusinessWinReqVO reqVO, Long userId);

    /**
     * 商机输单
     *
     * <p>丢单原因必填、丢单备注选填；成功后状态组=已丢单、预估赢单率=0，关键字段只读。
     *
     * @param reqVO  输单请求
     * @param userId 操作用户编号
     */
    void loseBusiness(@Valid CrmBusinessLoseReqVO reqVO, Long userId);

    /**
     * 调整商机状态组（仅管理员）
     *
     * <p>商机状态组为只读派生字段，普通销售无权修改。本接口仅支持「重新开启」
     * （已赢单/已丢单 → 未开始/进行中）或纠正开启中的状态组，关闭必须走赢单/输单按钮。
     *
     * @param reqVO  调整请求
     * @param userId 操作用户编号
     */
    void updateBusinessState(@Valid CrmBusinessUpdateStateReqVO reqVO, Long userId);

    /**
     * 删除商机
     *
     * @param id 编号
     */
    void deleteBusiness(Long id);

    /**
     * 商机转移
     *
     * @param reqVO  请求
     * @param userId 用户编号
     */
    void transferBusiness(CrmBusinessTransferReqVO reqVO, Long userId);

    /**
     * 获得商机
     *
     * @param id 编号
     * @return 商机
     */
    CrmBusinessDO getBusiness(Long id);

    /**
     * 校验商机是否有效
     *
     * @param id 编号
     * @return 商机
     */
    CrmBusinessDO validateBusiness(Long id);

    /**
     * 获得商机列表
     *
     * @param ids 编号
     * @return 商机列表
     */
    List<CrmBusinessDO> getBusinessList(Collection<Long> ids);

    /**
     * 获得商机 Map
     *
     * @param ids 编号
     * @return 商机 Map
     */
    default Map<Long, CrmBusinessDO> getBusinessMap(Collection<Long> ids) {
        return convertMap(getBusinessList(ids), CrmBusinessDO::getId);
    }

    /**
     * 获得指定商机编号的产品列表
     *
     * @param businessId 商机编号
     * @return 商机产品列表
     */
    List<CrmBusinessProductDO> getBusinessProductListByBusinessId(Long businessId);

    /**
     * 获得商机分页
     *
     * 数据权限：基于 {@link CrmBusinessDO}
     *
     * @param pageReqVO 分页查询
     * @param userId    用户编号
     * @return 商机分页
     */
    PageResult<CrmBusinessDO> getBusinessPage(CrmBusinessPageReqVO pageReqVO, Long userId);

    /**
     * 获得商机分页，基于指定客户
     *
     * 数据权限：基于 {@link CrmCustomerDO} 读取
     *
     * @param pageReqVO 分页查询
     * @return 商机分页
     */
    PageResult<CrmBusinessDO> getBusinessPageByCustomerId(CrmBusinessPageReqVO pageReqVO);

    /**
     * 获得商机分页，基于指定联系人
     *
     * 数据权限：基于 {@link CrmContactDO} 读取
     *
     * @param pageReqVO 分页参数
     * @return 商机分页
     */
    PageResult<CrmBusinessDO> getBusinessPageByContact(CrmBusinessPageReqVO pageReqVO);

    /**
     * 获取关联客户的商机数量
     *
     * @param customerId 客户编号
     * @return 数量
     */
    Long getBusinessCountByCustomerId(Long customerId);

    /**
     * 获得使用指定商机状态组的商机数量
     *
     * @param statusTypeId 商机状态组编号
     * @return 数量
     */
    Long getBusinessCountByStatusTypeId(Long statusTypeId);

    /**
     * 获得商机状态名称
     *
     * @param endStatus 结束状态
     * @param status    商机状态
     * @return 商机状态名称
     */
    default String getBusinessStatusName(Integer endStatus, CrmBusinessStatusDO status) {
        if (endStatus != null) {
            // 注意：endStatus 可能为 0 等历史/非法值（枚举只定义 1/2/3），此处需兜底，
            // 否则 fromStatus(...) 返回 null 会抛空指针，导致商机列表整体 500
            CrmBusinessEndStatusEnum endStatusEnum = CrmBusinessEndStatusEnum.fromStatus(endStatus);
            if (endStatusEnum != null) {
                return endStatusEnum.getName();
            }
        }
        return status == null ? null : status.getName();
    }

    /**
     * 获得商机列表
     *
     * @param customerId  客户编号
     * @param ownerUserId 负责人编号
     * @return 商机列表
     */
    List<CrmBusinessDO> getBusinessListByCustomerIdOwnerUserId(Long customerId, Long ownerUserId);

    /**
     * 获得商机列表，基于指定客户
     *
     * @param customerId 客户编号
     * @return 商机列表
     */
    List<CrmBusinessDO> getBusinessListByCustomerId(Long customerId);

    /**
     * 获得商机列表，基于指定联系人
     *
     * @param contactId 联系人编号
     * @return 商机列表
     */
    List<CrmBusinessDO> getBusinessListByContact(Long contactId);

    /**
     * 获得商机分页，目前用于【数据统计】
     *
     * @param pageVO 请求
     * @return 商机分页
     */
    PageResult<CrmBusinessDO> getBusinessPageByDate(CrmStatisticsFunnelReqVO pageVO);

}
