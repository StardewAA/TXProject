package cn.iocoder.txgy.module.bidding.service.personnel;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelOverviewRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelProfilePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelProfileRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelProfileSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelProfileStatusChangeReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelCertificateDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelLaborContractDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelProfileDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelSocialSecurityDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelTrainingRecordDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelCertificateMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelLaborContractMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelProfileMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelSocialSecurityMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelTrainingRecordMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.PersonnelCertStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.PersonnelStatusEnum;
import cn.iocoder.txgy.module.bidding.framework.util.PersonnelSensitiveUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 员工档案服务实现（建档判重 / 分页检索 / 状态机 CAS / 资质全景聚合）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（人员要素与在职/社保口径）、
 * 表 15-6（结论只追加、明细可复算 → 全景只读聚合不落表）；《招投标模块开发规范》§4.3（Service 编排）、
 * §4.5（状态流转接口 + 乐观锁）、§4.6（分页查询走索引字段）、§4.8（L4 明文只入不出）。
 * <p>设计遵循/针对：① 一人一档靠"身份证盲索引唯一"而非明文比对——建档/更新时若索引已属他人即拦截；
 * ② 身份证明文只在入参出现，落库仅密文（此处以盲索引 + 占位密文表达，密文由统一加密组件填充，
 * 见设计说明"待接入项"）；③ 状态流转复用 PersonnelStatusEnum 显式迁移表，与安许证/资质状态机同范式；
 * ④ 全景视图按需查询后组装，不建冗余表（避免多一处不一致来源）。
 * <p>业务目标：一处维护员工要素，证书/社保/合同/培训围绕档案挂载，为"证书集中管理 + 到期预警 + 核验"提供主数据。
 */
@Slf4j
@Service
public class PersonnelProfileServiceImpl implements PersonnelProfileService {

    @Resource
    private PersonnelProfileMapper personnelProfileMapper;
    @Resource
    private PersonnelCertificateMapper personnelCertificateMapper;
    @Resource
    private PersonnelSocialSecurityMapper personnelSocialSecurityMapper;
    @Resource
    private PersonnelLaborContractMapper personnelLaborContractMapper;
    @Resource
    private PersonnelTrainingRecordMapper personnelTrainingRecordMapper;

