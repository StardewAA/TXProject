package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.EnterpriseQualificationSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationApplyPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationApplyRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationComplianceReviewDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationComplianceReviewMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.EnterpriseQualificationDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationApplyRecordDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.EnterpriseQualificationMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationApplyRecordMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.QualificationApplyStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.QualificationApplyTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.QualificationSerialEnum;
import cn.iocoder.txgy.module.bidding.enums.QualificationStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 企业资质主档/申报流服务实现（T 轨；延续申报与审批办结同事务编排）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-1/15-7（状态机、办结回写同事务+version 乐观锁、
 * binded_cert_id 边界）；《招投标模块开发规范》§4.5（状态流转接口化/乐观锁 CAS）。
 * <p>设计遵循/针对：① UK 判重防重复建档；② binded_cert_id 校验通用台账存在性（边界 §十五：影像/验真归
 * certificate_info）；③ 申报流只追加状态变更（状态机 + version CAS），延续办结回写主档 VALID+valid_to；
 * ④ 审批退回还原 VALID，临近到期由预警扫描（M-later QUAL_EXPIRY 并入）重新置 EXPIRING_SOON；
 * ⑤ 本模块当前版本申报仅支持延续 RENEW（其余类型走核查编排后再扩展，报 QUAL_APPLY_PARAM_INVALID）。
 */
@Slf4j
@Service
public class EnterpriseQualificationServiceImpl implements EnterpriseQualificationService {

