package cn.iocoder.txgy.module.erp.dal.mysql.recognition;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.recognition.ErpRecognitionImportRecordDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图片识别导入记录 Mapper。
 */
@Mapper
public interface ErpRecognitionImportRecordMapper extends BaseMapperX<ErpRecognitionImportRecordDO> {

    default ErpRecognitionImportRecordDO selectByTaskId(Long taskId) {
        return selectOne(ErpRecognitionImportRecordDO::getTaskId, taskId);
    }

}
