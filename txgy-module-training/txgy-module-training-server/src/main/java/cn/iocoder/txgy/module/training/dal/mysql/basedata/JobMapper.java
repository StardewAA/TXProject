package cn.iocoder.txgy.module.training.dal.mysql.basedata;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.basedata.JobDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 岗位工种 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface JobMapper extends BaseMapperX<JobDO> {

    default PageResult<JobDO> selectPage(JobPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<JobDO>()
                .likeIfPresent(JobDO::getName, reqVO.getName())
                .eqIfPresent(JobDO::getCode, reqVO.getCode())
                .eqIfPresent(JobDO::getSeqCode, reqVO.getSeqCode())
                .eqIfPresent(JobDO::getMaxLevel, reqVO.getMaxLevel())
                .eqIfPresent(JobDO::getStatus, reqVO.getStatus())
                .likeIfPresent(JobDO::getLearnDomainCodes, reqVO.getLearnDomainCode())
                .likeIfPresent(JobDO::getKeySkills, reqVO.getKeySkills())
                .betweenIfPresent(JobDO::getCreateTime, reqVO.getCreateTime())
                .orderByAsc(JobDO::getSeqCode)
                .orderByAsc(JobDO::getSort)
                .orderByAsc(JobDO::getCode));
    }

    /**
     * 全量工种列表（按序列 + 排序 + 编码升序，供下拉与学习地图工种选择引用）
     */
    default List<JobDO> selectList() {
        return selectList(new LambdaQueryWrapperX<JobDO>()
                .orderByAsc(JobDO::getSeqCode)
                .orderByAsc(JobDO::getSort)
                .orderByAsc(JobDO::getCode));
    }

    /**
     * 按工种编码查询（唯一键）
     */
    default JobDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapperX<JobDO>()
                .eq(JobDO::getCode, code)
                .last("limit 1"));
    }

}
