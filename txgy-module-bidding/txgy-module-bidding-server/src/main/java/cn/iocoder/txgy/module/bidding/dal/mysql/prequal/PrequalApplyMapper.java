package cn.iocoder.txgy.module.bidding.dal.mysql.prequal;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalApplyPageReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.prequal.PrequalApplyDO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 资格预审申请 Mapper。
 */
@Mapper
public interface PrequalApplyMapper extends BaseMapperX<PrequalApplyDO> {

    /** 按预审+主体查申请（幂等：同一主体对同一预审只提交一次）。 */
    default PrequalApplyDO selectByPrequalAndSubject(Long prequalId, Long subjectId) {
        return selectOne(PrequalApplyDO::getPrequalId, prequalId,
                PrequalApplyDO::getSubjectId, subjectId);
    }

    /** 按预审查申请列表（资格审查用）。 */
    default List<PrequalApplyDO> selectListByPrequal(Long prequalId) {
        return selectList(PrequalApplyDO::getPrequalId, prequalId);
    }

    /** 统计通过数（条例第19条：通过者<3家应重新招标）。 */
    default long countPassed(Long prequalId) {
        return selectCount(Wrappers.lambdaQuery(PrequalApplyDO.class)
                .eq(PrequalApplyDO::getPrequalId, prequalId)
                .eq(PrequalApplyDO::getStatus, "PASSED"));
    }

    /** 申请分页（按预审/主体/状态/标段过滤；标段过滤见契约《投标与资格预审多文件化-2026-10-02》R6）。 */
    default PageResult<PrequalApplyDO> selectPage(PrequalApplyPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<PrequalApplyDO>()
                .eqIfPresent(PrequalApplyDO::getBidSectionCode, reqVO.getBidSectionCode())
                .eqIfPresent(PrequalApplyDO::getPrequalId, reqVO.getPrequalId())
                .eqIfPresent(PrequalApplyDO::getSubjectId, reqVO.getSubjectId())
                .eqIfPresent(PrequalApplyDO::getStatus, reqVO.getStatus())
                .orderByDesc(PrequalApplyDO::getApplyTime));
    }
}
