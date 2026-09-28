package cn.iocoder.txgy.module.bidding.dal.mysql.personnel;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelCertificateDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 员工证书 Mapper。
 *
 * <p>规范出处：《招投标模块开发规范》§4.2/§5 六-2（检索条件命中索引前缀、时间列建索引以支撑到期扫描）；
 * 《电子证件全流程管理_数据库设计.md》v3.3 §九（到期预警扫描口径）与表 15-3（注册有效期 reg_valid_to）。
 * <p>为什么这样写：配证要先按"证书类型 + 可用状态 + 有效期覆盖"过滤出"证"，再回查"人"，
 * 比"先取全员再逐人查证"少几个数量级的查询（投标配证性能关键路径）。
 * <p>业务目标：支撑证书查询、配证取数、到期预警三类高频读。
 */
@Mapper
public interface PersonnelCertificateMapper extends BaseMapperX<PersonnelCertificateDO> {

    /** 同人同类型证书编号查重（UK 口径）。 */
    default PersonnelCertificateDO selectByCertNo(Long personId, String certType, String certNo) {
        return selectOne(new LambdaQueryWrapper<PersonnelCertificateDO>()
                .eq(PersonnelCertificateDO::getPersonId, personId)
                .eq(PersonnelCertificateDO::getCertType, certType)
                .eq(PersonnelCertificateDO::getCertNo, certNo)
                .last("LIMIT 1"));
    }

    /** 某员工在册证书（当前有效记录优先，供详情页与核验使用）。 */
    default List<PersonnelCertificateDO> selectByPerson(Long personId) {
        return selectList(new LambdaQueryWrapper<PersonnelCertificateDO>()
                .eq(PersonnelCertificateDO::getPersonId, personId)
                .eq(PersonnelCertificateDO::getIsCurrent, true)
                .orderByAsc(PersonnelCertificateDO::getCertType)
                .orderByDesc(PersonnelCertificateDO::getValidTo));
    }

    /**
     * 配证候选证书：企业内指定类型、状态可用（VALID/EXPIRING_SOON）、且有效期覆盖 coverTo（NULL=长期视为覆盖）。
     * 一次查出候选证书，服务层再与人员档案做内存合并，避免逐人查库（配证性能）。
     */
    default List<PersonnelCertificateDO> selectUsableByType(Long entId, String certType, LocalDate coverTo) {
        LambdaQueryWrapper<PersonnelCertificateDO> query = new LambdaQueryWrapper<PersonnelCertificateDO>()
                .eq(PersonnelCertificateDO::getEntId, entId)
                .eq(PersonnelCertificateDO::getCertType, certType)
                .eq(PersonnelCertificateDO::getIsCurrent, true)
                .in(PersonnelCertificateDO::getCertStatus, "VALID", "EXPIRING_SOON");
        if (coverTo != null) {
            query.and(w -> w.isNull(PersonnelCertificateDO::getValidTo)
                    .or().ge(PersonnelCertificateDO::getValidTo, coverTo));
        }
        return selectList(query.last("LIMIT 1000"));
    }

    /** 某员工的可用证书（人证合一核验时挑选最贴合的证书）。 */
    default List<PersonnelCertificateDO> selectUsableByPerson(Long personId, String certType) {
        LambdaQueryWrapper<PersonnelCertificateDO> query = new LambdaQueryWrapper<PersonnelCertificateDO>()
                .eq(PersonnelCertificateDO::getPersonId, personId)
                .eq(PersonnelCertificateDO::getIsCurrent, true)
                .in(PersonnelCertificateDO::getCertStatus, "VALID", "EXPIRING_SOON");
        if (certType != null) {
            query.eq(PersonnelCertificateDO::getCertType, certType);
        }
        return selectList(query.orderByDesc(PersonnelCertificateDO::getValidTo));
    }

    /**
     * 到期窗口**唯一定义**（2026-09-13 统一口径，三处共用：预警扫描 / 到期清单 / 分页到期筛选）。
     *
     * <p>为什么单独抽出来：此前同一件事有三份实现且口径互相打架——
     * <ul>
     *   <li>预警扫描与本 Mapper：valid_to ∈ <b>(today, …]</b> 开区间（今天到期的证不在名单里）、<b>且不过滤 is_current</b>
     *       → 被新证取代的历史证也会被扫出来告警；</li>
     *   <li>分页 expiringDays：[today, …] 闭区间、且过滤 is_current=true → 与扫描结果对不上，
     *       现场表现为"页面说 3 张要到期、预警却报了 5 条"；</li>
     *   <li>到期清单：走本 Mapper 但又用 entId 在 Java 内存里过滤（多查一轮、口径再分叉一次）。</li>
     * </ul>
     * 现统一为：可用状态（VALID/EXPIRING_SOON）+ 仅当前记录（is_current=1）+ valid_to ∈ <b>[today, today+days]</b>。
     * 下界取闭区间是刻意的：**今天到期**必须出现在清单与预警里（旧开区间会漏掉"最后一天"）。
     * <p>与证照 EXPIRY、资质 QUAL_EXPIRY 扫描同范式（v3.3 §九）。
     */
    static void applyExpiringWindow(LambdaQueryWrapper<PersonnelCertificateDO> query, LocalDate today, int days) {
        query.in(PersonnelCertificateDO::getCertStatus, "VALID", "EXPIRING_SOON")
                .eq(PersonnelCertificateDO::getIsCurrent, true)
                .isNotNull(PersonnelCertificateDO::getValidTo)
                .ge(PersonnelCertificateDO::getValidTo, today)
                .le(PersonnelCertificateDO::getValidTo, today.plusDays(days));
    }

