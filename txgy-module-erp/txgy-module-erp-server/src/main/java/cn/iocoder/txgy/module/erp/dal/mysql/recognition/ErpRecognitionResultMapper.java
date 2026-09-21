package cn.iocoder.txgy.module.erp.dal.mysql.recognition;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.recognition.ErpRecognitionResultDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图片识别结果 Mapper。
 */
@Mapper
public interface ErpRecognitionResultMapper extends BaseMapperX<ErpRecognitionResultDO> {

    default ErpRecognitionResultDO selectByTaskId(Long taskId) {
        return selectOne(ErpRecognitionResultDO::getTaskId, taskId);
    }

}
