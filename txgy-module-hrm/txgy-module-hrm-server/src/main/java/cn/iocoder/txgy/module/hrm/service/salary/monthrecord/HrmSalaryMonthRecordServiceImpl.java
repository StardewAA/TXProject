package cn.iocoder.txgy.module.hrm.service.salary.monthrecord;

import cn.iocoder.txgy.module.hrm.service.salary.config.HrmSalaryConfigService;
import cn.iocoder.txgy.module.hrm.service.salary.config.HrmSalaryGroupService;
import cn.iocoder.txgy.module.hrm.service.salary.config.HrmSalaryOptionService;
import cn.iocoder.txgy.module.hrm.service.salary.config.HrmSalaryTaxRuleService;
import cn.iocoder.txgy.module.hrm.service.salary.employee.HrmSalaryEmployeeInfoService;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.date.LocalDateTimeUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.attendance.vo.statistics.HrmAttendanceMonthRecordRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeListReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.option.HrmSalaryOptionValueVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.monthrecord.HrmSalaryMonthRecordCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.monthrecord.HrmSalaryMonthRecordPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.monthrecord.HrmSalaryMonthOnMonthRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.monthrecord.HrmSalaryPayrollReadinessRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.performancedata.HrmSalaryPerformanceRespVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeChangeRecordDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.insurance.monthrecord.HrmInsuranceMonthEmployeeRecordDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.config.HrmSalaryConfigDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.config.HrmSalaryGroupDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.monthrecord.HrmSalaryMonthEmployeeRecordDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.monthrecord.HrmSalaryMonthRecordDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.config.HrmSalaryOptionDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.config.HrmSalaryTaxRuleDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.salary.monthrecord.HrmSalaryMonthRecordMapper;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeChangeTypeEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.info.HrmEmployeeEntryStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.salary.config.HrmSalaryOptionCodeEnum;
import cn.iocoder.txgy.module.hrm.service.attendance.statistics.HrmAttendanceStatisticsService;
import cn.iocoder.txgy.module.hrm.service.employee.employment.HrmEmployeeChangeRecordService;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.hrm.service.insurance.monthrecord.HrmInsuranceMonthEmployeeRecordService;
import cn.iocoder.txgy.module.hrm.service.insurance.monthrecord.HrmInsuranceMonthRecordService;
import cn.iocoder.txgy.module.hrm.service.salary.performancedata.HrmSalaryPerformanceService;
import cn.iocoder.txgy.module.hrm.enums.salary.config.HrmSalarySocialSecurityMonthTypeEnum;
import cn.iocoder.txgy.module.hrm.enums.salary.monthrecord.HrmSalaryMonthRecordStatusEnum;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.hutool.core.util.ObjectUtil.defaultIfNull;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMultiMap;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.filterList;
import static cn.iocoder.txgy.framework.common.util.date.LocalDateTimeUtils.isAfterOrEqual;
import static cn.iocoder.txgy.framework.common.util.date.LocalDateTimeUtils.isBeforeOrEqual;
import static cn.iocoder.txgy.framework.common.util.number.MoneyUtils.priceAdd;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.SALARY_CONFIG_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.SALARY_MONTH_RECORD_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.SALARY_MONTH_RECORD_CANNOT_DELETE_ONLY;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.SALARY_MONTH_RECORD_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.SALARY_MONTH_RECORD_NOT_LATEST;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.SALARY_MONTH_RECORD_STATUS_INVALID;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.SALARY_PAYROLL_GROUP_MISSING;
import static cn.iocoder.txgy.module.hrm.enums.LogRecordConstants.*;
import static cn.iocoder.txgy.module.hrm.dal.dataobject.salary.config.HrmSalaryOptionDO.ROOT_PARENT_CODE;
import static cn.iocoder.txgy.module.hrm.util.HrmExcelUtils.getCell;
import static cn.iocoder.txgy.module.hrm.util.HrmExcelUtils.parseAmount;

