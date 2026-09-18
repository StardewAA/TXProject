package cn.iocoder.txgy.module.mes.dal.mysql.tm.toolissue;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolissue.vo.MesTmToolIssuePageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolissue.MesTmToolIssueDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MesTmToolIssueMapper extends BaseMapperX<MesTmToolIssueDO> {

    default PageResult<MesTmToolIssueDO> selectPage(MesTmToolIssuePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MesTmToolIssueDO>()
                .likeIfPresent(MesTmToolIssueDO::getCode, reqVO.getCode())
                .likeIfPresent(MesTmToolIssueDO::getName, reqVO.getName())
                .eqIfPresent(MesTmToolIssueDO::getWorkstationId, reqVO.getWorkstationId())
                .eqIfPresent(MesTmToolIssueDO::getStatus, reqVO.getStatus())
                .orderByDesc(MesTmToolIssueDO::getId));
    }

    default MesTmToolIssueDO selectByCode(String code) {
        return selectOne(MesTmToolIssueDO::getCode, code);
    }
}
