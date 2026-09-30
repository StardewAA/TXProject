package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateImportExcelVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateImportFailureVO;
import cn.iocoder.txgy.module.bidding.enums.certificate.CertDictHelper;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateImportRespVO;
import cn.iocoder.txgy.module.bidding.enums.certificate.CertDictTypes;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertChangeLogPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertChangeLogRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertHashChainVerifyRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificatePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateStatsReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateStatsRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertChangeLogDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertChangeLogMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.ruleconfig.BiddingDictDataMapper;
import cn.iocoder.txgy.module.bidding.dal.dataobject.ruleconfig.BiddingDictDataDO;
import cn.iocoder.txgy.module.bidding.enums.CertificateChangeSourceEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateChangeTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateSourceChannelEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateVerificationStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 证照台账服务实现（T 轨；certificate_info 与 cert_change_logs 同事务写入）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §二（台账状态/字段口径）、§13（变更审计哈希链）、
 * §18 v3.4；《招投标模块开发规范》§4.5（乐观锁 CAS、幂等判重）与 §5（表、审计列、只追加日志）。
 * <p>设计遵循/针对：① 建档以 cert_no 租户内唯一 UK 判重（§5 六-1），重复建档抛 CERTIFICATE_NO_DUPLICATE；
 * ② 状态流转只允许 CertificateStatusEnum 迁移表内路径（51号 ZZZT 语义），吊销/注销记 REVOKE、其余记 STATUS_CHANGE；
 * ③ 每次写操作追加哈希链日志（record_hash=SHA-256(证书ID|字段|旧值|新值|类型|来源|IP|批次|prev_hash)，
 * 首条 prev_hash=ROOT），日志与主表同事务、只追加（等保 2.0 三级）；④ @Version 乐观锁由
 * BiddingMybatisConfiguration 补注册的拦截器生效（开发规范 §4.5 乐观锁款、平台核对 2026-09-03），影响行 0 视为冲突。
 */
@Slf4j
@Service
public class CertificateServiceImpl implements CertificateService {

    /** 哈希链链首标记（v3.3 §13；prev_hash 缺省 ROOT） */
    private static final String HASH_CHAIN_ROOT = "ROOT";

    /** 证照台账状态字典类型（电子证照域字典，注册于 bidding_dict_data；不带 bidding_ 前缀） */
    private static final String DICT_TYPE_CERT_STATUS = "cert_status";

    /**
     * 证照类型→大类 归属映射字典类型（契约 R2 新增大类筛选用；见 sql/history/2026-09-20-cert-category-dict.sql）。
     *
     * <p>取值约定：{@code dict_value=证照类型 code}，{@code dict_label=大类 code}（=“值→归属”映射表，
     * 与普通字典“值→展示名”相反；注册时即按此写入，见建表脚本的注释）。
     */
    private static final String DICT_TYPE_CERT_TYPE_CATEGORY = "cert_type_category";

    /**
     * 平台默认行的租户编号（与 {@code MetaDictServiceImpl} / 规则配置同口径）。
     *
     * <p>电子证照域字典（cert_type / cert_status / cert_category …）统一注册在平台租户 0 下，
     * 租户行仅用于差异化覆盖。若只按当前租户查，租户 1 下查不到任何行 → 结果为空白，
     * 表现为“筛选/配色静默失效”（2026-09-21 实测：certCategory=IDENTITY 恒返回 0 条）。
     */
    private static final long PLATFORM_TENANT_ID = 0L;

