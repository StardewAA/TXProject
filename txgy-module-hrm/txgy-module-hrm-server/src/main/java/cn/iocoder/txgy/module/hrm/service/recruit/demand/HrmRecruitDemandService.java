package cn.iocoder.txgy.module.hrm.service.recruit.demand;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.demand.HrmRecruitDemandPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.demand.HrmRecruitDemandSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.demand.HrmRecruitDemandDO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;

/**
 * HRM 用工需求单 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmRecruitDemandService {

    /**
     * 创建用工需求单
     *
     * @param createReqVO 需求信息
     * @return 需求编号
     */
    Long createRecruitDemand(HrmRecruitDemandSaveReqVO createReqVO);

    /**
     * 更新用工需求单
     *
     * @param updateReqVO 需求信息
     */
    void updateRecruitDemand(HrmRecruitDemandSaveReqVO updateReqVO);

    /**
     * 更新用工需求单状态
     *
     * @param id 需求编号
     * @param status 状态
     */
    void updateRecruitDemandStatus(Long id, Integer status);

    /**
     * 删除用工需求单
     *
     * @param id 需求编号
     */
    void deleteRecruitDemand(Long id);

    /**
     * 获得用工需求单
     *
     * @param id 需求编号
     * @return 需求单
     */
    HrmRecruitDemandDO getRecruitDemand(Long id);

    /**
     * 校验需求单存在
     *
     * @param id 需求编号
     * @return 需求单
     */
    HrmRecruitDemandDO validateRecruitDemandExists(Long id);

    /**
     * 获得用工需求单分页
     *
     * @param pageReqVO 分页条件
     * @return 分页结果
     */
    PageResult<HrmRecruitDemandDO> getRecruitDemandPage(HrmRecruitDemandPageReqVO pageReqVO);

    /**
     * 获得用工需求单列表
     *
     * @param ids 需求编号集合
     * @return 需求单列表
     */
    List<HrmRecruitDemandDO> getRecruitDemandList(Collection<Long> ids);

    /**
     * 获得需求单 Map
     *
     * @param ids 需求编号集合
     * @return 需求单 Map
     */
    default Map<Long, HrmRecruitDemandDO> getRecruitDemandMap(Collection<Long> ids) {
        return convertMap(getRecruitDemandList(ids), HrmRecruitDemandDO::getId);
    }

    /**
     * 获得可招聘的需求单精简列表（待招聘 / 招聘中）
     *
     * @return 需求单列表
     */
    List<HrmRecruitDemandDO> getRecruitingDemandList();

    /**
     * 录用一人后回写到岗人数；到齐自动置为「已完成」
     *
     * @param demandId 需求编号
     */
    void increaseOnboardCount(Long demandId);

    /**
     * 校验需求单是否还能继续录用（未完成且未超编）
     *
     * @param demandId 需求编号
     */
    void validateRecruitDemandCanRecruit(Long demandId);

}
