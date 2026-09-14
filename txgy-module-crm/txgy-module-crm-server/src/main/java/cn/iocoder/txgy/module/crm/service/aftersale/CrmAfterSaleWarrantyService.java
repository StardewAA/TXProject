package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty.CrmAfterSaleWarrantyPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty.CrmAfterSaleWarrantySaveReqVO;
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
     * @param pageReqVO 分页查询
     * @return 质保档案分页
     */
    PageResult<CrmAfterSaleWarrantyDO> getWarrantyPage(CrmAfterSaleWarrantyPageReqVO pageReqVO);

    /**
     * 获得质保档案列表
     *
     * @param ids 编号
     * @return 质保档案列表
     */
    List<CrmAfterSaleWarrantyDO> getWarrantyList(Collection<Long> ids);

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
     * 【定时任务】质保到期站内信提醒（按提醒天数提前、remind_sent_date 防重复）
     *
     * @return 本次发送提醒的质保档案数量
     */
    int sendWarrantyExpireRemind();

}
