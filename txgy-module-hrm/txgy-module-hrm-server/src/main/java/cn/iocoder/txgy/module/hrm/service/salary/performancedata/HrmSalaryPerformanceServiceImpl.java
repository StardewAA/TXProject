package cn.iocoder.txgy.module.hrm.service.salary.performancedata;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.common.util.collection.CollectionUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.performancedata.HrmSalaryPerformanceRespVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.performance.assessment.HrmPerformanceAssessmentDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.performance.plan.HrmPerformancePlanDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.performance.assessment.HrmPerformanceAssessmentMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.performance.plan.HrmPerformancePlanMapper;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HRM 绩效结果取数 Service 实现类
 *
 * <p>只取"流程已完成（process_status = 2）"的考核单，保证不会把进行中的成绩带进工资。</p>
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmSalaryPerformanceServiceImpl implements HrmSalaryPerformanceService {

    /**
     * 流程处理状态：2 已完成
     */
    private static final Integer PROCESS_STATUS_FINISHED = 2;

    @Resource
    private HrmPerformanceAssessmentMapper assessmentMapper;
    @Resource
    private HrmPerformancePlanMapper planMapper;
    @Resource
    private HrmEmployeeService employeeService;

    @Override
    public HrmSalaryPerformanceRespVO getLatestPerformance(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        List<HrmPerformanceAssessmentDO> list = assessmentMapper.selectList(
                new LambdaQueryWrapperX<HrmPerformanceAssessmentDO>()
                        .eq(HrmPerformanceAssessmentDO::getEmployeeId, employeeId)
                        .eq(HrmPerformanceAssessmentDO::getProcessStatus, PROCESS_STATUS_FINISHED)
                        .orderByDesc(HrmPerformanceAssessmentDO::getId)
                        .last("LIMIT 1"));
        if (list.isEmpty()) {
            HrmSalaryPerformanceRespVO empty = new HrmSalaryPerformanceRespVO();
            empty.setEmployeeId(employeeId);
            empty.setFound(false);
            return empty;
        }
        return buildList(list).get(0);
    }

    @Override
    public List<HrmSalaryPerformanceRespVO> getPerformanceListByPlan(Long planId) {
        if (planId == null) {
            return List.of();
        }
        return buildList(assessmentMapper.selectList(
                new LambdaQueryWrapperX<HrmPerformanceAssessmentDO>()
                        .eq(HrmPerformanceAssessmentDO::getPlanId, planId)
                        .eq(HrmPerformanceAssessmentDO::getProcessStatus, PROCESS_STATUS_FINISHED)));
    }

    @Override
    public Map<Long, HrmSalaryPerformanceRespVO> getLatestPerformanceMap(Collection<Long> employeeIds) {
        if (CollUtil.isEmpty(employeeIds)) {
            return Collections.emptyMap();
        }
        // 1. 一次性查出这批员工的「已完成」考核单，按 id 倒序
        List<HrmPerformanceAssessmentDO> assessments = assessmentMapper.selectList(
                new LambdaQueryWrapperX<HrmPerformanceAssessmentDO>()
                        .in(HrmPerformanceAssessmentDO::getEmployeeId, employeeIds)
                        .eq(HrmPerformanceAssessmentDO::getProcessStatus, PROCESS_STATUS_FINISHED)
                        .orderByDesc(HrmPerformanceAssessmentDO::getId));
        if (CollUtil.isEmpty(assessments)) {
            return Collections.emptyMap();
        }
        // 2. 同一员工可能有多条已完成考核，只保留最新一条
        Map<Long, HrmPerformanceAssessmentDO> latestMap = new LinkedHashMap<>();
        for (HrmPerformanceAssessmentDO assessment : assessments) {
            latestMap.putIfAbsent(assessment.getEmployeeId(), assessment);
        }
        return CollectionUtils.convertMap(buildList(new ArrayList<>(latestMap.values())),
                HrmSalaryPerformanceRespVO::getEmployeeId);
    }

    private List<HrmSalaryPerformanceRespVO> buildList(List<HrmPerformanceAssessmentDO> assessments) {
        if (assessments.isEmpty()) {
            return List.of();
        }
        Set<Long> employeeIds = assessments.stream().map(HrmPerformanceAssessmentDO::getEmployeeId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, HrmEmployeeDO> employeeMap =
                employeeIds.isEmpty() ? Map.of() : employeeService.getEmployeeMap(employeeIds);
        Set<Long> planIds = assessments.stream().map(HrmPerformanceAssessmentDO::getPlanId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, HrmPerformancePlanDO> planMap = planIds.isEmpty() ? Map.of()
                : planMapper.selectList(HrmPerformancePlanDO::getId, planIds).stream()
                        .collect(Collectors.toMap(HrmPerformancePlanDO::getId, item -> item, (a, b) -> a));
        List<HrmSalaryPerformanceRespVO> result = new ArrayList<>(assessments.size());
        for (HrmPerformanceAssessmentDO assessment : assessments) {
            HrmSalaryPerformanceRespVO vo = new HrmSalaryPerformanceRespVO();
            vo.setAssessmentId(assessment.getId());
            vo.setPlanId(assessment.getPlanId());
            HrmPerformancePlanDO plan = assessment.getPlanId() == null ? null : planMap.get(assessment.getPlanId());
            if (plan != null) {
                vo.setPlanName(plan.getName());
            }
            vo.setEmployeeId(assessment.getEmployeeId());
            HrmEmployeeDO employee = assessment.getEmployeeId() == null ? null
                    : employeeMap.get(assessment.getEmployeeId());
            if (employee != null) {
                vo.setEmployeeName(employee.getName());
                vo.setJobNumber(employee.getJobNumber());
            }
            vo.setScore(assessment.getScore());
            vo.setResultLevel(assessment.getResultLevel());
            vo.setCoefficient(assessment.getCoefficient());
            vo.setArchiveTime(assessment.getArchiveTime());
            vo.setFound(true);
            result.add(vo);
        }
        return result;
    }

}
