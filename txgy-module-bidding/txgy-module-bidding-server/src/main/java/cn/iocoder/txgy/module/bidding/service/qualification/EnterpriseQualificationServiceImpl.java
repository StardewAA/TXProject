package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.EnterpriseQualificationSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationApplyPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationApplyRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationExportVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationImportExcelVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationImportFailureVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationImportRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationStatsRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationAlertPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationAlertRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationAssetRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationElementPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationPerformanceRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationPermitPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationPermitRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationReviewPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationReviewRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationStaffRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationStandardOptionsRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationStandardPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationStandardRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertAlertRecordDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationAssetDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationComplianceReviewDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationPerformanceDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationSafetyPermitDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationStaffConfigDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationStandardDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertAlertRecordMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationAssetMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationPerformanceMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationSafetyPermitMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationStaffConfigMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationStandardMapper;
import cn.iocoder.txgy.module.bidding.enums.CertificateAlertTypeEnum;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    @Resource
    private QualificationStandardMapper qualificationStandardMapper;
    @Resource
    private QualificationStaffConfigMapper qualificationStaffConfigMapper;
    @Resource
    private QualificationPerformanceMapper qualificationPerformanceMapper;
    @Resource
    private QualificationAssetMapper qualificationAssetMapper;
    @Resource
    private QualificationSafetyPermitMapper qualificationSafetyPermitMapper;
    @Resource
    private CertAlertRecordMapper certAlertRecordMapper;
    @Resource
    private cn.iocoder.txgy.module.bidding.service.subject.SubjectProfileService subjectProfileService;
    @Resource
    private cn.iocoder.txgy.module.bidding.service.review.ReviewTaskService reviewTaskService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createQualification(EnterpriseQualificationSaveReqVO reqVO) {
        // 规范出处：《后端变更通知-给前端》§12 —— "我是谁"类 id 由后端推导（一个租户只有一家公司）。
        // 为什么这样写：entId 是"本企业主体"，前端不必传；缺省取本租户主体档案 id（与证照建档 org_id 同一入口）。
        if (reqVO.getEntId() == null) { reqVO.setEntId(subjectProfileService.getOwnSubjectId()); }
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
        // 规范出处：契约《平台审查任务中心》§5"触发"——企业资质建档成功后生成 biz_type=QUALIFICATION 任务。
        // 与证照建档同一口径：同一事务、建档失败不产生任务。
        reviewTaskService.createTask(cn.iocoder.txgy.module.bidding.enums.ReviewBizTypeEnum.QUALIFICATION.name(),
                qual.getId(), qual.getEntId(), null, null, qual.getCertNo(), reqVO.getRemark());
        log.info("[qual][create] ent={} qual={} 建档完成 id={}", reqVO.getEntId(), reqVO.getCertNo(), qual.getId());
        return qual.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long updateQualification(EnterpriseQualificationSaveReqVO reqVO) {
        // 规范出处：契约《企业资质页-页面级契约-v1.0.md》§4 G2（详情/编辑独立页，内联编辑）；
        //   《招投标模块开发规范》§4.5（乐观锁 CAS）、§5 三-7（code 存储）。
        // 为什么这样写：① id/version 在此**手动判空**——EnterpriseQualificationSaveReqVO 是为"建档"设计的，
        //   其 id/version 注释虽写"更新必填"却**没有 @NotNull**（建档时本就不该有），故不能靠注解兜底；
        //   且 version 缺失若直接丢给 ensureVersion，会报"已被他人修改，请刷新"——语义误导（实际是没传），
        //   故与 id 一并归入 QUALIFICATION_PARAM_INVALID；
        //   ② ensureVersion 前置校验 + updateById 的 @Version CAS 双保险：前者给前端明确错误，
        //   后者封住"校验通过到写入之间"的并发窗口；
        //   ③ UK 判重**排除自身**（selectByCertNoExcludeId）——UK 四字段多数原样回传，不排除会把自己
        //   判成重复，让"只改备注"的正常保存失败；
        //   ④ qualStatus **不进入参**：状态归状态机（changeStatus）与延续申报流管理，客户端不得直写；
        //   ⑤ valid_to 变更后按下列口径重算状态，否则"过期资质延期后仍显示已过期"且永不自愈
        //   ——到期扫描对已过期记录是 days <= 0 → continue 直接跳过，不会把 EXPIRED 改回 VALID。
        // 业务目标：详情页能就地改正录错的有效期/编号/机关，状态与有效期始终自洽，并发编辑不互相覆盖。
        if (reqVO.getId() == null || reqVO.getVersion() == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_PARAM_INVALID);
        }
        EnterpriseQualificationDO qual = loadQualification(reqVO.getId());
        ensureVersion(reqVO.getVersion(), qual.getVersion());
        // UK 判重（ent 沿用库内主体，不允许经本接口改挂主体——改挂属于新建档后的数据治理动作）
        if (enterpriseQualificationMapper.selectByCertNoExcludeId(qual.getId(), qual.getEntId(),
                reqVO.getQualSerial(), reqVO.getQualCategory(), reqVO.getQualGrade(), reqVO.getCertNo()) != null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_NO_DUPLICATE);
        }
        // 关联台账证书存在性校验（与建档同口径；未传则保留原绑定，不支持经本接口解绑）
        if (reqVO.getBindedCertId() != null) {
            if (certificateInfoMapper.selectById(reqVO.getBindedCertId()) == null) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_NOT_EXISTS);
            }
            qual.setBindedCertId(reqVO.getBindedCertId());
        }
        // 可编辑字段（entId / sourceChannel 不在可编辑集内，保持库内原值）
        qual.setQualSerial(validateSerial(reqVO.getQualSerial()));
        qual.setQualCategory(reqVO.getQualCategory());
        qual.setQualGrade(reqVO.getQualGrade());
        qual.setCertNo(reqVO.getCertNo());
        qual.setIssuingAuthority(reqVO.getIssuingAuthority());
        qual.setIssueDate(reqVO.getIssueDate());
        qual.setValidTo(reqVO.getValidTo());
        qual.setApprovalScope(reqVO.getApprovalScope());
        qual.setRemark(reqVO.getRemark());
        // 状态重算：只动"日期推导态"，业务态原样保留（见方法注释）
        qual.setQualStatus(resolveStatusAfterUpdate(qual.getQualStatus(), reqVO.getValidTo()));
        if (enterpriseQualificationMapper.updateById(qual) == 0) { // @Version CAS
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_DATA_MODIFIED);
        }
        // TODO(审查任务联动)：建档走 reviewTaskService.createTask(biz_type=QUALIFICATION)，本接口改动了
        //  certNo/序列/类别/等级等审查依据字段却**不更新已有审查任务**，平台审查中心看到的仍是建档时快照。
        //  契约口径待定（重开任务 or 作废旧任务再建），接入审批流时一并处理，见《企业资质页-页面级契约》§4 G2。
        // TODO(状态流转出口)：EnterpriseQualificationService#changeStatus 目前**无 Controller 出口**
        //  （证照/人员证书/安许证都有 /status 端点，唯独资质主档没有），故 EXPIRED 业务上几乎不可达。
        //  本方法的状态重算口径（哪些态可被日期改写）须与之对齐，补出口时勿改口径。
        log.info("[qual][update] id={} ent={} certNo={} status={} validTo={}", qual.getId(), qual.getEntId(),
                qual.getCertNo(), qual.getQualStatus(), qual.getValidTo());
        return qual.getId();
    }

    @Override
    public QualificationStatsRespVO getQualificationStats(Long entId) {
        // 规范出处：五页改造缺接口契约 Q5（2026-09-18）。口径与 page 同源：同 entId 同 deleted（逻辑删自动过滤），
        // 保证看板数字与列表条数一致（验收 §7.2）；状态码取 QualificationStatusEnum 字面值，不硬编码数字。
        LambdaQueryWrapper<EnterpriseQualificationDO> base = new LambdaQueryWrapper<EnterpriseQualificationDO>()
                .eq(EnterpriseQualificationDO::getEntId, entId);
        long total = enterpriseQualificationMapper.selectCount(base);
        long valid = enterpriseQualificationMapper.selectCount(new LambdaQueryWrapper<EnterpriseQualificationDO>()
                .eq(EnterpriseQualificationDO::getEntId, entId)
                .eq(EnterpriseQualificationDO::getQualStatus, QualificationStatusEnum.VALID.name()));
        long expiring = enterpriseQualificationMapper.selectCount(new LambdaQueryWrapper<EnterpriseQualificationDO>()
                .eq(EnterpriseQualificationDO::getEntId, entId)
                .eq(EnterpriseQualificationDO::getQualStatus, QualificationStatusEnum.EXPIRING_SOON.name()));
        long expired = enterpriseQualificationMapper.selectCount(new LambdaQueryWrapper<EnterpriseQualificationDO>()
                .eq(EnterpriseQualificationDO::getEntId, entId)
                .eq(EnterpriseQualificationDO::getQualStatus, QualificationStatusEnum.EXPIRED.name()));
        // 未关联人员口径（Q5b 裁定）：本主体下 qualification_staff_config 中没有任何人员配置行的资质数。
        // 先取"已有人配置"的资质 id 集合（按 ent 过滤防跨企业串数），再取补集计数。
        List<Object> staffedQualIdObjs = qualificationStaffConfigMapper.selectObjs(
                new LambdaQueryWrapper<QualificationStaffConfigDO>()
                        .select(QualificationStaffConfigDO::getQualId)
                        .eq(QualificationStaffConfigDO::getEntId, entId)
                        .isNotNull(QualificationStaffConfigDO::getQualId)
                        .groupBy(QualificationStaffConfigDO::getQualId));
        long unlinkedStaff = total;
        if (!staffedQualIdObjs.isEmpty()) {
            List<Long> staffedQualIds = staffedQualIdObjs.stream()
                    .map(o -> Long.valueOf(String.valueOf(o))).collect(Collectors.toList());
            unlinkedStaff = enterpriseQualificationMapper.selectCount(new LambdaQueryWrapper<EnterpriseQualificationDO>()
                    .eq(EnterpriseQualificationDO::getEntId, entId)
                    .notIn(EnterpriseQualificationDO::getId, staffedQualIds));
        }
        // 前端已发布契约字段 byStatus（api/bidding/qualification/index.ts 的 Stats）：六态逐码计数、缺失补 0。
        // 为什么逐个 count 而不是另写 SQL 分组：与上面 valid/expiring/expired 三次计数同源同口径
        // （同 mapper、同租户过滤、同 entId），不引入第二套统计路径；状态码遍历枚举，新增状态自动纳入。
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (QualificationStatusEnum status : QualificationStatusEnum.values()) {
            byStatus.put(status.name(), enterpriseQualificationMapper.selectCount(
                    new LambdaQueryWrapper<EnterpriseQualificationDO>()
                            .eq(EnterpriseQualificationDO::getEntId, entId)
                            .eq(EnterpriseQualificationDO::getQualStatus, status.name())));
        }
        QualificationStatsRespVO resp = new QualificationStatsRespVO();
        resp.setTotal(total);
        resp.setValid(valid);
        resp.setExpiring(expiring);
        resp.setExpired(expired);
        resp.setUnlinkedStaff(unlinkedStaff);
        resp.setByStatus(byStatus);
        log.info("[qual][stats] ent={} total={} valid={} expiring={} expired={} unlinkedStaff={}",
                entId, total, valid, expiring, expired, unlinkedStaff);
        return resp;
    }

    @Override
    public QualificationImportRespVO importQualifications(List<QualificationImportExcelVO> rows, Long operatorId) {
        // 规范出处：五页改造契约 Q6（2026-09-28）——资质 Excel 导入、行级回执（单个失败不影响其余）。
        // 为什么这样写：逐行复用 createQualification（UK 判重/状态默认/enId 推导同一套口径），
        // 避免 Excel 模板与建档口径分叉；不回滚已成功行（前端凭回执定位失败行重导）。
        QualificationImportRespVO resp = new QualificationImportRespVO();
        List<QualificationImportFailureVO> failures = new ArrayList<>();
        int success = 0;
        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                QualificationImportExcelVO row = rows.get(i);
                int rowNum = i + 2; // Excel 第 1 行是表头，数据从第 2 行起
                try {
                    EnterpriseQualificationSaveReqVO req = new EnterpriseQualificationSaveReqVO();
                    req.setQualSerial(row.getQualSerial());
                    req.setQualCategory(row.getQualCategory());
                    req.setQualGrade(row.getQualGrade());
                    req.setCertNo(row.getCertNo());
                    req.setIssuingAuthority(row.getIssuingAuthority());
                    req.setIssueDate(row.getIssueDate());
                    req.setValidTo(row.getValidTo());
                    req.setApprovalScope(row.getApprovalScope());
                    req.setRemark(row.getRemark());
                    createQualification(req);
                    success++;
                } catch (Exception e) {
                    log.warn("[qual][import] 第{}行失败: {}", rowNum, e.getMessage());
                    QualificationImportFailureVO f = new QualificationImportFailureVO();
                    f.setRowNum(rowNum);
                    f.setMessage(e instanceof ServiceException se ? se.getMessage()
                            : "导入失败：" + e.getMessage());
                    failures.add(f);
                }
            }
        }
        resp.setSuccessCount(success);
        resp.setFailureCount(failures.size());
        // 前端已发布字段（ImportResp 读 totalRows/failCount，见 2026-09-28 对账）：两个都发，前端不用改。
        resp.setFailCount(failures.size());
        resp.setTotalRows(rows == null ? 0 : rows.size());
        resp.setFailures(failures);
        log.info("[qual][import] 完成：成功 {} 行，失败 {} 行，operator={}",
                success, failures.size(), operatorId);
        return resp;
    }

    @Override
    public List<QualificationExportVO> exportQualifications(QualificationPageReqVO reqVO) {
        // 规范出处：契约 Q6（2026-09-28）——导出范围=当前筛选（与 page 同过滤口径，不做分页截断）。
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
        List<EnterpriseQualificationDO> rows = enterpriseQualificationMapper.selectList(query);
        // 合同 Q8 同款：批量统计要素台账人员数，避免逐行 N+1
        Map<Long, Long> staffCounts = rows.isEmpty() ? Map.of()
                : qualificationStaffConfigMapper.selectList(new LambdaQueryWrapper<QualificationStaffConfigDO>()
                        .in(QualificationStaffConfigDO::getQualId, rows.stream()
                                .map(EnterpriseQualificationDO::getId).collect(Collectors.toList())))
                .stream().collect(Collectors.groupingBy(QualificationStaffConfigDO::getQualId, Collectors.counting()));
        List<QualificationExportVO> result = new ArrayList<>();
        for (EnterpriseQualificationDO row : rows) {
            QualificationExportVO out = new QualificationExportVO();
            out.setId(row.getId());
            out.setQualSerial(row.getQualSerial());
            out.setQualCategory(row.getQualCategory());
            out.setQualGrade(row.getQualGrade());
            out.setCertNo(row.getCertNo());
            out.setIssuingAuthority(row.getIssuingAuthority());
            out.setIssueDate(row.getIssueDate());
            out.setValidTo(row.getValidTo());
            out.setQualStatus(row.getQualStatus());
            out.setStaffCount(staffCounts.getOrDefault(row.getId(), 0L));
            result.add(out);
        }
        return result;
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

    // ==================== 查询：资质标准（页签 3） ====================

    @Override
    public PageResult<QualificationStandardRespVO> getStandardPage(QualificationStandardPageReqVO reqVO) {
        // 规范出处：契约 §4 G4；表 15-2；《02-法规标准与核验渠道.md》line 37（不得静态固化）。
        // 为什么这样写：① 筛选项全可空——标准库是"查依据"的字典式页面，默认看全量；
        //   ② onlyEffective 用 effective_from/effective_to 判定，而不是把"当前版本"写进代码或枚举，
        //      资质改革换版时只改数据；③ 版本倒序 + 序列升序，让"最新依据"排在首行便于核对。
        // 业务目标：达标核查的判定依据可查、可按版本追溯，且换版零代码改动。
        LambdaQueryWrapper<QualificationStandardDO> query = new LambdaQueryWrapper<>();
        query.eq(StringUtils.hasText(reqVO.getQualSerial()), QualificationStandardDO::getQualSerial, reqVO.getQualSerial());
        query.eq(StringUtils.hasText(reqVO.getQualCategory()), QualificationStandardDO::getQualCategory, reqVO.getQualCategory());
        query.eq(StringUtils.hasText(reqVO.getQualGrade()), QualificationStandardDO::getQualGrade, reqVO.getQualGrade());
        query.eq(StringUtils.hasText(reqVO.getStdVersion()), QualificationStandardDO::getStdVersion, reqVO.getStdVersion());
        LocalDate today = LocalDate.now();
        if (Boolean.TRUE.equals(reqVO.getOnlyEffective())) {
            query.le(QualificationStandardDO::getEffectiveFrom, today)
                    .and(w -> w.isNull(QualificationStandardDO::getEffectiveTo)
                            .or().ge(QualificationStandardDO::getEffectiveTo, today));
        }
        query.orderByDesc(QualificationStandardDO::getStdVersion)
                .orderByAsc(QualificationStandardDO::getQualSerial);
        Page<QualificationStandardDO> page = qualificationStandardMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), query);
        return new PageResult<>(page.getRecords().stream()
                .map(std -> toStandardRespVO(std, today)).collect(Collectors.toList()), page.getTotal());
    }

    @Override
    public QualificationStandardOptionsRespVO getStandardOptions() {
        // 规范出处：契约 §4 G4；《02-法规标准与核验渠道.md》line 37（资质类别/等级不得静态固化）。
        // 为什么这样写：① 维度取自**现行标准库**（按生效区间过滤）而非固定字典——国发〔2021〕7号"证照分离"
        //   改革期间类别压减/合并、新旧标准交替，固化进字典或枚举就意味着换版要改代码；
        // ② 在内存建三级树：标准库是每"序列/类别/等级/版本"一行的小表，一次查询内建树比三次 distinct
        //   查询更省往返，且三级结构来自同一份快照，不会出现层级错配；
        // ③ 用 LinkedHashMap/LinkedHashSet 保持库内顺序稳定，避免每次请求下拉顺序跳动。
        // 业务目标：建档与筛选的可选维度始终与库内现行标准一致，改革换版只改数据。
        LocalDate today = LocalDate.now();
        List<QualificationStandardDO> rows = qualificationStandardMapper.selectList(
                new LambdaQueryWrapper<QualificationStandardDO>()
                        .le(QualificationStandardDO::getEffectiveFrom, today)
                        .and(w -> w.isNull(QualificationStandardDO::getEffectiveTo)
                                .or().ge(QualificationStandardDO::getEffectiveTo, today))
                        .orderByDesc(QualificationStandardDO::getStdVersion)
                        .orderByAsc(QualificationStandardDO::getQualSerial));
        QualificationStandardOptionsRespVO vo = new QualificationStandardOptionsRespVO();
        vo.setStdVersions(rows.stream().map(QualificationStandardDO::getStdVersion)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList()));
        Map<String, Map<String, Set<String>>> tree = new LinkedHashMap<>();
        for (QualificationStandardDO row : rows) {
            if (row.getQualSerial() == null || row.getQualCategory() == null) {
                continue; // 维度不完整的行不进下拉（登记时必填，历史脏数据在此兜底）
            }
            Set<String> grades = tree
                    .computeIfAbsent(row.getQualSerial(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(row.getQualCategory(), k -> new LinkedHashSet<>());
            if (row.getQualGrade() != null) {
                grades.add(row.getQualGrade());
            }
        }
        List<QualificationStandardOptionsRespVO.SerialOption> serials = new ArrayList<>();
        tree.forEach((serial, categories) -> {
            QualificationStandardOptionsRespVO.SerialOption serialOption = new QualificationStandardOptionsRespVO.SerialOption();
            serialOption.setQualSerial(serial);
            List<QualificationStandardOptionsRespVO.CategoryOption> categoryOptions = new ArrayList<>();
            categories.forEach((category, grades) -> {
                QualificationStandardOptionsRespVO.CategoryOption categoryOption = new QualificationStandardOptionsRespVO.CategoryOption();
                categoryOption.setQualCategory(category);
                categoryOption.setGrades(new ArrayList<>(grades));
                categoryOptions.add(categoryOption);
            });
            serialOption.setCategories(categoryOptions);
            serials.add(serialOption);
        });
        vo.setSerials(serials);
        return vo;
    }

    private QualificationStandardRespVO toStandardRespVO(QualificationStandardDO std, LocalDate today) {
        QualificationStandardRespVO vo = new QualificationStandardRespVO();
        vo.setId(std.getId());
        vo.setStdVersion(std.getStdVersion());
        vo.setQualSerial(std.getQualSerial());
        vo.setQualCategory(std.getQualCategory());
        vo.setQualGrade(std.getQualGrade());
        vo.setReqAssets(std.getReqAssets());
        vo.setReqStaff(std.getReqStaff());
        vo.setReqPerformance(std.getReqPerformance());
        vo.setReqEquipment(std.getReqEquipment());
        vo.setEffectiveFrom(std.getEffectiveFrom());
        vo.setEffectiveTo(std.getEffectiveTo());
        vo.setSourceRef(std.getSourceRef());
        // 生效判定统一在后端：effective_from ≤ 基准日 且（无截止 或 截止日 ≥ 基准日）
        vo.setIsEffective(std.getEffectiveFrom() != null && !std.getEffectiveFrom().isAfter(today)
                && (std.getEffectiveTo() == null || !std.getEffectiveTo().isBefore(today)));
        return vo;
    }

    // ==================== 查询：要素台账 / 核查历史 / 安许 / 预警（页签 3、4） ====================

    @Override
    public PageResult<QualificationStaffRespVO> getStaffPage(QualificationElementPageReqVO reqVO) {
        // 规范出处：契约 §4 G5；表 15-3。
        // 为什么这样写（安全）：源表有 id_card_enc（身份证密文）与 id_card_blind_index（盲索引），
        //   出参 VO 里根本没有这两个字段 —— 靠"DTO 不承载"而不是靠前端不显示来保证密文不出接口；
        //   盲索引同样不给：它能被用来做跨库人员关联比对。
        requireEntId(reqVO.getEntId());
        LambdaQueryWrapper<QualificationStaffConfigDO> query = new LambdaQueryWrapper<>();
        query.eq(QualificationStaffConfigDO::getEntId, reqVO.getEntId());
        query.eq(reqVO.getQualId() != null, QualificationStaffConfigDO::getQualId, reqVO.getQualId());
        query.orderByDesc(QualificationStaffConfigDO::getId);
        Page<QualificationStaffConfigDO> page = qualificationStaffConfigMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), query);
        return new PageResult<>(page.getRecords().stream().map(this::toStaffRespVO).collect(Collectors.toList()),
                page.getTotal());
    }

    @Override
    public PageResult<QualificationPerformanceRespVO> getPerformancePage(QualificationElementPageReqVO reqVO) {
        // 规范出处：契约 §4 G5；表 15-4。业绩是达标与加分的核心证据，按竣工日期倒序便于看最近业绩。
        requireEntId(reqVO.getEntId());
        LambdaQueryWrapper<QualificationPerformanceDO> query = new LambdaQueryWrapper<>();
        query.eq(QualificationPerformanceDO::getEntId, reqVO.getEntId());
        query.eq(reqVO.getQualId() != null, QualificationPerformanceDO::getQualId, reqVO.getQualId());
        query.orderByDesc(QualificationPerformanceDO::getCompletionDate)
                .orderByDesc(QualificationPerformanceDO::getId);
        Page<QualificationPerformanceDO> page = qualificationPerformanceMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), query);
        return new PageResult<>(page.getRecords().stream().map(this::toPerformanceRespVO).collect(Collectors.toList()),
                page.getTotal());
    }

    @Override
    public PageResult<QualificationAssetRespVO> getAssetPage(QualificationElementPageReqVO reqVO) {
        // 规范出处：契约 §4 G5；表 15-5。资产按统计年度取值，故按 statYear 倒序（最新的年报口径在前）。
        requireEntId(reqVO.getEntId());
        LambdaQueryWrapper<QualificationAssetDO> query = new LambdaQueryWrapper<>();
        query.eq(QualificationAssetDO::getEntId, reqVO.getEntId());
        query.orderByDesc(QualificationAssetDO::getStatYear).orderByAsc(QualificationAssetDO::getAssetType);
        Page<QualificationAssetDO> page = qualificationAssetMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), query);
        return new PageResult<>(page.getRecords().stream().map(this::toAssetRespVO).collect(Collectors.toList()),
                page.getTotal());
    }

    @Override
    public PageResult<QualificationReviewRespVO> getReviewPage(QualificationReviewPageReqVO reqVO) {
        // 规范出处：契约 §4 G6；表 15-6（只追加）。
        // 为什么这样写：核查表只追加，按 reviewedAt 倒序即"最近一次在前"；stdVersion + elementSnapshot 必须带出，
        //   否则历史结论在标准换版或要素变更后无法解释/复算。
        requireEntId(reqVO.getEntId());
        LambdaQueryWrapper<QualificationComplianceReviewDO> query = new LambdaQueryWrapper<>();
        query.eq(QualificationComplianceReviewDO::getEntId, reqVO.getEntId());
        query.eq(reqVO.getQualId() != null, QualificationComplianceReviewDO::getQualId, reqVO.getQualId());
        query.eq(StringUtils.hasText(reqVO.getReviewResult()),
                QualificationComplianceReviewDO::getReviewResult, reqVO.getReviewResult());
        query.orderByDesc(QualificationComplianceReviewDO::getReviewedAt)
                .orderByDesc(QualificationComplianceReviewDO::getId);
        Page<QualificationComplianceReviewDO> page = qualificationComplianceReviewMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), query);
        return new PageResult<>(page.getRecords().stream().map(this::toReviewRespVO).collect(Collectors.toList()),
                page.getTotal());
    }

    @Override
    public PageResult<QualificationPermitRespVO> getPermitPage(QualificationPermitPageReqVO reqVO) {
        // 规范出处：契约 §4 G7；表 14-8 + 风险联动列。
        // 为什么这样写：① 宽限期内的证"还没过期但不合规"，因此排序与资质台账一致（有有效期者按到期先后、
        //   未填有效期置末），让最紧急的排前面；② expiringInDays 只取"未过期且 ≤ N 天"，
        //   已过期的走 permitStatus=EXPIRED（与预警扫描口径一致）。
        requireEntId(reqVO.getEntId());
        LambdaQueryWrapper<QualificationSafetyPermitDO> query = new LambdaQueryWrapper<>();
        query.eq(QualificationSafetyPermitDO::getEntId, reqVO.getEntId());
        query.eq(StringUtils.hasText(reqVO.getPermitStatus()),
                QualificationSafetyPermitDO::getPermitStatus, reqVO.getPermitStatus());
        if (reqVO.getExpiringInDays() != null) {
            LocalDate today = LocalDate.now();
            query.isNotNull(QualificationSafetyPermitDO::getValidTo);
            query.ge(QualificationSafetyPermitDO::getValidTo, today);
            query.le(QualificationSafetyPermitDO::getValidTo, today.plusDays(reqVO.getExpiringInDays()));
        }
        if (StringUtils.hasText(reqVO.getKeyword())) {
            String keyword = reqVO.getKeyword().trim();
            query.and(w -> w.like(QualificationSafetyPermitDO::getPermitNo, keyword)
                    .or().like(QualificationSafetyPermitDO::getIssuingAuthority, keyword));
        }
        query.last("ORDER BY (valid_to IS NULL), valid_to ASC, id DESC");
        Page<QualificationSafetyPermitDO> page = qualificationSafetyPermitMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), query);
        LocalDate today = LocalDate.now();
        return new PageResult<>(page.getRecords().stream()
                .map(permit -> toPermitRespVO(permit, today)).collect(Collectors.toList()), page.getTotal());
    }

    @Override
    public QualificationPermitRespVO getPermit(Long id) {
        // 规范出处：契约 §4 G7。为什么这样写：不存在直接报 SAFETY_PERMIT_NOT_EXISTS（1090109024），
        // 不返回空壳对象——否则前端会把"查不到"渲染成"数据为空"。
        QualificationSafetyPermitDO permit = qualificationSafetyPermitMapper.selectById(id);
        if (permit == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_PERMIT_NOT_EXISTS);
        }
        return toPermitRespVO(permit, LocalDate.now());
    }

    @Override
    public PageResult<QualificationAlertRespVO> getAlertPage(QualificationAlertPageReqVO reqVO) {
        // 规范出处：契约 §4 G8；cert_alert_records（alertType=QUAL_EXPIRY 三源：QUAL/SAFETY_PERMIT/STAFF）。
        // 为什么这样写：① 预警表没有 ent_id（只有 refType/refId 指向业务对象），按企业过滤必须
        //   "先取该企业的资质/安许 id，再按 (refType, refId) **成对**匹配"——只按 refId 匹配会串对象
        //   （不同表的 id 可能相同，例：资质 9001 与人员记录 9001）；② 若该企业既无资质也无安许，
        //   直接返回空页而不查库（避免 IN () 这种非法 SQL 与无谓查询）。
        requireEntId(reqVO.getEntId());
        LambdaQueryWrapper<CertAlertRecordDO> query = new LambdaQueryWrapper<>();
        query.eq(CertAlertRecordDO::getAlertType, CertificateAlertTypeEnum.QUAL_EXPIRY.name());
        query.eq(StringUtils.hasText(reqVO.getRefType()), CertAlertRecordDO::getRefType, reqVO.getRefType());
        query.eq(StringUtils.hasText(reqVO.getAlertLevel()), CertAlertRecordDO::getAlertLevel, reqVO.getAlertLevel());
        query.eq(reqVO.getHandled() != null, CertAlertRecordDO::getHandled, reqVO.getHandled());
        List<Long> qualIds = enterpriseQualificationMapper.selectList(
                        new LambdaQueryWrapper<EnterpriseQualificationDO>()
                                .eq(EnterpriseQualificationDO::getEntId, reqVO.getEntId()))
                .stream().map(EnterpriseQualificationDO::getId).collect(Collectors.toList());
        List<Long> permitIds = qualificationSafetyPermitMapper.selectList(
                        new LambdaQueryWrapper<QualificationSafetyPermitDO>()
                                .eq(QualificationSafetyPermitDO::getEntId, reqVO.getEntId()))
                .stream().map(QualificationSafetyPermitDO::getId).collect(Collectors.toList());
        if (qualIds.isEmpty() && permitIds.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0L);
        }
        query.and(w -> {
            if (!qualIds.isEmpty()) {
                w.and(x -> x.eq(CertAlertRecordDO::getRefType, REF_TYPE_QUAL)
                        .in(CertAlertRecordDO::getRefId, qualIds));
            }
            if (!permitIds.isEmpty()) {
                w.or(x -> x.eq(CertAlertRecordDO::getRefType, REF_TYPE_SAFETY_PERMIT)
                        .in(CertAlertRecordDO::getRefId, permitIds));
            }
        });
        query.orderByDesc(CertAlertRecordDO::getTriggeredAt).orderByDesc(CertAlertRecordDO::getId);
        Page<CertAlertRecordDO> page = certAlertRecordMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), query);
        return new PageResult<>(page.getRecords().stream().map(this::toAlertRespVO).collect(Collectors.toList()),
                page.getTotal());
    }

    /** 预警来源类型：资质主档 / 安许证（与 QualificationAlertServiceImpl 的幂等键前缀同一口径）。 */
    private static final String REF_TYPE_QUAL = "QUAL";
    private static final String REF_TYPE_SAFETY_PERMIT = "SAFETY_PERMIT";

    /** 查询前兜底：entId 必填（防直接调用服务方退化成全租户查询）。 */
    private void requireEntId(Long entId) {
        if (entId == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_QUERY_ENT_REQUIRED);
        }
    }

    private QualificationStaffRespVO toStaffRespVO(QualificationStaffConfigDO staff) {
        QualificationStaffRespVO vo = new QualificationStaffRespVO();
        vo.setId(staff.getId());
        vo.setEntId(staff.getEntId());
        vo.setQualId(staff.getQualId());
        vo.setStaffRole(staff.getStaffRole());
        vo.setPersonName(staff.getPersonName());
        vo.setMajor(staff.getMajor());
        vo.setRegNo(staff.getRegNo());
        vo.setTitleLevel(staff.getTitleLevel());
        vo.setIsFullTime(staff.getIsFullTime());
        vo.setSocialSecurityStatus(staff.getSocialSecurityStatus());
        vo.setSocialSecurityTenant(staff.getSocialSecurityTenant());
        vo.setEduHours(staff.getEduHours());
        vo.setEduHoursRequired(staff.getEduHoursRequired());
        vo.setRegValidTo(staff.getRegValidTo());
        vo.setBindedCertId(staff.getBindedCertId());
        vo.setVersion(staff.getVersion());
        // 刻意不映射 idCardEnc / idCardBlindIndex（身份证密文与盲索引不出接口）
        return vo;
    }

    private QualificationPerformanceRespVO toPerformanceRespVO(QualificationPerformanceDO performance) {
        QualificationPerformanceRespVO vo = new QualificationPerformanceRespVO();
        vo.setId(performance.getId());
        vo.setEntId(performance.getEntId());
        vo.setQualId(performance.getQualId());
        vo.setProjectName(performance.getProjectName());
        vo.setProjectType(performance.getProjectType());
        vo.setScaleMetrics(performance.getScaleMetrics());
        vo.setContractAmount(performance.getContractAmount());
        vo.setCompletionDate(performance.getCompletionDate());
        vo.setRole(performance.getRole());
        vo.setQualityResult(performance.getQualityResult());
        vo.setOwnerName(performance.getOwnerName());
        vo.setIsPlatformVerified(performance.getIsPlatformVerified());
        vo.setEvidenceFileIds(performance.getEvidenceFileIds());
        return vo;
    }

    private QualificationAssetRespVO toAssetRespVO(QualificationAssetDO asset) {
        QualificationAssetRespVO vo = new QualificationAssetRespVO();
        vo.setId(asset.getId());
        vo.setEntId(asset.getEntId());
        vo.setAssetType(asset.getAssetType());
        vo.setName(asset.getName());
        vo.setSpec(asset.getSpec());
        vo.setQuantity(asset.getQuantity());
        vo.setValueAmount(asset.getValueAmount());
        vo.setStatYear(asset.getStatYear());
        vo.setEvidenceFileIds(asset.getEvidenceFileIds());
        return vo;
    }

    private QualificationReviewRespVO toReviewRespVO(QualificationComplianceReviewDO review) {
        QualificationReviewRespVO vo = new QualificationReviewRespVO();
        vo.setId(review.getId());
        vo.setEntId(review.getEntId());
        vo.setQualId(review.getQualId());
        vo.setStdVersion(review.getStdVersion());
        vo.setReviewType(review.getReviewType());
        vo.setReviewResult(review.getReviewResult());
        vo.setElementSnapshot(review.getElementSnapshot());
        vo.setDiffItems(review.getDiffItems());
        vo.setReviewer(review.getReviewer());
        vo.setReviewedAt(review.getReviewedAt());
        vo.setLinkedApplyId(review.getLinkedApplyId());
        return vo;
    }

    private QualificationPermitRespVO toPermitRespVO(QualificationSafetyPermitDO permit, LocalDate today) {
        QualificationPermitRespVO vo = new QualificationPermitRespVO();
        vo.setId(permit.getId());
        vo.setEntId(permit.getEntId());
        vo.setPermitNo(permit.getPermitNo());
        vo.setIssuingAuthority(permit.getIssuingAuthority());
        vo.setIssueDate(permit.getIssueDate());
        vo.setValidTo(permit.getValidTo());
        // 未填有效期返回 null（前端显示"未填"），不要算成 0 天
        vo.setDaysToExpire(permit.getValidTo() == null ? null
                : (int) ChronoUnit.DAYS.between(today, permit.getValidTo()));
        vo.setPermitStatus(permit.getPermitStatus());
        vo.setLegalPerson(permit.getLegalPerson());
        vo.setBindedCertId(permit.getBindedCertId());
        vo.setRiskFlag(permit.getRiskFlag());
        vo.setRiskLevel(permit.getRiskLevel());
        vo.setRiskEvaluatedAt(permit.getRiskEvaluatedAt());
        vo.setGraceExpireAt(permit.getGraceExpireAt());
        vo.setGraceReason(permit.getGraceReason());
        vo.setGraceSourceRef(permit.getGraceSourceRef());
        vo.setFrequentAlertCount(permit.getFrequentAlertCount());
        vo.setFrequentWindowStart(permit.getFrequentWindowStart());
        vo.setRuleVersion(permit.getRuleVersion());
        vo.setRemark(permit.getRemark());
        vo.setVersion(permit.getVersion());
        return vo;
    }

    private QualificationAlertRespVO toAlertRespVO(CertAlertRecordDO alert) {
        QualificationAlertRespVO vo = new QualificationAlertRespVO();
        vo.setId(alert.getId());
        // 处理端点用版本做 CAS，读侧必须回传（否则前端发不出合法请求，见 VO javadoc ④）
        vo.setVersion(alert.getVersion());
        vo.setRefType(alert.getRefType());
        vo.setRefId(alert.getRefId());
        vo.setRefName(alert.getRefName());
        vo.setCertId(alert.getCertId());
        vo.setAlertType(alert.getAlertType());
        vo.setAlertLevel(alert.getAlertLevel());
        vo.setAlertMessage(alert.getAlertMessage());
        vo.setTriggeredAt(alert.getTriggeredAt());
        vo.setIdempotentKey(alert.getIdempotentKey());
        vo.setNotifyChannel(alert.getNotifyChannel());
        vo.setNotifyTarget(alert.getNotifyTarget());
        vo.setNotifyStatus(alert.getNotifyStatus());
        vo.setHandled(alert.getHandled());
        vo.setHandledBy(alert.getHandledBy());
        vo.setHandledAt(alert.getHandledAt());
        vo.setHandleNote(alert.getHandleNote());
        return vo;
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
        // 批准范围：详情页「基本信息」栏要展示，此前只在 DO/SaveReqVO 有、出参漏了 → 加了字段不拷贝会恒为 null
        vo.setApprovalScope(qual.getApprovalScope());
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

    /**
     * 更新后的状态推导：只对"日期推导态"按新 valid_to 重算，业务态原样保留。
     *
     * <p>为什么这样写：QualificationStatusEnum 的六个态分两类 ——
     * ① 日期推导态 VALID / EXPIRING_SOON / EXPIRED：语义完全由 valid_to 决定，改期后必须跟着走，
     * 否则"把过期资质延到明年"仍停在 EXPIRED，且**永不自愈**（到期扫描对 days &lt;= 0 直接 continue，
     * 只在窗口内升 EXPIRING_SOON，不会把 EXPIRED 改回 VALID）；
     * ② 业务态 PENDING_RENEW / LIMITED / REVOKED：由状态机（changeStatus）与申报流写，与日期无关。
     * 尤其 PENDING_RENEW —— 在途延续申报期间被本接口改成 VALID 会污染 {@link #rejectRenewalApply}
     * 的还原判断（该方法靠 PENDING_RENEW 决定是否回写 VALID）。REVOKED 是唯一终态
     * （见 QualificationStatusEnum 静态块中 allow(REVOKED) 无后继）。
     *
     * <p>为什么不只保护终态：人员证书域的同名先例（PersonnelCertificateServiceImpl#resolveStatusAfterUpdate）
     * 只保护 REVOKED/CANCELLED，因为人员证书没有 PENDING_RENEW 这类中间态；资质域照抄会误伤。
     *
     * @param originStatus 库内原状态（null 视为推导态，兜底历史脏数据）
     * @param newValidTo   新有效期至（null = 长期）
     */
    private String resolveStatusAfterUpdate(String originStatus, LocalDate newValidTo) {
        boolean derivedByDate = originStatus == null
                || QualificationStatusEnum.VALID.name().equals(originStatus)
                || QualificationStatusEnum.EXPIRING_SOON.name().equals(originStatus)
                || QualificationStatusEnum.EXPIRED.name().equals(originStatus);
        if (!derivedByDate) {
            return originStatus;
        }
        if (newValidTo != null && newValidTo.isBefore(LocalDate.now())) {
            return QualificationStatusEnum.EXPIRED.name();
        }
        // 未过期一律回 VALID；"是否进入预警窗口"交给到期扫描按规则配置的窗口升 EXPIRING_SOON，
        // 本接口不硬编码窗口天数（与 QualificationAlertServiceImpl 的窗口口径保持一致）。
        return QualificationStatusEnum.VALID.name();
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
