package cn.iocoder.txgy.module.bidding.service.personnel;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertificatePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertificateSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelCertificateDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelProfileDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelCertificateMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelProfileMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.PersonnelCertStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 员工证书服务实现（登记判重 / 状态机 CAS / 分类检索 / 到期清单）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（注册有效期与注册单位）、§九（到期预警按
 * valid_to 窗口扫描）；《注册建造师管理规定》（建设部令第153号）第十条（注册证书有效期 3 年、届满前
 * 30 日申请延续）——有期限的证书没有有效期就无法预警，故保存时强制校验；
 * 资质管理/06-电子证照设计材料对比与推进依据 §二-3/§二-4（cert_sub_type 细分、next_review_date 复审维度）；
 * 《招投标模块开发规范》§4.5（乐观锁 CAS）、§4.6（分页走索引字段）、§5 三-5（附件只存 file_id）。
 * <p>设计遵循/针对：① 同人同类型同编号唯一（DDL uk_pcert_no）——不同人的同号证书、同人的同号重复录入都要拦；
 * ② 状态流转复用 PersonnelCertStatusEnum 显式迁移表，EXPIRING_SOON 由预警扫描自动置位，人工不得直接设置；
 * ③ 到期清单与预警扫描共用同一"可用状态 + valid_to 窗口"口径，避免页面显示与告警口径不一致；
 * ④ 证书归属企业必须与员工档案一致，防止"证书挂 A 企业、人属 B 企业"的错配污染后续一致性核验。
 * <p>业务目标：证书台账一次录入，长期可用——能筛、能预警、能证明"这个人此刻确实持证"。
 */
@Slf4j
@Service
public class PersonnelCertificateServiceImpl implements PersonnelCertificateService {

    /** 必须有有效期才可预警的证书类型（长期有效的证书类型不在此列，如部分荣誉/学历长期有效）。 */
    private static final List<String> EXPIRY_REQUIRED_TYPES = List.of(
            "ID_CARD", "REG_PRACTICE_CERT", "SAFETY_ASSESS_CERT", "SKILL_CERT", "TITLE_CERT");

