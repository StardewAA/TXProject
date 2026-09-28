package cn.iocoder.txgy.module.hrm.service.contract;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.contract.vo.HrmContractSignRecordPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.contract.vo.HrmContractSignRecordSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.company.HrmCompanyProfileDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmContractSignRecordDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeContractDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.contract.HrmContractSignRecordMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.employment.HrmEmployeeContractMapper;
import cn.iocoder.txgy.module.hrm.enums.MessageTemplateConstants;
import cn.iocoder.txgy.module.hrm.enums.contract.HrmContractSignStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.contract.HrmContractSignWayEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeContractStatusEnum;
import cn.iocoder.txgy.module.hrm.service.company.HrmCompanyProfileService;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.txgy.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CONTRACT_SIGN_EMPLOYEE_REQUIRED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CONTRACT_SIGN_FILE_REQUIRED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CONTRACT_SIGN_RECORD_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CONTRACT_SIGN_RECORD_PENDING_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CONTRACT_SIGN_RECORD_SIGNED_CANNOT_DELETE;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.EMPLOYEE_CONTRACT_NOT_EXISTS;

/**
 * HRM 合同签署记录 Service 实现类
 *
 * <p>行业场景：防腐保温项目现场以纸质合同签署为主，签署件需要归档留痕、并支持对未签人员催办。</p>
 *
 * @author OPENLAB BS
 */
@Service
@Validated
@Slf4j
public class HrmContractSignRecordServiceImpl implements HrmContractSignRecordService {

    @Resource
    private HrmContractSignRecordMapper signRecordMapper;
    @Resource
    private HrmEmployeeContractMapper contractMapper;
    @Resource
    private HrmEmployeeService employeeService;
    @Resource
    private HrmCompanyProfileService companyProfileService;
    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;
    /** 合同文件台账（B2：签署件归档 + 版本溯源） */
    @Resource
    private cn.iocoder.txgy.module.hrm.dal.mysql.contract.HrmEmployeeContractFileMapper contractFileMapper;

    @Override
    public Long createSignRecord(HrmContractSignRecordSaveReqVO reqVO) {
        // 1. 校验合同，并补齐员工与合同编号
        HrmEmployeeContractDO contract = validateContract(reqVO.getContractId());
        if (contract.getEmployeeId() == null) {
            throw exception(CONTRACT_SIGN_EMPLOYEE_REQUIRED);
        }
        // 2. 同一合同只允许存在一条待签记录，避免重复催办
        if (signRecordMapper.selectByContractIdAndStatus(contract.getId(),
                HrmContractSignStatusEnum.PENDING.getStatus()) != null) {
            throw exception(CONTRACT_SIGN_RECORD_PENDING_EXISTS);
        }
        Integer status = reqVO.getStatus() == null ? HrmContractSignStatusEnum.PENDING.getStatus() : reqVO.getStatus();
        // 3. 已签必须上传签署件
        validateSignedFile(status, reqVO.getFileUrls());

        HrmContractSignRecordDO record = BeanUtils.toBean(reqVO, HrmContractSignRecordDO.class)
                .setEmployeeId(contract.getEmployeeId())
                .setContractNo(contract.getNo())
                .setStatus(status)
                .setUrgeCount(0)
                .setSignEmployerName(resolveEmployerName(reqVO.getSignEmployerId(),
                        contract.getSignCompany()));
        signRecordMapper.insert(record);
        // 签署闭环：登记即「已签」时，同步把合同置为执行中并归档签署件
        syncContractSignStatus(contract.getId(), record);
        return record.getId();
    }

