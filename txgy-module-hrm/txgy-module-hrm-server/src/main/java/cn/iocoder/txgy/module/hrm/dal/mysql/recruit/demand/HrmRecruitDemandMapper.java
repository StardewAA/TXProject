package cn.iocoder.txgy.module.hrm.dal.mysql.recruit.demand;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.demand.HrmRecruitDemandPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.demand.HrmRecruitDemandDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface HrmRecruitDemandMapper extends BaseMapperX<HrmRecruitDemandDO> {

    default PageResult<HrmRecruitDemandDO> selectPage(HrmRecruitDemandPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmRecruitDemandDO>()
                .likeIfPresent(HrmRecruitDemandDO::getDemandNo, reqVO.getDemandNo())
                .likeIfPresent(HrmRecruitDemandDO::getDemandName, reqVO.getDemandName())
                .likeIfPresent(HrmRecruitDemandDO::getWorkType, reqVO.getWorkType())
                .eqIfPresent(HrmRecruitDemandDO::getDeptId, reqVO.getDeptId())
                .eqIfPresent(HrmRecruitDemandDO::getStatus, reqVO.getStatus())
                .eqIfPresent(HrmRecruitDemandDO::getUrgency, reqVO.getUrgency())
                .betweenIfPresent(HrmRecruitDemandDO::getPlanEntryDate, reqVO.getPlanEntryDate())
                .orderByDesc(HrmRecruitDemandDO::getId));
    }

    default List<HrmRecruitDemandDO> selectListByStatus(Integer status) {
        return selectList(new LambdaQueryWrapperX<HrmRecruitDemandDO>()
                .eqIfPresent(HrmRecruitDemandDO::getStatus, status)
                .orderByDesc(HrmRecruitDemandDO::getId));
    }

    default HrmRecruitDemandDO selectByDemandNo(String demandNo) {
        return selectOne(HrmRecruitDemandDO::getDemandNo, demandNo);
    }

    /**
     * 到岗人数原子自增：并发入职同一需求单时不丢更新（P1，替代「读-改-写」）
     *
     * @param id 需求单编号
     * @return 受影响行数（0 表示需求单不存在或已删除）
     */
    @Update("UPDATE hrm_recruit_demand SET onboard_count = IFNULL(onboard_count, 0) + 1, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0")
    int increaseOnboardCount(@Param("id") Long id);

    /**
     * 入职占位（原子）：仅在「未超过编制」时把到岗人数 +1
     *
     * <p>Q-001 修复：原实现是「先校验编制（select）→ 再建档（createEmployee）→ 再自增」，
     * 并发入职同一需求单时两个事务都能通过校验（check-then-act），导致到岗人数突破编制。
     * 改为在建档之前先做条件自增占位：只有真正抢到编制的那个事务才能继续建档，
     * 另一并发事务受影响行数为 0，直接抛出「编制已满」业务异常。</p>
     *
     * <p>占位与建档处于同一事务：建档失败会一起回滚，不会出现「占了编制却没建成档」。</p>
     *
     * @param id 需求单编号
     * @return 受影响行数（0 表示需求单不存在 / 已删除 / 编制已满）
     */
    @Update("UPDATE hrm_recruit_demand SET onboard_count = IFNULL(onboard_count, 0) + 1, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 "
            + "AND (headcount IS NULL OR headcount <= 0 OR IFNULL(onboard_count, 0) < headcount)")
    int reserveOnboardCount(@Param("id") Long id);

}
