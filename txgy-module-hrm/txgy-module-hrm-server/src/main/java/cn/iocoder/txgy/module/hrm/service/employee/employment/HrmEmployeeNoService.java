package cn.iocoder.txgy.module.hrm.service.employee.employment;

import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeNoRuleSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeNoRuleDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * HRM 工号生成 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmEmployeeNoService {

    /**
     * 为当前租户生成一个工号（格式：租户缩写 + 顺序号）
     *
     * @return 工号
     */
    String generateJobNumber();

    /**
     * 预览当前租户下一个工号（不占用序号）
     *
     * @return 下一个工号；无规则时返回 null
     */
    String previewNextJobNumber();

    /**
     * 获得当前租户的工号规则列表
     *
     * @return 工号规则列表
     */
    List<HrmEmployeeNoRuleDO> getRuleList();

    /**
     * 获得工号规则
     *
     * @param id 规则编号
     * @return 工号规则
     */
    HrmEmployeeNoRuleDO getRule(Long id);

    /**
     * 保存工号规则（无编号新增、有编号修改）
     *
     * @param reqVO 工号规则
     * @return 规则编号
     */
    Long saveRule(@Valid HrmEmployeeNoRuleSaveReqVO reqVO);

    /**
     * 删除工号规则
     *
     * @param id 规则编号
     */
    void deleteRule(Long id);

    /**
     * 按给定配置预览工号样例（用于配置界面即时预览，不落库）
     *
     * @param reqVO 工号规则
     * @return 工号样例
     */
    String previewSampleJobNumber(HrmEmployeeNoRuleSaveReqVO reqVO);

}