    @Resource
    private CertificateInfoMapper certificateInfoMapper;
    @Resource
    private BiddingDictDataMapper biddingDictDataMapper;
    @Resource
    private CertDictHelper certDictHelper;
    @Resource
    private CertChangeLogMapper certChangeLogMapper;
    @Resource
    private cn.iocoder.txgy.module.bidding.service.subject.SubjectProfileService subjectProfileService;
    @jakarta.annotation.Resource
    private cn.iocoder.txgy.module.bidding.service.review.ReviewTaskService reviewTaskService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCertificate(CertificateSaveReqVO reqVO) {
        // 幂等判重：证书编号租户内唯一（UK uk_cert_no，开发规范 §4.5/§5 六-1）
        if (certificateInfoMapper.selectByCertNo(reqVO.getCertNo()) != null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_NO_DUPLICATE);
        }
        CertificateInfoDO cert = new CertificateInfoDO();
        cert.setCertName(reqVO.getCertName());
        cert.setCertNo(reqVO.getCertNo());
        cert.setCertType(reqVO.getCertType());
        cert.setCertCategoryCode(reqVO.getCertCategoryCode());
        cert.setHolderName(reqVO.getHolderName());
        // 缺省即"本企业"：holder 不传时后端自己补 ORG + 本租户主体 id（前端只该传"要操作哪个对象"，不该传"我是谁"）
        cert.setHolderType(reqVO.getHolderType() == null ? "ORG" : reqVO.getHolderType());
        cert.setHolderId(reqVO.getHolderId() != null ? reqVO.getHolderId() : subjectProfileService.getOwnSubjectId());
        // 企业维度锚点（org_id）：由后端从"本租户主体档案"推导，不由请求携带。
        // 实测缺陷：此前漏写 ⇒ org_id 恒为 NULL ⇒ 材料完备性（按 org_id = 企业 过滤）永远查不到任何证照。
        cert.setOrgId(subjectProfileService.getOwnSubjectId());
        applyEditable(cert, reqVO);
        // 台账默认事实：状态 ACTIVE（自查只认 ACTIVE）、验真 UNVERIFIED、来源 MANUAL（v3.3 §二）
        cert.setCertStatus(CertificateStatusEnum.ACTIVE.name());
        cert.setVerificationStatus(CertificateVerificationStatusEnum.UNVERIFIED.name());
        cert.setSourceChannel(CertificateSourceChannelEnum.MANUAL.name());
        certificateInfoMapper.insert(cert);
        // 规范出处：《后端变更请求-平台审查任务中心-2026-09-20》§5"触发"——企业录入证照后**后端自动生成审查任务**。
        // 为什么放在建档内：前端无单独创建入口，若靠前端再调一次就可能漏建（漏建=永远不进审查队列，静默积压）。
        // 事务性：与建档同一事务，建档失败则不产生任务（不会出现"任务在、业务没有"的孤儿任务）。
        reviewTaskService.createTask(cn.iocoder.txgy.module.bidding.enums.ReviewBizTypeEnum.CERTIFICATE.name(), cert.getId(), cert.getOrgId(),
                cert.getHolderName(), null, cert.getCertNo(), reqVO.getRemark());

