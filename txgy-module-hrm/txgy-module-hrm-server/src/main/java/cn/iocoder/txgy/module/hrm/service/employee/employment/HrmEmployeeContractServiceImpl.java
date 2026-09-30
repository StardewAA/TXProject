package cn.iocoder.txgy.module.hrm.service.employee.employment;

import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.contract.HrmEmployeeContractSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeContractDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.company.HrmCompanyProfileDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmEmployeeContractFileDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmContractTemplateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitOfferDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.employment.HrmEmployeeContractMapper;
import cn.iocoder.txgy.module.hrm.service.company.HrmCompanyProfileService;
import cn.iocoder.txgy.module.hrm.service.contract.HrmContractTemplateService;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeContractTypeEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeContractStatusEnum;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.service.impl.DiffParseFunction;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;

import static cn.hutool.core.util.ObjectUtil.notEqual;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.date.LocalDateTimeUtils.getDayBeginTime;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_CONTRACT_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_RESOURCE_BELONG_INVALID;
import static cn.iocoder.txgy.module.hrm.enums.LogRecordConstants.*;

/**
 * HRM 员工合同 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
@Slf4j
public class HrmEmployeeContractServiceImpl implements HrmEmployeeContractService {

    /**
     * 自动生成合同的默认期限（单位：年）
     *
     * <p>发 Offer / 转员工时 HR 还没有机会填合同期限，留空会导致两个后果：
     * ① 生成的合同文件里「合同结束日期」为空；
     * ② 签署完成后合同虽然置为「执行中」，但因为没有结束日期，永远进不了「合同到期预警」。
     * 因此按合同类型预置一个默认期限（与员工合同手工新增表单的默认值一致），HR 可在员工合同中调整。</p>
     */
    private static final int DEFAULT_TERM_YEARS = 1;

    /**
     * 「全部」档位的到期窗口上限（天）：10 年，等价于不限制。
     * 用于「合同到期预警」页面选择「全部」时展示所有执行中的合同倒计时。
     */
    private static final int DEFAULT_EXPIRE_WARNING_MAX_DAYS = 3650;

    @Resource
    private HrmEmployeeContractMapper contractMapper;
    @Resource
    private HrmEmployeeService employeeService;
    @Resource
    private HrmContractTemplateService contractTemplateService;
    @Resource
    private HrmCompanyProfileService companyProfileService;
    /** 合同文件台账（B1：生成稿/签署件分类 + 版本溯源） */
    @Resource
    private cn.iocoder.txgy.module.hrm.dal.mysql.contract.HrmEmployeeContractFileMapper contractFileMapper;
    /** 渲染服务：与渲染服务互为依赖，用 @Lazy 打破循环 */
    @Resource
    @org.springframework.context.annotation.Lazy
    private cn.iocoder.txgy.module.hrm.service.contract.HrmContractRenderService contractRenderService;
    /** 签署记录：生成待签合同后自动登记待签记录，用 @Lazy 打破循环 */
    @Resource
    @org.springframework.context.annotation.Lazy
    private cn.iocoder.txgy.module.hrm.service.contract.HrmContractSignRecordService contractSignRecordService;
    /** 文件服务：把渲染出的合同落盘，拿到可下载/可预览的 URL */
    @Resource
    private cn.iocoder.txgy.module.infra.api.file.FileApi fileApi;

    @Override
    public Long generatePendingContract(Long contractId) {
        // 1. 校验合同与员工
        HrmEmployeeContractDO contract = validateContractExists(contractId);
        if (contract.getEmployeeId() == null) {
            throw exception(EMPLOYEE_RESOURCE_BELONG_INVALID, "合同");
        }
        cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO employee =
                employeeService.validateEmployeeExists(contract.getEmployeeId());

        // 2. 渲染合同文件（模板 + 员工/公司信息），按「员工姓名-合同类型.docx」命名后归档到文件服务
        byte[] content = contractRenderService.renderDocx(contractId);
        String fileName = buildContractFileName(contract, employee);
        String fileUrl = fileApi.createFile(content, fileName, "hrm/contract",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        // 3. 写入合同文件台账（type=1 生成稿，版本号自增，记录模板号 + 关键字段快照用于溯源）
        int version = contractFileMapper.selectListByContractId(contractId).stream()
                .filter(item -> HrmEmployeeContractFileDO.FILE_TYPE_GENERATED.equals(item.getFileType()))
                .map(HrmEmployeeContractFileDO::getVersion)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
        HrmEmployeeContractFileDO file = new HrmEmployeeContractFileDO()
                .setContractId(contractId)
                .setEmployeeId(employee.getId())
                .setFileType(HrmEmployeeContractFileDO.FILE_TYPE_GENERATED)
                .setVersion(version)
                .setFileName(fileName)
                .setFileUrl(fileUrl)
                .setTemplateId(contract.getTemplateId())
                .setRenderSnapshot(buildRenderSnapshot(contract));
        contractFileMapper.insert(file);

        // 4. 自动登记「待签」签署记录，HR 上传签署件后即完成同步（合同置执行中 + 归档 + 开启到期提醒）
        contractSignRecordService.createPendingSignRecordIfAbsent(contractId);
        return file.getId();
    }

    @Override
    public java.util.List<HrmEmployeeContractFileDO> getContractFileList(Long contractId) {
        return contractFileMapper.selectListByContractId(contractId);
    }

    @Override
    public Long renewContract(Long contractId, Integer termYears, Long templateId) {
        // 1. 校验原合同
        HrmEmployeeContractDO origin = validateContractExists(contractId);
        if (origin.getEmployeeId() == null) {
            throw exception(EMPLOYEE_RESOURCE_BELONG_INVALID, "合同");
        }
        // 2. 续签模板：入参优先；换模板时合同类型随模板走（否则沿用原合同）
        HrmContractTemplateDO template = templateId != null
                ? contractTemplateService.validateTemplateExists(templateId) : null;
        Integer contractType = template != null ? template.getContractType() : origin.getType();
        Long finalTemplateId = template != null ? template.getId() : origin.getTemplateId();
        // 3. 续签年限：入参 > 原合同期限 > 模板默认（无固定期限类合同不设期限）
        Integer term;
        if (termYears != null && termYears > 0) {
            term = termYears;
        } else if (origin.getTerm() != null && origin.getTerm() > 0) {
            term = origin.getTerm();
        } else {
            term = defaultTermOf(contractType);
        }
        // 3. 新周期：起始日 = 原到期日次日（无到期日则从今天起）
        LocalDateTime startTime = origin.getEndTime() != null
                ? getDayBeginTime(origin.getEndTime().plusDays(1))
                : getDayBeginTime(LocalDateTime.now());
        HrmEmployeeContractDO renew = new HrmEmployeeContractDO()
                .setNo(StrUtil.blankToDefault(origin.getNo(), "HT" + contractId) + "-XQ")
                .setEmployeeId(origin.getEmployeeId())
                .setType(contractType)
                .setTemplateId(finalTemplateId)
                .setSignCompany(origin.getSignCompany())
                .setStartTime(startTime)
                .setTerm(term)
                .setEndTime(term == null ? null : startTime.plusYears(term))
                .setPayAmount(origin.getPayAmount())
                .setStatus(HrmEmployeeContractStatusEnum.NOT_PERFORMED.getStatus())
                // 续签同样要进入到期预警
                .setExpireRemind(true)
                .setSort(origin.getSort() == null ? 0 : origin.getSort() + 1)
                .setRemark("由合同 " + origin.getNo() + " 续签生成");
        contractMapper.insert(renew);
        // 4. 生成待签合同文件（命名：员工姓名-合同类型.docx）+ 自动登记待签签署记录
        generatePendingContract(renew.getId());
        return renew.getId();
    }

    /**
     * 合同文件名：员工姓名-合同类型.docx（例：孙莉-固定期限劳动合同.docx）
     */
    private static String buildContractFileName(HrmEmployeeContractDO contract,
                                               cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO employee) {
        String employeeName = StrUtil.blankToDefault(employee.getName(), "员工");
        String typeName = java.util.Arrays.stream(HrmEmployeeContractTypeEnum.values())
                .filter(item -> item.getType().equals(contract.getType()))
                .map(HrmEmployeeContractTypeEnum::getName)
                .findFirst().orElse("劳动合同");
        return employeeName + "-" + typeName + ".docx";
    }

    /**
     * 生成时的关键字段快照：模板改动后仍可还原这份合同签署时的要素
     */
    private static String buildRenderSnapshot(HrmEmployeeContractDO contract) {
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("contractNo", contract.getNo());
        snapshot.put("contractType", contract.getType());
        snapshot.put("templateId", contract.getTemplateId());
        snapshot.put("startTime", contract.getStartTime());
        snapshot.put("endTime", contract.getEndTime());
        snapshot.put("term", contract.getTerm());
        snapshot.put("payAmount", contract.getPayAmount());
        snapshot.put("signCompany", contract.getSignCompany());
        return cn.hutool.json.JSONUtil.toJsonStr(snapshot);
    }

    @Override
    public List<HrmEmployeeContractDO> getExpireWarningList(int warnDays) {
        // QA 修复：warnDays <= 0 表示「不限制到期窗口」。
        // 此前 <=0 会被强制改成 30 天，导致刚签署完成、到期日在 90 天以外的合同
        // 在任何预警窗口下都查不到，用户看不到「签署完成后开始倒计时」。
        int days = warnDays <= 0 ? DEFAULT_EXPIRE_WARNING_MAX_DAYS : warnDays;
        return contractMapper.selectExpireWarningList(days);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public int expireOverdueContracts() {
        // Q-011 / I-011：此前只有「即将到期」预警，合同过了到期日状态仍停在「执行中」，
        // 导致台账里看不出哪些合同已经失效。这里把到期日已过的执行中合同置为「已到期」。
        List<HrmEmployeeContractDO> overdueList = contractMapper.selectExpiredInProgressList();
        int expired = 0;
        for (HrmEmployeeContractDO contract : overdueList) {
            try {
                contractMapper.updateById(new HrmEmployeeContractDO().setId(contract.getId())
                        .setStatus(HrmEmployeeContractStatusEnum.EXPIRED.getStatus()));
                expired++;
            } catch (Throwable ex) {
                log.warn("[expireOverdueContracts][合同({})置为已到期失败]", contract.getId(), ex);
            }
        }
        if (expired > 0) {
            log.info("[expireOverdueContracts][到期日已过的执行中合同 {} 份已置为「已到期」]", expired);
        }
        return expired;
    }

    @Override
    @LogRecord(type = HRM_EMPLOYEE_TYPE, subType = HRM_EMPLOYEE_CONTRACT_CREATE_SUB_TYPE,
            bizNo = "{{#reqVO.employeeId}}", success = HRM_EMPLOYEE_CONTRACT_CREATE_SUCCESS)
    public Long createContract(HrmEmployeeContractSaveReqVO reqVO) {
        // 1. 校验员工
        employeeService.validateEmployeeExists(reqVO.getEmployeeId());

        // 2. 创建合同
        HrmEmployeeContractDO contract = BeanUtils.toBean(reqVO, HrmEmployeeContractDO.class)
                .setStartTime(getDayBeginTime(reqVO.getStartTime()))
                .setEndTime(getDayBeginTime(reqVO.getEndTime()))
                .setSignTime(getDayBeginTime(reqVO.getSignTime()));
        if (HrmEmployeeContractTypeEnum.NON_FIXED_TERM_LABOR_CONTRACT.getType().equals(contract.getType())) {
            contract.setTerm(null);
        }
        contractMapper.insert(contract);

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("contract", contract);
        return contract.getId();
    }

    @Override
    @LogRecord(type = HRM_EMPLOYEE_TYPE, subType = HRM_EMPLOYEE_CONTRACT_UPDATE_SUB_TYPE,
            bizNo = "{{#reqVO.employeeId}}", success = HRM_EMPLOYEE_CONTRACT_UPDATE_SUCCESS)
    public void updateContract(HrmEmployeeContractSaveReqVO reqVO) {
        // 1. 校验员工和合同
        employeeService.validateEmployeeExists(reqVO.getEmployeeId());
        HrmEmployeeContractDO contract = validateContractExists(reqVO.getId());
        if (notEqual(contract.getEmployeeId(), reqVO.getEmployeeId())) {
            throw exception(EMPLOYEE_RESOURCE_BELONG_INVALID, "合同");
        }

        // 2. 更新合同
        contractMapper.updateById(BeanUtils.toBean(reqVO, HrmEmployeeContractDO.class)
                .setEmployeeId(null)
                .setStartTime(getDayBeginTime(reqVO.getStartTime()))
                .setEndTime(getDayBeginTime(reqVO.getEndTime()))
                .setSignTime(getDayBeginTime(reqVO.getSignTime())));
        if (HrmEmployeeContractTypeEnum.NON_FIXED_TERM_LABOR_CONTRACT.getType().equals(reqVO.getType())) {
            contractMapper.updateTermById(reqVO.getId(), null);
        }

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable(DiffParseFunction.OLD_OBJECT,
                BeanUtils.toBean(contract, HrmEmployeeContractSaveReqVO.class));
    }

    @Override
    @LogRecord(type = HRM_EMPLOYEE_TYPE, subType = HRM_EMPLOYEE_CONTRACT_DELETE_SUB_TYPE,
            bizNo = "{{#contract.employeeId}}", success = HRM_EMPLOYEE_CONTRACT_DELETE_SUCCESS)
    public void deleteContract(Long id) {
        // 1. 校验员工合同存在
        HrmEmployeeContractDO contract = validateContractExists(id);

        // 2. 删除员工合同
        contractMapper.deleteById(id);

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("contract", contract);
    }

    @Override
    public HrmEmployeeContractDO validateContractExists(Long id) {
        HrmEmployeeContractDO contract = contractMapper.selectById(id);
        if (contract == null) {
            throw exception(EMPLOYEE_CONTRACT_NOT_EXISTS);
        }
        return contract;
    }

    @Override
    public List<HrmEmployeeContractDO> getContractListByEmployeeId(Long employeeId) {
        return contractMapper.selectListByEmployeeId(employeeId);
    }

    @Override
    public Long getExpireEmployeeCountInMonth(LocalDateTime[] endTimes) {
        return contractMapper.selectCountByEndTimeBetween(endTimes);
    }

    @Override
    public Long createDraftForOffer(HrmRecruitOfferDO offer) {
        // 1. 幂等：同一 Offer 只生成一份草稿
        HrmEmployeeContractDO exists = contractMapper.selectBySourceOfferId(offer.getId());
        if (exists != null) {
            return exists.getId();
        }

        // 2. 选择默认启用模板，合同类型跟随模板；未配置模板时不阻断 Offer 发放
        HrmContractTemplateDO template = contractTemplateService.getDefaultEnabledTemplate();
        if (template == null) {
            log.warn("[createDraftForOffer][Offer({}) 未配置启用的合同模板，跳过合同草稿生成]", offer.getId());
            return null;
        }

        // 3. 生成合同草稿：员工尚未建档，employeeId 为空，入职后回写
        HrmCompanyProfileDO company = companyProfileService.getDefaultProfile();
        LocalDateTime startTime = offer.getPlannedOnboardDate() != null
                ? offer.getPlannedOnboardDate().atStartOfDay() : null;
        HrmEmployeeContractDO contract = new HrmEmployeeContractDO()
                .setNo("HT-" + offer.getOfferNo())
                .setType(template.getContractType())
                .setTemplateId(template.getId())
                .setSourceOfferId(offer.getId())
                .setStatus(HrmEmployeeContractStatusEnum.NOT_PERFORMED.getStatus())
                .setSignCompany(company != null ? company.getName() : null)
                .setStartTime(startTime)
                // 预置合同期限与结束日期：否则合同文件缺结束日期，且签署完成后无法进入到期预警
                .setTerm(defaultTermOf(template.getContractType()))
                .setEndTime(defaultEndTimeOf(template.getContractType(), startTime))
                .setPayAmount(offer.getSalaryAmount())
                .setExpireRemind(true)
                .setSort(0)
                .setRemark("由 Offer " + offer.getOfferNo() + " 自动生成");
        contractMapper.insert(contract);
        return contract.getId();
    }

    @Override
    public Long createDraftForEmployee(
            cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO employee) {
        // 1. 入参保护 + 幂等：该员工已有任意合同（含草稿）时不重复生成
        if (employee == null || employee.getId() == null) {
            return null;
        }
        if (!contractMapper.selectListByEmployeeId(employee.getId()).isEmpty()) {
            return null;
        }
        // 2. 未配置启用模板时不阻断建档（与 Offer 路径保持一致）
        HrmContractTemplateDO template = contractTemplateService.getDefaultEnabledTemplate();
        if (template == null) {
            log.warn("[createDraftForEmployee][员工({}) 未配置启用的合同模板，跳过合同草稿生成]", employee.getId());
            return null;
        }
        // 3. 生成合同草稿：起止日期按「入职日期 + 默认期限」推算，HR 可再调整
        HrmCompanyProfileDO company = companyProfileService.getDefaultProfile();
        LocalDateTime startTime = employee.getEntryTime() != null
                ? getDayBeginTime(employee.getEntryTime())
                : getDayBeginTime(LocalDateTime.now());
        HrmEmployeeContractDO contract = new HrmEmployeeContractDO()
                .setNo("HT-" + cn.hutool.core.util.StrUtil.blankToDefault(
                        employee.getJobNumber(), String.valueOf(employee.getId())))
                .setEmployeeId(employee.getId())
                .setType(template.getContractType())
                .setTemplateId(template.getId())
                .setStatus(HrmEmployeeContractStatusEnum.NOT_PERFORMED.getStatus())
                .setSignCompany(company != null ? company.getName() : null)
                .setStartTime(startTime)
                .setTerm(defaultTermOf(template.getContractType()))
                .setEndTime(defaultEndTimeOf(template.getContractType(), startTime))
                .setExpireRemind(true)
                .setSort(0)
                .setRemark("员工建档自动生成");
        contractMapper.insert(contract);
        return contract.getId();
    }

    @Override
    public void bindEmployeeByOfferId(Long sourceOfferId, Long employeeId, LocalDateTime entryTime) {
        HrmEmployeeContractDO contract = contractMapper.selectBySourceOfferId(sourceOfferId);
        if (contract == null) {
            return;
        }
        HrmEmployeeContractDO update = new HrmEmployeeContractDO()
                .setId(contract.getId()).setEmployeeId(employeeId);
        if (contract.getStartTime() == null && entryTime != null) {
            update.setStartTime(getDayBeginTime(entryTime));
        }
        contractMapper.updateById(update);
        // 记录操作日志上下文（合同绑定员工）
        LogRecordContext.putVariable("contract", contract);
    }

    @Override
    public HrmEmployeeContractDO getContractBySourceOfferId(Long sourceOfferId) {
        if (sourceOfferId == null) {
            return null;
        }
        return contractMapper.selectBySourceOfferId(sourceOfferId);
    }

    @Override
    public void markGeneratedFile(Long id, String fileUrl, String remarkAppend) {
        HrmEmployeeContractDO contract = validateContractExists(id);
        HrmEmployeeContractDO update = new HrmEmployeeContractDO().setId(id)
                .setFileUrls(java.util.Collections.singletonList(fileUrl));
        if (StrUtil.isNotBlank(remarkAppend)) {
            String remark = StrUtil.blankToDefault(contract.getRemark(), "");
            update.setRemark(StrUtil.sub(remark + "；" + remarkAppend, 0, 500));
        }
        contractMapper.updateById(update);
    }

    @Override
    public void applyTemplateByEmploymentType(Long sourceOfferId, Long contractTemplateId) {
        HrmEmployeeContractDO contract = getContractBySourceOfferId(sourceOfferId);
        if (contract == null || contractTemplateId == null) {
            return;
        }
        // 模板不存在时直接报错，避免转员工后拿到一份渲染不出内容的合同
        HrmContractTemplateDO template = contractTemplateService.validateTemplateExists(contractTemplateId);
        contractMapper.updateById(new HrmEmployeeContractDO().setId(contract.getId())
                .setTemplateId(template.getId())
                .setType(template.getContractType()));
        // 合同类型随用工形式的模板变化，期限与结束日期要一起跟着重算（无固定期限需清空）
        contractMapper.updateTermAndEndTimeById(contract.getId(),
                defaultTermOf(template.getContractType()),
                defaultEndTimeOf(template.getContractType(), contract.getStartTime()));
    }

    /**
     * 计算默认合同期限（年）：无固定期限、以完成一定工作任务为期限的合同不设期限
     *
     * @param contractType 合同类型
     * @return 期限（年）；不设期限时返回 null
     */
    private static Integer defaultTermOf(Integer contractType) {
        if (contractType == null
                || HrmEmployeeContractTypeEnum.NON_FIXED_TERM_LABOR_CONTRACT.getType().equals(contractType)
                || HrmEmployeeContractTypeEnum.WORK_TASK_LABOR_CONTRACT.getType().equals(contractType)) {
            return null;
        }
        return DEFAULT_TERM_YEARS;
    }

    /**
     * 按合同类型与开始日期计算默认合同结束日期：开始日期或期限任一为空时返回 null
     *
     * @param contractType 合同类型
     * @param startTime    合同开始日期
     * @return 合同结束日期；无法推算时返回 null
     */
    private static LocalDateTime defaultEndTimeOf(Integer contractType, LocalDateTime startTime) {
        Integer term = defaultTermOf(contractType);
        return term == null || startTime == null ? null : startTime.plusYears(term);
    }

}
