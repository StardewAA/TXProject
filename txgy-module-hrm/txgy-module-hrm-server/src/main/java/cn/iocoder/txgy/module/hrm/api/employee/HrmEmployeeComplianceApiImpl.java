package cn.iocoder.txgy.module.hrm.api.employee;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.api.employee.dto.HrmEmployeeComplianceRespDTO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.blacklist.HrmEmployeeBlacklistDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeCertificateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.safety.HrmHealthCheckDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.safety.HrmSafetyEducationDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.blacklist.HrmEmployeeBlacklistMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.experience.HrmEmployeeCertificateMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.info.HrmEmployeeMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.safety.HrmHealthCheckMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.safety.HrmSafetyEducationMapper;
import cn.iocoder.txgy.module.hrm.enums.employee.info.HrmEmployeeEntryStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.safety.HrmHealthCheckResultEnum;
import cn.iocoder.txgy.module.hrm.enums.safety.HrmSafetyEducationLevelEnum;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 员工上岗合规状态 API 实现类
 *
 * <p>给 MES 预留：MES 进场 / 派工前调用，判断"这个人合不合格"。</p>
 *
 * @author OPENLAB BS
 */
@RestController
@Validated
public class HrmEmployeeComplianceApiImpl implements HrmEmployeeComplianceApi {

    @Resource
    private HrmEmployeeMapper employeeMapper;
    @Resource
    private HrmEmployeeCertificateMapper certificateMapper;
    @Resource
    private HrmSafetyEducationMapper safetyEducationMapper;
    @Resource
    private HrmHealthCheckMapper healthCheckMapper;
    @Resource
    private HrmEmployeeBlacklistMapper blacklistMapper;

    @Override
    public CommonResult<HrmEmployeeComplianceRespDTO> getEmployeeCompliance(Long employeeId) {
        if (employeeId == null) {
            return success(null);
        }
        List<HrmEmployeeComplianceRespDTO> list = buildList(List.of(employeeId));
        return success(CollUtil.getFirst(list));
    }

    @Override
    public CommonResult<List<HrmEmployeeComplianceRespDTO>> getEmployeeComplianceList(Collection<Long> employeeIds) {
        if (CollUtil.isEmpty(employeeIds)) {
            return success(List.of());
        }
        return success(buildList(employeeIds));
    }

    private List<HrmEmployeeComplianceRespDTO> buildList(Collection<Long> employeeIds) {
        List<HrmEmployeeDO> employees = employeeMapper.selectList(HrmEmployeeDO::getId, employeeIds);
        if (CollUtil.isEmpty(employees)) {
            return List.of();
        }
        Set<Long> ids = employees.stream().map(HrmEmployeeDO::getId).collect(Collectors.toSet());
        // 1. 批量装载四类资质数据，避免 N+1 查询
        Map<Long, List<HrmEmployeeCertificateDO>> certificateMap = certificateMapper
                .selectList(new LambdaQueryWrapperX<HrmEmployeeCertificateDO>()
                        .in(HrmEmployeeCertificateDO::getEmployeeId, ids))
                .stream().collect(Collectors.groupingBy(HrmEmployeeCertificateDO::getEmployeeId));
        Map<Long, List<HrmSafetyEducationDO>> educationMap = safetyEducationMapper
                .selectList(new LambdaQueryWrapperX<HrmSafetyEducationDO>()
                        .in(HrmSafetyEducationDO::getEmployeeId, ids))
                .stream().collect(Collectors.groupingBy(HrmSafetyEducationDO::getEmployeeId));
        Map<Long, List<HrmHealthCheckDO>> healthMap = healthCheckMapper
                .selectList(new LambdaQueryWrapperX<HrmHealthCheckDO>()
                        .in(HrmHealthCheckDO::getEmployeeId, ids))
                .stream().collect(Collectors.groupingBy(HrmHealthCheckDO::getEmployeeId));
        List<HrmEmployeeBlacklistDO> blacklists = blacklistMapper
                .selectList(new LambdaQueryWrapperX<HrmEmployeeBlacklistDO>()
                        .in(HrmEmployeeBlacklistDO::getEmployeeId, ids)
                        .eq(HrmEmployeeBlacklistDO::getStatus, 1));
        Map<Long, HrmEmployeeBlacklistDO> blacklistMap = blacklists.stream()
                .collect(Collectors.toMap(HrmEmployeeBlacklistDO::getEmployeeId, item -> item, (a, b) -> a));
        // 2. 逐人组装结论
        List<HrmEmployeeComplianceRespDTO> result = new ArrayList<>(employees.size());
        for (HrmEmployeeDO employee : employees) {
            result.add(buildOne(employee, certificateMap.getOrDefault(employee.getId(), List.of()),
                    educationMap.getOrDefault(employee.getId(), List.of()),
                    healthMap.getOrDefault(employee.getId(), List.of()),
                    blacklistMap.get(employee.getId())));
        }
        return result;
    }