        // CREATE 变更日志：主事实字段（哈希链，v3.3 §13）
        List<Change> facts = new ArrayList<>();
        facts.add(new Change("cert_no", "", reqVO.getCertNo()));
        facts.add(new Change("cert_name", "", reqVO.getCertName()));
        facts.add(new Change("holder_name", "", reqVO.getHolderName()));
        facts.add(new Change("cert_status", "", CertificateStatusEnum.ACTIVE.name()));
        appendChangeLog(cert.getId(), CertificateChangeTypeEnum.CREATE, null, facts);
        return cert.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCertificate(CertificateSaveReqVO reqVO) {
        CertificateInfoDO db = certificateInfoMapper.selectById(reqVO.getId());
        if (db == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_NOT_EXISTS);
        }
        ensureVersionMatch(reqVO.getVersion(), db);
        // 只记录实际变化的可编辑字段（v3.3 §13 UPDATE 行）
        List<Change> diffs = diffEditable(db, reqVO);
        if (diffs.isEmpty()) {
            return;
        }
        applyEditable(db, reqVO);
        int rows = certificateInfoMapper.updateById(db);
        if (rows == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_DATA_MODIFIED);
        }
        appendChangeLog(db.getId(), CertificateChangeTypeEnum.UPDATE, null, diffs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long certificateId, String targetStatus, Integer version, String reason,
                             CertificateChangeSourceEnum opSource, String opIp) {
        CertificateStatusEnum target;
        try {
            target = CertificateStatusEnum.valueOf(targetStatus);
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_STATUS_TRANSITION_INVALID);
        }
        CertificateInfoDO db = certificateInfoMapper.selectById(certificateId);
        if (db == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_NOT_EXISTS);
        }
        String from = db.getCertStatus();
        if (target.name().equals(from)) {
            return; // 幂等放行：目标状态即当前状态
        }
        if (!CertificateStatusEnum.isTransitionAllowed(from, target.name())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_STATUS_TRANSITION_INVALID);
        }
        ensureVersionMatch(version, db);

        db.setCertStatus(target.name());
        int rows = certificateInfoMapper.updateById(db);
        if (rows == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_DATA_MODIFIED);
        }
        // 吊销/注销记 REVOKE，其余状态流转记 STATUS_CHANGE（v3.3 §13）
        CertificateChangeTypeEnum type = (target == CertificateStatusEnum.REVOKED
                || target == CertificateStatusEnum.CANCELLED)
                ? CertificateChangeTypeEnum.REVOKE : CertificateChangeTypeEnum.STATUS_CHANGE;
        List<Change> logs = new ArrayList<>();
        logs.add(new Change("cert_status", from, target.name()));
        if (reason != null && !reason.isBlank()) {
            logs.add(new Change("reason", "", reason));
        }
        appendChangeLog(certificateId, type, opSource == null ? CertificateChangeSourceEnum.MANUAL : opSource,
                opIp, logs);
    }

    @Override
    public CertificateRespVO getCertificate(Long id) {
        CertificateInfoDO cert = certificateInfoMapper.selectById(id);
        if (cert == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_NOT_EXISTS);
        }
        return toRespVO(cert);
    }

    @Override
    public PageResult<CertificateRespVO> getCertificatePage(CertificatePageReqVO reqVO) {
        // Q3 起过滤条件收敛到 buildFilterQuery（page 与 stats 同口径，且支持状态多选）。
        LambdaQueryWrapper<CertificateInfoDO> query = buildFilterQuery(reqVO.getCertNo(), reqVO.getCertType(),
                reqVO.getCertCategory(),
                reqVO.getCertStatus(), reqVO.getCertStatuses(), reqVO.getVerificationStatus(),
                reqVO.getVerificationStatuses(), reqVO.getCertName(), reqVO.getHolderName());
        query.orderByDesc(CertificateInfoDO::getId);
        Page<CertificateInfoDO> page = certificateInfoMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), query);
        return new PageResult<>(page.getRecords().stream().map(this::toRespVO).toList(), page.getTotal());
    }

    @Override
    public CertificateStatsRespVO getCertificateStats(CertificateStatsReqVO reqVO) {
        // 规范出处：五页改造缺接口契约 Q3（2026-09-28，P2 优化项）——看板统计聚合接口，
        // "出参按状态全量计数（含暂扣/吊销/注销），并支持 certStatus in (...) 多选筛选"。
        // 为什么这样写：① 过滤条件与 getCertificatePage 共用 buildFilterQuery，杜绝"看板数 ≠ 列表数"；
        // ② 只投影三个分组列后在内存分组：单次查询、无 N+1，新增状态码也不必改 SQL；
        // ③ 状态键按枚举全量铺开并补 0，前端卡片无需判空兜底（缺失键会让前端 undefined 参与加法）。
        // 业务目标：看板卡片/分布图 1 次请求取齐，替代前端 5 次 pageSize=1 并发计数。
        List<CertificateInfoDO> rows = certificateInfoMapper.selectList(
                buildFilterQuery(reqVO.getCertNo(), reqVO.getCertType(), reqVO.getCertCategory(),
                                reqVO.getCertStatus(), reqVO.getCertStatuses(), reqVO.getVerificationStatus(),
                                reqVO.getVerificationStatuses(), reqVO.getCertName(), reqVO.getHolderName())
                        .select(CertificateInfoDO::getCertStatus, CertificateInfoDO::getVerificationStatus,
                                CertificateInfoDO::getCertType));
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (CertificateStatusEnum status : CertificateStatusEnum.values()) {
            statusCounts.put(status.name(), 0L);
        }
        Map<String, Long> verificationCounts = new LinkedHashMap<>();
        for (CertificateVerificationStatusEnum status : CertificateVerificationStatusEnum.values()) {
            verificationCounts.put(status.name(), 0L);
        }
        Map<String, Long> typeCounts = new LinkedHashMap<>();
        for (CertificateInfoDO row : rows) {
            if (StringUtils.hasText(row.getCertStatus())) {
                statusCounts.merge(row.getCertStatus(), 1L, Long::sum);
            }
            if (StringUtils.hasText(row.getVerificationStatus())) {
                verificationCounts.merge(row.getVerificationStatus(), 1L, Long::sum);
            }
            if (StringUtils.hasText(row.getCertType())) {
                typeCounts.merge(row.getCertType(), 1L, Long::sum);
            }
        }
        CertificateStatsRespVO resp = new CertificateStatsRespVO();
        resp.setTotal((long) rows.size());
        resp.setStatusCounts(statusCounts);
        resp.setVerificationCounts(verificationCounts);
        resp.setTypeCounts(typeCounts);
        // ===== 前端已发布契约字段（2026-09-28 对账后补齐）=====
        // 为什么两个口径都发：映射（statusCounts）是后端既有口径，扁平桶/byStatus 是前端已发布类型
        // （api/bidding/certificate/index.ts 的 CertificateStats）在读；只发一个必然有一边显示空。
        // 分桶为什么查字典而不是写死：桶归属=字典 color_type（success/warning/error/info），
        // 将来新增状态码或调色只需改字典数据，不用动代码（用户硬约束：枚举动态配置，严禁硬编码）。
        resp.setByStatus(statusCounts);
        Map<String, String> colorByStatus = new HashMap<>();
        for (BiddingDictDataDO dict : listEnabledDict(DICT_TYPE_CERT_STATUS)) {
            colorByStatus.put(dict.getDictValue(), dict.getColorType());
        }
        long active = 0L;
        long expiring = 0L;
        long abnormal = 0L;
        long cancelled = 0L;
        for (Map.Entry<String, Long> entry : statusCounts.entrySet()) {
            String color = colorByStatus.get(entry.getKey());
            if ("success".equals(color)) {
                active += entry.getValue();
            } else if ("warning".equals(color)) {
                expiring += entry.getValue();
            } else if ("error".equals(color)) {
                abnormal += entry.getValue();
            } else if ("info".equals(color)) {
                cancelled += entry.getValue();
            }
        }
        resp.setActive(active);
        resp.setExpiring(expiring);
        resp.setAbnormal(abnormal);
        resp.setCancelled(cancelled);
        resp.setUnverified(verificationCounts.getOrDefault(
                CertificateVerificationStatusEnum.UNVERIFIED.name(), 0L));
        return resp;
    }

    /** DO → 自建响应 VO（转换统一在服务层，开发规范 §3.1/§4.2）。 */
    private CertificateRespVO toRespVO(CertificateInfoDO cert) {
        CertificateRespVO resp = new CertificateRespVO();
        resp.setId(cert.getId());
        resp.setVersion(cert.getVersion());
        resp.setCertName(cert.getCertName());
        resp.setCertNo(cert.getCertNo());
        resp.setCertType(cert.getCertType());
        // 中文名统一在服务层由字典解析（字典为唯一权威来源，规范 §5 三-7）——前端无需自行映射枚举
        resp.setCertTypeName(certDictHelper.label(CertDictTypes.CERT_TYPE, cert.getCertType()));
        // 大类：由「类型→大类」映射字典解析出归属，再取大类中文名
        String categoryCode = certDictHelper.label(CertDictTypes.CERT_TYPE_CATEGORY, cert.getCertType());
        resp.setCertCategory(categoryCode);
        resp.setCertCategoryName(certDictHelper.label(CertDictTypes.CERT_CATEGORY, categoryCode));
        resp.setCertCategoryCode(cert.getCertCategoryCode());
        resp.setCertLevel(cert.getCertLevel());
        resp.setCertScope(cert.getCertScope());
        resp.setHolderName(cert.getHolderName());
        resp.setHolderType(cert.getHolderType());
        resp.setHolderId(cert.getHolderId());
        resp.setIssuingAuthority(cert.getIssuingAuthority());
        resp.setIssuingAuthorityCode(cert.getIssuingAuthorityCode());
        resp.setIssueDate(cert.getIssueDate());
        resp.setValidFrom(cert.getValidFrom());
        resp.setValidTo(cert.getValidTo());
        resp.setCertStatus(cert.getCertStatus());
        resp.setCertStatusName(certDictHelper.label(CertDictTypes.CERT_STATUS, cert.getCertStatus()));
        resp.setVerificationStatus(cert.getVerificationStatus());
        resp.setVerificationStatusName(certDictHelper.label(CertDictTypes.VERIFICATION_STATUS, cert.getVerificationStatus()));
        resp.setLastVerifiedAt(cert.getLastVerifiedAt());
        resp.setVerifyApiSource(cert.getVerifyApiSource());
        resp.setOfficialVerifyUrl(cert.getOfficialVerifyUrl());
        resp.setSourceChannel(cert.getSourceChannel());
        resp.setECertIdent(cert.getECertIdent());
        resp.setRemark(cert.getRemark());
        resp.setCreateTime(cert.getCreateTime());
        resp.setUpdateTime(cert.getUpdateTime());
        return resp;
    }

    @Override
    public CertHashChainVerifyRespVO verifyChangeLogChain(Long certId) {
        // 规范出处：v3.3 §13（哈希链公式）、v3.4 §18.3（定根 ROOT）；等保 2.0 三级留痕防篡改。
        // 口径：逐行重算 + prev 链接核对，返回首处断链行（不是"整体 true/false"了事，要给定位依据）。
        CertHashChain.VerifyResult r = CertHashChain.verifyChain(certChangeLogMapper, certId);
        CertHashChainVerifyRespVO vo = new CertHashChainVerifyRespVO();
        vo.setOk(r.ok());
        vo.setChecked(r.checked());
        vo.setBrokenAtId(r.brokenAtId());
        vo.setReason(r.reason());
        vo.setExpected(r.expected());
        vo.setActual(r.actual());
        return vo;
    }

    @Override
    public PageResult<CertChangeLogRespVO> getChangeLogPage(CertChangeLogPageReqVO reqVO) {
        // 规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §18.2（变更记录只追加审计）、
        //   v3.3 §13（哈希链）；用户 2026-09-21 实测要求补"查看历史"的读出口。
        // 为什么是分页而不是全量：单条证照的留痕是**字段级**的（一次建档即写 4~5 行，
        //   多次更新会持续累积），全量下发会随使用时长无界增长——分页是唯一可长期成立的形态。
        PageResult<CertChangeLogDO> page = certChangeLogMapper.selectPage(reqVO);
        // 中文名解析：字典不可用时返回 null，前端回退显示 code（与证照域其它读接口同口径）
        Map<String, String> changeTypeLabels = certDictHelper.labelMap(CertDictTypes.CHANGE_TYPE);
        Map<String, String> opSourceLabels = certDictHelper.labelMap(CertDictTypes.OP_SOURCE);
        List<CertChangeLogRespVO> list = page.getList().stream().map(log -> {
            CertChangeLogRespVO vo = new CertChangeLogRespVO();
            vo.setId(log.getId());
            vo.setCertId(log.getCertId());
            vo.setFieldName(log.getFieldName());
            vo.setOldValue(log.getOldValue());
            vo.setNewValue(log.getNewValue());
            vo.setChangeType(log.getChangeType());
            vo.setChangeTypeName(changeTypeLabels.get(log.getChangeType()));
            vo.setOpSource(log.getOpSource());
            vo.setOpSourceName(opSourceLabels.get(log.getOpSource()));
            vo.setOpIp(log.getOpIp());
            vo.setBatchId(log.getBatchId());
            // 操作人取 creator（BaseDO 的审计字段；前端如需人名再查用户中心）
            vo.setOperator(log.getCreator());
            vo.setCreateTime(log.getCreateTime());
            vo.setRecordHash(log.getRecordHash());
            vo.setPrevHash(log.getPrevHash());
            return vo;
        }).toList();
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public CertificateImportRespVO importCertificates(List<CertificateImportExcelVO> rows, Long operatorId) {
        // 规范出处：五页改造契约 Q1（2026-09-28）——Excel 导入、行级回执（单个失败不影响其余）。
        // 为什么这样写：导入是"批量建档"的运维动作，逐行复用 createCertificate 判重/建日志逻辑，
        // 避免 Excel 模板与建档口径分叉；不回滚已成功行（前端凭回执定位失败行重导）。
        CertificateImportRespVO resp = new CertificateImportRespVO();
        List<CertificateImportFailureVO> failures = new ArrayList<>();
        int success = 0;
        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                CertificateImportExcelVO row = rows.get(i);
                int rowNum = i + 2; // Excel 第 1 行是表头，数据从第 2 行起
                try {
                    CertificateSaveReqVO req = new CertificateSaveReqVO();
                    req.setCertName(row.getCertName());
                    req.setCertNo(row.getCertNo());
                    req.setCertType(row.getCertType());
                    req.setHolderName(row.getHolderName());
                    req.setCertLevel(row.getCertLevel());
                    req.setCertScope(row.getCertScope());
                    req.setIssuingAuthority(row.getIssuingAuthority());
                    req.setIssuingAuthorityCode(row.getIssuingAuthorityCode());
                    req.setIssueDate(row.getIssueDate());
                    req.setValidFrom(row.getValidFrom());
                    req.setValidTo(row.getValidTo());
                    req.setRemark(row.getRemark());
                    createCertificate(req);
                    success++;
                } catch (Exception e) {
                    log.warn("[cert][import] 第{}行失败: {}", rowNum, e.getMessage());
                    CertificateImportFailureVO f = new CertificateImportFailureVO();
                    f.setRowNum(rowNum);
                    f.setMessage(e instanceof ServiceException se ? se.getMessage()
                            : "导入失败：" + e.getMessage());
                    failures.add(f);
                }
            }
        }
        resp.setSuccessCount(success);
        resp.setFailureCount(failures.size());
        // 前端已发布字段（ImportResp 读 totalRows/failCount，见 2026-09-28 对账）：
        // failCount 与 failureCount 同义都发；totalRows=数据行数（不含表头），前端用于"共 N 行，成功 X 失败 Y"。
        resp.setFailCount(failures.size());
        resp.setTotalRows(rows == null ? 0 : rows.size());
        resp.setFailures(failures);
        log.info("[cert][import] 完成：成功 {} 行，失败 {} 行，operator={}",
                success, failures.size(), operatorId);
        return resp;
    }

    // ========== 内部工具 ==========

    /**
     * 台账统一过滤条件（Q3 起 page 与 stats 共用）：保证"看板数 = 列表数"，不出现两套口径。
     *
     * <p>多选优先口径：{@code certStatuses}/{@code verificationStatuses} 非空时走 in(...)，否则回退单选 eq。
     * 两者同时生效会形成交集（业务上无意义），故明确以多选为准（契约 Q3：原接口只支持单个 code）。
     */
    /**
     * 取某字典类型的启用行：平台默认（租户 0）打底 → 当前租户覆盖同名 value。
     *
     * <p>与 {@code MetaDictServiceImpl#getDicts} 的合并口径保持一致，避免“下拉能选、筛选查不到”。
     * 覆盖键用 dict_value（同一 code 的租户行改的是文案/配色，不新增 code）。
     */
    private List<BiddingDictDataDO> listEnabledDict(String dictType) {
        Map<String, BiddingDictDataDO> merged = new LinkedHashMap<>();
        for (BiddingDictDataDO row : biddingDictDataMapper.selectEnabledByType(PLATFORM_TENANT_ID, dictType)) {
            merged.put(row.getDictValue(), row);
        }
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null && tenantId != PLATFORM_TENANT_ID) {
            for (BiddingDictDataDO row : biddingDictDataMapper.selectEnabledByType(tenantId, dictType)) {
                merged.put(row.getDictValue(), row);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private LambdaQueryWrapper<CertificateInfoDO> buildFilterQuery(String certNo, String certType, String certCategory,
            String certStatus, List<String> certStatuses, String verificationStatus,
            List<String> verificationStatuses, String certName, String holderName) {
        LambdaQueryWrapper<CertificateInfoDO> query = new LambdaQueryWrapper<>();
        query.eq(StringUtils.hasText(certNo), CertificateInfoDO::getCertNo, certNo);
        query.eq(StringUtils.hasText(certType), CertificateInfoDO::getCertType, certType);
        // 证照大类筛选（契约 R2）：大类不是台账列，需按字典 cert_type_category 展开成 certType in (...)。
        // 为什么查字典而不是写死映射：归口由运营在字典维护（新增/调整类型归属不需改代码发版）；
        // 与 certType 同传时形成交集（业务上等价于"该大类下的指定类型"）。
        if (StringUtils.hasText(certCategory)) {
            List<String> typesInCategory = listEnabledDict(DICT_TYPE_CERT_TYPE_CATEGORY).stream()
                    .filter(d -> certCategory.equals(d.getDictLabel()))
                    .map(BiddingDictDataDO::getDictValue)
                    .toList();
            if (typesInCategory.isEmpty()) {
                // 大类下无类型：返回空集（而非忽略条件全量返回，否则用户以为筛选没生效）
                query.eq(CertificateInfoDO::getId, -1L);
            } else {
                query.in(CertificateInfoDO::getCertType, typesInCategory);
            }
        }
        if (certStatuses != null && !certStatuses.isEmpty()) {
            query.in(CertificateInfoDO::getCertStatus, certStatuses);
        } else {
            query.eq(StringUtils.hasText(certStatus), CertificateInfoDO::getCertStatus, certStatus);
        }
        if (verificationStatuses != null && !verificationStatuses.isEmpty()) {
            query.in(CertificateInfoDO::getVerificationStatus, verificationStatuses);
        } else {
            query.eq(StringUtils.hasText(verificationStatus),
                    CertificateInfoDO::getVerificationStatus, verificationStatus);
        }
        query.like(StringUtils.hasText(certName), CertificateInfoDO::getCertName, certName);
        query.like(StringUtils.hasText(holderName), CertificateInfoDO::getHolderName, holderName);
        return query;
    }

    /** 可编辑字段集合（业务键 certNo/certType/holder 建档后不可改，v3.3 §二/§七） */
    private static final List<String> EDITABLE = List.of(
            "cert_name", "cert_level", "cert_scope", "issuing_authority", "issuing_authority_code",
            "issue_date", "valid_from", "valid_to", "remark");

    private void applyEditable(CertificateInfoDO cert, CertificateSaveReqVO reqVO) {
        cert.setCertName(reqVO.getCertName());
        cert.setCertLevel(reqVO.getCertLevel());
        cert.setCertScope(reqVO.getCertScope());
        cert.setIssuingAuthority(reqVO.getIssuingAuthority());
        cert.setIssuingAuthorityCode(reqVO.getIssuingAuthorityCode());
        cert.setIssueDate(reqVO.getIssueDate());
        cert.setValidFrom(reqVO.getValidFrom());
        cert.setValidTo(reqVO.getValidTo());
        cert.setRemark(reqVO.getRemark());
    }

    private List<Change> diffEditable(CertificateInfoDO db, CertificateSaveReqVO reqVO) {
        List<Change> diffs = new ArrayList<>();
        if (!eq(db.getCertName(), reqVO.getCertName())) diffs.add(new Change("cert_name", v(db.getCertName()), v(reqVO.getCertName())));
        if (!eq(db.getCertLevel(), reqVO.getCertLevel())) diffs.add(new Change("cert_level", v(db.getCertLevel()), v(reqVO.getCertLevel())));
        if (!eq(db.getCertScope(), reqVO.getCertScope())) diffs.add(new Change("cert_scope", v(db.getCertScope()), v(reqVO.getCertScope())));
        if (!eq(db.getIssuingAuthority(), reqVO.getIssuingAuthority())) diffs.add(new Change("issuing_authority", v(db.getIssuingAuthority()), v(reqVO.getIssuingAuthority())));
        if (!eq(db.getIssuingAuthorityCode(), reqVO.getIssuingAuthorityCode())) diffs.add(new Change("issuing_authority_code", v(db.getIssuingAuthorityCode()), v(reqVO.getIssuingAuthorityCode())));
        if (!eq(d(db.getIssueDate()), d(reqVO.getIssueDate()))) diffs.add(new Change("issue_date", d(db.getIssueDate()), d(reqVO.getIssueDate())));
        if (!eq(d(db.getValidFrom()), d(reqVO.getValidFrom()))) diffs.add(new Change("valid_from", d(db.getValidFrom()), d(reqVO.getValidFrom())));
        if (!eq(d(db.getValidTo()), d(reqVO.getValidTo()))) diffs.add(new Change("valid_to", d(db.getValidTo()), d(reqVO.getValidTo())));
        if (!eq(db.getRemark(), reqVO.getRemark())) diffs.add(new Change("remark", v(db.getRemark()), v(reqVO.getRemark())));
        return diffs;
    }

    /** 乐观锁预检：页面回传版本与库内不一致即为已被他人修改（§4.5），最终一致性仍由 updateById CAS 兜底。 */
    private void ensureVersionMatch(Integer reqVersion, CertificateInfoDO db) {
        if (reqVersion != null && !reqVersion.equals(db.getVersion())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_DATA_MODIFIED);
        }
    }

    /** 追加哈希链日志（与主表同事务；只追加，防重由 UK(cert_id, prev_hash, …) 链式保证 —— v3.3 §13、v3.4 §18.3-1）。 */
    private void appendChangeLog(Long certId, CertificateChangeTypeEnum type,
                                 CertificateChangeSourceEnum source, List<Change> changes) {
        appendChangeLog(certId, type, source == null ? CertificateChangeSourceEnum.MANUAL : source,
                null, null, changes);
    }

    private void appendChangeLog(Long certId, CertificateChangeTypeEnum type, CertificateChangeSourceEnum source,
                                 String opIp, List<Change> changes) {
        appendChangeLog(certId, type, source, opIp, null, changes);
    }

    private void appendChangeLog(Long certId, CertificateChangeTypeEnum type, CertificateChangeSourceEnum source,
                                 String opIp, String batchId, List<Change> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        String prev = certChangeLogMapper.selectLastRecordHash(certId);
        if (prev == null) {
            prev = HASH_CHAIN_ROOT;
        }
        String typeName = type.name();
        String sourceName = source == null ? CertificateChangeSourceEnum.MANUAL.name() : source.name();
        for (Change change : changes) {
            CertChangeLogDO log = new CertChangeLogDO();
            log.setCertId(certId);
            log.setFieldName(change.field);
            log.setOldValue(change.oldValue);
            log.setNewValue(change.newValue);
            log.setChangeType(typeName);
            log.setOpSource(sourceName);
            log.setOpIp(opIp);
            log.setBatchId(batchId);
            log.setPrevHash(prev);
            // 记录哈希：走 CertHashChain.recordHash **唯一实现**（v3.3 §13）。
            // 注意：null 归一已收口在 recordHash 内部，这里**不要再自行** opIp == null ? "" : opIp ——
            // 原先两处各归一一次正是"链永远验不过"的根因（2026-09-29 修正）。
            String recordHash = CertHashChain.recordHash(certId, change.field, change.oldValue, change.newValue,
                    typeName, sourceName, opIp, batchId, prev);
            log.setRecordHash(recordHash);
            certChangeLogMapper.insert(log);
            prev = recordHash;
        }
    }

    private static boolean eq(String a, String b) {
        return (a == null ? "" : a).equals(b == null ? "" : b);
    }

    private static String v(String value) {
        return value == null ? "" : value;
    }

    private static String d(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    private static String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 单字段变更（field：old → new），用于变更审计日志行（v3.3 §13）。 */
    private record Change(String field, String oldValue, String newValue) {
    }
}