    @Override
    public void updateSignRecord(HrmContractSignRecordSaveReqVO reqVO) {
        HrmContractSignRecordDO exists = validateSignRecordExists(reqVO.getId());
        HrmEmployeeContractDO contract = validateContract(
                reqVO.getContractId() == null ? exists.getContractId() : reqVO.getContractId());
        Integer status = reqVO.getStatus() == null ? exists.getStatus() : reqVO.getStatus();
        validateSignedFile(status, reqVO.getFileUrls() == null ? exists.getFileUrls() : reqVO.getFileUrls());

        HrmContractSignRecordDO updateObj = BeanUtils.toBean(reqVO, HrmContractSignRecordDO.class)
                .setId(exists.getId())
                .setEmployeeId(contract.getEmployeeId())
                .setContractNo(contract.getNo())
                .setStatus(status);
        // 用工主体名称：按入参反查，未传则沿用原值
        if (reqVO.getSignEmployerId() != null) {
            updateObj.setSignEmployerName(resolveEmployerName(reqVO.getSignEmployerId(), contract.getSignCompany()));
        }
        signRecordMapper.updateById(updateObj);
        // 签署闭环：状态变化（已签 / 作废）时同步合同状态与归档件
        syncContractSignStatus(contract.getId(), signRecordMapper.selectById(exists.getId()));
    }

    @Override
    public Long createPendingSignRecordIfAbsent(Long contractId) {
        // 1. 校验合同：合同必须已绑定员工，否则无法确定乙方签署人与台账归属
        HrmEmployeeContractDO contract = validateContract(contractId);
        if (contract.getEmployeeId() == null) {
            throw exception(CONTRACT_SIGN_EMPLOYEE_REQUIRED);
        }
        // 2. 幂等：同一合同已有待签记录时直接复用，避免转员工/重发通知重复登记
        HrmContractSignRecordDO exists = signRecordMapper.selectByContractIdAndStatus(contract.getId(),
                HrmContractSignStatusEnum.PENDING.getStatus());
        if (exists != null) {
            return exists.getId();
        }
        // 3. 登记待签记录：现场以纸质合同为主，默认签署方式为「线下签署」
        HrmEmployeeDO employee = employeeService.validateEmployeeExists(contract.getEmployeeId());
        HrmContractSignRecordDO record = new HrmContractSignRecordDO()
                .setContractId(contract.getId())
                .setEmployeeId(contract.getEmployeeId())
                .setContractNo(contract.getNo())
                .setSignEmployerId(resolveEmployerId(contract.getSignCompany()))
                .setSignEmployerName(contract.getSignCompany())
                .setSignPerson(employee == null ? null : employee.getName())
                .setSignWay(HrmContractSignWayEnum.OFFLINE.getWay())
                .setStatus(HrmContractSignStatusEnum.PENDING.getStatus())
                .setUrgeCount(0)
                .setRemark("发送劳动合同时自动登记，待签署");
        signRecordMapper.insert(record);
        return record.getId();
    }

    @Override
    public void deleteSignRecord(Long id) {
        HrmContractSignRecordDO record = validateSignRecordExists(id);
        if (HrmContractSignStatusEnum.SIGNED.getStatus().equals(record.getStatus())) {
            throw exception(CONTRACT_SIGN_RECORD_SIGNED_CANNOT_DELETE);
        }
        signRecordMapper.deleteById(id);
    }

    @Override
    public HrmContractSignRecordDO validateSignRecordExists(Long id) {
        HrmContractSignRecordDO record = signRecordMapper.selectById(id);
        if (record == null) {
            throw exception(CONTRACT_SIGN_RECORD_NOT_EXISTS);
        }
        return record;
    }

    @Override
    public PageResult<HrmContractSignRecordDO> getSignRecordPage(HrmContractSignRecordPageReqVO reqVO) {
        return signRecordMapper.selectPage(reqVO);
    }

    @Override
    public List<HrmContractSignRecordDO> getSignRecordListByContractId(Long contractId) {
        return signRecordMapper.selectListByContractId(contractId);
    }

    @Override
    public List<HrmContractSignRecordDO> getSignRecordListByContractIds(Collection<Long> contractIds) {
        return signRecordMapper.selectListByContractIds(contractIds);
    }

