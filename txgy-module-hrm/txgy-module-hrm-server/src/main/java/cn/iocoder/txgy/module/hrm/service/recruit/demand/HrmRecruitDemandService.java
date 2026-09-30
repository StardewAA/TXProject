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
     * 入职占位：编制未满时原子地把到岗人数 +1，并返回是否占位成功。
     *
     * <p>并发安全：并发入职同一需求单时只有一个事务能占位成功，另一个直接得到 false（编制已满），
     * 避免「先校验编制再建档」的 check-then-act 竞态突破编制。</p>
     *
     * @param demandId 需求编号；为空时直接返回 true（无需回写编制的场景）
     * @return 是否占位成功
     */
    boolean reserveOnboardCount(Long demandId);

    /**
     * 按最新到岗人数刷新需求单状态：到齐自动置「已完成」，未到齐且仍「待招聘」则进入「招聘中」。
     *
     * <p>与 {@link #increaseOnboardCount(Long)} 的区别：本方法不再自增人数，
     * 供「入职前已原子占位」的流程在建档成功后单独刷新状态，避免重复计数。</p>
     *
     * @param demandId 需求编号
     */
    void refreshOnboardStatus(Long demandId);

    /**
     * 校验需求单是否还能继续录用（未完成且未超编）
     *
     * @param demandId 需求编号
     */
    void validateRecruitDemandCanRecruit(Long demandId);

}
