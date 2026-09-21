package cn.iocoder.txgy.module.hrm.service.employee.info;

import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.file.HrmEmployeeFileSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeFileDO;

import java.util.List;

/**
 * HRM 员工材料附件 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmEmployeeFileService {

    /**
     * 保存员工指定类型的材料附件
     *
     * @param reqVO 附件信息
     */
    void saveEmployeeFiles(HrmEmployeeFileSaveReqVO reqVO);

    /**
     * 获得员工材料附件列表
     *
     * @param employeeId 员工编号
     * @return 材料附件列表
     */
    List<HrmEmployeeFileDO> getEmployeeFileList(Long employeeId);

    /**
     * 追加员工材料附件（只增不删，自动按地址去重）
     *
     * <p>与 {@link #saveEmployeeFiles} 的区别：后者是「按类型整体覆盖」，
     * 系统自动生成档案文档时必须用追加语义，避免把 HR 手工上传的材料删掉。</p>
     *
     * @param employeeId 员工编号
     * @param type 附件类型
     * @param fileUrls 附件地址列表
     * @return 实际新增的附件地址
     */
    List<String> appendEmployeeFiles(Long employeeId, Integer type, List<String> fileUrls);

}
