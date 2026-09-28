package cn.iocoder.txgy.module.bidding.service.meta;

import cn.iocoder.txgy.module.bidding.controller.admin.meta.vo.OptionItemRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelCertificateDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelProfileDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.project.ProjectDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.EnterpriseQualificationDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationSafetyPermitDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectCompanyDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectProfileDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelCertificateMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelProfileMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.project.ProjectMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.EnterpriseQualificationMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationSafetyPermitMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectCompanyMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectProfileMapper;
import cn.iocoder.txgy.module.bidding.enums.BiddingOptionTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.PersonnelStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 统一候选选项 Service 实现（GET /bidding/meta/options）。
 *
 * <p>契约见《24-统一候选接口-options-契约-2026-09-15》。
 *
 * <p>实现口径（为什么这么写）：
 * <ol>
 *   <li><b>一个形状</b>：六类数据源各自只做"取行 → 映射成同一个 VO"，不在 Controller 层做类型分支，
 *       前端因此只需要一个下拉组件 + 一个请求方法。</li>
 *   <li><b>id 走字符串</b>：雪花 ID 超 JS 安全整数，数字化下发会被静默改写（看着是 A、提交成 B），
 *       故 value 一律十进制字符串。</li>
 *   <li><b>禁用而非过滤</b>：失效/离职对象仍返回但 {@code disabled=true}——用户常常正是在找那条过期证照，
 *       直接过滤会让人以为"数据没了"。仅当状态**明确**不可用时才禁用：状态为空（存量/未评估）不拦，
 *       避免把可用数据挡在外面。</li>
 *   <li><b>复用状态权威口径</b>：人员候选直接调 {@link PersonnelStatusEnum#isUsable}（"仅在职可作为配证候选"），
 *       而不是在 Service 里重写一遍状态白名单。</li>
 *   <li>租户与逻辑删除由框架拦截器统一处理（这些 DO 均为 TenantBaseDO），本类不手写 tenant_id / deleted 条件。</li>
 * </ol>
 */
@Service
@Validated
public class BiddingOptionServiceImpl implements BiddingOptionService {

    /** 默认返回上限：够填满一个下拉，又不至于把大表拉全。 */
    public static final int DEFAULT_LIMIT = 20;
    /** 最大返回上限：下拉搜索场景超过 50 条用户也不会翻，超过即应改精确关键字。 */
    public static final int MAX_LIMIT = 50;
    /** 名称全空时的占位（宁可显示"未命名"也不要留空导致前端出现空气泡）。 */
    public static final String UNNAMED = "未命名";

    /** 证照可用状态（与 {@code CertificateStatusEnum} / 字典 cert_status 对齐；仅这两种可选）。 */
    private static final Set<String> USABLE_CERT = Set.of("ACTIVE", "EXPIRING_SOON");
    /** 企业资质可用状态（与 {@code QualificationStatusEnum} / 字典 qual_status 对齐）。 */
    private static final Set<String> USABLE_QUAL = Set.of("VALID", "EXPIRING_SOON");
    /** 安许证可用状态（与 {@code SafetyPermitStatusEnum} / 字典 safety_permit_status 对齐）。 */
    private static final Set<String> USABLE_PERMIT = Set.of("VALID", "EXPIRING_SOON");

    @Resource
    private SubjectProfileMapper subjectProfileMapper;
    @Resource
    private SubjectCompanyMapper subjectCompanyMapper;
    @Resource
    private PersonnelProfileMapper personnelProfileMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private CertificateInfoMapper certificateInfoMapper;
    @Resource
    private EnterpriseQualificationMapper enterpriseQualificationMapper;
    @Resource
    private QualificationSafetyPermitMapper qualificationSafetyPermitMapper;
    @Resource
    private PersonnelCertificateMapper personnelCertificateMapper;

    @Override
    public List<OptionItemRespVO> listOptions(BiddingOptionTypeEnum type, String keyword, Integer limit) {
        int size = normalizeLimit(limit);
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return switch (type) {
            case SUBJECT -> subjectOptions(kw, size);
            case SUBJECT_COMPANY -> subjectCompanyOptions(kw, size);
            case PERSONNEL -> personnelOptions(kw, size);
            case PROJECT -> projectOptions(kw, size);
            case CERTIFICATE -> certificateOptions(kw, size);
            case QUALIFICATION -> qualificationOptions(kw, size);
            case SAFETY_PERMIT -> safetyPermitOptions(kw, size);
            case PERSONNEL_CERT -> personnelCertOptions(kw, size);
        };
    }

    // ==================== 各数据源 ====================

    private List<OptionItemRespVO> subjectOptions(String kw, int limit) {
        LambdaQueryWrapper<SubjectProfileDO> w = new LambdaQueryWrapper<>();
        if (kw != null) {
            w.and(q -> q.like(SubjectProfileDO::getSubjectName, kw).or().like(SubjectProfileDO::getUscc, kw));
        }
        w.orderByDesc(SubjectProfileDO::getId);
        List<SubjectProfileDO> rows = subjectProfileMapper.selectPage(new Page<>(1, limit), w).getRecords();
        List<OptionItemRespVO> list = new ArrayList<>(rows.size());
        for (SubjectProfileDO row : rows) {
            // 主体无"可用/不可用"状态语义（是否完整由资质核查判定），故不禁用
            list.add(item(row.getId(), firstNonBlank(row.getSubjectName()), extraOf(row.getUscc()), false, null));
        }
        return list;
    }

    private List<OptionItemRespVO> subjectCompanyOptions(String kw, int limit) {
        LambdaQueryWrapper<SubjectCompanyDO> w = new LambdaQueryWrapper<>();
        if (kw != null) {
            w.and(q -> q.like(SubjectCompanyDO::getCompanyName, kw).or().like(SubjectCompanyDO::getUscc, kw));
        }
        w.orderByDesc(SubjectCompanyDO::getId);
        List<SubjectCompanyDO> rows = subjectCompanyMapper.selectPage(new Page<>(1, limit), w).getRecords();
        List<OptionItemRespVO> list = new ArrayList<>(rows.size());
        for (SubjectCompanyDO row : rows) {
            // 停用企业仍返回但 disabled=true（前端自行决定是否可选；不影响已归属的历史证照/资质）
            boolean disabled = row.getEnabled() == null || !row.getEnabled();
            list.add(item(row.getId(), firstNonBlank(row.getCompanyName(), UNNAMED),
                    extraOf(row.getUscc()), disabled, null));
        }
        return list;
    }

    private List<OptionItemRespVO> personnelOptions(String kw, int limit) {
        LambdaQueryWrapper<PersonnelProfileDO> w = new LambdaQueryWrapper<>();
        if (kw != null) {
            w.and(q -> q.like(PersonnelProfileDO::getPersonName, kw).or().like(PersonnelProfileDO::getPersonNo, kw));
        }
        w.orderByDesc(PersonnelProfileDO::getId);
        List<PersonnelProfileDO> rows = personnelProfileMapper.selectPage(new Page<>(1, limit), w).getRecords();
        List<OptionItemRespVO> list = new ArrayList<>(rows.size());
        for (PersonnelProfileDO row : rows) {
            list.add(item(row.getId(), firstNonBlank(row.getPersonName(), row.getPersonNo()),
                    extraOf(row.getPersonNo()), personNotUsable(row.getPersonnelStatus()), row.getPersonnelStatus()));
        }
        return list;
    }

    private List<OptionItemRespVO> projectOptions(String kw, int limit) {
        LambdaQueryWrapper<ProjectDO> w = new LambdaQueryWrapper<>();
        if (kw != null) {
            w.and(q -> q.like(ProjectDO::getProjectName, kw).or().like(ProjectDO::getProjectCode, kw));
        }
        w.orderByDesc(ProjectDO::getId);
        List<ProjectDO> rows = projectMapper.selectPage(new Page<>(1, limit), w).getRecords();
        List<OptionItemRespVO> list = new ArrayList<>(rows.size());
        for (ProjectDO row : rows) {
            // 项目 state 是"招标阶段"语义，不代表对象不可用，故不禁用；前端可按 status 展示阶段标签
            list.add(item(row.getId(), firstNonBlank(row.getProjectName(), row.getProjectCode()),
                    extraOf(row.getProjectCode()), false, row.getState()));
        }
        return list;
    }

    private List<OptionItemRespVO> certificateOptions(String kw, int limit) {
        LambdaQueryWrapper<CertificateInfoDO> w = new LambdaQueryWrapper<>();
        if (kw != null) {
            w.and(q -> q.like(CertificateInfoDO::getCertName, kw).or().like(CertificateInfoDO::getCertNo, kw));
        }
        w.orderByDesc(CertificateInfoDO::getId);
        List<CertificateInfoDO> rows = certificateInfoMapper.selectPage(new Page<>(1, limit), w).getRecords();
        List<OptionItemRespVO> list = new ArrayList<>(rows.size());
        for (CertificateInfoDO row : rows) {
            list.add(item(row.getId(), firstNonBlank(row.getCertName(), row.getCertNo()),
                    extraOf(row.getCertNo()), notUsable(row.getCertStatus(), USABLE_CERT), row.getCertStatus()));
        }
        return list;
    }

    private List<OptionItemRespVO> qualificationOptions(String kw, int limit) {
        LambdaQueryWrapper<EnterpriseQualificationDO> w = new LambdaQueryWrapper<>();
        if (kw != null) {
            w.and(q -> q.like(EnterpriseQualificationDO::getCertNo, kw).or().like(EnterpriseQualificationDO::getQualSerial, kw));
        }
        w.orderByDesc(EnterpriseQualificationDO::getId);
        List<EnterpriseQualificationDO> rows = enterpriseQualificationMapper.selectPage(new Page<>(1, limit), w).getRecords();
        List<OptionItemRespVO> list = new ArrayList<>(rows.size());
        for (EnterpriseQualificationDO row : rows) {
            list.add(item(row.getId(), firstNonBlank(row.getCertNo(), row.getQualSerial()),
                    extraOf(row.getQualSerial(), row.getQualGrade()), notUsable(row.getQualStatus(), USABLE_QUAL),
                    row.getQualStatus()));
        }
        return list;
    }

    private List<OptionItemRespVO> safetyPermitOptions(String kw, int limit) {
        LambdaQueryWrapper<QualificationSafetyPermitDO> w = new LambdaQueryWrapper<>();
        if (kw != null) {
            w.and(q -> q.like(QualificationSafetyPermitDO::getPermitNo, kw)
                    .or().like(QualificationSafetyPermitDO::getIssuingAuthority, kw));
        }
        w.orderByDesc(QualificationSafetyPermitDO::getId);
        List<QualificationSafetyPermitDO> rows = qualificationSafetyPermitMapper.selectPage(new Page<>(1, limit), w).getRecords();
        List<OptionItemRespVO> list = new ArrayList<>(rows.size());
        for (QualificationSafetyPermitDO row : rows) {
            list.add(item(row.getId(), firstNonBlank(row.getPermitNo()), extraOf(row.getIssuingAuthority()),
                    notUsable(row.getPermitStatus(), USABLE_PERMIT), row.getPermitStatus()));
        }
        return list;
    }

    private List<OptionItemRespVO> personnelCertOptions(String kw, int limit) {
        LambdaQueryWrapper<PersonnelCertificateDO> w = new LambdaQueryWrapper<>();
        if (kw != null) {
            w.and(q -> q.like(PersonnelCertificateDO::getCertName, kw).or().like(PersonnelCertificateDO::getCertNo, kw));
        }
        w.orderByDesc(PersonnelCertificateDO::getId);
        List<PersonnelCertificateDO> rows = personnelCertificateMapper.selectPage(new Page<>(1, limit), w).getRecords();
        List<OptionItemRespVO> list = new ArrayList<>(rows.size());
        for (PersonnelCertificateDO row : rows) {
            list.add(item(row.getId(), firstNonBlank(row.getCertName(), row.getCertNo()),
                    extraOf(row.getCertNo()), notUsable(row.getCertStatus(), USABLE_CERT), row.getCertStatus()));
        }
        return list;
    }

    // ==================== 纯函数（单元测试覆盖，不依赖 DB） ====================

    /** 归一化上限：空 → 默认 20；<1 → 默认 20；>50 → 截到 50（宁可少给也不要慢查询打崩库）。 */
    static int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /** 取第一个非空白值；全空返回"未命名"。 */
    static String firstNonBlank(String... candidates) {
        if (candidates != null) {
            for (String c : candidates) {
                if (c != null && !c.isBlank()) {
                    return c.trim();
                }
            }
        }
        return UNNAMED;
    }

    /** 用 " · " 拼接非空片段；全空返回 null（不输出空串，避免前端渲染出多余分隔符）。 */
    static String extraOf(String... parts) {
        StringBuilder sb = new StringBuilder();
        if (parts != null) {
            for (String p : parts) {
                if (p != null && !p.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append(" · ");
                    }
                    sb.append(p.trim());
                }
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * 状态白名单判定：**只有明确处于不可用状态才禁用**。
     * 状态为空（存量数据/未评估）不禁用——不能因为"没填"就把可用数据挡在外面。
     */
    static boolean notUsable(String status, Set<String> usable) {
        if (status == null || status.isBlank()) {
            return false;
        }
        return !usable.contains(status.trim().toUpperCase());
    }

    /** 人员候选判定：复用 {@link PersonnelStatusEnum#isUsable}（仅在职可用）；字典外的未知状态保守禁用。 */
    static boolean personNotUsable(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        try {
            return !PersonnelStatusEnum.isUsable(PersonnelStatusEnum.valueOf(status.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return true;
        }
    }

    private static OptionItemRespVO item(Long id, String label, String extra, boolean disabled, String status) {
        OptionItemRespVO vo = new OptionItemRespVO();
        vo.setValue(id == null ? null : String.valueOf(id));
        vo.setLabel(label);
        vo.setExtra(extra);
        vo.setDisabled(disabled);
        vo.setStatus(status);
        return vo;
    }
}