    @Resource
    private EnterpriseQualificationMapper enterpriseQualificationMapper;
    @Resource
    private QualificationApplyRecordMapper qualificationApplyRecordMapper;
    @Resource
    private CertificateInfoMapper certificateInfoMapper;
    @Resource
    private QualificationComplianceReviewMapper qualificationComplianceReviewMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createQualification(EnterpriseQualificationSaveReqVO reqVO) {
        // UK 判重（15-1：tenant+serial+category+grade+cert_no；建档幂等，规范 §4.5）
        if (enterpriseQualificationMapper.selectByCertNo(reqVO.getEntId(), reqVO.getQualSerial(),
                reqVO.getQualCategory(), reqVO.getQualGrade(), reqVO.getCertNo()) != null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_NO_DUPLICATE);
        }
        // 关联台账证书存在性校验（§十五 边界：影像/验真复用 certificate_info）
        if (reqVO.getBindedCertId() != null
                && certificateInfoMapper.selectById(reqVO.getBindedCertId()) == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_NOT_EXISTS);
        }
        EnterpriseQualificationDO qual = new EnterpriseQualificationDO();
        qual.setEntId(reqVO.getEntId());
        qual.setQualSerial(validateSerial(reqVO.getQualSerial()));
        qual.setQualCategory(reqVO.getQualCategory());
        qual.setQualGrade(reqVO.getQualGrade());
        qual.setCertNo(reqVO.getCertNo());
        qual.setIssuingAuthority(reqVO.getIssuingAuthority());
        qual.setIssueDate(reqVO.getIssueDate());
        qual.setValidTo(reqVO.getValidTo());
        qual.setApprovalScope(reqVO.getApprovalScope());
        qual.setQualStatus(QualificationStatusEnum.VALID.name());
        qual.setBindedCertId(reqVO.getBindedCertId());
        qual.setSourceChannel("MANUAL");
        qual.setRemark(reqVO.getRemark());
        enterpriseQualificationMapper.insert(qual);
        log.info("[qual][create] ent={} qual={} 建档完成 id={}", reqVO.getEntId(), reqVO.getCertNo(), qual.getId());
        return qual.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long qualificationId, String targetStatus, Integer version, String reason,
                             Long operatorId) {
        EnterpriseQualificationDO qual = loadQualification(qualificationId);
        ensureVersion(version, qual.getVersion());
        QualificationStatusEnum from = parseQualStatus(qual.getQualStatus());
        QualificationStatusEnum to;
        try {
            to = QualificationStatusEnum.valueOf(targetStatus);
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_STATUS_TRANSITION_INVALID);
        }
        if (!QualificationStatusEnum.isTransitionAllowed(from, to)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_STATUS_TRANSITION_INVALID);
        }
        qual.setQualStatus(to.name());
        if (StringUtils.hasText(reason)) {
            qual.setRemark(appendReason(qual.getRemark(), reason));
        }
        if (enterpriseQualificationMapper.updateById(qual) == 0) { // @Version CAS
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_DATA_MODIFIED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitRenewalApply(Long qualificationId, Integer version, Long ownerBy, LocalDate dueDate,
                                   String remark) {
        EnterpriseQualificationDO qual = loadQualification(qualificationId);
        ensureVersion(version, qual.getVersion());
        QualificationStatusEnum from = parseQualStatus(qual.getQualStatus());
        if (!QualificationStatusEnum.isTransitionAllowed(from, QualificationStatusEnum.PENDING_RENEW)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_STATUS_TRANSITION_INVALID);
        }
        // 申报流 SUBMITTED（同事务）
        QualificationApplyRecordDO apply = new QualificationApplyRecordDO();
        apply.setEntId(qual.getEntId());
        apply.setQualId(qualificationId);
        apply.setApplyType(QualificationApplyTypeEnum.RENEW.name());
        apply.setSubmitDate(LocalDate.now());
        apply.setDueDate(dueDate);
        apply.setStatus(QualificationApplyStatusEnum.SUBMITTED.name());
        apply.setOwnerBy(ownerBy);
        apply.setRemark(remark);
        qualificationApplyRecordMapper.insert(apply);
        // 主档 → PENDING_RENEW（待续期，15-7）
        qual.setQualStatus(QualificationStatusEnum.PENDING_RENEW.name());
        if (enterpriseQualificationMapper.updateById(qual) == 0) { // @Version CAS
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_DATA_MODIFIED);
        }
        return apply.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long approveRenewalApply(Long applyId, Integer version, String resultNo, String newCertNo,
                                    LocalDate newValidTo, Long reviewerId) {
        QualificationApplyRecordDO apply = loadApply(applyId);
        ensureVersion(version, apply.getVersion());
        if (!QualificationApplyTypeEnum.RENEW.name().equals(apply.getApplyType())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUAL_APPLY_PARAM_INVALID);
        }
        QualificationApplyStatusEnum from = parseApplyStatus(apply.getStatus());
        if (!QualificationApplyStatusEnum.isTransitionAllowed(from, QualificationApplyStatusEnum.APPROVED)
                && !QualificationApplyStatusEnum.isTransitionAllowed(from, QualificationApplyStatusEnum.ISSUED)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUAL_APPLY_STATUS_INVALID);
        }
        if (newValidTo == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUAL_APPLY_PARAM_INVALID);
        }
        // 申报 → APPROVED（发文）→ ISSUED（领证）
        apply.setStatus(QualificationApplyStatusEnum.APPROVED.name());
        apply.setResultNo(resultNo);
        apply.setNewCertNo(newCertNo);
        apply.setNewValidTo(newValidTo);
        apply.setUpdater(String.valueOf(reviewerId));
        qualificationApplyRecordMapper.updateById(apply);
        apply.setStatus(QualificationApplyStatusEnum.ISSUED.name());
        qualificationApplyRecordMapper.updateById(apply);

        // 办结回写主档（15-7：同事务 + version 乐观锁）
        EnterpriseQualificationDO qual = loadQualification(apply.getQualId());
        qual.setQualStatus(QualificationStatusEnum.VALID.name());
        qual.setValidTo(newValidTo);
        if (StringUtils.hasText(newCertNo)) {
            qual.setCertNo(newCertNo);
        }
        if (enterpriseQualificationMapper.updateById(qual) == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_DATA_MODIFIED);
        }
        return apply.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long rejectRenewalApply(Long applyId, Integer version, Long reviewerId, String remark) {
        QualificationApplyRecordDO apply = loadApply(applyId);
        ensureVersion(version, apply.getVersion());
        QualificationApplyStatusEnum from = parseApplyStatus(apply.getStatus());
        if (!QualificationApplyStatusEnum.isTransitionAllowed(from, QualificationApplyStatusEnum.REJECTED)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUAL_APPLY_STATUS_INVALID);
        }
        apply.setStatus(QualificationApplyStatusEnum.REJECTED.name());
        apply.setUpdater(String.valueOf(reviewerId));
        if (StringUtils.hasText(remark)) {
            apply.setRemark(remark);
        }
        qualificationApplyRecordMapper.updateById(apply);
        // 主档 PENDING_RENEW → VALID 还原（15-1 联动；临近到期由预警扫描重新置 EXPIRING_SOON）
        EnterpriseQualificationDO qual = loadQualification(apply.getQualId());
        if (QualificationStatusEnum.PENDING_RENEW.name().equals(qual.getQualStatus())) {
            qual.setQualStatus(QualificationStatusEnum.VALID.name());
            if (enterpriseQualificationMapper.updateById(qual) == 0) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_DATA_MODIFIED);
            }
        }
        return apply.getId();
    }

    // ========== 内部工具 ==========

    // ==================== 查询：资质台账 / 详情 / 申报单（页签 1、2） ====================

    @Override
    public PageResult<QualificationRespVO> getQualificationPage(QualificationPageReqVO reqVO) {
        // 规范出处：契约《企业资质页-页面级契约-v1.0.md》§4 G1；《招投标模块开发规范》§4.1（分页口径）。
        // 为什么这样写：① 服务层兜底 entId 非空——只靠控制器 @Valid 的话，直接调用服务方会退化成查全租户数据；
        //   ② 筛选条件全部下推 SQL（非内存过滤），否则分页 total 会失真；
        //   ③ expiringInDays = "有效期内且距到期 ≤ N 天"（已过期不含，过期查询走 qualStatus=EXPIRED），
        //      这样"即将到期"快捷筛选与预警扫描口径一致；
        //   ④ 排序"有有效期者按到期先后、长期(null)置末"：valid_to IS NULL 参与排序，避免长期资质把
        //      即将到期的行挤到后面的分页里。
        // 业务目标：台账列表一次拿全渲染与按钮态所需字段，且分页与筛选口径和后端预警保持同一套规则。
        if (reqVO.getEntId() == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_QUERY_ENT_REQUIRED);
        }
        LambdaQueryWrapper<EnterpriseQualificationDO> query = new LambdaQueryWrapper<>();
        query.eq(EnterpriseQualificationDO::getEntId, reqVO.getEntId());
        query.eq(StringUtils.hasText(reqVO.getQualSerial()), EnterpriseQualificationDO::getQualSerial, reqVO.getQualSerial());
        query.eq(StringUtils.hasText(reqVO.getQualCategory()), EnterpriseQualificationDO::getQualCategory, reqVO.getQualCategory());
        query.eq(StringUtils.hasText(reqVO.getQualGrade()), EnterpriseQualificationDO::getQualGrade, reqVO.getQualGrade());
        query.eq(StringUtils.hasText(reqVO.getQualStatus()), EnterpriseQualificationDO::getQualStatus, reqVO.getQualStatus());
        if (reqVO.getExpiringInDays() != null) {
            LocalDate today = LocalDate.now();
            query.isNotNull(EnterpriseQualificationDO::getValidTo);
            query.ge(EnterpriseQualificationDO::getValidTo, today);
            query.le(EnterpriseQualificationDO::getValidTo, today.plusDays(reqVO.getExpiringInDays()));
        }
        if (StringUtils.hasText(reqVO.getKeyword())) {
            String keyword = reqVO.getKeyword().trim();
            query.and(w -> w.like(EnterpriseQualificationDO::getCertNo, keyword)
                    .or().like(EnterpriseQualificationDO::getIssuingAuthority, keyword));
        }
        query.last("ORDER BY (valid_to IS NULL), valid_to ASC, id DESC");
        Page<EnterpriseQualificationDO> page = enterpriseQualificationMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), query);
        // 批量查"在途申报"（一次 in 查询），避免逐行查询的 N+1
        Set<Long> pendingQualIds = selectPendingQualIds(page.getRecords().stream()
                .map(EnterpriseQualificationDO::getId).collect(Collectors.toList()));
        LocalDate today = LocalDate.now();
        List<QualificationRespVO> list = page.getRecords().stream()
                .map(qual -> toRespVO(qual, today, pendingQualIds.contains(qual.getId())))
                .collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public QualificationDetailRespVO getQualification(Long id) {
        // 规范出处：契约 §4 G2；《电子证件全流程管理_数据库设计.md》v3.3 §十五（binded_cert_id 边界）。
        // 为什么这样写：① 主档不存在即报 QUALIFICATION_NOT_EXISTS（1090109015），不返回空壳对象，
        //   免得前端把"没查到"渲染成"数据为空"；② binded_cert_id 允许为空（资质可只登记文字信息），
        //   证照被删也不报错，摘要字段留空由前端走空态；③ 核查记录取 reviewed_at 最新一条并带出 stdVersion，
        //   使"达标结论"可追溯到依据的标准版本，否则不能作为投标资格审核证据。
        // 业务目标：资质详情一屏拿全"主档 + 影像来源 + 核查依据"。
        EnterpriseQualificationDO qual = loadQualification(id);
        LocalDate today = LocalDate.now();
        QualificationDetailRespVO vo = new QualificationDetailRespVO();
        fillRespVO(vo, qual, today,
                selectPendingQualIds(Collections.singletonList(qual.getId())).contains(qual.getId()));
        if (qual.getBindedCertId() != null) {
            CertificateInfoDO cert = certificateInfoMapper.selectById(qual.getBindedCertId());
            if (cert != null) {
                vo.setBindedCertName(cert.getCertName());
                vo.setBindedCertNo(cert.getCertNo());
                vo.setBindedCertStatus(cert.getCertStatus());
            }
        }
        List<QualificationComplianceReviewDO> reviews = qualificationComplianceReviewMapper.selectList(
                new LambdaQueryWrapper<QualificationComplianceReviewDO>()
                        .eq(QualificationComplianceReviewDO::getQualId, id)
                        .orderByDesc(QualificationComplianceReviewDO::getReviewedAt)
                        .last("LIMIT 1"));
        if (!reviews.isEmpty()) {
            QualificationComplianceReviewDO review = reviews.get(0);
            QualificationDetailRespVO.LatestReview latest = new QualificationDetailRespVO.LatestReview();
            latest.setStdVersion(review.getStdVersion());
            latest.setReviewResult(review.getReviewResult());
            latest.setReviewedAt(review.getReviewedAt());
            latest.setDiffItems(review.getDiffItems());
            vo.setLatestReview(latest);
        }
        return vo;
    }

    @Override
    public PageResult<QualificationApplyRespVO> getApplyPage(QualificationApplyPageReqVO reqVO) {
        // 规范出处：契约 §4 G3；表 15-7（申报流 8 态：DRAFT/SUBMITTED/ACCEPTED/REVIEWING/APPROVED/PUBLISHED/ISSUED/REJECTED）。
        // 为什么这样写：① entId 兜底同上（跨企业隔离）；② 状态筛选可空——页面按状态分泳道各查一次，
        //   "全部"视图不传；③ 排序 submitDate 倒序（再按 id 倒序）保证同日提交的顺序稳定，翻页不重不漏。
        // 业务目标：申报进度可见、审批动作可直接发起（出参带 version）。
        if (reqVO.getEntId() == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_QUERY_ENT_REQUIRED);
        }
        LambdaQueryWrapper<QualificationApplyRecordDO> query = new LambdaQueryWrapper<>();
        query.eq(QualificationApplyRecordDO::getEntId, reqVO.getEntId());
        query.eq(reqVO.getQualId() != null, QualificationApplyRecordDO::getQualId, reqVO.getQualId());
        query.eq(StringUtils.hasText(reqVO.getApplyType()), QualificationApplyRecordDO::getApplyType, reqVO.getApplyType());
        query.eq(StringUtils.hasText(reqVO.getStatus()), QualificationApplyRecordDO::getStatus, reqVO.getStatus());
        query.orderByDesc(QualificationApplyRecordDO::getSubmitDate).orderByDesc(QualificationApplyRecordDO::getId);
        Page<QualificationApplyRecordDO> page = qualificationApplyRecordMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), query);
        List<QualificationApplyRespVO> list = page.getRecords().stream()
                .map(this::toApplyRespVO).collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal());
    }

    // ==================== 查询私有工具 ====================

    /** 台账行 → 出参：剩余天数与"在途申报"标记由后端统一判定，避免各页面各算一套。 */
    private QualificationRespVO toRespVO(EnterpriseQualificationDO qual, LocalDate today, boolean hasPendingApply) {
        QualificationRespVO vo = new QualificationRespVO();
        fillRespVO(vo, qual, today, hasPendingApply);
        return vo;
    }

    private void fillRespVO(QualificationRespVO vo, EnterpriseQualificationDO qual, LocalDate today,
                            boolean hasPendingApply) {
        vo.setId(qual.getId());
        vo.setEntId(qual.getEntId());
        vo.setQualSerial(qual.getQualSerial());
        vo.setQualCategory(qual.getQualCategory());
        vo.setQualGrade(qual.getQualGrade());
        vo.setCertNo(qual.getCertNo());
        vo.setIssuingAuthority(qual.getIssuingAuthority());
        vo.setIssueDate(qual.getIssueDate());
        vo.setValidTo(qual.getValidTo());
        vo.setQualStatus(qual.getQualStatus());
        // 长期（valid_to 为空）返回 null，前端显示"长期"，不要算成 0 天
        vo.setDaysToExpire(qual.getValidTo() == null ? null
                : (int) ChronoUnit.DAYS.between(today, qual.getValidTo()));
        vo.setBindedCertId(qual.getBindedCertId());
        vo.setHasPendingApply(hasPendingApply);
        vo.setSourceChannel(qual.getSourceChannel());
        vo.setRemark(qual.getRemark());
        vo.setVersion(qual.getVersion());
    }

    private QualificationApplyRespVO toApplyRespVO(QualificationApplyRecordDO apply) {
        QualificationApplyRespVO vo = new QualificationApplyRespVO();
        vo.setId(apply.getId());
        vo.setEntId(apply.getEntId());
        vo.setQualId(apply.getQualId());
        vo.setApplyType(apply.getApplyType());
        vo.setTargetGrade(apply.getTargetGrade());
        vo.setSubmitDate(apply.getSubmitDate());
        vo.setDueDate(apply.getDueDate());
        vo.setStatus(apply.getStatus());
        vo.setResultNo(apply.getResultNo());
        vo.setNewCertNo(apply.getNewCertNo());
        vo.setNewValidTo(apply.getNewValidTo());
        vo.setOwnerBy(apply.getOwnerBy());
        vo.setRemark(apply.getRemark());
        vo.setVersion(apply.getVersion());
        return vo;
    }

    /**
     * 批量查询"在途申报"的资质 id（用于列表按钮态）。
     *
     * <p>为什么这样写：在途 = 状态不属于 DRAFT（还没提交）/ ISSUED（已办结）/ REJECTED（已退回）。
     * 用"排除终态"而不是"枚举 SUBMITTED"——申报流有 8 态（含受理/审核/公示），
     * 若只认 SUBMITTED，受理后就误判为"可再次申报"，会重复提交。
     */
    private Set<Long> selectPendingQualIds(List<Long> qualIds) {
        if (qualIds == null || qualIds.isEmpty()) {
            return Collections.emptySet();
        }
        return qualificationApplyRecordMapper.selectList(new LambdaQueryWrapper<QualificationApplyRecordDO>()
                        .in(QualificationApplyRecordDO::getQualId, qualIds)
                        .notIn(QualificationApplyRecordDO::getStatus, PENDING_EXCLUDED_STATUS))
                .stream().map(QualificationApplyRecordDO::getQualId).collect(Collectors.toSet());
    }

    /** 不算"在途"的申报状态：草稿（未提交）、已办结、已退回。 */
    private static final List<String> PENDING_EXCLUDED_STATUS = List.of(
            QualificationApplyStatusEnum.DRAFT.name(),
            QualificationApplyStatusEnum.ISSUED.name(),
            QualificationApplyStatusEnum.REJECTED.name());

    private EnterpriseQualificationDO loadQualification(Long qualificationId) {
        EnterpriseQualificationDO qual = enterpriseQualificationMapper.selectById(qualificationId);
        if (qual == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_NOT_EXISTS);
        }
        return qual;
    }

    private QualificationApplyRecordDO loadApply(Long applyId) {
        QualificationApplyRecordDO apply = qualificationApplyRecordMapper.selectById(applyId);
        if (apply == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUAL_APPLY_NOT_EXISTS);
        }
        return apply;
    }

    private void ensureVersion(Integer clientVersion, Integer currentVersion) {
        if (clientVersion == null || !Objects.equals(clientVersion, currentVersion)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_DATA_MODIFIED);
        }
    }

    private String validateSerial(String qualSerial) {
        try {
            return QualificationSerialEnum.valueOf(qualSerial).name();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_STATUS_TRANSITION_INVALID);
        }
    }

    private QualificationStatusEnum parseQualStatus(String status) {
        try {
            return QualificationStatusEnum.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_STATUS_TRANSITION_INVALID);
        }
    }

    private QualificationApplyStatusEnum parseApplyStatus(String status) {
        try {
            return QualificationApplyStatusEnum.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUAL_APPLY_STATUS_INVALID);
        }
    }

    private String appendReason(String current, String reason) {
        return current == null || current.isBlank() ? reason : current + "；" + reason;
    }
}