    /**
     * 合同签署闭环：签署记录状态变化时，同步员工合同的状态、签署时间与归档附件
     *
     * <p>行业场景：现场合同以纸质签署为主，HR 在「签署记录」里登记结果并上传签署件后，
     * 员工合同要自动从「未执行」变为「执行中」（即已签生效），签署件同时归档到合同附件，
     * 避免出现"合同签了但系统里还是未执行"的两套账。</p>
     */
    private void syncContractSignStatus(Long contractId, HrmContractSignRecordDO record) {
        if (record != null && HrmContractSignStatusEnum.SIGNED.getStatus().equals(record.getStatus())) {
            markContractSigned(contractId, record);
        } else {
            revertContractSigned(contractId);
        }
    }

    /**
     * 合同置为「执行中」：写入签署时间，并把签署件合并归档到合同附件
     */
    private void markContractSigned(Long contractId, HrmContractSignRecordDO record) {
        HrmEmployeeContractDO contract = contractMapper.selectById(contractId);
        if (contract == null) {
            return;
        }
        HrmEmployeeContractDO update = new HrmEmployeeContractDO().setId(contractId)
                .setStatus(HrmEmployeeContractStatusEnum.IN_PROGRESS.getStatus())
                .setSignTime(record.getSignTime() == null ? LocalDateTime.now() : record.getSignTime())
                // 签署完成即开启到期提醒：否则手工新增/未勾选「到期提醒」的合同
                // 即使签署完成也不会进入「合同到期预警」（预警清单只查 expire_remind = true）
                .setExpireRemind(true);
        // 归档签署件：与合同已有附件合并去重（原合同文件保留）
        List<String> fileUrls = new java.util.ArrayList<>();
        if (CollUtil.isNotEmpty(contract.getFileUrls())) {
            fileUrls.addAll(contract.getFileUrls());
        }
        if (CollUtil.isNotEmpty(record.getFileUrls())) {
            record.getFileUrls().stream()
                    .filter(StrUtil::isNotBlank)
                    .filter(url -> !fileUrls.contains(url))
                    .forEach(fileUrls::add);
        }
        if (CollUtil.isNotEmpty(fileUrls)) {
            update.setFileUrls(fileUrls);
        }
        contractMapper.updateById(update);
        // B2：签署件写入合同文件台账（type=2），文件名按「员工姓名-合同类型(签署件)」，并记录签署记录号用于溯源
        archiveSignedFilesToLedger(contract, record);
    }

    /**
     * 把签署件归档到合同文件台账：区分「生成稿 / 签署件」，同名多份自动递增版本
     */
    private void archiveSignedFilesToLedger(HrmEmployeeContractDO contract, HrmContractSignRecordDO record) {
        if (contract == null || record == null || CollUtil.isEmpty(record.getFileUrls())) {
            return;
        }
        HrmEmployeeDO employee = contract.getEmployeeId() == null ? null
                : employeeService.getEmployee(contract.getEmployeeId());
        String employeeName = employee == null || StrUtil.isBlank(employee.getName())
                ? "员工" : employee.getName();
        String typeName = java.util.Arrays.stream(
                        cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeContractTypeEnum.values())
                .filter(item -> item.getType().equals(contract.getType()))
                .map(cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeContractTypeEnum::getName)
                .findFirst().orElse("劳动合同");
        int version = contractFileMapper.selectListByContractId(contract.getId()).stream()
                .filter(item -> cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmEmployeeContractFileDO
                        .FILE_TYPE_SIGNED.equals(item.getFileType()))
                .map(cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmEmployeeContractFileDO::getVersion)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
        for (String url : record.getFileUrls()) {
            if (StrUtil.isBlank(url)) {
                continue;
            }
            String ext = StrUtil.subAfter(url, ".", true);
            ext = StrUtil.isBlank(ext) || ext.length() > 5 ? "pdf" : ext;
            String fileName = employeeName + "-" + typeName + "(签署件)." + ext;
            contractFileMapper.insert(new cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmEmployeeContractFileDO()
                    .setContractId(contract.getId())
                    .setEmployeeId(contract.getEmployeeId())
                    .setFileType(cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmEmployeeContractFileDO
                            .FILE_TYPE_SIGNED)
                    .setVersion(version)
                    .setFileName(fileName)
                    .setFileUrl(url)
                    .setTemplateId(contract.getTemplateId())
                    .setSignRecordId(record.getId()));
        }
    }

