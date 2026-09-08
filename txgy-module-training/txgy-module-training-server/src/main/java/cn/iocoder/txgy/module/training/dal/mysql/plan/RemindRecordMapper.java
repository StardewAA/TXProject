package cn.iocoder.txgy.module.training.dal.mysql.plan;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.RemindRecordPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.RemindRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RemindRecordMapper extends BaseMapperX<RemindRecordDO> {

    default PageResult<RemindRecordDO> selectPage(RemindRecordPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RemindRecordDO>()
                .eq(RemindRecordDO::getClassId, reqVO.getClassId())
                .eqIfPresent(RemindRecordDO::getStatus, reqVO.getStatus())
                .likeIfPresent(RemindRecordDO::getUserName, reqVO.getUserName())
                .orderByDesc(RemindRecordDO::getId));
    }

}