/**
 * HRM 月度工资 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
@Slf4j
public class HrmSalaryMonthRecordServiceImpl implements HrmSalaryMonthRecordService {

    private static final String MONTH_RECORD_TITLE_TEMPLATE = "%d年%d月工资表";

    @Resource
    private HrmSalaryMonthRecordMapper monthRecordMapper;

    @Resource
    private HrmSalaryOptionService salaryOptionService;
    @Resource
    private HrmSalaryConfigService salaryConfigService;
    @Resource
    private HrmEmployeeService employeeService;
    @Resource
    private HrmEmployeeChangeRecordService employeeChangeRecordService;
    @Resource
    private HrmAttendanceStatisticsService attendanceStatisticsService;
    @Resource
    private HrmInsuranceMonthEmployeeRecordService insuranceMonthEmployeeRecordService;
    @Resource
    private HrmInsuranceMonthRecordService insuranceMonthRecordService;
    @Resource
    private HrmSalaryMonthEmployeeRecordService monthEmployeeRecordService;
    @Resource
    private HrmSalaryEmployeeInfoService salaryEmployeeInfoService;
    @Resource
    private HrmSalaryGroupService salaryGroupService;
    @Resource
    private cn.iocoder.txgy.module.hrm.service.salary.piece.HrmSalaryPieceRecordService pieceRecordService;
    @Resource
    private HrmSalaryTaxRuleService salaryTaxRuleService;
    @Resource
    private HrmSalaryPerformanceService salaryPerformanceService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = HRM_SALARY_MONTH_TYPE, subType = HRM_SALARY_MONTH_CREATE_SUB_TYPE,
            bizNo = "{{#salaryMonthRecord.id}}", success = HRM_SALARY_MONTH_CREATE_SUCCESS)
    public Long createMonthRecord(HrmSalaryMonthRecordCreateReqVO reqVO) {
        // 1. 校验年月唯一性
        if (monthRecordMapper.selectByYearMonth(reqVO.getYear(), reqVO.getMonth()) != null) {
            throw exception(SALARY_MONTH_RECORD_EXISTS);
        }

        // 2.1 根据工资配置计算计薪周期
        YearMonth yearMonth = YearMonth.of(reqVO.getYear(), reqVO.getMonth());
        HrmSalaryConfigDO salaryConfig = salaryConfigService.getSalaryConfig();
        LocalDate defaultStartDate = resolveMonthDay(yearMonth,
                salaryConfig == null ? null : salaryConfig.getCycleStartDay(), 1);
        YearMonth endYearMonth = salaryConfig != null && salaryConfig.getCycleStartDay() != null
                && salaryConfig.getCycleStartDay() > 1 ? yearMonth.plusMonths(1) : yearMonth;
        LocalDate defaultEndDate = resolveMonthDay(endYearMonth,
                salaryConfig == null ? null : salaryConfig.getCycleEndDay(), endYearMonth.lengthOfMonth());
        // 2.2 创建月度工资表
        HrmSalaryMonthRecordDO monthRecord = BeanUtils.toBean(reqVO, HrmSalaryMonthRecordDO.class)
                .setTitle(reqVO.getTitle() != null ? reqVO.getTitle()
                        : String.format(MONTH_RECORD_TITLE_TEMPLATE, reqVO.getYear(), reqVO.getMonth()))
                .setStartTime(reqVO.getStartTime() == null ? defaultStartDate.atStartOfDay() : reqVO.getStartTime())
                .setEndTime(reqVO.getEndTime() == null ? defaultEndDate.atStartOfDay() : reqVO.getEndTime())
                .setEmployeeCount(0).setStatus(HrmSalaryMonthRecordStatusEnum.UNCOMPUTED.getStatus())
                .setExpectedPaySalary(BigDecimal.ZERO).setPersonalTax(BigDecimal.ZERO).setRealPaySalary(BigDecimal.ZERO)
                .setOptionHeaders(buildOptionHeaderList(salaryOptionService.getSalaryOptionList(false, true)));
        // 唯一性由数据库兜底（uk_hrm_salary_month_record_active_year_month，Q-012 / I-004）：
        // 并发创建同年月工资表时上面的「先查后插」可能同时通过，这里把唯一键冲突统一转成业务提示。
        try {
            monthRecordMapper.insert(monthRecord);
        } catch (DuplicateKeyException ex) {
            throw exception(SALARY_MONTH_RECORD_EXISTS);
        }

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("salaryMonthRecord", monthRecord);
        return monthRecord.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = HRM_SALARY_MONTH_TYPE, subType = HRM_SALARY_MONTH_CREATE_SUB_TYPE,
            bizNo = "{{#salaryMonthRecord.id}}", success = HRM_SALARY_MONTH_CREATE_SUCCESS)
    public Long createNextMonthRecord() {
        // 1. 获得最近月度工资表
        HrmSalaryMonthRecordDO lastRecord = monthRecordMapper.selectLast();

        // 2.1 首次创建时，使用工资配置的开始年月
        YearMonth nextMonth;
        if (lastRecord == null) {
            HrmSalaryConfigDO salaryConfig = salaryConfigService.getSalaryConfig();
            if (salaryConfig == null || salaryConfig.getStartYear() == null || salaryConfig.getStartMonth() == null) {
                throw exception(SALARY_CONFIG_NOT_EXISTS);
            }
            nextMonth = YearMonth.of(salaryConfig.getStartYear(), salaryConfig.getStartMonth());
        } else {
            // 2.2 非首次创建时，归档最近月度工资表
            if (ObjectUtil.notEqual(lastRecord.getStatus(), HrmSalaryMonthRecordStatusEnum.HISTORY.getStatus())) {
                monthRecordMapper.updateById(new HrmSalaryMonthRecordDO()
                        .setId(lastRecord.getId()).setStatus(HrmSalaryMonthRecordStatusEnum.HISTORY.getStatus()));
            }
            nextMonth = YearMonth.of(lastRecord.getYear(), lastRecord.getMonth()).plusMonths(1);
        }

        // 3. 创建下月工资表
        return createMonthRecord(new HrmSalaryMonthRecordCreateReqVO()
                .setYear(nextMonth.getYear()).setMonth(nextMonth.getMonthValue()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = HRM_SALARY_MONTH_TYPE, subType = HRM_SALARY_MONTH_COMPUTE_SUB_TYPE,
            bizNo = "{{#salaryMonthRecord.id}}", success = HRM_SALARY_MONTH_COMPUTE_SUCCESS)
    public void computeMonthRecord(Long id) {
        computeMonthRecord(id, false, false,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void computeMonthRecord(Long id, List<Map<Integer, String>> attendanceRows,
                                   List<Map<Integer, String>> additionalDeductionRows,
                                   List<Map<Integer, String>> cumulativeTaxRows) {
        computeMonthRecord(id, false, false,
                attendanceRows, additionalDeductionRows, cumulativeTaxRows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = HRM_SALARY_MONTH_TYPE, subType = HRM_SALARY_MONTH_COMPUTE_SUB_TYPE,
            bizNo = "{{#salaryMonthRecord.id}}", success = HRM_SALARY_MONTH_COMPUTE_SUCCESS)
    public void computeMonthRecord(Long id, boolean syncInsuranceData, boolean syncAttendanceData,
                                   List<Map<Integer, String>> attendanceRows,
                                   List<Map<Integer, String>> additionalDeductionRows,
                                   List<Map<Integer, String>> cumulativeTaxRows) {
        computeMonthRecord(id, syncInsuranceData, syncAttendanceData, false,
                attendanceRows, additionalDeductionRows, cumulativeTaxRows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = HRM_SALARY_MONTH_TYPE, subType = HRM_SALARY_MONTH_COMPUTE_SUB_TYPE,
            bizNo = "{{#salaryMonthRecord.id}}", success = HRM_SALARY_MONTH_COMPUTE_SUCCESS)
    public void computeMonthRecord(Long id, boolean syncInsuranceData, boolean syncAttendanceData,
                                   boolean ignoreNoSalaryGroup,
                                   List<Map<Integer, String>> attendanceRows,
                                   List<Map<Integer, String>> additionalDeductionRows,
                                   List<Map<Integer, String>> cumulativeTaxRows) {
        // 1.1 校验工资表并构造计薪周期
        HrmSalaryMonthRecordDO monthRecord = validateMonthRecordEditableForUpdate(id);
        LocalDateTime[] salaryTimes = LocalDateTimeUtils.getDateTimeRange(
                monthRecord.getStartTime().toLocalDate(), monthRecord.getEndTime().toLocalDate());
        // 1.2 获得计薪周期内、且已分配薪资组的员工
        List<HrmEmployeeDO> payrollEmployees = getPayrollEmployeeList(monthRecord);
        Map<Long, HrmSalaryGroupDO> salaryGroupMap = salaryGroupService.getEmployeeSalaryGroupMap(payrollEmployees);
        // 计件工资：按当月「计件计量明细」汇总，核算时自动写入「计件工资」薪资项
        Map<Long, BigDecimal> pieceAmountMap = pieceRecordService.getPieceAmountMap(
                convertSet(payrollEmployees, HrmEmployeeDO::getId), monthRecord.getYear(), monthRecord.getMonth());
        // Q-013 / I-008：未分配薪资组的计薪人员此前会被静默丢弃（工资表里"少了人"却不提示），
        // 现在默认阻断并给出名单，HR 确认后可勾选「忽略未入组人员」继续核算。
        List<HrmEmployeeDO> noSalaryGroupEmployees = filterList(payrollEmployees,
                employee -> !salaryGroupMap.containsKey(employee.getId()));
        if (CollUtil.isNotEmpty(noSalaryGroupEmployees) && !ignoreNoSalaryGroup) {
            throw exception(SALARY_PAYROLL_GROUP_MISSING, noSalaryGroupEmployees.size(),
                    buildEmployeeNamePreview(noSalaryGroupEmployees));
        }
        payrollEmployees = filterList(payrollEmployees, employee -> salaryGroupMap.containsKey(employee.getId()));

        // 2. 加载工资项、考勤、社保和导入数据
        List<HrmSalaryOptionDO> optionList = salaryOptionService.getSalaryOptionList(false);
        Map<Integer, HrmSalaryOptionDO> optionMap = convertMap(optionList, HrmSalaryOptionDO::getCode);
        Map<String, AttendanceImportData> attendanceImportMap = syncAttendanceData
                ? Collections.emptyMap() : parseAttendanceImportData(attendanceRows, optionMap);
        Map<Long, HrmAttendanceMonthRecordRespVO> attendanceMap = syncAttendanceData
                ? attendanceStatisticsService.getAttendanceMonthRecordMap(
                        monthRecord.getYear(), monthRecord.getMonth(),
                        convertList(payrollEmployees, HrmEmployeeDO::getId))
                : Collections.emptyMap();
        HrmSalaryConfigDO salaryConfig = salaryConfigService.getSalaryConfig();
        YearMonth socialSecurityYearMonth = resolveSocialSecurityYearMonth(
                YearMonth.of(monthRecord.getYear(), monthRecord.getMonth()), salaryConfig);
        if (syncInsuranceData) {
            insuranceMonthRecordService.validateMonthRecordExists(
                    socialSecurityYearMonth.getYear(), socialSecurityYearMonth.getMonthValue());
        }
        Map<Long, HrmInsuranceMonthEmployeeRecordDO> insuranceMap = syncInsuranceData
                ? insuranceMonthEmployeeRecordService.getNormalMonthEmployeeRecordMap(
                        socialSecurityYearMonth.getYear(), socialSecurityYearMonth.getMonthValue())
                : Collections.emptyMap();
        Map<String, List<HrmSalaryOptionValueVO>> additionalDeductionImportMap =
                parseOptionImportData(additionalDeductionRows, getAdditionalDeductionImportColumns(), optionMap);
        Map<String, List<HrmSalaryOptionValueVO>> cumulativeTaxImportMap =
                parseOptionImportData(cumulativeTaxRows, getCumulativeTaxImportColumns(), optionMap);
        // 2.5 绩效结果（用于算绩效工资：绩效工资基数 × 绩效系数）
        Map<Long, HrmSalaryPerformanceRespVO> performanceMap = salaryPerformanceService
                .getLatestPerformanceMap(convertSet(payrollEmployees, HrmEmployeeDO::getId));
        Map<Long, HrmSalaryTaxRuleDO> taxRuleMap = salaryTaxRuleService.getSalaryTaxRuleMap(
                convertSet(salaryGroupMap.values(), HrmSalaryGroupDO::getTaxRuleId));
        HrmSalaryTaxRuleDO defaultTaxRule = CollUtil.getFirst(salaryTaxRuleService.getSalaryTaxRuleList());

        // 3. 逐个员工核算工资
        List<HrmSalaryMonthEmployeeRecordDO> employeeRecords = new ArrayList<>();
        Map<Long, List<HrmSalaryOptionValueVO>> employeeOptionValueMap = new LinkedHashMap<>();
        Map<Long, HrmSalaryTaxRuleDO> employeeTaxRuleMap = new LinkedHashMap<>();
        for (HrmEmployeeDO employee : payrollEmployees) {
            // 3.1 获得本周期生效薪资；没有薪资档案时继承上月可维护薪资项
            String jobNumber = employee.getJobNumber();
            List<HrmSalaryOptionValueVO> values = BeanUtils.toBean(
                    salaryEmployeeInfoService.getEffectiveSalaryOptionList(employee, salaryTimes),
                    HrmSalaryOptionValueVO.class);
            if (CollUtil.isEmpty(values)) {
                YearMonth previousMonth = YearMonth.of(monthRecord.getYear(), monthRecord.getMonth()).minusMonths(1);
                HrmSalaryMonthEmployeeRecordDO previousEmployeeRecord = monthEmployeeRecordService
                        .getMonthEmployeeRecordByEmployeeIdAndYearMonth(employee.getId(), previousMonth.getYear(), previousMonth.getMonthValue());
                if (previousEmployeeRecord != null) {
                    values = convertList(previousEmployeeRecord.getOptionValues(),
                            optionValue -> new HrmSalaryOptionValueVO().setCode(optionValue.getCode()).setName(optionValue.getName()).setValue(optionValue.getValue()),
                            optionValue -> {
                                HrmSalaryOptionDO option = optionMap.get(optionValue.getCode());
                                return option != null && option.getParentCode() != null
                                        && option.getParentCode() >= HrmSalaryOptionDO.ADJUSTABLE_CATEGORY_MIN_CODE
                                        && option.getParentCode() <= HrmSalaryOptionDO.ADJUSTABLE_CATEGORY_MAX_CODE;
                            });
                }
            }

            // 3.2 合并考勤、社保、专项附加扣除和累计个税数据
            AttendanceImportData attendanceData = syncAttendanceData
                    ? buildAttendanceData(attendanceMap.get(employee.getId()), optionMap)
                    : jobNumber == null ? null : attendanceImportMap.get(jobNumber);
            if (attendanceData != null) {
                values = mergeOptionValues(values, attendanceData.getOptionValues());
            }
            if (syncInsuranceData) {
                HrmInsuranceMonthEmployeeRecordDO insurance = insuranceMap.get(employee.getId());
                if (insurance == null) {
                    // 社保月记录缺失时，个人社保/公积金会按 0 参与核算（存在多发风险）。
                    // 按约束不改算钱逻辑，这里只做可观测留痕；发放前提示见 getPayrollReadiness（P2）
                    log.warn("[computeSalaryMonthRecord][员工({}-{})在社保年月 {}-{} 无社保月记录，个人社保/公积金按 0 核算]",
                            employee.getId(), employee.getName(),
                            socialSecurityYearMonth.getYear(), socialSecurityYearMonth.getMonthValue());
                }
                values = mergeOptionValues(values, buildInsuranceOptionValues(insurance, optionMap));
            }
            if (jobNumber != null) {
                values = mergeOptionValues(values, additionalDeductionImportMap.getOrDefault(jobNumber, Collections.emptyList()));
                values = mergeOptionValues(values, cumulativeTaxImportMap.getOrDefault(jobNumber, Collections.emptyList()));
            }
            // 3.2.5 绩效工资：绩效工资基数 × 绩效系数（无已完成考核时系数按 1）
            values = applyPerformanceSalary(values, performanceMap.get(employee.getId()));

            // 3.2.6 计件工资：按当月「计件计量明细」的 工程量 × 单价 汇总（不参与首月按天数折算，
            //        因为计件是工人实际完成的工程量）
            BigDecimal pieceAmount = pieceAmountMap.get(employee.getId());
            if (pieceAmount != null) {
                values = mergePieceworkSalary(values, pieceAmount);
            }

            // 3.3 构建员工月工资记录，并准备批量核算所需数据
            HrmSalaryGroupDO salaryGroup = salaryGroupMap.get(employee.getId());
            HrmSalaryTaxRuleDO taxRule = taxRuleMap.getOrDefault(salaryGroup.getTaxRuleId(), defaultTaxRule);
            BigDecimal salaryStandard = defaultIfNull(salaryGroup.getSalaryStandard(), HrmSalaryGroupDO.DEFAULT_SALARY_STANDARD);
            employeeRecords.add(HrmSalaryMonthEmployeeRecordDO.builder()
                    .monthRecordId(id).employeeId(employee.getId())
                    .actualWorkDay(attendanceData == null ? salaryStandard : attendanceData.getActualWorkDay())
                    .needWorkDay(salaryStandard).year(monthRecord.getYear()).month(monthRecord.getMonth()).build());
            employeeOptionValueMap.put(employee.getId(), values);
            employeeTaxRuleMap.put(employee.getId(), taxRule);
        }
        employeeRecords = monthEmployeeRecordService.calculateMonthEmployeeRecordList(
                employeeRecords, employeeOptionValueMap, employeeTaxRuleMap);
        monthEmployeeRecordService.saveMonthEmployeeRecordList(id, employeeRecords);

        // 4. 更新工资表状态、表头和汇总
        monthRecordMapper.updateById(new HrmSalaryMonthRecordDO().setId(id).setStatus(HrmSalaryMonthRecordStatusEnum.COMPUTED.getStatus())
                .setOptionHeaders(buildOptionHeaderList(salaryOptionService.getSalaryOptionList(false, true))));
        updateMonthRecordSummary(id);

        // 5. 记录操作日志上下文
        LogRecordContext.putVariable("salaryMonthRecord", monthRecord);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = HRM_SALARY_MONTH_TYPE, subType = HRM_SALARY_MONTH_DELETE_SUB_TYPE,
            bizNo = "{{#salaryMonthRecord.id}}", success = HRM_SALARY_MONTH_DELETE_SUCCESS)
    public void deleteMonthRecord(Long id) {
        // 1. 校验仅删除最新工资表，并至少保留一张工资表
        HrmSalaryMonthRecordDO salaryMonthRecord = validateMonthRecordEditableForUpdate(id);
        HrmSalaryMonthRecordDO lastMonthRecord = monthRecordMapper.selectLast();
        if (lastMonthRecord == null || ObjectUtil.notEqual(lastMonthRecord.getId(), salaryMonthRecord.getId())) {
            throw exception(SALARY_MONTH_RECORD_NOT_LATEST);
        }
        if (monthRecordMapper.selectCount() <= 1) {
            throw exception(SALARY_MONTH_RECORD_CANNOT_DELETE_ONLY);
        }

        // 2.1 删除当前月员工工资记录和工资表
        monthEmployeeRecordService.deleteMonthEmployeeRecordListByMonthRecordId(id);
        monthRecordMapper.deleteById(id);
        // 2.2 恢复上一月工资表为已核算状态
        HrmSalaryMonthRecordDO previousMonthRecord = monthRecordMapper.selectLast();
        monthRecordMapper.updateById(new HrmSalaryMonthRecordDO()
                .setId(previousMonthRecord.getId()).setStatus(HrmSalaryMonthRecordStatusEnum.COMPUTED.getStatus()));

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("salaryMonthRecord", salaryMonthRecord);
    }

    @Override
    public HrmSalaryMonthRecordDO getMonthRecord(Long id) {
        return monthRecordMapper.selectById(id);
    }

    @Override
    public HrmSalaryMonthRecordDO getMonthRecordByYearMonth(Integer year, Integer month) {
        return monthRecordMapper.selectByYearMonth(year, month);
    }

    @Override
    public HrmSalaryMonthRecordDO getLastMonthRecord() {
        return monthRecordMapper.selectLast();
    }

    @Override
    public HrmSalaryPayrollReadinessRespVO getPayrollReadiness(Long monthRecordId) {
        // 1. 获得月度工资表和计薪周期内的员工
        HrmSalaryMonthRecordDO monthRecord = monthRecordId == null ? monthRecordMapper.selectLast()
                : monthRecordMapper.selectById(monthRecordId);
        if (monthRecord == null) {
            return null;
        }
        List<HrmEmployeeDO> validEmployees = getPayrollEmployeeList(monthRecord);

        // 2. 筛选已配置薪资组和已定薪的员工
        Map<Long, HrmSalaryGroupDO> salaryGroupMap =
                salaryGroupService.getEmployeeSalaryGroupMap(validEmployees);
        List<HrmEmployeeDO> payrollCandidates =
                filterList(validEmployees, employee -> salaryGroupMap.containsKey(employee.getId()));
        List<HrmEmployeeDO> noSalaryGroupEmployees =
                filterList(validEmployees, employee -> !salaryGroupMap.containsKey(employee.getId()));
        Set<Long> candidateIds = convertSet(payrollCandidates, HrmEmployeeDO::getId);
        Set<Long> salaryEmployeeIds = salaryEmployeeInfoService.getSalaryEmployeeInfoMap(candidateIds).keySet();

        // 3. 获得实际计薪员工和工资周期内的员工异动
        List<HrmSalaryMonthEmployeeRecordDO> employeeRecords = monthEmployeeRecordService
                .getMonthEmployeeRecordListByMonthRecordId(monthRecord.getId());
        Set<Long> payrollEmployeeIds = CollUtil.isEmpty(employeeRecords) ? candidateIds
                : convertSet(employeeRecords, HrmSalaryMonthEmployeeRecordDO::getEmployeeId);
        List<HrmEmployeeDO> noSalaryEmployees = filterList(payrollCandidates,
                employee -> employee.getId() != null && !salaryEmployeeIds.contains(employee.getId()));
        List<HrmEmployeeChangeRecordDO> changeRecords = employeeChangeRecordService
                .getEmployeeChangeRecordListByEmployeeIdsAndEffectTimeBetween(payrollEmployeeIds,
                        LocalDateTimeUtils.getDateTimeRange(monthRecord.getStartTime().toLocalDate(),
                                monthRecord.getEndTime().toLocalDate()));

        // 4. 构建核算准备情况
        HrmSalaryConfigDO salaryConfig = salaryConfigService.getSalaryConfig();
        YearMonth salaryYearMonth = YearMonth.of(monthRecord.getYear(), monthRecord.getMonth());
        YearMonth socialSecurityYearMonth = resolveSocialSecurityYearMonth(salaryYearMonth, salaryConfig);
        List<HrmSalaryPayrollReadinessRespVO.Employee> noSalaryEmployeeVOs =
                buildPayrollReadinessEmployeeList(noSalaryEmployees);
        List<HrmSalaryPayrollReadinessRespVO.Employee> noSalaryGroupEmployeeVOs =
                buildPayrollReadinessEmployeeList(noSalaryGroupEmployees);
        // 4.1 社保数据缺失检查：当月无社保月记录时个人社保/公积金会按 0 核算，
        //     发放前必须让 HR 看到，避免静默多发（不改算钱逻辑，只做提示，P2）
        Map<Long, HrmInsuranceMonthEmployeeRecordDO> insuranceMap =
                insuranceMonthEmployeeRecordService.getNormalMonthEmployeeRecordMap(
                        socialSecurityYearMonth.getYear(), socialSecurityYearMonth.getMonthValue());
        List<HrmEmployeeDO> insuranceMissingEmployees = filterList(payrollCandidates,
                employee -> employee.getId() != null && !insuranceMap.containsKey(employee.getId()));
        List<HrmSalaryPayrollReadinessRespVO.Employee> insuranceMissingEmployeeVOs =
                buildPayrollReadinessEmployeeList(insuranceMissingEmployees);
        // 4.2 考勤数据缺失检查：当月没有任何考勤数据（出勤天数为 0）时，缺勤/迟到扣款会按 0 核算，
        //     发放前提示 HR 核对打卡与请假数据（同样不改算钱逻辑）
        Map<Long, cn.iocoder.txgy.module.hrm.controller.admin.attendance.vo.statistics.HrmAttendanceMonthRecordRespVO>
                attendanceMap = attendanceStatisticsService.getAttendanceMonthRecordMap(
                        monthRecord.getYear(), monthRecord.getMonth(),
                        convertList(payrollCandidates, HrmEmployeeDO::getId));
        List<HrmEmployeeDO> attendanceMissingEmployees = filterList(payrollCandidates, employee -> {
            if (employee.getId() == null) {
                return false;
            }
            var attendance = attendanceMap.get(employee.getId());
            return attendance == null || attendance.getAttendDays() == null || attendance.getAttendDays() == 0;
        });
        List<HrmSalaryPayrollReadinessRespVO.Employee> attendanceMissingEmployeeVOs =
                buildPayrollReadinessEmployeeList(attendanceMissingEmployees);
        // 4.3 计薪项单位缺失：启用且参与计算的计薪项应配置单位（选择计薪项时自动带出）
        List<String> salaryOptionUnitMissingNames = convertList(
                filterList(salaryOptionService.getSalaryOptionList(), option ->
                        Boolean.TRUE.equals(option.getEnabled()) && Boolean.TRUE.equals(option.getCalculateEnabled())
                                && cn.hutool.core.util.StrUtil.isBlank(option.getUnit())
                                && option.getParentCode() != null
                                && !cn.iocoder.txgy.module.hrm.dal.dataobject.salary.config.HrmSalaryOptionDO.ROOT_PARENT_CODE
                                .equals(option.getParentCode())),
                cn.iocoder.txgy.module.hrm.dal.dataobject.salary.config.HrmSalaryOptionDO::getName);
        return new HrmSalaryPayrollReadinessRespVO()
                .setMonthRecordId(monthRecord.getId()).setTitle(monthRecord.getTitle())
                .setYear(monthRecord.getYear()).setMonth(monthRecord.getMonth())
                .setStartTime(monthRecord.getStartTime()).setEndTime(monthRecord.getEndTime())
                .setSocialSecurityYearMonth(socialSecurityYearMonth.toString())
                .setPayrollEmployeeCount((long) payrollEmployeeIds.size())
                .setSalaryEmployeeCount((long) salaryEmployeeIds.size())
                .setNoSalaryEmployeeCount((long) noSalaryEmployeeVOs.size())
                .setNoSalaryGroupEmployeeCount((long) noSalaryGroupEmployeeVOs.size())
                .setInsuranceMissingEmployeeCount((long) insuranceMissingEmployeeVOs.size())
                .setAttendanceMissingEmployeeCount((long) attendanceMissingEmployeeVOs.size())
                .setSalaryOptionUnitMissingCount((long) salaryOptionUnitMissingNames.size())
                .setChangeEmployeeCount((long) changeRecords.size())
                .setChangeTypeCountMap(buildPayrollReadinessChangeTypeCountMap(changeRecords))
                .setNoSalaryEmployees(noSalaryEmployeeVOs)
                .setNoSalaryGroupEmployees(noSalaryGroupEmployeeVOs)
                .setInsuranceMissingEmployees(insuranceMissingEmployeeVOs)
                .setAttendanceMissingEmployees(attendanceMissingEmployeeVOs)
                .setSalaryOptionUnitMissingNames(salaryOptionUnitMissingNames);
    }

    private List<HrmSalaryPayrollReadinessRespVO.Employee> buildPayrollReadinessEmployeeList(
            List<HrmEmployeeDO> employees) {
        return convertList(employees, employee ->
                BeanUtils.toBean(employee, HrmSalaryPayrollReadinessRespVO.Employee.class)
                        .setEmployeeId(employee.getId()).setEmployeeName(employee.getName()));
    }

    @Override
    public HrmSalaryMonthOnMonthRespVO getMonthOnMonthComparison(Long monthRecordId) {
        // 1. 本月工资表 + 上月工资表（上年 12 月 → 本年 1 月由 YearMonth 自动处理）
        HrmSalaryMonthRecordDO monthRecord = validateMonthRecordExists(monthRecordId);
        YearMonth currentYearMonth = YearMonth.of(monthRecord.getYear(), monthRecord.getMonth());
        YearMonth previousYearMonth = currentYearMonth.minusMonths(1);
        HrmSalaryMonthRecordDO previousRecord = monthRecordMapper.selectByYearMonth(
                previousYearMonth.getYear(), previousYearMonth.getMonthValue());

        // 2. 两个月的员工工资记录
        List<HrmSalaryMonthEmployeeRecordDO> currentRecords =
                monthEmployeeRecordService.getMonthEmployeeRecordListByMonthRecordId(monthRecordId);
        List<HrmSalaryMonthEmployeeRecordDO> previousRecords = previousRecord == null
                ? Collections.emptyList()
                : monthEmployeeRecordService.getMonthEmployeeRecordListByMonthRecordId(previousRecord.getId());

        // 3. 员工信息（姓名/工号）
        Set<Long> employeeIds = new LinkedHashSet<>();
        employeeIds.addAll(convertSet(currentRecords, HrmSalaryMonthEmployeeRecordDO::getEmployeeId));
        employeeIds.addAll(convertSet(previousRecords, HrmSalaryMonthEmployeeRecordDO::getEmployeeId));
        employeeIds.remove(null);
        Map<Long, HrmEmployeeDO> employeeMap = employeeIds.isEmpty()
                ? Collections.emptyMap() : employeeService.getEmployeeMap(employeeIds);
        Map<Long, HrmSalaryMonthEmployeeRecordDO> previousRecordMap =
                convertMap(previousRecords, HrmSalaryMonthEmployeeRecordDO::getEmployeeId);

        // 4. 员工级别环比：本月有记录的先出，上月有、本月没有的补 LEFT，按实发变动绝对值倒序
        List<HrmSalaryMonthOnMonthRespVO.EmployeeRow> employeeRows = new ArrayList<>();
        Set<Long> currentEmployeeIds = new HashSet<>();
        for (HrmSalaryMonthEmployeeRecordDO current : currentRecords) {
            if (current.getEmployeeId() == null) {
                continue;
            }
            currentEmployeeIds.add(current.getEmployeeId());
            HrmSalaryMonthEmployeeRecordDO previous = previousRecordMap.get(current.getEmployeeId());
            employeeRows.add(buildMonthOnMonthEmployeeRow(current.getEmployeeId(), employeeMap,
                    current.getExpectedPaySalary(), current.getRealPaySalary(),
                    previous == null ? null : previous.getExpectedPaySalary(),
                    previous == null ? null : previous.getRealPaySalary()));
        }
        for (HrmSalaryMonthEmployeeRecordDO previous : previousRecords) {
            if (previous.getEmployeeId() == null || currentEmployeeIds.contains(previous.getEmployeeId())) {
                continue;
            }
            employeeRows.add(buildMonthOnMonthEmployeeRow(previous.getEmployeeId(), employeeMap,
                    null, null, previous.getExpectedPaySalary(), previous.getRealPaySalary()));
        }
        employeeRows.sort((left, right) -> absOrZero(deltaOf(right.getCurrentRealPaySalary(),
                        right.getPreviousRealPaySalary()))
                .compareTo(absOrZero(deltaOf(left.getCurrentRealPaySalary(), left.getPreviousRealPaySalary()))));

        // 5. 薪资项级别环比：同编码配对求差，按变动绝对值倒序（供前端高亮增减项）
        Map<Integer, HrmSalaryMonthEmployeeRecordDO.OptionValue> currentOptionMap =
                convertMap(monthEmployeeRecordService.getMonthOptionSummary(currentRecords),
                        HrmSalaryMonthEmployeeRecordDO.OptionValue::getCode);
        Map<Integer, HrmSalaryMonthEmployeeRecordDO.OptionValue> previousOptionMap =
                previousRecords.isEmpty() ? Collections.emptyMap()
                        : convertMap(monthEmployeeRecordService.getMonthOptionSummary(previousRecords),
                        HrmSalaryMonthEmployeeRecordDO.OptionValue::getCode);
        Set<Integer> optionCodes = new LinkedHashSet<>(currentOptionMap.keySet());
        optionCodes.addAll(previousOptionMap.keySet());
        List<HrmSalaryMonthOnMonthRespVO.OptionRow> optionRows = new ArrayList<>();
        for (Integer code : optionCodes) {
            if (code == null) {
                continue;
            }
            HrmSalaryMonthEmployeeRecordDO.OptionValue currentOption = currentOptionMap.get(code);
            HrmSalaryMonthEmployeeRecordDO.OptionValue previousOption = previousOptionMap.get(code);
            BigDecimal currentValue = currentOption == null ? null : currentOption.getValue();
            BigDecimal previousValue = previousOption == null ? null : previousOption.getValue();
            optionRows.add(new HrmSalaryMonthOnMonthRespVO.OptionRow()
                    .setCode(code)
                    .setName(currentOption != null ? currentOption.getName()
                            : previousOption == null ? String.valueOf(code) : previousOption.getName())
                    .setCurrentValue(currentValue).setPreviousValue(previousValue)
                    .setDelta(deltaOf(currentValue, previousValue)));
        }
        optionRows.sort((left, right) -> absOrZero(right.getDelta()).compareTo(absOrZero(left.getDelta())));

        // 6. 组装结果
        return new HrmSalaryMonthOnMonthRespVO()
                .setMonthRecordId(monthRecord.getId()).setTitle(monthRecord.getTitle())
                .setYear(monthRecord.getYear()).setMonth(monthRecord.getMonth())
                .setHasPrevious(previousRecord != null)
                .setPreviousMonthRecordId(previousRecord == null ? null : previousRecord.getId())
                .setPreviousTitle(previousRecord == null ? null : previousRecord.getTitle())
                .setPreviousYear(previousRecord == null ? null : previousRecord.getYear())
                .setPreviousMonth(previousRecord == null ? null : previousRecord.getMonth())
                .setCurrent(buildMonthOnMonthTotals(monthRecord, currentRecords))
                .setPrevious(previousRecord == null ? null : buildMonthOnMonthTotals(previousRecord, previousRecords))
                .setOptionRows(optionRows)
                .setEmployeeRows(employeeRows);
    }

    private HrmSalaryMonthOnMonthRespVO.Totals buildMonthOnMonthTotals(
            HrmSalaryMonthRecordDO monthRecord, List<HrmSalaryMonthEmployeeRecordDO> employeeRecords) {
        // 已核算的工资表优先用表头汇总值；未汇总（为 0）时按员工记录实时合计，避免环比全 0
        BigDecimal expectedPay = monthRecord.getExpectedPaySalary();
        BigDecimal realPay = monthRecord.getRealPaySalary();
        if ((expectedPay == null || expectedPay.signum() == 0) && CollUtil.isNotEmpty(employeeRecords)) {
            expectedPay = employeeRecords.stream()
                    .map(HrmSalaryMonthEmployeeRecordDO::getExpectedPaySalary)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        if ((realPay == null || realPay.signum() == 0) && CollUtil.isNotEmpty(employeeRecords)) {
            realPay = employeeRecords.stream()
                    .map(HrmSalaryMonthEmployeeRecordDO::getRealPaySalary)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        BigDecimal personalTax = monthRecord.getPersonalTax();
        if ((personalTax == null || personalTax.signum() == 0) && CollUtil.isNotEmpty(employeeRecords)) {
            personalTax = employeeRecords.stream()
                    .map(HrmSalaryMonthEmployeeRecordDO::getPersonalTax)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return new HrmSalaryMonthOnMonthRespVO.Totals()
                .setEmployeeCount((long) employeeRecords.size())
                .setExpectedPaySalary(expectedPay)
                .setRealPaySalary(realPay)
                .setPersonalTax(personalTax)
                .setPersonalInsuranceAmount(monthRecord.getPersonalInsuranceAmount())
                .setPersonalProvidentFundAmount(monthRecord.getPersonalProvidentFundAmount());
    }

    private HrmSalaryMonthOnMonthRespVO.EmployeeRow buildMonthOnMonthEmployeeRow(
            Long employeeId, Map<Long, HrmEmployeeDO> employeeMap,
            BigDecimal currentExpectedPay, BigDecimal currentRealPay,
            BigDecimal previousExpectedPay, BigDecimal previousRealPay) {
        HrmEmployeeDO employee = employeeMap.get(employeeId);
        String changeType;
        if (currentRealPay == null) {
            changeType = "LEFT";
        } else if (previousRealPay == null) {
            changeType = "NEW";
        } else if (currentRealPay.compareTo(previousRealPay) == 0) {
            changeType = "SAME";
        } else {
            changeType = "CHANGED";
        }
        return new HrmSalaryMonthOnMonthRespVO.EmployeeRow()
                .setEmployeeId(employeeId)
                .setEmployeeName(employee == null ? null : employee.getName())
                .setJobNumber(employee == null ? null : employee.getJobNumber())
                .setCurrentExpectedPaySalary(currentExpectedPay)
                .setCurrentRealPaySalary(currentRealPay)
                .setPreviousExpectedPaySalary(previousExpectedPay)
                .setPreviousRealPaySalary(previousRealPay)
                .setChangeType(changeType);
    }

    /** 本月 - 上月；任一侧为空时按另一侧取反计算，两侧都为空返回 null */
    private BigDecimal deltaOf(BigDecimal currentValue, BigDecimal previousValue) {
        if (currentValue == null && previousValue == null) {
            return null;
        }
        return defaultIfNull(currentValue, BigDecimal.ZERO).subtract(defaultIfNull(previousValue, BigDecimal.ZERO));
    }

    private BigDecimal absOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.abs();
    }

    /**
     * 员工姓名预览：最多展示 5 个「姓名(工号)」，超出部分用「等」收尾（Q-013 / I-008 提示用）
     */
    private String buildEmployeeNamePreview(List<HrmEmployeeDO> employees) {
        return employees.stream().limit(5)
                .map(employee -> StrUtil.blankToDefault(employee.getName(), "未命名")
                        + (StrUtil.isBlank(employee.getJobNumber()) ? "" : "(" + employee.getJobNumber() + ")"))
                .collect(java.util.stream.Collectors.joining("、"))
                + (employees.size() > 5 ? " 等" : "");
    }

    @Override
    public List<HrmEmployeeDO> getPayrollEmployeeList(HrmSalaryMonthRecordDO monthRecord) {
        HrmEmployeeListReqVO employeeReqVO = new HrmEmployeeListReqVO();
        List<HrmEmployeeDO> employees = employeeService.getEmployeeList(employeeReqVO);
        employees = filterList(employees, employee -> (employee.getEntryTime() == null
                        || isBeforeOrEqual(employee.getEntryTime().toLocalDate(), monthRecord.getEndTime().toLocalDate()))
                        && (HrmEmployeeEntryStatusEnum.ACTIVE_STATUSES.contains(employee.getEntryStatus())
                        || Objects.equals(employee.getEntryStatus(), HrmEmployeeEntryStatusEnum.LEFT.getStatus())
                        && employee.getLeaveTime() != null
                        && isAfterOrEqual(employee.getLeaveTime().toLocalDate(), monthRecord.getStartTime().toLocalDate())));
        employees.sort(Comparator.comparing(HrmEmployeeDO::getId));
        return employees;
    }

    @Override
    public PageResult<HrmSalaryMonthRecordDO> getMonthRecordPage(HrmSalaryMonthRecordPageReqVO reqVO) {
        return monthRecordMapper.selectPage(reqVO);
    }

    @Override
    public List<HrmSalaryMonthRecordDO> getMonthRecordListByStatus(Integer status) {
        return monthRecordMapper.selectListByStatus(status);
    }

    @Override
    public HrmSalaryMonthRecordDO validateMonthRecordExists(Long id) {
        HrmSalaryMonthRecordDO monthRecord = monthRecordMapper.selectById(id);
        if (monthRecord == null) {
            throw exception(SALARY_MONTH_RECORD_NOT_EXISTS);
        }
        return monthRecord;
    }

    @Override
    public HrmSalaryMonthRecordDO validateMonthRecordExistsForUpdate(Long id) {
        HrmSalaryMonthRecordDO monthRecord = monthRecordMapper.selectByIdForUpdate(id);
        if (monthRecord == null) {
            throw exception(SALARY_MONTH_RECORD_NOT_EXISTS);
        }
        return monthRecord;
    }

    @Override
    public HrmSalaryMonthRecordDO validateMonthRecordEditable(Long id) {
        HrmSalaryMonthRecordDO monthRecord = validateMonthRecordExists(id);
        if (Objects.equals(monthRecord.getStatus(), HrmSalaryMonthRecordStatusEnum.HISTORY.getStatus())) {
            throw exception(SALARY_MONTH_RECORD_STATUS_INVALID);
        }
        return monthRecord;
    }

    @Override
    public HrmSalaryMonthRecordDO validateMonthRecordEditableForUpdate(Long id) {
        HrmSalaryMonthRecordDO monthRecord = validateMonthRecordExistsForUpdate(id);
        if (Objects.equals(monthRecord.getStatus(), HrmSalaryMonthRecordStatusEnum.HISTORY.getStatus())) {
            throw exception(SALARY_MONTH_RECORD_STATUS_INVALID);
        }
        return monthRecord;
    }

    @Override
    public void updateMonthRecordSummary(Long id) {
        // 1.1 校验工资表可编辑
        HrmSalaryMonthRecordDO monthRecord = validateMonthRecordEditable(id);
        // 1.2 计算员工工资汇总
        List<HrmSalaryMonthEmployeeRecordDO> employeeRecords = monthEmployeeRecordService
                .getMonthEmployeeRecordListByMonthRecordId(monthRecord.getId());
        Map<Integer, BigDecimal> optionSummary = convertMap(
                monthEmployeeRecordService.getMonthOptionSummary(employeeRecords),
                HrmSalaryMonthEmployeeRecordDO.OptionValue::getCode,
                option -> defaultIfNull(option.getValue(), BigDecimal.ZERO));

        // 2. 更新工资表汇总数据
        HrmSalaryMonthRecordDO updateObj = new HrmSalaryMonthRecordDO()
                .setId(id).setEmployeeCount(employeeRecords.size());
        updateObj.setExpectedPaySalary(priceAdd(convertList(employeeRecords,
                HrmSalaryMonthEmployeeRecordDO::getExpectedPaySalary).toArray(new BigDecimal[0])));
        updateObj.setPersonalTax(priceAdd(convertList(employeeRecords,
                HrmSalaryMonthEmployeeRecordDO::getPersonalTax).toArray(new BigDecimal[0])));
        updateObj.setRealPaySalary(priceAdd(convertList(employeeRecords,
                HrmSalaryMonthEmployeeRecordDO::getRealPaySalary).toArray(new BigDecimal[0])));
        updateObj.setPersonalInsuranceAmount(
                optionSummary.getOrDefault(HrmSalaryOptionCodeEnum.PERSONAL_INSURANCE.getCode(), BigDecimal.ZERO));
        updateObj.setPersonalProvidentFundAmount(
                optionSummary.getOrDefault(HrmSalaryOptionCodeEnum.PERSONAL_PROVIDENT_FUND.getCode(), BigDecimal.ZERO));
        updateObj.setCorporateInsuranceAmount(
                optionSummary.getOrDefault(HrmSalaryOptionCodeEnum.CORPORATE_INSURANCE.getCode(), BigDecimal.ZERO));
        updateObj.setCorporateProvidentFundAmount(
                optionSummary.getOrDefault(HrmSalaryOptionCodeEnum.CORPORATE_PROVIDENT_FUND.getCode(), BigDecimal.ZERO));
        monthRecordMapper.updateById(updateObj);
    }

    private AttendanceImportData buildAttendanceData(
            HrmAttendanceMonthRecordRespVO attendance, Map<Integer, HrmSalaryOptionDO> optionMap) {
        if (attendance == null) {
            return null;
        }
        List<HrmSalaryOptionValueVO> optionValues = Arrays.asList(
                importOptionValue(HrmSalaryOptionCodeEnum.OVERTIME_PAY.getCode(), optionMap, BigDecimal.ZERO),
                importOptionValue(HrmSalaryOptionCodeEnum.LATE_DEDUCTION.getCode(), optionMap,
                        defaultIfNull(attendance.getLateDeductAmount(), BigDecimal.ZERO)),
                importOptionValue(HrmSalaryOptionCodeEnum.EARLY_DEDUCTION.getCode(), optionMap,
                        defaultIfNull(attendance.getEarlyDeductAmount(), BigDecimal.ZERO)),
                importOptionValue(HrmSalaryOptionCodeEnum.ABSENTEEISM_DEDUCTION.getCode(), optionMap,
                        defaultIfNull(attendance.getAbsenteeismDeductAmount(), BigDecimal.ZERO)),
                importOptionValue(HrmSalaryOptionCodeEnum.LEAVE_DEDUCTION.getCode(), optionMap, BigDecimal.ZERO),
                importOptionValue(HrmSalaryOptionCodeEnum.MISSING_CARD_DEDUCTION.getCode(), optionMap,
                        defaultIfNull(attendance.getMisscardDeductAmount(), BigDecimal.ZERO)),
                importOptionValue(HrmSalaryOptionCodeEnum.COMPREHENSIVE_DEDUCTION.getCode(), optionMap, BigDecimal.ZERO),
                importOptionValue(HrmSalaryOptionCodeEnum.ATTENDANCE_DEDUCTION_TOTAL.getCode(), optionMap,
                        defaultIfNull(attendance.getAttendanceDeductAmount(), BigDecimal.ZERO)));
        return new AttendanceImportData(
                defaultIfNull(attendance.getActualDays(), BigDecimal.ZERO), optionValues);
    }

    private List<HrmSalaryOptionValueVO> buildInsuranceOptionValues(
            HrmInsuranceMonthEmployeeRecordDO insurance, Map<Integer, HrmSalaryOptionDO> optionMap) {
        if (insurance == null) {
            return Arrays.asList(
                    importOptionValue(HrmSalaryOptionCodeEnum.PERSONAL_INSURANCE.getCode(), optionMap, BigDecimal.ZERO),
                    importOptionValue(HrmSalaryOptionCodeEnum.PERSONAL_PROVIDENT_FUND.getCode(), optionMap, BigDecimal.ZERO),
                    importOptionValue(HrmSalaryOptionCodeEnum.CORPORATE_INSURANCE.getCode(), optionMap, BigDecimal.ZERO),
                    importOptionValue(HrmSalaryOptionCodeEnum.CORPORATE_PROVIDENT_FUND.getCode(), optionMap, BigDecimal.ZERO));
        }
        return Arrays.asList(
                importOptionValue(HrmSalaryOptionCodeEnum.PERSONAL_INSURANCE.getCode(), optionMap,
                        defaultIfNull(insurance.getPersonalInsuranceAmount(), BigDecimal.ZERO)),
                importOptionValue(HrmSalaryOptionCodeEnum.PERSONAL_PROVIDENT_FUND.getCode(), optionMap,
                        defaultIfNull(insurance.getPersonalProvidentFundAmount(), BigDecimal.ZERO)),
                importOptionValue(HrmSalaryOptionCodeEnum.CORPORATE_INSURANCE.getCode(), optionMap,
                        defaultIfNull(insurance.getCorporateInsuranceAmount(), BigDecimal.ZERO)),
                importOptionValue(HrmSalaryOptionCodeEnum.CORPORATE_PROVIDENT_FUND.getCode(), optionMap,
                        defaultIfNull(insurance.getCorporateProvidentFundAmount(), BigDecimal.ZERO)));
    }

    /**
     * 按绩效结果折算绩效工资
     *
     * <p>取「绩效工资」工资项在薪资档案里的金额作为基数，乘以员工最近一次已完成考核的绩效系数；
     * 没有已完成考核（或考核未给系数）时系数按 1，即按基数发放；未配置该工资项时原样返回。</p>
     *
     * @param values      员工当前薪资项值（薪资档案 + 考勤 + 社保等已合并）
     * @param performance 员工最近一次已完成的绩效结果，可为 null
     * @return 折算后的薪资项值
     */
    private List<HrmSalaryOptionValueVO> applyPerformanceSalary(List<HrmSalaryOptionValueVO> values,
                                                                HrmSalaryPerformanceRespVO performance) {
        if (CollUtil.isEmpty(values)) {
            return values;
        }
        // 1. 找绩效工资项，其薪资档案金额即绩效工资基数
        BigDecimal performanceBase = null;
        Integer performanceCode = HrmSalaryOptionCodeEnum.PERFORMANCE_SALARY.getCode();
        for (HrmSalaryOptionValueVO value : values) {
            if (ObjectUtil.equal(value.getCode(), performanceCode)) {
                performanceBase = value.getValue();
                break;
            }
        }
        if (performanceBase == null) {
            return values;
        }
        // 2. 绩效系数：无已完成考核按 1
        BigDecimal coefficient = performance != null && performance.getCoefficient() != null
                ? performance.getCoefficient() : BigDecimal.ONE;
        BigDecimal performanceSalary = defaultIfNull(performanceBase, BigDecimal.ZERO)
                .multiply(coefficient).setScale(2, RoundingMode.HALF_UP);
        // 3. 用折算后的绩效工资替换原基数
        List<HrmSalaryOptionValueVO> result = new ArrayList<>(values.size());
        for (HrmSalaryOptionValueVO value : values) {
            if (ObjectUtil.equal(value.getCode(), performanceCode)) {
                result.add(new HrmSalaryOptionValueVO().setCode(value.getCode()).setName(value.getName())
                        .setValue(performanceSalary));
            } else {
                result.add(value);
            }
        }
        return result;
    }

    private Map<String, AttendanceImportData> parseAttendanceImportData(List<Map<Integer, String>> rows,
                                                                        Map<Integer, HrmSalaryOptionDO> optionMap) {
        // 1. 无导入数据时直接返回
        if (CollUtil.isEmpty(rows)) {
            return Collections.emptyMap();
        }

        // 2. 按工号解析考勤金额和实际出勤天数
        List<ImportColumn> columns = getAttendanceImportColumns();
        Map<String, AttendanceImportData> dataMap = new HashMap<>();
        for (Map<Integer, String> row : rows) {
            String jobNumber = getCell(row, 2);
            if (StrUtil.isBlank(jobNumber)) {
                continue;
            }
            List<HrmSalaryOptionValueVO> values = new ArrayList<>();
            BigDecimal attendanceDeductionTotal = BigDecimal.ZERO;
            for (ImportColumn column : columns) {
                BigDecimal amount = parseAmount(getCell(row, column.getIndex()), BigDecimal.ZERO);
                values.add(importOptionValue(column.getCode(), optionMap, amount));
                if (ObjectUtil.notEqual(column.getCode(), HrmSalaryOptionCodeEnum.OVERTIME_PAY.getCode())) {
                    attendanceDeductionTotal = attendanceDeductionTotal.add(amount);
                }
            }
            values.add(importOptionValue(HrmSalaryOptionCodeEnum.ATTENDANCE_DEDUCTION_TOTAL.getCode(), optionMap, attendanceDeductionTotal));
            dataMap.put(jobNumber, new AttendanceImportData(parseAmount(getCell(row, 11), BigDecimal.ZERO), values));
        }
        return dataMap;
    }

    /**
     * 按员工工号解析导入的薪资项金额
     *
     * @param rows 导入行数据
     * @param columns 导入列定义
     * @param optionMap 薪资项 Map
     * @return 工号与薪资项金额列表的映射
     */
    private Map<String, List<HrmSalaryOptionValueVO>> parseOptionImportData(
            List<Map<Integer, String>> rows, List<ImportColumn> columns, Map<Integer, HrmSalaryOptionDO> optionMap) {
        // 1. 无导入数据时直接返回
        if (CollUtil.isEmpty(rows)) {
            return Collections.emptyMap();
        }

        // 2. 按工号解析各导入列对应的薪资项金额
        Map<String, List<HrmSalaryOptionValueVO>> dataMap = new HashMap<>();
        for (Map<Integer, String> row : rows) {
            String jobNumber = getCell(row, 2);
            if (StrUtil.isBlank(jobNumber)) {
                continue;
            }
            List<HrmSalaryOptionValueVO> values = new ArrayList<>();
            for (ImportColumn column : columns) {
                values.add(importOptionValue(column.getCode(), optionMap,
                        parseAmount(getCell(row, column.getIndex()), BigDecimal.ZERO)));
            }
            dataMap.put(jobNumber, values);
        }
        return dataMap;
    }

    private List<ImportColumn> getAttendanceImportColumns() {
        return Arrays.asList(
                new ImportColumn(4, HrmSalaryOptionCodeEnum.OVERTIME_PAY.getCode()),
                new ImportColumn(5, HrmSalaryOptionCodeEnum.LATE_DEDUCTION.getCode()),
                new ImportColumn(6, HrmSalaryOptionCodeEnum.EARLY_DEDUCTION.getCode()),
                new ImportColumn(7, HrmSalaryOptionCodeEnum.ABSENTEEISM_DEDUCTION.getCode()),
                new ImportColumn(8, HrmSalaryOptionCodeEnum.LEAVE_DEDUCTION.getCode()),
                new ImportColumn(9, HrmSalaryOptionCodeEnum.MISSING_CARD_DEDUCTION.getCode()),
                new ImportColumn(10, HrmSalaryOptionCodeEnum.COMPREHENSIVE_DEDUCTION.getCode())
        );
    }

    private List<ImportColumn> getCumulativeTaxImportColumns() {
        return Arrays.asList(
                new ImportColumn(4, HrmSalaryOptionCodeEnum.LAST_MONTH_CUMULATIVE_INCOME.getCode()),
                new ImportColumn(5, HrmSalaryOptionCodeEnum.LAST_MONTH_CUMULATIVE_DEDUCT_EXPENSE.getCode()),
                new ImportColumn(6, HrmSalaryOptionCodeEnum.LAST_MONTH_CUMULATIVE_SPECIAL_DEDUCTION.getCode()),
                new ImportColumn(7, HrmSalaryOptionCodeEnum.LAST_MONTH_PREPAID_TAX.getCode())
        );
    }

    private List<ImportColumn> getAdditionalDeductionImportColumns() {
        return Arrays.asList(
                new ImportColumn(4, HrmSalaryOptionCodeEnum.CHILD_EDUCATION_DEDUCTION.getCode()),
                new ImportColumn(5, HrmSalaryOptionCodeEnum.HOUSE_RENT_DEDUCTION.getCode()),
                new ImportColumn(6, HrmSalaryOptionCodeEnum.HOUSE_LOAN_DEDUCTION.getCode()),
                new ImportColumn(7, HrmSalaryOptionCodeEnum.ELDERLY_SUPPORT_DEDUCTION.getCode()),
                new ImportColumn(8, HrmSalaryOptionCodeEnum.CONTINUING_EDUCATION_DEDUCTION.getCode())
        );
    }

    private HrmSalaryOptionValueVO importOptionValue(Integer code, Map<Integer, HrmSalaryOptionDO> optionMap,
                                                      BigDecimal amount) {
        HrmSalaryOptionDO option = optionMap.get(code);
        HrmSalaryOptionCodeEnum optionCode = HrmSalaryOptionCodeEnum.valueOf(code);
        String name = option != null ? option.getName() : optionCode != null ? optionCode.getName() : "薪资项" + code;
        return new HrmSalaryOptionValueVO().setCode(code).setName(name).setValue(amount);
    }

    /**
     * 把当月「计件计量明细」汇总出来的计件金额写入「计件工资」薪资项（覆盖档案里可能填的示意值）
     *
     * <p>兼容两种编码：标准编码 60101 与历史简码 60；档案里已存在哪一项就写哪一项，都没有则用标准编码。</p>
     *
     * @param values 员工当月薪资项
     * @param pieceAmount 计件金额合计（工程量 × 单价）
     * @return 合并后的薪资项
     */
    private List<HrmSalaryOptionValueVO> mergePieceworkSalary(
            List<HrmSalaryOptionValueVO> values, BigDecimal pieceAmount) {
        Integer code = HrmSalaryOptionCodeEnum.PIECEWORK_SALARY.getCode();
        for (HrmSalaryOptionValueVO value : values) {
            // 60 为历史简码「计件工资」，60101 为标准编码
            if (Objects.equals(value.getCode(), code) || Objects.equals(value.getCode(), 60)) {
                code = value.getCode();
                break;
            }
        }
        return mergeOptionValues(values, Collections.singletonList(
                new HrmSalaryOptionValueVO().setCode(code).setName("计件工资")
                        .setValue(pieceAmount.setScale(2, java.math.RoundingMode.HALF_UP))));
    }

    private List<HrmSalaryOptionValueVO> mergeOptionValues(
            List<HrmSalaryOptionValueVO> baseValues,
            List<HrmSalaryOptionValueVO> importValues) {
        if (CollUtil.isEmpty(importValues)) {
            return baseValues;
        }
        Map<Integer, HrmSalaryOptionValueVO> valueMap = new LinkedHashMap<>();
        for (HrmSalaryOptionValueVO value : baseValues) {
            if (value.getCode() != null) {
                valueMap.put(value.getCode(), value);
            }
        }
        for (HrmSalaryOptionValueVO value : importValues) {
            if (value.getCode() != null) {
                valueMap.put(value.getCode(), value);
            }
        }
        return new ArrayList<>(valueMap.values());
    }

    private Map<Integer, Long> buildPayrollReadinessChangeTypeCountMap(List<HrmEmployeeChangeRecordDO> changeRecords) {
        Map<Integer, Long> changeTypeCountMap = new LinkedHashMap<>();
        changeTypeCountMap.put(HrmEmployeeChangeTypeEnum.REGULAR.getType(), 0L);
        changeTypeCountMap.put(HrmEmployeeChangeTypeEnum.TRANSFER.getType(), 0L);
        changeTypeCountMap.put(HrmEmployeeChangeTypeEnum.PROMOTION.getType(), 0L);
        changeTypeCountMap.put(HrmEmployeeChangeTypeEnum.DEMOTION.getType(), 0L);
        changeTypeCountMap.put(HrmEmployeeChangeTypeEnum.FULL_TIME.getType(), 0L);
        for (HrmEmployeeChangeRecordDO changeRecord : changeRecords) {
            if (changeRecord.getType() == null) {
                continue;
            }
            changeTypeCountMap.merge(changeRecord.getType(), 1L, Long::sum);
        }
        return changeTypeCountMap;
    }

    private LocalDate resolveMonthDay(YearMonth yearMonth, Integer configuredDay, int defaultDay) {
        int day = configuredDay == null ? defaultDay : configuredDay;
        return yearMonth.atDay(Math.max(1, Math.min(day, yearMonth.lengthOfMonth())));
    }

    private YearMonth resolveSocialSecurityYearMonth(YearMonth salaryYearMonth, HrmSalaryConfigDO salaryConfig) {
        if (salaryConfig == null || salaryConfig.getSocialSecurityMonthType() == null) {
            return salaryYearMonth;
        }
        if (HrmSalarySocialSecurityMonthTypeEnum.PREVIOUS_MONTH.getType().equals(salaryConfig.getSocialSecurityMonthType())) {
            return salaryYearMonth.minusMonths(1);
        }
        if (HrmSalarySocialSecurityMonthTypeEnum.NEXT_MONTH.getType().equals(salaryConfig.getSocialSecurityMonthType())) {
            return salaryYearMonth.plusMonths(1);
        }
        return salaryYearMonth;
    }

    private List<HrmSalaryMonthRecordDO.OptionHeader> buildOptionHeaderList(List<HrmSalaryOptionDO> options) {
        Map<Integer, List<HrmSalaryOptionDO>> childrenMap =
                convertMultiMap(options, HrmSalaryOptionDO::getParentCode);
        List<HrmSalaryOptionDO> categories =
                childrenMap.getOrDefault(ROOT_PARENT_CODE, Collections.emptyList());
        return convertList(categories, category -> {
            List<HrmSalaryMonthRecordDO.OptionHeader> children = convertList(
                    childrenMap.getOrDefault(category.getCode(), Collections.emptyList()),
                    option -> HrmSalaryMonthRecordDO.OptionHeader.builder()
                            .code(option.getCode()).name(option.getName())
                            .children(Collections.emptyList()).build());
            return HrmSalaryMonthRecordDO.OptionHeader.builder()
                    .code(category.getCode()).name(category.getName()).children(children).build();
        });
    }

    /**
     * 薪资导入列定义
     */
    @Getter
    @AllArgsConstructor
    @SuppressWarnings("ClassCanBeRecord")
    private static class ImportColumn {

        /**
         * Excel 列索引
         */
        private final int index;
        /**
         * 薪资项编码
         */
        private final int code;

    }

    /**
     * 员工考勤导入数据
     */
    @Getter
    @AllArgsConstructor
    @SuppressWarnings("ClassCanBeRecord")
    private static class AttendanceImportData {

        /**
         * 实际出勤天数
         */
        private final BigDecimal actualWorkDay;
        /**
         * 薪资项金额列表
         */
        private final List<HrmSalaryOptionValueVO> optionValues;

    }

}