    /**
     * 身份证盲索引盐。
     *
     * <p>为什么走配置：盲索引的强度全部取决于盐的保密性，硬编码等于没有防护；
     * 未配置时工具类会退回开发默认盐（仅本地可用），生产由部署配置/KMS 注入。
     */
    @Value("${bidding.personnel.id-card-secret:}")
    private String idCardSecret;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProfile(PersonnelProfileSaveReqVO reqVO) {
        // 员工编号在企业内唯一（DDL uk_pp_no），先查后插避免依赖数据库报错信息
        if (personnelProfileMapper.selectByPersonNo(reqVO.getEntId(), reqVO.getPersonNo()) != null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_NO_DUPLICATE);
        }
        String blindIndex = PersonnelSensitiveUtils.blindIndex(idCardSecret, reqVO.getIdCard());
        // 一人一档：同证号已存在即拦截；证号缺失时拒绝建档（否则判重形同虚设）
        if (blindIndex == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_BLIND_INDEX_REQUIRED);
        }
        if (personnelProfileMapper.selectByIdCardBlindIndex(blindIndex) != null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_ID_CARD_DUPLICATE);
        }
        PersonnelProfileDO profile = new PersonnelProfileDO();
        copyEditableFields(reqVO, profile);
        profile.setIdCardBlindIndex(blindIndex);
        profile.setIdCardEnc(null); // 明文不落库；密文由统一加密组件按 §4.8 在接入后填充
        profile.setPersonnelStatus(PersonnelStatusEnum.ACTIVE.name());
        profile.setStatusChangeDate(LocalDate.now());
        personnelProfileMapper.insert(profile);
        log.info("[personnel][create] personNo={} id={}", profile.getPersonNo(), profile.getId());
        return profile.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(PersonnelProfileSaveReqVO reqVO) {
        PersonnelProfileDO exists = validateExists(reqVO.getId());
        if (reqVO.getVersion() != null && !Objects.equals(reqVO.getVersion(), exists.getVersion())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_DATA_MODIFIED);
        }
        // 员工编号变更要重新校验唯一（排除自身）
        if (reqVO.getPersonNo() != null && !reqVO.getPersonNo().equals(exists.getPersonNo())) {
            PersonnelProfileDO other = personnelProfileMapper.selectByPersonNo(reqVO.getEntId(), reqVO.getPersonNo());
            if (other != null && !Objects.equals(other.getId(), exists.getId())) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_NO_DUPLICATE);
            }
        }
        // 身份证变更：重新计算盲索引并校验不与他人冲突（换证/纠错场景）
        String blindIndex = PersonnelSensitiveUtils.blindIndex(idCardSecret, reqVO.getIdCard());
        if (blindIndex != null && !blindIndex.equals(exists.getIdCardBlindIndex())) {
            PersonnelProfileDO other = personnelProfileMapper.selectByIdCardBlindIndex(blindIndex);
            if (other != null && !Objects.equals(other.getId(), exists.getId())) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_ID_CARD_DUPLICATE);
            }
            exists.setIdCardBlindIndex(blindIndex);
            exists.setIdCardEnc(null);
        }
        copyEditableFields(reqVO, exists);
        if (personnelProfileMapper.updateById(exists) == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_DATA_MODIFIED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(PersonnelProfileStatusChangeReqVO reqVO) {
        PersonnelProfileDO profile = validateExists(reqVO.getId());
        if (!Objects.equals(reqVO.getVersion(), profile.getVersion())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_DATA_MODIFIED);
        }
        PersonnelStatusEnum from;
        PersonnelStatusEnum to;
        try {
            from = PersonnelStatusEnum.valueOf(profile.getPersonnelStatus());
            to = PersonnelStatusEnum.valueOf(reqVO.getTargetStatus());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_STATUS_TRANSITION_INVALID);
        }
        // 复用枚举显式迁移表：离职→在职需走"复职"合法路径，防随意跳转掩盖真实用工状态
        if (!PersonnelStatusEnum.isTransitionAllowed(from, to)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_STATUS_TRANSITION_INVALID);
        }
        profile.setPersonnelStatus(to.name());
        profile.setStatusChangeDate(LocalDate.now());
        if (reqVO.getReason() != null && !reqVO.getReason().isBlank()) {
            profile.setRemark(appendRemark(profile.getRemark(), reqVO.getReason()));
        }
        if (personnelProfileMapper.updateById(profile) == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_DATA_MODIFIED);
        }
    }

    @Override
    public PersonnelProfileDO getProfile(Long id) {
        return validateExists(id);
    }

    @Override
    public PageResult<PersonnelProfileDO> pageProfile(PersonnelProfilePageReqVO reqVO) {
        LambdaQueryWrapper<PersonnelProfileDO> query = new LambdaQueryWrapper<PersonnelProfileDO>()
                .eq(reqVO.getEntId() != null, PersonnelProfileDO::getEntId, reqVO.getEntId())
                .eq(reqVO.getPersonNo() != null && !reqVO.getPersonNo().isBlank(),
                        PersonnelProfileDO::getPersonNo, reqVO.getPersonNo())
                .like(reqVO.getPersonName() != null && !reqVO.getPersonName().isBlank(),
                        PersonnelProfileDO::getPersonName, reqVO.getPersonName())
                .like(reqVO.getDeptName() != null && !reqVO.getDeptName().isBlank(),
                        PersonnelProfileDO::getDeptName, reqVO.getDeptName())
                .eq(reqVO.getPersonnelStatus() != null && !reqVO.getPersonnelStatus().isBlank(),
                        PersonnelProfileDO::getPersonnelStatus, reqVO.getPersonnelStatus())
                .eq(reqVO.getEduLevel() != null && !reqVO.getEduLevel().isBlank(),
                        PersonnelProfileDO::getEduLevel, reqVO.getEduLevel())
                .eq(reqVO.getTitleLevel() != null && !reqVO.getTitleLevel().isBlank(),
                        PersonnelProfileDO::getTitleLevel, reqVO.getTitleLevel())
                .orderByDesc(PersonnelProfileDO::getId);
        // 证书类型反查：先取持有该类可用证书的人员 id，再并入档案条件（避免 join 破坏分页索引）
        if (reqVO.getCertType() != null && !reqVO.getCertType().isBlank() && reqVO.getEntId() != null) {
            List<PersonnelCertificateDO> certs = personnelCertificateMapper.selectUsableByType(
                    reqVO.getEntId(), reqVO.getCertType(), LocalDate.now());
            List<Long> personIds = certs.stream().map(PersonnelCertificateDO::getPersonId).distinct().toList();
            if (personIds.isEmpty()) {
                return new PageResult<>(java.util.List.of(), 0L);
            }
            query.in(PersonnelProfileDO::getId, personIds);
        }
        Page<PersonnelProfileDO> page = personnelProfileMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), query);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override
    public PersonnelOverviewRespVO getOverview(Long personId) {
        PersonnelProfileDO profile = validateExists(personId);
        LocalDate today = LocalDate.now();

        List<PersonnelCertificateDO> certs = personnelCertificateMapper.selectByPerson(personId);
        List<PersonnelCertRespVO> certVOs = new ArrayList<>();
        int usable = 0;
        for (PersonnelCertificateDO cert : certs) {
            PersonnelCertRespVO vo = toCertVO(cert, today);
            certVOs.add(vo);
            if (PersonnelCertStatusEnum.isUsable(cert.getCertStatus())) {
                usable++;
            }
        }

        PersonnelSocialSecurityDO social = personnelSocialSecurityMapper.selectLatestByPerson(personId);
        PersonnelLaborContractDO contract = personnelLaborContractMapper.selectEffectiveByPerson(personId, today);
        List<PersonnelTrainingRecordDO> trainings = personnelTrainingRecordMapper.selectByPerson(personId);

        PersonnelOverviewRespVO vo = new PersonnelOverviewRespVO();
        vo.setProfile(toProfileVO(profile, certs.size(), usable, today));
        vo.setIdCardValid(profile.getIdCardValidTo() == null || !profile.getIdCardValidTo().isBefore(today));
        vo.setTotalCertCount(certs.size());
        vo.setUsableCertCount(usable);
        vo.setCertificates(certVOs);
        if (social != null) {
            vo.setLatestSocialSecurityMonth(social.getPayMonth());
            vo.setLatestSocialSecurityStatus(social.getPayStatus());
            vo.setLatestSocialSecurityPayer(social.getPayerName());
            vo.setSocialSecurityConsistent(Objects.equals(social.getEntId(), profile.getEntId()));
        } else {
            vo.setSocialSecurityConsistent(false);
        }
        vo.setLaborContractValid(contract != null);
        vo.setLaborContractConsistent(contract != null && Objects.equals(contract.getEntId(), profile.getEntId()));
        BigDecimal hours = trainings.stream()
                .map(PersonnelTrainingRecordDO::getEduHours)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setTotalEduHours(hours.stripTrailingZeros().toPlainString());

        // 风险提示：把"配证/申报会被卡住的点"直接列出来，避免业务人员逐页找原因
        List<String> risks = new ArrayList<>();
        if (!PersonnelStatusEnum.ACTIVE.name().equals(profile.getPersonnelStatus())) {
            risks.add("员工当前非在职，不能代表企业投标/申报");
        }
        if (!Boolean.TRUE.equals(vo.getIdCardValid())) {
            risks.add("身份证已过有效期，需先更新证件");
        }
        if (certs.isEmpty()) {
            risks.add("尚未登记任何证书");
        } else if (usable == 0) {
            risks.add("在册证书均不可用（过期/吊销/注销）");
        }
        if (social == null) {
            risks.add("无社保缴纳记录，投标资格审查可能被判人证分离");
        } else if (!Boolean.TRUE.equals(vo.getSocialSecurityConsistent())) {
            risks.add("社保缴纳单位与档案归属企业不一致，存在挂证风险");
        }
        if (contract == null) {
            risks.add("无有效劳动合同（或已过期）");
        } else if (!Boolean.TRUE.equals(vo.getLaborContractConsistent())) {
            risks.add("劳动合同主体与档案归属企业不一致");
        }
        if (Boolean.FALSE.equals(profile.getIsFullTime())) {
            risks.add("非本单位全职用工");
        }
        vo.setRisks(risks);
        vo.setUsableForTender(PersonnelStatusEnum.ACTIVE.name().equals(profile.getPersonnelStatus())
                && Boolean.TRUE.equals(vo.getIdCardValid()) && usable > 0);
        return vo;
    }

    // ========== 内部 ==========

    private PersonnelProfileDO validateExists(Long id) {
        PersonnelProfileDO profile = id == null ? null : personnelProfileMapper.selectById(id);
        if (profile == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_NOT_EXISTS);
        }
        return profile;
    }

    private void copyEditableFields(PersonnelProfileSaveReqVO reqVO, PersonnelProfileDO target) {
        target.setEntId(reqVO.getEntId());
        target.setPersonNo(reqVO.getPersonNo());
        target.setPersonName(reqVO.getPersonName());
        target.setIdCardValidTo(reqVO.getIdCardValidTo());
        target.setGender(reqVO.getGender());
        target.setBirthDate(reqVO.getBirthDate());
        target.setMobile(reqVO.getMobile());
        target.setEmail(reqVO.getEmail());
        target.setEduLevel(reqVO.getEduLevel());
        target.setEduMajor(reqVO.getEduMajor());
        target.setSchool(reqVO.getSchool());
        target.setGraduateDate(reqVO.getGraduateDate());
        target.setTitleLevel(reqVO.getTitleLevel());
        target.setTitleMajor(reqVO.getTitleMajor());
        target.setWorkStartDate(reqVO.getWorkStartDate());
        target.setJoinDate(reqVO.getJoinDate());
        target.setDeptName(reqVO.getDeptName());
        target.setPositionName(reqVO.getPositionName());
        target.setEmploymentType(reqVO.getEmploymentType());
        target.setArchiveNo(reqVO.getArchiveNo());
        target.setBindedAuthUserId(reqVO.getBindedAuthUserId());
        target.setRemark(reqVO.getRemark());
    }

    private PersonnelProfileRespVO toProfileVO(PersonnelProfileDO profile, int totalCert, int usableCert,
                                               LocalDate today) {
        PersonnelProfileRespVO vo = new PersonnelProfileRespVO();
        vo.setId(profile.getId());
        vo.setTenantId(profile.getTenantId());
        vo.setEntId(profile.getEntId());
        vo.setPersonNo(profile.getPersonNo());
        vo.setPersonName(profile.getPersonName());
        // L4 只回掩码：明文既不在库里（仅密文）也不出参，页面用掩码做人工核对
        vo.setIdCardMasked(PersonnelSensitiveUtils.maskIdCard(profile.getIdCardEnc()));
        vo.setIdCardValidTo(profile.getIdCardValidTo());
        vo.setGender(profile.getGender());
        vo.setBirthDate(profile.getBirthDate());
        vo.setMobile(PersonnelSensitiveUtils.maskMobile(profile.getMobile()));
        vo.setEmail(profile.getEmail());
        vo.setEduLevel(profile.getEduLevel());
        vo.setEduMajor(profile.getEduMajor());
        vo.setSchool(profile.getSchool());
        vo.setGraduateDate(profile.getGraduateDate());
        vo.setTitleLevel(profile.getTitleLevel());
        vo.setTitleMajor(profile.getTitleMajor());
        vo.setWorkStartDate(profile.getWorkStartDate());
        vo.setJoinDate(profile.getJoinDate());
        vo.setDeptName(profile.getDeptName());
        vo.setPositionName(profile.getPositionName());
        vo.setEmploymentType(profile.getEmploymentType());
        vo.setIsFullTime(profile.getIsFullTime());
        vo.setPersonnelStatus(profile.getPersonnelStatus());
        vo.setStatusChangeDate(profile.getStatusChangeDate());
        vo.setArchiveNo(profile.getArchiveNo());
        vo.setRemark(profile.getRemark());
        vo.setVersion(profile.getVersion());
        vo.setCertCount(totalCert);
        vo.setUsableCertCount(usableCert);
        vo.setIdCardValid(profile.getIdCardValidTo() == null || !profile.getIdCardValidTo().isBefore(today));
        return vo;
    }

    static PersonnelCertRespVO toCertVO(PersonnelCertificateDO cert, LocalDate today) {
        PersonnelCertRespVO vo = new PersonnelCertRespVO();
        vo.setId(cert.getId());
        vo.setEntId(cert.getEntId());
        vo.setPersonId(cert.getPersonId());
        vo.setCertType(cert.getCertType());
        vo.setCertName(cert.getCertName());
        vo.setCertNo(cert.getCertNo());
        vo.setCertLevel(cert.getCertLevel());
        vo.setCertSubType(cert.getCertSubType());
        vo.setMajor(cert.getMajor());
        vo.setEduLevel(cert.getEduLevel());
        vo.setIssuingAuthority(cert.getIssuingAuthority());
        vo.setIssueDate(cert.getIssueDate());
        vo.setValidFrom(cert.getValidFrom());
        vo.setValidTo(cert.getValidTo());
        vo.setNextReviewDate(cert.getNextReviewDate());
        vo.setRegisterUnit(cert.getRegisterUnit());
        vo.setRegisterUnitCode(cert.getRegisterUnitCode());
        vo.setRegisterNo(cert.getRegisterNo());
        vo.setCertStatus(cert.getCertStatus());
        vo.setPlatformVerified(cert.getPlatformVerified());
        vo.setVerifyUrl(cert.getVerifyUrl());
        vo.setVerifiedAt(cert.getVerifiedAt());
        vo.setBindedCertId(cert.getBindedCertId());
        vo.setFileId(cert.getFileId());
        vo.setSourceChannel(cert.getSourceChannel());
        vo.setRemark(cert.getRemark());
        vo.setVersion(cert.getVersion());
        vo.setRemainingDays(cert.getValidTo() == null ? null : ChronoUnit.DAYS.between(today, cert.getValidTo()));
        vo.setReviewRemainingDays(cert.getNextReviewDate() == null ? null
                : ChronoUnit.DAYS.between(today, cert.getNextReviewDate()));
        return vo;
    }

    private String appendRemark(String origin, String append) {
        if (origin == null || origin.isBlank()) {
            return append;
        }
        return origin + "；" + append;
    }
}