    /**
     * 合同回滚为「未执行」：仅当该合同已没有其它「已签」记录时回滚（作废场景）
     */
    private void revertContractSigned(Long contractId) {
        if (signRecordMapper.selectByContractIdAndStatus(contractId,
                HrmContractSignStatusEnum.SIGNED.getStatus()) != null) {
            return;
        }
        contractMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<HrmEmployeeContractDO>()
                .eq(HrmEmployeeContractDO::getId, contractId)
                .set(HrmEmployeeContractDO::getStatus, HrmEmployeeContractStatusEnum.NOT_PERFORMED.getStatus())
                .set(HrmEmployeeContractDO::getSignTime, null));
    }

    @Override
    public int urgeSignRecords(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return 0;
        }
        int count = 0;
        for (Long id : ids) {
            HrmContractSignRecordDO record = signRecordMapper.selectById(id);
            if (record == null || !HrmContractSignStatusEnum.PENDING.getStatus().equals(record.getStatus())) {
                continue;
            }
            // 1. 更新催办次数与时间
            int urgeCount = record.getUrgeCount() == null ? 1 : record.getUrgeCount() + 1;
            signRecordMapper.updateById(new HrmContractSignRecordDO().setId(id)
                    .setUrgeCount(urgeCount).setLastUrgeTime(LocalDateTime.now()));
            // 2. 给员工绑定的后台账号发待签署站内信
            sendSignNotice(record);
            count++;
        }
        return count;
    }

    /**
     * 给员工发送待签署站内信；发送失败只记日志，不影响催办计数
     */
    private void sendSignNotice(HrmContractSignRecordDO record) {
        try {
            HrmEmployeeDO employee = employeeService.getEmployee(record.getEmployeeId());
            if (employee == null || employee.getUserId() == null) {
                return;
            }
            Map<String, Object> templateParams = new HashMap<>();
            templateParams.put("contractNo",
                    StrUtil.blankToDefault(record.getContractNo(), String.valueOf(record.getContractId())));
            notifyMessageSendApi.sendSingleMessageToAdmin(new NotifySendSingleToUserReqDTO()
                    .setUserId(employee.getUserId())
                    .setTemplateCode(MessageTemplateConstants.CONTRACT_PENDING_SIGN)
                    .setTemplateParams(templateParams));
        } catch (Throwable ex) {
            log.warn("[sendSignNotice][signRecordId({}) 发送待签署通知失败]", record.getId(), ex);
        }
    }

    private HrmEmployeeContractDO validateContract(Long contractId) {
        HrmEmployeeContractDO contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw exception(EMPLOYEE_CONTRACT_NOT_EXISTS);
        }
        return contract;
    }

    private void validateSignedFile(Integer status, List<String> fileUrls) {
        if (HrmContractSignStatusEnum.SIGNED.getStatus().equals(status) && CollUtil.isEmpty(fileUrls)) {
            throw exception(CONTRACT_SIGN_FILE_REQUIRED);
        }
    }

    /**
     * 解析签署用工主体名称：优先取入参主体，其次取合同上的签约主体名称
     */
    private String resolveEmployerName(Long signEmployerId, String fallback) {
        if (signEmployerId == null) {
            return fallback;
        }
        HrmCompanyProfileDO profile = companyProfileService.getProfileList().stream()
                .filter(item -> signEmployerId.equals(item.getId()))
                .findFirst().orElse(null);
        return profile == null ? fallback : profile.getName();
    }

    /**
     * 按签约主体名称反查用工主体编号；名称未维护或匹配不到时返回 null（只留名称，不阻断登记）
     */
    private Long resolveEmployerId(String signCompanyName) {
        if (StrUtil.isBlank(signCompanyName)) {
            return null;
        }
        HrmCompanyProfileDO profile = companyProfileService.getProfileList().stream()
                .filter(item -> signCompanyName.equals(item.getName()))
                .findFirst().orElse(null);
        return profile == null ? null : profile.getId();
    }

}
