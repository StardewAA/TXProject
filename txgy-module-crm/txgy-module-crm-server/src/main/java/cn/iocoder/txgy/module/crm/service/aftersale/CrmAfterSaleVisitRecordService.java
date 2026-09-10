package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitrecord.CrmAfterSaleVisitRecordPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitrecord.CrmAfterSaleVisitRecordSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitRecordDO;
import jakarta.validation.Valid;

/**
 * 售后回访记录 Service 接口
 *
 * @author txgycloud
 */
public interface CrmAfterSaleVisitRecordService {

    /**
     * 登记回访记录；若关联回访任务，将同步完成任务并回写质保档案最近回访时间
     *
     * @param createReqVO 创建信息
     * @param userId      用户编号
     * @return 回访记录编号
     */
    Long createVisitRecord(@Valid CrmAfterSaleVisitRecordSaveReqVO createReqVO, Long userId);

    /**
     * 更新回访记录（仅登记人本人或管理员可编辑）
     *
     * @param updateReqVO 更新信息
     * @param userId      用户编号
     */
    void updateVisitRecord(@Valid CrmAfterSaleVisitRecordSaveReqVO updateReqVO, Long userId);

    /**
     * 删除回访记录（仅登记人本人或管理员可删除）；关联任务将回退为待处理
     *
     * @param id     编号
     * @param userId 用户编号
     */
    void deleteVisitRecord(Long id, Long userId);

    /**
     * 获得回访记录
     *
     * @param id 编号
     * @return 回访记录
     */
    CrmAfterSaleVisitRecordDO getVisitRecord(Long id);

    /**
     * 校验回访记录是否存在
     *
     * @param id 编号
     * @return 回访记录
     */
    CrmAfterSaleVisitRecordDO validateVisitRecord(Long id);

    /**
     * 获得回访记录分页
     *
     * @param pageReqVO 分页查询
     * @return 回访记录分页
     */
    PageResult<CrmAfterSaleVisitRecordDO> getVisitRecordPage(CrmAfterSaleVisitRecordPageReqVO pageReqVO);

}
