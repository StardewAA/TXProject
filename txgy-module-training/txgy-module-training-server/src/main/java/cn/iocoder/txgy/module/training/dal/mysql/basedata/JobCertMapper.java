package cn.iocoder.txgy.module.training.dal.mysql.basedata;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobCertPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.basedata.JobCertDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 工种资格证 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface JobCertMapper extends BaseMapperX<JobCertDO> {

    default PageResult<JobCertDO> selectPage(JobCertPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<JobCertDO>()
                .eqIfPresent(JobCertDO::getJobCode, reqVO.getJobCode())
                .likeIfPresent(JobCertDO::getJobName, reqVO.getJobName())
                .likeIfPresent(JobCertDO::getCertName, reqVO.getCertName())
                .eqIfPresent(JobCertDO::getIsRequired, reqVO.getIsRequired())
                .betweenIfPresent(JobCertDO::getCreateTime, reqVO.getCreateTime())
                .orderByAsc(JobCertDO::getJobCode)
                .orderByAsc(JobCertDO::getSort)
                .orderByAsc(JobCertDO::getId));
    }

    /**
     * 按工种查询资格证要求清单
     */
    default List<JobCertDO> selectListByJobCode(String jobCode) {
        return selectList(new LambdaQueryWrapperX<JobCertDO>()
                .eq(JobCertDO::getJobCode, jobCode)
                .orderByAsc(JobCertDO::getSort)
                .orderByAsc(JobCertDO::getId));
    }

    /**
     * 统计某工种下的资格证数量（删除工种前引用校验）
     */
    default Long selectCountByJobCode(String jobCode) {
        return selectCount(new LambdaQueryWrapperX<JobCertDO>()
                .eq(JobCertDO::getJobCode, jobCode));
    }

    /**
     * 按 工种 + 资格证名称 查询（唯一键校验）
     */
    default JobCertDO selectByJobCodeAndCertName(String jobCode, String certName) {
        return selectOne(new LambdaQueryWrapperX<JobCertDO>()
                .eq(JobCertDO::getJobCode, jobCode)
                .eq(JobCertDO::getCertName, certName)
                .last("limit 1"));
    }

}
