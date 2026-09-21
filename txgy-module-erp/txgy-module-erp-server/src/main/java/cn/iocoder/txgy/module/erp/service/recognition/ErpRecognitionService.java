package cn.iocoder.txgy.module.erp.service.recognition;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.erp.controller.admin.recognition.vo.ErpRecognitionResultCorrectReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.recognition.vo.ErpRecognitionTaskCreateReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.recognition.vo.ErpRecognitionTaskPageReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.recognition.ErpRecognitionResultDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.recognition.ErpRecognitionTaskDO;
import org.springframework.web.multipart.MultipartFile;

public interface ErpRecognitionService {

    Long createTask(ErpRecognitionTaskCreateReqVO createReqVO);

    Long importExcel(MultipartFile file, String sourceFileUrl, String bizType) throws Exception;

    void retryTask(Long taskId);

    void deleteTask(Long taskId);

    PageResult<ErpRecognitionTaskDO> getTaskPage(ErpRecognitionTaskPageReqVO pageReqVO);

    ErpRecognitionTaskDO getTask(Long taskId);

    ErpRecognitionResultDO getResultByTaskId(Long taskId);

    void correctResult(ErpRecognitionResultCorrectReqVO correctReqVO);

    void confirmTask(Long taskId, Integer resultVersion);

    Long importRecognitionTask(Long taskId, Integer resultVersion);

}
