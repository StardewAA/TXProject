package cn.iocoder.txgy.module.hrm.service.salary.monthrecord;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.monthrecord.HrmSalaryMonthRecordCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.monthrecord.HrmSalaryMonthRecordPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.monthrecord.HrmSalaryPayrollReadinessRespVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.monthrecord.HrmSalaryMonthRecordDO;

import java.util.List;
import java.util.Map;

/**
 * HRM 月度工资 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmSalaryMonthRecordService {

    /**
     * 创建月度工资表
     *
     * @param reqVO 创建信息
     * @return 月度工资表编号
     */
    Long createMonthRecord(HrmSalaryMonthRecordCreateReqVO reqVO);

    /**
     * 创建下月工资表
     *
     * @return 月度工资表编号
     */
    Long createNextMonthRecord();

    /**
     * 核算月度工资表
     *
     * @param id 月度工资表编号
     */
    void computeMonthRecord(Long id);

    /**
     * 使用导入数据核算月度工资表
     *
     * @param id 月度工资表编号
     * @param attendanceRows 考勤导入数据
     * @param additionalDeductionRows 专项附加扣除导入数据
     * @param cumulativeTaxRows 累计个税导入数据
     */
    void computeMonthRecord(Long id, List<Map<Integer, String>> attendanceRows,
                            List<Map<Integer, String>> additionalDeductionRows,
                            List<Map<Integer, String>> cumulativeTaxRows);

    /**
     * 核算月度工资表
     *
     * @param id 月度工资表编号
     * @param syncInsuranceData 是否同步社保数据
     * @param syncAttendanceData 是否同步考勤数据
     * @param attendanceRows 考勤导入数据
     * @param additionalDeductionRows 专项附加扣除导入数据
     * @param cumulativeTaxRows 累计个税导入数据
     */
    void computeMonthRecord(Long id, boolean syncInsuranceData, boolean syncAttendanceData,
                            List<Map<Integer, String>> attendanceRows,
                            List<Map<Integer, String>> additionalDeductionRows,
                            List<Map<Integer, String>> cumulativeTaxRows);

    /**
     * 核算月度工资表（带「未入组人员」处理策略）
     *
     * @param id 月度工资表编号
     * @param syncInsuranceData 是否同步社保数据
     * @param syncAttendanceData 是否同步考勤数据
     * @param ignoreNoSalaryGroup 是否忽略「未分配薪资组」的计薪人员并继续核算（Q-013 / I-008）
     * @param attendanceRows 考勤导入数据
     * @param additionalDeductionRows 专项附加扣除导入数据
     * @param cumulativeTaxRows 累计个税导入数据
     */
    void computeMonthRecord(Long id, boolean syncInsuranceData, boolean syncAttendanceData,
                            boolean ignoreNoSalaryGroup,
                            List<Map<Integer, String>> attendanceRows,
                            List<Map<Integer, String>> additionalDeductionRows,
                            List<Map<Integer, String>> cumulativeTaxRows);

    /**
     * 工资表「环比上月」对比（I-023）
     *
     * <p>汇总口径 + 薪资项级别 + 员工级别的逐项增减，供工资表详情页的环比面板高亮展示；
     * 上月没有工资表时 {@code hasPrevious=false}，其余字段为空集合。</p>
     *
     * @param monthRecordId 本月工资表编号
     * @return 环比对比结果
     */
    cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.monthrecord.HrmSalaryMonthOnMonthRespVO
            getMonthOnMonthComparison(Long monthRecordId);

    /**
     * 删除月度工资表
     *
     * @param id 月度工资表编号
     */
    void deleteMonthRecord(Long id);

    /**
     * 获得月度工资表
     *
     * @param id 月度工资表编号
     * @return 月度工资表
     */
    HrmSalaryMonthRecordDO getMonthRecord(Long id);

    /**
     * 获得指定年月的月度工资表
     *
     * @param year 年份
     * @param month 月份
     * @return 月度工资表
     */
    HrmSalaryMonthRecordDO getMonthRecordByYearMonth(Integer year, Integer month);

    /**
     * 获得最近月度工资表
     *
     * @return 最近月度工资表
     */
    HrmSalaryMonthRecordDO getLastMonthRecord();

    /**
     * 获得工资核算准备情况
     *
     * @param monthRecordId 月度工资表编号
     * @return 工资核算准备情况
     */
    HrmSalaryPayrollReadinessRespVO getPayrollReadiness(Long monthRecordId);

    /**
     * 获得计薪员工列表
     *
     * @param monthRecord 月度工资表
     * @return 员工列表
     */
    List<HrmEmployeeDO> getPayrollEmployeeList(HrmSalaryMonthRecordDO monthRecord);

    /**
     * 获得月度工资表分页
     *
     * @param reqVO 分页查询
     * @return 月度工资表分页
     */
    PageResult<HrmSalaryMonthRecordDO> getMonthRecordPage(HrmSalaryMonthRecordPageReqVO reqVO);

    /**
     * 获得指定状态的月度工资表列表
     *
     * @param status 状态
     * @return 月度工资表列表
     */
    List<HrmSalaryMonthRecordDO> getMonthRecordListByStatus(Integer status);

    /**
     * 校验月度工资表是否存在
     *
     * @param id 月度工资表编号
     * @return 月度工资表
     */
    HrmSalaryMonthRecordDO validateMonthRecordExists(Long id);

    /**
     * 校验月度工资表是否存在，并锁定当前记录
     *
     * @param id 月度工资表编号
     * @return 月度工资表
     */
    HrmSalaryMonthRecordDO validateMonthRecordExistsForUpdate(Long id);

    /**
     * 校验月度工资表是否可编辑
     *
     * @param id 月度工资表编号
     * @return 月度工资表
     */
    HrmSalaryMonthRecordDO validateMonthRecordEditable(Long id);

    /**
     * 校验月度工资表是否可编辑，并锁定当前记录
     *
     * @param id 月度工资表编号
     * @return 月度工资表
     */
    HrmSalaryMonthRecordDO validateMonthRecordEditableForUpdate(Long id);

    /**
     * 更新月度工资表汇总
     *
     * @param id 月度工资表编号
     */
    void updateMonthRecordSummary(Long id);

}
