package cn.iocoder.txgy.module.hrm.service.employee.config;

import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.config.HrmEmployeeArchiveFieldConfigSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.config.HrmEmployeeCreateFieldConfigSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.config.HrmEmployeeFieldConfigRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeSaveReqVO;

import java.util.List;
import java.util.Set;

/**
 * HRM 员工字段配置 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmEmployeeFieldConfigService {

    /**
     * 获得新建员工字段配置
     *
     * @param entryStatus 入职状态
     * @return 字段配置列表
     */
    List<HrmEmployeeFieldConfigRespVO> getEmployeeCreateFieldConfigList(Integer entryStatus);

    /**
     * 保存新建员工字段配置
     *
     * @param reqVO 字段配置
     */
    void saveEmployeeCreateFieldConfig(HrmEmployeeCreateFieldConfigSaveReqVO reqVO);

    /**
     * 校验新建员工请求只包含当前配置可见的字段
     *
     * @param reqVO 员工保存请求
     * @param entryStatus 目标入职状态
     */
    void validateEmployeeCreateFields(HrmEmployeeSaveReqVO reqVO, Integer entryStatus);

    /**
     * 清空「当前入职状态下未显示」的字段
     *
     * <p>用于招聘转员工等自动建档场景：候选人数据可能包含租户未开放的字段（如最高学历），
     * 若直接提交会被"字段未显示不能提交"的校验拦截，这里按字段配置自动剔除。</p>
     *
     * @param reqVO 员工信息
     * @param entryStatus 入职状态
     */
    void clearInvisibleCreateFields(HrmEmployeeSaveReqVO reqVO, Integer entryStatus);

    /**
     * 获得员工档案字段配置
     *
     * @return 字段配置列表
     */
    List<HrmEmployeeFieldConfigRespVO> getEmployeeArchiveFieldConfigList();

    /**
     * 保存员工档案字段配置
     *
     * @param reqVO 字段配置
     */
    void saveEmployeeArchiveFieldConfig(HrmEmployeeArchiveFieldConfigSaveReqVO reqVO);

    /**
     * 获得员工端可见的档案字段名
     *
     * @return 字段名集合
     */
    Set<String> getVisibleArchiveFieldNames();

    /**
     * 获得允许员工编辑的档案字段名
     *
     * @return 字段名集合
     */
    Set<String> getEditableArchiveFieldNames();

}
