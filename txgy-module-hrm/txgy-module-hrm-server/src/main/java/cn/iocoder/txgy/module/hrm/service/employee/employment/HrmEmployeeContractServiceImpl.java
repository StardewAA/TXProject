package cn.iocoder.txgy.module.hrm.service.employee.employment;

import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.contract.HrmEmployeeContractSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeContractDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.company.HrmCompanyProfileDO;
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

    @Resource
    private HrmEmployeeContractMapper contractMapper;
    @Resource
    private HrmEmployeeService employeeService;
    @Resource
    private HrmContractTemplateService contractTemplateService;
    @Resource
    private HrmCompanyProfileService companyProfileService;

    @Override
    public List<HrmEmployeeContractDO> getExpireWarningList(int warnDays) {
        int days = warnDays <= 0 ? 30 : warnDays;
        return contractMapper.selectExpireWarningList(days);
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
        HrmEmployeeContractDO contract = new HrmEmployeeContractDO()
                .setNo("HT-" + offer.getOfferNo())
                .setType(template.getContractType())
                .setTemplateId(template.getId())
                .setSourceOfferId(offer.getId())
                .setStatus(HrmEmployeeContractStatusEnum.NOT_PERFORMED.getStatus())
                .setSignCompany(company != null ? company.getName() : null)
                .setStartTime(offer.getPlannedOnboardDate() != null
                        ? offer.getPlannedOnboardDate().atStartOfDay() : null)
                .setPayAmount(offer.getSalaryAmount())
                .setExpireRemind(true)
                .setSort(0)
                .setRemark("由 Offer " + offer.getOfferNo() + " 自动生成");
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
    }

}