    private HrmEmployeeComplianceRespDTO buildOne(HrmEmployeeDO employee,
                                                  List<HrmEmployeeCertificateDO> certificates,
                                                  List<HrmSafetyEducationDO> educations,
                                                  List<HrmHealthCheckDO> healthChecks,
                                                  HrmEmployeeBlacklistDO blacklist) {
        HrmEmployeeComplianceRespDTO resp = new HrmEmployeeComplianceRespDTO();
        resp.setEmployeeId(employee.getId());
        resp.setJobNumber(employee.getJobNumber());
        resp.setName(employee.getName());
        resp.setMobile(employee.getMobile());
        resp.setEntryStatus(employee.getEntryStatus());
        HrmEmployeeEntryStatusEnum entryStatus = HrmEmployeeEntryStatusEnum.valueOf(employee.getEntryStatus());
        resp.setEntryStatusName(entryStatus == null ? null : entryStatus.getName());

        List<String> reasons = new ArrayList<>();
        // 2.1 在职状态：只有在职员工才可能上岗
        boolean onJob = HrmEmployeeEntryStatusEnum.ACTIVE.getStatus().equals(employee.getEntryStatus());
        resp.setOnJob(onJob);
        if (!onJob) {
            reasons.add("员工当前非在职状态（" + (resp.getEntryStatusName() == null ? "未知" : resp.getEntryStatusName()) + "）");
        }
        // 2.2 黑名单
        resp.setBlacklistHit(blacklist != null);
        if (blacklist != null) {
            resp.setBlacklistReason(blacklist.getReason());
            reasons.add("命中黑名单：" + blacklist.getReason());
        }
        // 2.3 证照有效期
        LocalDateTime now = LocalDateTime.now();
        List<String> invalidCertificates = certificates.stream()
                .filter(item -> item.getEndTime() != null && item.getEndTime().isBefore(now))
                .map(HrmEmployeeCertificateDO::getName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        resp.setCertificateTotal(certificates.size());
        resp.setCertificateValidCount(certificates.size() - invalidCertificates.size());
        resp.setInvalidCertificates(invalidCertificates);
        if (CollUtil.isNotEmpty(invalidCertificates)) {
            reasons.add("证照已过期：" + String.join("、", invalidCertificates));
        }
        // 2.4 三级安全教育（HRM 侧只校验公司级；项目级/班组级由 MES 结合具体项目校验）
        Set<Integer> passedLevels = educations.stream()
                .filter(item -> Boolean.TRUE.equals(item.getQualified()))
                .map(HrmSafetyEducationDO::getLevel)
                .collect(Collectors.toSet());
        List<String> passedLevelNames = new ArrayList<>();
        List<String> missingLevelNames = new ArrayList<>();
        for (HrmSafetyEducationLevelEnum level : HrmSafetyEducationLevelEnum.values()) {
            if (passedLevels.contains(level.getLevel())) {
                passedLevelNames.add(level.getName());
            } else {
                missingLevelNames.add(level.getName());
            }
        }
        resp.setPassedEducationLevels(passedLevelNames);
        resp.setMissingEducationLevels(missingLevelNames);
        boolean companyEducated = passedLevels.contains(HrmSafetyEducationLevelEnum.COMPANY.getLevel());
        resp.setSafetyEducationPassed(companyEducated);
        if (!companyEducated) {
            reasons.add("未完成公司级安全教育");
        }
        // 2.5 体检：取有效期最晚的一条合格记录
        LocalDate today = LocalDate.now();
        HrmHealthCheckDO latestValid = healthChecks.stream()
                .filter(item -> HrmHealthCheckResultEnum.QUALIFIED.getResult().equals(item.getResult()))
                .filter(item -> item.getValidUntil() == null || !item.getValidUntil().isBefore(today))
                .max((a, b) -> {
                    LocalDate left = a.getValidUntil() == null ? LocalDate.MIN : a.getValidUntil();
                    LocalDate right = b.getValidUntil() == null ? LocalDate.MIN : b.getValidUntil();
                    return left.compareTo(right);
                })
                .orElse(null);
        resp.setHealthCheckPassed(latestValid != null);
        resp.setHealthValidUntil(latestValid == null ? null : latestValid.getValidUntil());
        if (latestValid == null) {
            reasons.add("没有在有效期内的合格体检记录");
        }
        // 2.6 总体结论
        resp.setReasons(reasons);
        resp.setPassed(reasons.isEmpty());
        resp.setCheckedAt(LocalDateTime.now());
        return resp;
    }

}
