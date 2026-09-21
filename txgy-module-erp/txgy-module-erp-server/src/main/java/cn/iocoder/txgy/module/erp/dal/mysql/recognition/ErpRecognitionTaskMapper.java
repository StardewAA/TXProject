package cn.iocoder.txgy.module.erp.dal.mysql.recognition;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.controller.admin.recognition.vo.ErpRecognitionTaskPageReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.recognition.ErpRecognitionTaskDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图片识别任务 Mapper。
 */
@Mapper
public interface ErpRecognitionTaskMapper extends BaseMapperX<ErpRecognitionTaskDO> {

    default PageResult<ErpRecognitionTaskDO> selectPage(ErpRecognitionTaskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ErpRecognitionTaskDO>()
                .eqIfPresent(ErpRecognitionTaskDO::getBizType, reqVO.getBizType())
                .eqIfPresent(ErpRecognitionTaskDO::getStatus, reqVO.getStatus())
                .likeIfPresent(ErpRecognitionTaskDO::getSourceFileUrl, reqVO.getKeyword())
                .orderByDesc(ErpRecognitionTaskDO::getId));
    }

    default ErpRecognitionTaskDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(ErpRecognitionTaskDO::getId, id);
    }

}
