package cn.iocoder.txgy.module.hrm.dal.mysql.policy;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.policy.vo.HrmPolicyPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.policy.HrmPolicyDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HrmPolicyMapper extends BaseMapperX<HrmPolicyDO> {

    default PageResult<HrmPolicyDO> selectPage(HrmPolicyPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HrmPolicyDO>()
                .likeIfPresent(HrmPolicyDO::getName, reqVO.getName())
                .eqIfPresent(HrmPolicyDO::getCategory, reqVO.getCategory())
                .eqIfPresent(HrmPolicyDO::getScope, reqVO.getScope())
                .eqIfPresent(HrmPolicyDO::getStatus, reqVO.getStatus())
                .orderByDesc(HrmPolicyDO::getId));
    }
}
