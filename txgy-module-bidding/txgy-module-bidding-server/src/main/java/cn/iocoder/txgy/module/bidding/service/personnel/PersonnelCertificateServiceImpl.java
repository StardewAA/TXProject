package cn.iocoder.txgy.module.bidding.service.personnel;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.bidding.service.certificate.CertificateVerifyService;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertificatePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertPageByPersonReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertPersonRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertStatsRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertAlertNotifyReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertBatchRenewFailVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertBatchRenewItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertBatchRenewRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertBatchRenewReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertRenewItemReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertificateSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertNotifyOutboxDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelCertificateDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelProfileDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertNotifyOutboxMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelCertificateMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelProfileMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.CertNotifyBizTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.CertNotifySendStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.PersonnelCertStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertImportExcelVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertImportFailureVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertImportRespVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    @jakarta.annotation.Resource
    private cn.iocoder.txgy.module.bidding.service.subject.SubjectProfileService subjectProfileService;
    @Resource
    private PersonnelProfileMapper personnelProfileMapper;
    @Resource
    private CertNotifyOutboxMapper certNotifyOutboxMapper;
    @Resource
    private CertificateVerifyService certificateVerifyService;

    /**
     * 自身代理（用于导入的"逐行独立事务"）。
     *
     * <p>为什么需要：{@code createCertificate} 标了 {@code @Transactional}，但**类内自调用不走代理**，
     * 事务注解会失效 —— 那样一行的唯一键冲突就会把整批已成功的行一并回滚，与"行级回执、单个失败不影响其余"
     * 的承诺矛盾。注入自身代理后每次调用都是独立事务边界。
     * {@code @Lazy} 避免与 Spring 的 Bean 创建顺序耦合（本类可能被其它服务反向依赖）。
     */
    @org.springframework.context.annotation.Lazy
    @Resource
    private PersonnelCertificateService self;

    private PersonnelCertificateService requireSelf() {
        return self == null ? this : self;
    }

    @Override
    public PersonnelCertImportRespVO importCertificates(List<PersonnelCertImportExcelVO> rows, Long entId) {
        List<PersonnelCertImportExcelVO> list = rows == null ? List.of() : rows;
        PersonnelCertImportRespVO resp = new PersonnelCertImportRespVO();
        resp.setTotalRows(list.size());
        List<PersonnelCertImportFailureVO> failures = new ArrayList<>();
        int success = 0;
        // 行号从 2 起：Excel 第 1 行是表头，业务行自 2 开始（与证照/资质导入同口径）
        int rowNum = 1;
        Long resolvedEntId = entId != null ? entId : subjectProfileService.getOwnSubjectId();
        for (PersonnelCertImportExcelVO row : list) {
            rowNum++;
            // 逐行独立事务：单行失败不影响其余。用 REQUIRES_NEW 而非把整批包在一个事务里
            // —— 否则一行的唯一键冲突会把已成功的行一并回滚，与"行级回执"承诺矛盾。
            try {
                requireSelf().createCertificate(toSaveReqVO(row, resolvedEntId));
                success++;
            } catch (ServiceException e) {
                failures.add(new PersonnelCertImportFailureVO(rowNum, e.getMessage()));
            } catch (Exception e) {
                // 兜底：非业务异常也要变成"该行失败"，不能让一整批导入因一行脏数据 500
                failures.add(new PersonnelCertImportFailureVO(rowNum, "导入失败：" + e.getMessage()));
            }
        }
        resp.setSuccessCount(success);
        resp.setFailureCount(failures.size());
        resp.setFailCount(failures.size());
        resp.setFailures(failures);
        return resp;
    }

    /** Excel 行 → 建档入参（字段一对一直传；entId 由调用方推导，personId 来自表格）。 */
    private PersonnelCertificateSaveReqVO toSaveReqVO(PersonnelCertImportExcelVO row, Long entId) {
        PersonnelCertificateSaveReqVO req = new PersonnelCertificateSaveReqVO();
        req.setEntId(entId);
        req.setPersonId(row.getPersonId());
        req.setCertType(row.getCertType());
        req.setCertName(row.getCertName());
        req.setCertNo(row.getCertNo());
        req.setCertLevel(row.getCertLevel());
        req.setMajor(row.getMajor());
        req.setEduLevel(row.getEduLevel());
        req.setIssuingAuthority(row.getIssuingAuthority());
        req.setIssueDate(row.getIssueDate());
        req.setValidFrom(row.getValidFrom());
        req.setValidTo(row.getValidTo());
        req.setRemark(row.getRemark());
        return req;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCertificate(PersonnelCertificateSaveReqVO reqVO) {
        // 规范出处：《后端变更通知-给前端》§12 —— "我是谁"类 id 由后端推导（一个租户只有一家公司）。
        // 为什么这样写：entId 是"本企业主体"，前端不必传；缺省取本租户主体档案 id（与证照建档 org_id 同一入口）。
        if (reqVO.getEntId() == null) { reqVO.setEntId(subjectProfileService.getOwnSubjectId()); }
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
    public PageResult<PersonnelCertPersonRespVO> pageByPerson(PersonnelCertPageByPersonReqVO reqVO) {
        LocalDate today = LocalDate.now();
        // 规范出处：五页改造缺接口契约 Q9（2026-09-18）——按人维度分页，同一人的证书在同一页内完整。
        // 为什么这样写：现有 page 按证书分页，分页会把人切断（同一人可能跨页）；本接口改为
        // ① 先按证书筛选取持证人 id 集合（与 page 同口径：isCurrent=true + 状态/类型/到期窗口），
        // ② 再按人分页（personnel_profile + deptName 过滤），③ 一次性批量取本页人员的证书避免 N+1。
        LambdaQueryWrapper<PersonnelCertificateDO> certFilter = new LambdaQueryWrapper<PersonnelCertificateDO>()
                .eq(reqVO.getEntId() != null, PersonnelCertificateDO::getEntId, reqVO.getEntId())
                .eq(reqVO.getCertType() != null && !reqVO.getCertType().isBlank(),
                        PersonnelCertificateDO::getCertType, reqVO.getCertType())
                .eq(reqVO.getCertStatus() != null && !reqVO.getCertStatus().isBlank(),
                        PersonnelCertificateDO::getCertStatus, reqVO.getCertStatus())
                .eq(PersonnelCertificateDO::getIsCurrent, true);
        if (reqVO.getExpiringDays() != null && reqVO.getExpiringDays() > 0) {
            certFilter.in(PersonnelCertificateDO::getCertStatus, PersonnelCertStatusEnum.VALID.name(),
                            PersonnelCertStatusEnum.EXPIRING_SOON.name())
                    .isNotNull(PersonnelCertificateDO::getValidTo)
                    .ge(PersonnelCertificateDO::getValidTo, today)
                    .le(PersonnelCertificateDO::getValidTo, today.plusDays(reqVO.getExpiringDays()));
        }
        // ① 持证人集合（按人分组去重）
        List<Object> personIdObjs = personnelCertificateMapper.selectObjs(
                certFilter.select(PersonnelCertificateDO::getPersonId)
                        .groupBy(PersonnelCertificateDO::getPersonId));
        if (personIdObjs.isEmpty()) {
            return new PageResult<>(new ArrayList<>(), 0L);
        }
        Set<Long> personIds = personIdObjs.stream()
                .map(o -> Long.valueOf(String.valueOf(o))).collect(Collectors.toSet());
        // ② 按人分页（deptName 模糊过滤）
        LambdaQueryWrapper<PersonnelProfileDO> personQuery = new LambdaQueryWrapper<PersonnelProfileDO>()
                .in(PersonnelProfileDO::getId, personIds)
                .like(reqVO.getDeptName() != null && !reqVO.getDeptName().isBlank(),
                        PersonnelProfileDO::getDeptName, reqVO.getDeptName())
                .orderByAsc(PersonnelProfileDO::getPersonNo);
        Page<PersonnelProfileDO> personPage = personnelProfileMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), personQuery);
        if (personPage.getRecords().isEmpty()) {
            return new PageResult<>(new ArrayList<>(), personPage.getTotal());
        }
        // ③ 一次性取本页人员的同筛选证书（避免 N+1）
        List<Long> pagePersonIds = personPage.getRecords().stream()
                .map(PersonnelProfileDO::getId).collect(Collectors.toList());
        LambdaQueryWrapper<PersonnelCertificateDO> pageCertQuery = new LambdaQueryWrapper<PersonnelCertificateDO>()
                .in(PersonnelCertificateDO::getPersonId, pagePersonIds)
                .eq(reqVO.getCertType() != null && !reqVO.getCertType().isBlank(),
                        PersonnelCertificateDO::getCertType, reqVO.getCertType())
                .eq(reqVO.getCertStatus() != null && !reqVO.getCertStatus().isBlank(),
                        PersonnelCertificateDO::getCertStatus, reqVO.getCertStatus())
                .eq(PersonnelCertificateDO::getIsCurrent, true);
        if (reqVO.getExpiringDays() != null && reqVO.getExpiringDays() > 0) {
            pageCertQuery.in(PersonnelCertificateDO::getCertStatus, PersonnelCertStatusEnum.VALID.name(),
                            PersonnelCertStatusEnum.EXPIRING_SOON.name())
                    .isNotNull(PersonnelCertificateDO::getValidTo)
                    .ge(PersonnelCertificateDO::getValidTo, today)
                    .le(PersonnelCertificateDO::getValidTo, today.plusDays(reqVO.getExpiringDays()));
        }
        Map<Long, List<PersonnelCertificateDO>> certsByPerson = personnelCertificateMapper.selectList(pageCertQuery)
                .stream().collect(Collectors.groupingBy(PersonnelCertificateDO::getPersonId));
        // ④ 组装人维度出参
        List<PersonnelCertPersonRespVO> list = new ArrayList<>();
        for (PersonnelProfileDO profile : personPage.getRecords()) {
            List<PersonnelCertificateDO> certs = certsByPerson.getOrDefault(profile.getId(), Collections.emptyList());
            PersonnelCertPersonRespVO vo = new PersonnelCertPersonRespVO();
            vo.setPersonId(profile.getId());
            vo.setPersonName(profile.getPersonName());
            vo.setPersonNo(profile.getPersonNo());
            vo.setDeptName(profile.getDeptName());
            vo.setCertCount((long) certs.size());
            vo.setNearestValidTo(certs.stream().map(PersonnelCertificateDO::getValidTo)
                    .filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null));
            vo.setCerts(certs.stream()
                    .map(c -> PersonnelProfileServiceImpl.toCertVO(c, today)).collect(Collectors.toList()));
            list.add(vo);
        }
        log.info("[cert][page-by-person] total={} rows={}", personPage.getTotal(), list.size());
        return new PageResult<>(list, personPage.getTotal());
    }

    @Override
    public PersonnelCertStatsRespVO getCertStats(Long entId, String deptName) {
        // 规范出处：五页改造缺接口契约 Q10（2026-09-18）。口径与 page 同源：is_current=true 过滤；
        // deptName 非空时先按部门取员工集合，再统计其证书——五项聚合共用同一 personId 集合，避免口径打架。
        List<Long> deptPersonIds = null;
        if (deptName != null && !deptName.isBlank()) {
            List<PersonnelProfileDO> deptPersons = personnelProfileMapper.selectList(
                    new LambdaQueryWrapper<PersonnelProfileDO>()
                            .like(PersonnelProfileDO::getDeptName, deptName));
            if (deptPersons.isEmpty()) {
                return emptyCertStats();
            }
            deptPersonIds = deptPersons.stream().map(PersonnelProfileDO::getId).collect(Collectors.toList());
        }
        List<Long> finalDeptPersonIds = deptPersonIds;
        long certTotal = countCerts(entId, finalDeptPersonIds, w -> w);
        long expiring = countCerts(entId, finalDeptPersonIds,
                w -> w.eq(PersonnelCertificateDO::getCertStatus, PersonnelCertStatusEnum.EXPIRING_SOON.name()));
        long expired = countCerts(entId, finalDeptPersonIds,
                w -> w.eq(PersonnelCertificateDO::getCertStatus, PersonnelCertStatusEnum.EXPIRED.name()));
        long unverified = countCerts(entId, finalDeptPersonIds,
                w -> w.and(x -> x.isNull(PersonnelCertificateDO::getPlatformVerified)
                        .or().eq(PersonnelCertificateDO::getPlatformVerified, false)));
        // 持证员工数：按 person_id 去重（同部门过滤）
        LambdaQueryWrapper<PersonnelCertificateDO> holderQuery = new LambdaQueryWrapper<PersonnelCertificateDO>()
                .select(PersonnelCertificateDO::getPersonId)
                .eq(entId != null, PersonnelCertificateDO::getEntId, entId)
                .eq(PersonnelCertificateDO::getIsCurrent, true)
                .in(finalDeptPersonIds != null, PersonnelCertificateDO::getPersonId, finalDeptPersonIds)
                .groupBy(PersonnelCertificateDO::getPersonId);
        long holderCount = personnelCertificateMapper.selectObjs(holderQuery).size();
        PersonnelCertStatsRespVO resp = new PersonnelCertStatsRespVO();
        resp.setHolderCount(holderCount);
        resp.setCertTotal(certTotal);
        resp.setExpiring(expiring);
        resp.setExpired(expired);
        resp.setUnverified(unverified);
        log.info("[cert][stats] ent={} dept={} holders={} total={} expiring={} expired={} unverified={}",
                entId, deptName, holderCount, certTotal, expiring, expired, unverified);
        return resp;
    }

    /**
     * 证书计数（is_current=true + entId + 可选部门 personId 集合 + 追加条件），供 Q10 五项聚合复用。
     */
    private long countCerts(Long entId, List<Long> deptPersonIds,
                            java.util.function.Function<LambdaQueryWrapper<PersonnelCertificateDO>,
                                    LambdaQueryWrapper<PersonnelCertificateDO>> extra) {
        LambdaQueryWrapper<PersonnelCertificateDO> query = new LambdaQueryWrapper<PersonnelCertificateDO>()
                .eq(entId != null, PersonnelCertificateDO::getEntId, entId)
                .eq(PersonnelCertificateDO::getIsCurrent, true)
                .in(deptPersonIds != null, PersonnelCertificateDO::getPersonId, deptPersonIds);
        return personnelCertificateMapper.selectCount(extra.apply(query));
    }

    private PersonnelCertStatsRespVO emptyCertStats() {
        PersonnelCertStatsRespVO resp = new PersonnelCertStatsRespVO();
        resp.setHolderCount(0L);
        resp.setCertTotal(0L);
        resp.setExpiring(0L);
        resp.setExpired(0L);
        resp.setUnverified(0L);
        return resp;
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
        // 2026-09-13 统一口径：与预警扫描共用同一到期窗口定义（含 is_current 过滤与"今天到期"闭区间），
        // 且 entId 下推到 SQL——此前 entId 在内存里过滤，等于同一件事有第二套实现
        // [2026-09-26 修复] 0db6686 并发回退时被改回 2 参+内存过滤；按 2669ec9 原文恢复。
        return personnelCertificateMapper.selectExpiringCandidates(today, maxDays, entId).stream()
                .map(cert -> PersonnelProfileServiceImpl.toCertVO(cert, today))
                .toList();
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PersonnelCertBatchRenewRespVO batchRenew(PersonnelCertBatchRenewReqVO reqVO, Long operatorId) {
        // 规范出处：五页改造契约 Q11（2026-09-28）——批量续期逐条执行、逐条回执，单个失败不影响其余。
        // 2026-09-28 对账补齐前端已发布形态（personnel/index.ts 的 BatchRenewReq / BatchResult）：
        // ① 入参支持 items[]（逐条，各自新有效期）**或** certIds[]+newValidTo（统一批量）；
        // ② 出参包装成对象（total/successCount/failCount/failures[{id,name,message}]）——原先是裸数组，
        //    前端读 successCount 恒为 undefined，"部分成功"提示无法展示。
        List<PersonnelCertRenewItemReqVO> items = normalizeRenewItems(reqVO);
        List<PersonnelCertBatchRenewItemRespVO> itemResults = new ArrayList<>();
        List<PersonnelCertBatchRenewFailVO> failures = new ArrayList<>();
        for (PersonnelCertRenewItemReqVO item : items) {
            PersonnelCertBatchRenewItemRespVO one = new PersonnelCertBatchRenewItemRespVO();
            one.setId(item.getId());
            try {
                renewOne(item, operatorId);
                one.setSuccess(true);
            } catch (ServiceException e) {
                one.setSuccess(false);
                one.setMessage(e.getMessage());
            } catch (Exception e) {
                log.warn("[personnel-cert][batch-renew] id={} 续期失败: {}", item.getId(), e.getMessage());
                one.setSuccess(false);
                one.setMessage("续期失败：" + e.getMessage());
            }
            itemResults.add(one);
            if (!Boolean.TRUE.equals(one.getSuccess())) {
                PersonnelCertBatchRenewFailVO fail = new PersonnelCertBatchRenewFailVO();
                fail.setId(item.getId());
                // 证书名尽力而为：失败行前端也要能指认是哪张证（查不到就留空，不影响回执可用性）
                PersonnelCertificateDO cert = item.getId() == null ? null
                        : personnelCertificateMapper.selectById(item.getId());
                fail.setName(cert == null ? null : cert.getCertName());
                fail.setMessage(one.getMessage());
                failures.add(fail);
            }
        }
        int successCount = (int) itemResults.stream().filter(x -> Boolean.TRUE.equals(x.getSuccess())).count();
        PersonnelCertBatchRenewRespVO resp = new PersonnelCertBatchRenewRespVO();
        resp.setTotal(itemResults.size());
        resp.setSuccessCount(successCount);
        resp.setFailureCount(itemResults.size() - successCount);
        resp.setFailCount(itemResults.size() - successCount);
        resp.setItems(itemResults);
        resp.setFailures(failures);
        log.info("[personnel-cert][batch-renew] 完成：{} 成功，{} 失败，operator={}",
                successCount, failures.size(), operatorId);
        return resp;
    }

    /**
     * 入参归一到"逐条"形态：items[] 优先；否则用 certIds[]+newValidTo 展开；两者都空或统一模式缺日期 → 参数非法。
     *
     * <p>为什么归一：两种模式共用同一段执行逻辑，避免"统一批量"另写一遍导致口径分叉（如漏了状态重算/留痕）。
     */
    private List<PersonnelCertRenewItemReqVO> normalizeRenewItems(PersonnelCertBatchRenewReqVO reqVO) {
        if (reqVO.getItems() != null && !reqVO.getItems().isEmpty()) {
            return reqVO.getItems();
        }
        if (reqVO.getCertIds() != null && !reqVO.getCertIds().isEmpty()) {
            if (reqVO.getNewValidTo() == null) {
                // 统一模式没给新有效期 = 无值可写；明确报错而不是静默跳过（静默跳过会表现为"点了没反应"）
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_CERT_RENEW_INVALID);
            }
            List<PersonnelCertRenewItemReqVO> expanded = new ArrayList<>();
            for (Long certId : reqVO.getCertIds()) {
                PersonnelCertRenewItemReqVO item = new PersonnelCertRenewItemReqVO();
                item.setId(certId);
                item.setNewValidTo(reqVO.getNewValidTo());
                item.setRenewalType(reqVO.getRenewalType());
                expanded.add(item);
            }
            return expanded;
        }
        throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_CERT_RENEW_INVALID);
    }

    /** 单条续期（原逐条逻辑抽出；version 为空时跳过 CAS，供"统一批量"模式不带版本调用）。 */
    private void renewOne(PersonnelCertRenewItemReqVO item, Long operatorId) {
        PersonnelCertificateDO cert = validateCertExists(item.getId());
        if (item.getVersion() != null && !Objects.equals(item.getVersion(), cert.getVersion())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_DATA_MODIFIED);
        }
        // 新有效期不能早于今天、也不能早于原有效期（续期=向后延续，防误操作把有效期改短）
        LocalDate today = LocalDate.now();
        if (item.getNewValidTo() == null || item.getNewValidTo().isBefore(today)
                || (cert.getValidTo() != null && item.getNewValidTo().isBefore(cert.getValidTo()))) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_CERT_RENEW_INVALID);
        }
        String originStatus = cert.getCertStatus();
        cert.setValidTo(item.getNewValidTo());
        cert.setCertStatus(resolveStatusAfterUpdate(originStatus, item.getNewValidTo()));
        if (item.getRenewalType() != null && !item.getRenewalType().isBlank()) {
            cert.setRemark((cert.getRemark() == null ? "" : cert.getRemark() + "；")
                    + "批量续期(" + item.getRenewalType() + ")");
        }
        if (personnelCertificateMapper.updateById(cert) == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_DATA_MODIFIED);
        }
        log.info("[personnel-cert][batch-renew] id={} newValidTo={} operator={}",
                item.getId(), item.getNewValidTo(), operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int notifyExpiring(PersonnelCertAlertNotifyReqVO reqVO, Long operatorId) {
        // 规范出处：五页改造契约 Q12（2026-09-28）——批量到期提醒：personIds[]/certIds[] 二选一，
        // 逐证写通知发件箱（CHECK_REMIND），同日同证不重复（幂等口径与 Q15 催办一致）。
        List<PersonnelCertificateDO> certs = new ArrayList<>();
        if (reqVO.getCertIds() != null && !reqVO.getCertIds().isEmpty()) {
            certs.addAll(personnelCertificateMapper.selectBatchIds(reqVO.getCertIds()));
        } else if (reqVO.getPersonIds() != null && !reqVO.getPersonIds().isEmpty()) {
            for (Long personId : reqVO.getPersonIds()) {
                certs.addAll(personnelCertificateMapper.selectByPerson(personId));
            }
        }
        if (certs.isEmpty()) {
            return 0;
        }
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        int sent = 0;
        for (PersonnelCertificateDO cert : certs) {
            // 幂等：今日已对该证派发过 CHECK_REMIND 则跳过
            Long count = certNotifyOutboxMapper.selectCount(new LambdaQueryWrapper<CertNotifyOutboxDO>()
                    .eq(CertNotifyOutboxDO::getBizType, CertNotifyBizTypeEnum.CHECK_REMIND.name())
                    .eq(CertNotifyOutboxDO::getBizId, cert.getId())
                    .ge(CertNotifyOutboxDO::getCreateTime, todayStart));
            if (count != null && count > 0) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("bizType", CertNotifyBizTypeEnum.CHECK_REMIND.name());
            payload.put("bizId", cert.getId());
            payload.put("personId", cert.getPersonId());
            payload.put("certName", cert.getCertName());
            payload.put("certNo", cert.getCertNo());
            payload.put("validTo", cert.getValidTo() == null ? "" : cert.getValidTo().toString());
            // 前端已发布字段 message（personnel/index.ts 的 NotifyReq）：传了就用它当正文；
            // 不传则按模板生成——避免"前端不传 → 通知正文为空"这种静默降级。
            payload.put("message", reqVO.getMessage() != null && !reqVO.getMessage().isBlank()
                    ? reqVO.getMessage()
                    : "【到期提醒】" + cert.getCertName() + "（" + cert.getCertNo() + "）"
                            + (cert.getValidTo() == null ? "" : " 有效期至 " + cert.getValidTo()));
            payload.put("notifiedBy", operatorId);
            payload.put("notifiedAt", LocalDateTime.now().toString());
            CertNotifyOutboxDO outbox = new CertNotifyOutboxDO();
            outbox.setBizType(CertNotifyBizTypeEnum.CHECK_REMIND.name());
            outbox.setBizId(cert.getId());
            outbox.setChannel("IN_APP");
            outbox.setReceiver(String.valueOf(operatorId));
            outbox.setPayload(JsonUtils.toJsonString(payload));
            outbox.setSendStatus(CertNotifySendStatusEnum.PENDING.name());
            outbox.setRetryCount(0);
            outbox.setMaxRetry(5);
            certNotifyOutboxMapper.insert(outbox);
            sent++;
        }
        log.info("[personnel-cert][notify-expiring] 派发 {} 条到期提醒，operator={}", sent, operatorId);
        return sent;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verifyPersonnelCert(Long certId, String verifyChannel, Long operatorId) {
        // 规范出处：五页改造契约 Q13（2026-09-28）——证书级验真：复用证照域验真渠道，
        // 须先绑定证照台账（bindedCertId）；结论回写 platformVerified/verifiedAt 供看板"未验真"聚合。
        PersonnelCertificateDO cert = validateCertExists(certId);
        if (cert.getBindedCertId() == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_CERT_NOT_BOUND);
        }
        certificateVerifyService.verifyCertificate(cert.getBindedCertId(), verifyChannel, operatorId);
        cert.setPlatformVerified(true);
        cert.setVerifiedAt(LocalDateTime.now());
        personnelCertificateMapper.updateById(cert);
        log.info("[personnel-cert][verify] id={} bindedCertId={} operator={}",
                certId, cert.getBindedCertId(), operatorId);
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