    @Resource
    private PersonnelCertificateMapper personnelCertificateMapper;
    @Resource
    private PersonnelProfileMapper personnelProfileMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCertificate(PersonnelCertificateSaveReqVO reqVO) {
        validatePerson(reqVO.getPersonId(), reqVO.getEntId());
        if (personnelCertificateMapper.selectByCertNo(reqVO.getPersonId(), reqVO.getCertType(),
                reqVO.getCertNo()) != null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_CERT_NO_DUPLICATE);
        }
        if (EXPIRY_REQUIRED_TYPES.contains(reqVO.getCertType()) && reqVO.getValidTo() == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_CERT_EXPIRY_REQUIRED);
        }
        PersonnelCertificateDO cert = new PersonnelCertificateDO();
        BeanUtils.copyProperties(reqVO, cert);
        cert.setId(null);
        cert.setIsCurrent(true);
        cert.setCertStatus(initialStatus(reqVO.getValidTo()));
        personnelCertificateMapper.insert(cert);
        log.info("[personnel-cert][create] personId={} type={} no={} id={}",
                cert.getPersonId(), cert.getCertType(), cert.getCertNo(), cert.getId());
        return cert.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCertificate(PersonnelCertificateSaveReqVO reqVO) {
        PersonnelCertificateDO exists = validateCertExists(reqVO.getId());
        if (reqVO.getVersion() != null && !Objects.equals(reqVO.getVersion(), exists.getVersion())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_DATA_MODIFIED);
        }
        if (reqVO.getCertNo() != null && reqVO.getCertType() != null
                && !(reqVO.getCertNo().equals(exists.getCertNo())
                     && reqVO.getCertType().equals(exists.getCertType()))) {
            PersonnelCertificateDO other = personnelCertificateMapper.selectByCertNo(
                    exists.getPersonId(), reqVO.getCertType(), reqVO.getCertNo());
            if (other != null && !Objects.equals(other.getId(), exists.getId())) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_CERT_NO_DUPLICATE);
            }
        }
        Integer originVersion = exists.getVersion();
        String originStatus = exists.getCertStatus();
        BeanUtils.copyProperties(reqVO, exists);
        exists.setId(reqVO.getId());
        exists.setVersion(originVersion);
        // 状态不由本接口改写：有效期变化只影响"是否自动降为过期"，其余状态必须走 changeStatus
        exists.setCertStatus(resolveStatusAfterUpdate(originStatus, reqVO.getValidTo()));
        if (personnelCertificateMapper.updateById(exists) == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_DATA_MODIFIED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long certId, String targetStatus, Integer version, String reason) {
        PersonnelCertificateDO cert = validateCertExists(certId);
        if (!Objects.equals(version, cert.getVersion())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_DATA_MODIFIED);
        }
        PersonnelCertStatusEnum from;
        PersonnelCertStatusEnum to;
        try {
            from = PersonnelCertStatusEnum.valueOf(cert.getCertStatus());
            to = PersonnelCertStatusEnum.valueOf(targetStatus);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_CERT_STATUS_TRANSITION_INVALID);
        }
        // EXPIRING_SOON 是预警扫描的派生结果，不接受人工设置（否则预警口径可被绕过）
        if (PersonnelCertStatusEnum.EXPIRING_SOON == to) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_CERT_STATUS_TRANSITION_INVALID);
        }
        if (!PersonnelCertStatusEnum.isTransitionAllowed(from, to)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_CERT_STATUS_TRANSITION_INVALID);
        }
        cert.setCertStatus(to.name());
        if (reason != null && !reason.isBlank()) {
            cert.setRemark(cert.getRemark() == null || cert.getRemark().isBlank()
                    ? reason : cert.getRemark() + "；" + reason);
        }
        if (personnelCertificateMapper.updateById(cert) == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_DATA_MODIFIED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCertificate(Long certId) {
        validateCertExists(certId);
        personnelCertificateMapper.deleteById(certId);
    }

    @Override
    public PersonnelCertificateDO getCertificate(Long id) {
        return validateCertExists(id);
    }

    @Override
    public PageResult<PersonnelCertRespVO> pageCertificate(PersonnelCertificatePageReqVO reqVO) {
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<PersonnelCertificateDO> query = new LambdaQueryWrapper<PersonnelCertificateDO>()
                .eq(reqVO.getPersonId() != null, PersonnelCertificateDO::getPersonId, reqVO.getPersonId())
                .eq(reqVO.getEntId() != null, PersonnelCertificateDO::getEntId, reqVO.getEntId())
                .eq(reqVO.getCertType() != null && !reqVO.getCertType().isBlank(),
                        PersonnelCertificateDO::getCertType, reqVO.getCertType())
                .eq(reqVO.getCertNo() != null && !reqVO.getCertNo().isBlank(),
                        PersonnelCertificateDO::getCertNo, reqVO.getCertNo())
                .eq(reqVO.getCertStatus() != null && !reqVO.getCertStatus().isBlank(),
                        PersonnelCertificateDO::getCertStatus, reqVO.getCertStatus())
                .like(reqVO.getMajor() != null && !reqVO.getMajor().isBlank(),
                        PersonnelCertificateDO::getMajor, reqVO.getMajor())
                .eq(PersonnelCertificateDO::getIsCurrent, true)
                .orderByAsc(PersonnelCertificateDO::getValidTo)
                .orderByDesc(PersonnelCertificateDO::getId);
        // 到期窗口：只取"有效/临近到期"且 valid_to 落在 [今天, 今天+N] 的记录（与预警扫描同口径）
        if (reqVO.getExpiringDays() != null && reqVO.getExpiringDays() > 0) {
            query.in(PersonnelCertificateDO::getCertStatus, PersonnelCertStatusEnum.VALID.name(),
                            PersonnelCertStatusEnum.EXPIRING_SOON.name())
                    .isNotNull(PersonnelCertificateDO::getValidTo)
                    .ge(PersonnelCertificateDO::getValidTo, today)
                    .le(PersonnelCertificateDO::getValidTo, today.plusDays(reqVO.getExpiringDays()));
        }
        Page<PersonnelCertificateDO> page = personnelCertificateMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), query);
        return new PageResult<>(page.getRecords().stream()
                .map(c -> PersonnelProfileServiceImpl.toCertVO(c, today)).toList(), page.getTotal());
    }

    @Override
    public List<PersonnelCertRespVO> listByPerson(Long personId) {
        LocalDate today = LocalDate.now();
        return personnelCertificateMapper.selectByPerson(personId).stream()
                .map(c -> PersonnelProfileServiceImpl.toCertVO(c, today)).toList();
    }

    @Override
    public List<PersonnelCertRespVO> listExpiring(Long entId, int days) {
        LocalDate today = LocalDate.now();
        int maxDays = days > 0 ? days : 30;
        List<PersonnelCertRespVO> result = new ArrayList<>();
        for (PersonnelCertificateDO cert : personnelCertificateMapper.selectExpiringCandidates(today, maxDays)) {
            if (entId != null && !Objects.equals(entId, cert.getEntId())) {
                continue;
            }
            result.add(PersonnelProfileServiceImpl.toCertVO(cert, today));
        }
        return result;
    }

    @Override
    public Map<String, Integer> countUsableBySubType(Long entId, String certType) {
        Map<String, Integer> counted = new LinkedHashMap<>();
        for (Map<String, Object> row : personnelCertificateMapper.countUsableBySubType(entId, certType,
                LocalDate.now())) {
            Object subType = row.get("sub_type");
            Object cnt = row.get("cnt");
            counted.put(subType == null ? "UNSPECIFIED" : String.valueOf(subType),
                    cnt == null ? 0 : Integer.parseInt(String.valueOf(cnt)));
        }
        return counted;
    }

    // ========== 内部 ==========

    private void validatePerson(Long personId, Long entId) {
        PersonnelProfileDO profile = personId == null ? null : personnelProfileMapper.selectById(personId);
        if (profile == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_NOT_EXISTS);
        }
        // 证书归属企业必须与员工档案一致：否则会出现"证书挂在 A 企业、人属于 B 企业"的错配数据，
        // 直接影响后续社保/合同一致性核验的可信度
        if (entId != null && profile.getEntId() != null && !Objects.equals(entId, profile.getEntId())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_NOT_EXISTS);
        }
    }

    private PersonnelCertificateDO validateCertExists(Long id) {
        PersonnelCertificateDO cert = id == null ? null : personnelCertificateMapper.selectById(id);
        if (cert == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_CERT_NOT_EXISTS);
        }
        return cert;
    }

    /** 建档初始状态：已过期日期直接落 EXPIRED，否则 VALID（≤30 天由预警扫描置 EXPIRING_SOON）。 */
    private String initialStatus(LocalDate validTo) {
        if (validTo != null && validTo.isBefore(LocalDate.now())) {
            return PersonnelCertStatusEnum.EXPIRED.name();
        }
        return PersonnelCertStatusEnum.VALID.name();
    }

    /**
     * 更新后的状态推导：仅在"有效期已过"时降为 EXPIRED、"又未过期"时回到 VALID；
     * 吊销/注销等业务状态不受有效期影响，保持原值。
     */
    private String resolveStatusAfterUpdate(String originStatus, LocalDate newValidTo) {
        boolean terminalBusinessStatus = PersonnelCertStatusEnum.REVOKED.name().equals(originStatus)
                || PersonnelCertStatusEnum.CANCELLED.name().equals(originStatus);
        if (terminalBusinessStatus) {
            return originStatus;
        }
        if (newValidTo != null && newValidTo.isBefore(LocalDate.now())) {
            return PersonnelCertStatusEnum.EXPIRED.name();
        }
        return PersonnelCertStatusEnum.VALID.name();
    }
}
