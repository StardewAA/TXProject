package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty.CrmAfterSaleWarrantyPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty.CrmAfterSaleWarrantySaveReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty.CrmAfterSaleWarrantyUpdateStatusReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleWarrantyDO;
import jakarta.validation.Valid;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;

/**
 * 售后质保档案 Service 接口
 *
 * @author txgycloud
 */
public interface CrmAfterSaleWarrantyService {

    /**
     * 创建质保档案
     *
     * @param createReqVO 创建信息
     * @param userId      用户编号
     * @return 质保档案编号
     */
    Long createWarranty(@Valid CrmAfterSaleWarrantySaveReqVO createReqVO, Long userId);

    /**
     * 更新质保档案
     *
     * @param updateReqVO 更新信息
     */
    void updateWarranty(@Valid CrmAfterSaleWarrantySaveReqVO updateReqVO);

    /**
     * 删除质保档案
     *
     * @param id 编号
     */
    void deleteWarranty(Long id);

    /**
     * 获得质保档案
     *
     * @param id 编号
     * @return 质保档案
     */
    CrmAfterSaleWarrantyDO getWarranty(Long id);

    /**
     * 校验质保档案是否合法（存在且未删除）
     *
     * @param id 编号
     * @return 质保档案
     */
    CrmAfterSaleWarrantyDO validateWarranty(Long id);

    /**
     * 获得质保档案分页
     *
     * <p>当 pageReqVO.expiryType = 即将到期（1）时，按「待办：质保即将到期」口径实时查询：
     * 质保状态 = 质保中 + 售后责任人在（本人 + 下属）范围内 + 质保到期日在提前提醒天数内
     *
     * @param pageReqVO 分页查询
     * @param userId    当前登录用户编号（待办归属过滤使用）
     * @return 质保档案分页
     */
    PageResult<CrmAfterSaleWarrantyDO> getWarrantyPage(CrmAfterSaleWarrantyPageReqVO pageReqVO, Long userId);

    /**
     * 获得「质保即将到期」待办数量（实时查询，与分页查询同一口径，不落库）
     *
     * @param userId 当前登录用户编号
     * @return 待办数量
     */
    Long getWarrantyRemindCount(Long userId);

    /**
     * 获得质保档案列表
     *
     * @param ids 编号
     * @return 质保档案列表
     */
    List<CrmAfterSaleWarrantyDO> getWarrantyList(Collection<Long> ids);

    /**
     * 按合同编号集合获得质保档案列表
     *
     * <p>用于「一个合同仅允许一份质保档案」的识别：返回的合同即为「已创建质保档案」的合同。
     *
     * @param contractIds 合同编号集合
     * @return 质保档案列表
     */
    List<CrmAfterSaleWarrantyDO> getWarrantyListByContractIds(Collection<Long> contractIds);

    /**
     * 获得质保档案 Map
     *
     * @param ids 编号
     * @return 质保档案 Map
     */
    default Map<Long, CrmAfterSaleWarrantyDO> getWarrantyMap(Collection<Long> ids) {
        return convertMap(getWarrantyList(ids), CrmAfterSaleWarrantyDO::getId);
    }

    /**
     * 【定时任务】自动流转质保状态：
     * 到期日已过 -> 已到期终止；进入提前提醒天数区间 -> 即将到期；否则 -> 质保中
     *
     * @return 本次状态变更的数量
     */
    int autoUpdateWarrantyStatus();

    /**
     * 【手动干预】立即刷新当前租户的质保状态（与定时任务同一口径）
     *
     * <p>用途：不必等到每日 02:00 定时任务，管理员可随时手动触发一次全量重算；
     * 典型场景：刚导入历史数据、刚调整过到期日、需要立即验证流转结果。
     *
     * @return 本次状态变更的数量
     */
    int syncWarrantyStatus();

    /**
     * 【手动干预】手动修正单条质保档案的状态
     *
     * <p>自动流转是单向的（已到期终止不会被回退），当出现下列情况时由管理员手动纠正：
     * <ul>
     *   <li>到期日被人工延长，需要把「已到期终止」改回「质保中 / 即将到期」；</li>
     *   <li>业务上提前终止质保，需要直接置为「已到期终止」。</li>
     * </ul>
     * 操作会记录日志（操作人、原状态 → 新状态、原因）。
     *
     * @param reqVO  修正请求
     * @param userId 操作人编号
     */
    void updateWarrantyStatus(@Valid CrmAfterSaleWarrantyUpdateStatusReqVO reqVO, Long userId);

    /**
     * 【定时任务】质保到期站内信提醒（按提醒天数提前、remind_sent_date 防重复）
     *
     * @return 本次发送提醒的质保档案数量
     */
    int sendWarrantyExpireRemind();

}