    /** 到期预警候选证书（全租户；预警扫描用）。口径见 {@link #applyExpiringWindow}。 */
    default List<PersonnelCertificateDO> selectExpiringCandidates(LocalDate today, int maxDays) {
        return selectExpiringCandidates(today, maxDays, null);
    }

    /**
     * 到期候选证书（可按企业过滤；到期清单用）。
     *
     * <p>2026-09-13：由 2 参版本扩展出 entId——此前到期清单把 entId 放在服务层内存过滤，
     * 既多查一轮又让"清单口径"与"扫描口径"再次分叉；现在 entId 进 SQL，与扫描共用同一窗口定义。
     */
    default List<PersonnelCertificateDO> selectExpiringCandidates(LocalDate today, int maxDays, Long entId) {
        LambdaQueryWrapper<PersonnelCertificateDO> query = new LambdaQueryWrapper<>();
        applyExpiringWindow(query, today, maxDays);
        if (entId != null) {
            query.eq(PersonnelCertificateDO::getEntId, entId);
        }
        return selectList(query.orderByAsc(PersonnelCertificateDO::getValidTo));
    }

    /**
     * 复审/年审预警候选：next_review_date 落入 (today, today+maxDays] 且台账状态可用。
     *
     * <p>规范出处：06 文档 §二-4（复审/年审是与到期日独立的时间维度，逾期证书会被暂停/撤销）。
     * <p>业务目标：在证书到期前先把"复审窗口"提醒出来（司炉证届满前 1 个月须复审），
     * 避免出现"证书没到期但已失效"的投标事故。
     */
    default List<PersonnelCertificateDO> selectReviewDueCandidates(LocalDate today, int maxDays) {
        return selectList(new LambdaQueryWrapper<PersonnelCertificateDO>()
                .in(PersonnelCertificateDO::getCertStatus, "VALID", "EXPIRING_SOON")
                .isNotNull(PersonnelCertificateDO::getNextReviewDate)
                .gt(PersonnelCertificateDO::getNextReviewDate, today)
                .le(PersonnelCertificateDO::getNextReviewDate, today.plusDays(maxDays))
                .orderByAsc(PersonnelCertificateDO::getNextReviewDate));
    }

    /**
     * 按证书细分统计有效持证人数（安许证 A/B/C 三类人员达标统计用）。
     *
     * <p>规范出处：06 文档 §二-1（陕西 2026-03-26 起对安管 A/B/C 三类人员配备动态核查，
     * 任一类不达标即黄/红预警，三类全 0 判失效）；统计口径=按 cert_sub_type 计数且状态可用。
     * <p>业务目标：让企业提前看到"安许证对应的三类人员够不够"，而不是被主管部门通知才发现。
     */
    default List<Map<String, Object>> countUsableBySubType(Long entId, String certType, LocalDate asOf) {
        QueryWrapper<PersonnelCertificateDO> wrapper = new QueryWrapper<PersonnelCertificateDO>()
                .select("cert_sub_type AS sub_type", "COUNT(*) AS cnt")
                .eq("ent_id", entId)
                .eq("cert_type", certType)
                .eq("is_current", 1)
                .in("cert_status", "VALID", "EXPIRING_SOON")
                .groupBy("cert_sub_type");
        if (asOf != null) {
            wrapper.and(w -> w.isNull("valid_to").or().ge("valid_to", asOf));
        }
        return selectMaps(wrapper);
    }

    /** 按员工批量取证书（员工资质全景/批量核验用，避免 N+1）。 */
    default List<PersonnelCertificateDO> selectByPersonIds(List<Long> personIds) {
        if (personIds == null || personIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<PersonnelCertificateDO>()
                .in(PersonnelCertificateDO::getPersonId, personIds)
                .eq(PersonnelCertificateDO::getIsCurrent, true));
    }
}
