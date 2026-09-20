package cn.iocoder.txgy.module.training.dal.mysql.plan;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.QualificationRecordPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.QualificationRecordDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 上岗资格核查历史记录 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface QualificationRecordMapper extends BaseMapperX<QualificationRecordDO> {

    default PageResult<QualificationRecordDO> selectPage(QualificationRecordPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<QualificationRecordDO>()
                .likeIfPresent(QualificationRecordDO::getUserName, reqVO.getUserName())
                .likeIfPresent(QualificationRecordDO::getMobile, reqVO.getMobile())
                .orderByDesc(QualificationRecordDO::getId));
    }

}
