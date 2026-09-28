package cn.iocoder.txgy.module.bidding.service.certificate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateAlertPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateAlertRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertAlertRecordDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import java.util.List;


import cn.iocoder.txgy.framework.ruleconfig.RuleConfigKeys;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertAlertConfigDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertAlertRecordDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertNotifyOutboxDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertAlertConfigMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertAlertRecordMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertNotifyOutboxMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.enums.CertNotifyBizTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.CertNotifySendStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateAlertLevelEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateAlertTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateChangeSourceEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateStatusEnum;
import cn.iocoder.txgy.module.bidding.service.ruleconfig.RuleConfigService;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 证照到期预警服务实现（T 轨；扫描/派发均为按租户执行的定时任务业务方法）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §九（预警配置/记录/档位映射/幂等键）+§十一 表12
 * （发件箱可靠投递：PENDING→SENDING→SUCCESS/FAILED 退避重试→DEAD）；51号 ZZZT（≤30 天置 EXPIRING_SOON）；
 * 《招投标模块开发规范》§4.5（config/records 乐观锁）。
 * <p>设计遵循/针对：① 平台默认常量承担 v3.3“tenant_id NULL=平台默认”行（租户零配置即可用）；
 * ② 幂等键 cert_id:alert_type:yyyyMMdd（v3.3 §九 文案口径）；③ 记录与发件箱同事务写入，
 * 通知“不丢不重”（表12）；④ 发送失败指数退避 1/5/30 分、2/6 时，超限转 DEAD 死信并告警（表12）；
 * ⑤ 渠道发送经 CertNotifyChannelSender 接口隔离（开发规范 §6），默认日志落点闭环。
 */
@Slf4j
@Service
public class CertificateAlertServiceImpl implements CertificateAlertService {

    /** 平台默认阈值（天），升序存储：≤7→CRITICAL、≤30→WARNING、其余（≤60/90）→INFO（v3.3 §九） */
    private static final List<Integer> DEFAULT_THRESHOLDS = List.of(7, 30, 60, 90);
    /** 平台默认通知渠道 */
    private static final List<String> DEFAULT_CHANNELS = List.of("EMAIL", "IN_APP");

    /**
     * 规则配置审计场景名：证照到期预警扫描。
     * 与其它场景（QUAL_ALERT_SCAN / SAFETY_PERMIT_ALERT_SCAN / PERSONNEL_ALERT_SCAN / CERT_VERIFY）并列，
     * 用于回答"这条预警是按哪次、哪版配置判出来的"——同一阈值键在不同扫描里语义不同，故场景必须区分。
     */
    private static final String BIZ_SCENE_CERT_ALERT_SCAN = "CERT_ALERT_SCAN";
    /** 默认接收人占位（对接用户中心后由配置/组织架构决定，见 DDL 注释） */
    private static final String DEFAULT_RECEIVER = "TENANT_ADMIN";
    // 注：原此处硬编码 EXPIRING_SOON_DAYS = 30（"扫描时 ≤30 天置 EXPIRING_SOON"），而契约登记的键
    // cert.expiring.window.days（=60）才是"即将到期"的正式口径——运维改配置不生效（假配置）。
    // 现已改为经 RuleConfigService 解析（见 resolveExpiringSoonDays()），常量删除。
    /** 重试退避间隔（分）：1/5/30 分、2/6 时（v3.3 §十一 表12） */
    private static final int[] BACKOFF_MINUTES = {1, 5, 30, 120, 360};
    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private RuleConfigService ruleConfigService;

    @Resource
    private CertAlertConfigMapper certAlertConfigMapper;
    @Resource
    private CertAlertRecordMapper certAlertRecordMapper;
    @Resource
    private CertNotifyOutboxMapper certNotifyOutboxMapper;
    @Resource
    private CertificateInfoMapper certificateInfoMapper;
    @Resource
    private CertificateService certificateService;
    @Resource
    private CertNotifyChannelSender certNotifyChannelSender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scanExpiryAlerts(LocalDate today) {
        Config cfg = effectiveConfig();
        int expiringSoonDays = resolveExpiringSoonDays();
        if (!cfg.enabled || cfg.thresholds.isEmpty()) {
            return 0;
        }
        // 候选上界取"最大预警档位"与"状态流转窗口"的**较大者**：只按档位取会让
        // cert.expiring.status.days 配得比最大档位大时静默失效（窗口内的证照根本查不出来，
        // 表现为"配置改了没反应"）——生效路径上的截断与不接线同样是假配置。
        int candidateUpperBound = Math.max(cfg.thresholds.get(cfg.thresholds.size() - 1), expiringSoonDays);
        List<CertificateInfoDO> candidates = certificateInfoMapper.selectExpiringCandidates(today,
                candidateUpperBound);
        int created = 0;
        for (CertificateInfoDO cert : candidates) {
            if (cert.getValidTo() == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, cert.getValidTo());
            // 说明：expiringSoonDays 在循环外解析一次（每张证照解析会产生一条审计行，且结论相同）
            if (days <= 0) {
                continue; // 已到期交由过期处理任务（后续里程碑），不在此预警
            }
            String level = levelFor(days, cfg.thresholds);
            String idempotentKey = cert.getId() + ":" + CertificateAlertTypeEnum.EXPIRY.name() + ":"
                    + KEY_DATE.format(today);
            if (certAlertRecordMapper.selectByIdempotentKey(idempotentKey) != null) {
                continue; // 当日已触发，重跑幂等跳过
            }
            created += createAlertAndOutbox(cert, days, level, idempotentKey, cfg, today);
            if (CertificateStatusEnum.ACTIVE.name().equals(cert.getCertStatus())
                    && days <= expiringSoonDays) {
                certificateService.changeStatus(cert.getId(), CertificateStatusEnum.EXPIRING_SOON.name(),
                        cert.getVersion(), "到期预警扫描（≤" + expiringSoonDays + " 天）",
                        CertificateChangeSourceEnum.SYSTEM, null);
            }
        }
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int dispatchPending(int limit, LocalDateTime now) {
        List<CertNotifyOutboxDO> rows = certNotifyOutboxMapper.selectDispatchable(now, limit);
        for (CertNotifyOutboxDO row : rows) {
            // 取件 CAS：PENDING/FAILED 且到重试时间才允许流转 SENDING（v3.3 §十一 表12
            // 「靠状态条件更新防重复取件」）；影响行数为 0 = 已被其他实例取走，跳过不重复发送（§4.1）。
            if (certNotifyOutboxMapper.takeDispatchable(row.getId(), now) == 0) {
                continue;
            }
            try {
                certNotifyChannelSender.send(row.getChannel(), row.getReceiver(), row.getPayload());
                row.setSendStatus(CertNotifySendStatusEnum.SUCCESS.name());
                row.setSentAt(now);
                row.setNextRetryAt(null);
                row.setLastError(null);
                certNotifyOutboxMapper.updateById(row);
                if (CertNotifyBizTypeEnum.ALERT.name().equals(row.getBizType())) {
                    certAlertRecordMapper.updateNotifyStatus(row.getBizId(),
                            CertNotifySendStatusEnum.SUCCESS.name());
                }
            } catch (Exception e) {
                log.warn("[cert-notify][outbox={}] 发送失败：{}", row.getId(), e.getMessage());
                int retried = (row.getRetryCount() == null ? 0 : row.getRetryCount()) + 1;
                row.setRetryCount(retried);
                if (retried >= (row.getMaxRetry() == null ? 5 : row.getMaxRetry())) {
                    row.setSendStatus(CertNotifySendStatusEnum.DEAD.name()); // 死信转人工+告警
                } else {
                    row.setSendStatus(CertNotifySendStatusEnum.FAILED.name());
                    row.setNextRetryAt(now.plusMinutes(backoffMinutes(retried)));
                }
                row.setLastError(e.getMessage() == null ? "unknown" : e.getMessage());
                certNotifyOutboxMapper.updateById(row);
            }
        }
        return rows.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PageResult<CertificateAlertRespVO> getAlertPage(CertificateAlertPageReqVO reqVO) {
        LambdaQueryWrapper<CertAlertRecordDO> query = new LambdaQueryWrapper<CertAlertRecordDO>()
                // 证照类预警在预警表里以 cert_id 定位（refType 为空）；排除资质类与人员类，避免三域清单互相串数据
                .isNull(CertAlertRecordDO::getRefType)
                .isNotNull(CertAlertRecordDO::getCertId)
                .eq(reqVO.getCertId() != null, CertAlertRecordDO::getCertId, reqVO.getCertId())
                .eq(reqVO.getAlertType() != null, CertAlertRecordDO::getAlertType, reqVO.getAlertType())
                .eq(reqVO.getAlertLevel() != null, CertAlertRecordDO::getAlertLevel, reqVO.getAlertLevel())
                .eq(reqVO.getHandled() != null, CertAlertRecordDO::getHandled, reqVO.getHandled())
                .orderByDesc(CertAlertRecordDO::getTriggeredAt)
                .orderByDesc(CertAlertRecordDO::getId);
        // 按企业过滤：预警表不带企业字段，先取该企业的证照 id 集合（集合为空时直接返回空页，避免 IN () 语法错误）
        if (reqVO.getEntId() != null) {
            List<Long> certIds = certificateInfoMapper.selectList(new LambdaQueryWrapper<CertificateInfoDO>()
                            .eq(CertificateInfoDO::getOrgId, reqVO.getEntId()))
                    .stream().map(CertificateInfoDO::getId).toList();
            if (certIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L);
            }
            query.in(CertAlertRecordDO::getCertId, certIds);
        }
        Page<CertAlertRecordDO> page = certAlertRecordMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), query);
        return new PageResult<>(page.getRecords().stream().map(CertificateAlertServiceImpl::toAlertVO).toList(),
                page.getTotal());
    }

    private static CertificateAlertRespVO toAlertVO(CertAlertRecordDO record) {
        CertificateAlertRespVO vo = new CertificateAlertRespVO();
        vo.setId(record.getId());
        vo.setVersion(record.getVersion());
        vo.setCertId(record.getCertId());
        vo.setRefType(record.getRefType());
        vo.setRefId(record.getRefId());
        vo.setRefName(record.getRefName());
        vo.setAlertType(record.getAlertType());
        vo.setAlertLevel(record.getAlertLevel());
        vo.setAlertMessage(record.getAlertMessage());
        vo.setTriggeredAt(record.getTriggeredAt());
        vo.setHandled(record.getHandled());
        vo.setHandledBy(record.getHandledBy());
        vo.setHandledAt(record.getHandledAt());
        vo.setHandleNote(record.getHandleNote());
        return vo;
    }

    @Override
    public void markAlertHandled(Long alertId, Integer version, Long handledBy, String handleNote) {
        CertAlertRecordDO record = certAlertRecordMapper.selectById(alertId);
        if (record == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_ALERT_RECORD_NOT_EXISTS);
        }
        if (version == null || !version.equals(record.getVersion())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_ALERT_RECORD_MODIFIED);
        }
        record.setHandled(Boolean.TRUE);
        record.setHandledBy(handledBy);
        record.setHandledAt(LocalDateTime.now());
        record.setHandleNote(handleNote);
        if (certAlertRecordMapper.updateById(record) == 0) { // @Version CAS 兜底
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_ALERT_RECORD_MODIFIED);
        }
    }

    // ========== 内部工具 ==========

    /**
     * "即将到期"窗口（天）：扫描时距有效期止 ≤ 该值且状态为 ACTIVE 的证照，置为 EXPIRING_SOON。
     *
     * <p>规范出处：51 号文 ZZZT 口径（扫描 ≤30 天置"即将到期"）；键 {@code cert.expiring.status.days}（本键新增）。
     *
     * <p>为什么用**新键**而不是直接接 {@code cert.expiring.window.days}(=60)：
     * ① 后者是**展示口径**（列表角标/筛选），契约 §3.11 明确由前端传窗口天数、**后端有意不读**；
     * ② 本处是**状态流转口径**（把证照状态改成 EXPIRING_SOON），改宽会动全租户状态、可能联动其它流程。
     * 两者语义与风险都不同，合并会让"调角标"顺带"改状态"。故新键默认值取原硬编码值 30 —— **行为不变**，
     * 只是把写死的数字变成可配（原门禁欠账表里正是这条：擅自接到 60 会改变全租户状态流转口径）。
     * 原常量 EXPIRING_SOON_DAYS 删除，门禁欠账同步划掉。
     *
     * <p>解析失败即抛错（不静默回落）：本键写入时已由 validateFormat 校验为 INT，损坏只可能来自绕过接口的写库，
     * 此时"按某个默认值继续跑"会把错误藏起来——宁可这次扫描失败并在日志/Job 里暴露。
     */
    private int resolveExpiringSoonDays() {
        String value = String.valueOf(ruleConfigService.resolveWithAudit(
                RuleConfigKeys.CERT_EXPIRING_STATUS_DAYS, BIZ_SCENE_CERT_ALERT_SCAN).getValue());
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_VALUE_FORMAT_INVALID,
                    RuleConfigKeys.CERT_EXPIRING_STATUS_DAYS);
        }
    }

    /** 有效配置（含平台默认回落）。 */
    /**
     * 有效配置（含平台默认回落）。
     *
     * <p>规范出处：契约《电子证照模块-前端接口契约》规则键表 —— `cert.alert.thresholds`「多级预警提前天数 → 预警设置页多档输入 + 展示」。
     *
     * <p>为什么阈值必须走规则配置中心解析（而不是读 cert_alert_config 表）：
     * 契约把"预警设置页"的编辑对象登记为规则键 `cert.alert.thresholds`（该键有审计、版本、灰度、回滚），
     * 但本方法原来只读 {@code cert_alert_config} 表 —— 而**全仓没有任何端点能写那张表**
     * （控制器层搜不到预警设置端点）。两者叠加的后果是：用户在设置页改阈值**对预警毫无影响**，
     * 真实生效的一直是本类的硬编码常量 {@link #DEFAULT_THRESHOLDS}——即"看得见的旋钮没接线，真实的旋钮没人能拧"。
     * 改为经 {@link RuleConfigService#resolveWithAudit} 解析后：设置页的修改真正生效，且每次扫描取配置都留审计行。
     * 生效值不变（规则键的代码兜底同为 [90,60,30,7]，与 {@link #DEFAULT_THRESHOLDS} 同值），故本次是"接通旋钮"而非改口径。
     *
     * <p>表行仍承担"运营开关 + 通知渠道"（开关属运维参数，渠道是投递方式，均非业务口径）；
     * 阈值不再从表读——表没有写入入口，读了只会读到死值，反而掩盖配置不生效的问题。
     */
    private Config effectiveConfig() {
        CertAlertConfigDO row = certAlertConfigMapper.selectGlobal(CertificateAlertTypeEnum.EXPIRY.name());
        // getValue() 是 Object（值类型由 value_type 决定），这里按 JSON 文本解析
        String resolved = String.valueOf(ruleConfigService.resolveWithAudit(
                RuleConfigKeys.CERT_ALERT_THRESHOLDS, BIZ_SCENE_CERT_ALERT_SCAN).getValue());
        // DEFAULT_THRESHOLDS 仅作"解析失败的最后一道兜底"（规则值理论上必可解析，因其代码兜底也在登记表里）
        List<Integer> thresholds = parseInts(resolved, DEFAULT_THRESHOLDS);
        if (row == null) {
            return new Config(true, thresholds, DEFAULT_CHANNELS);
        }
        List<String> channels = parseStrs(row.getNotifyChannels(), DEFAULT_CHANNELS);
        return new Config(Boolean.TRUE.equals(row.getEnabled()), thresholds, channels);
    }

    /** 建预警记录 + 每渠道一条发件箱（同事务；幂等键已查重）。返回 1。 */
    private int createAlertAndOutbox(CertificateInfoDO cert, long days, String level, String idempotentKey,
                                     Config cfg, LocalDate today) {
        String message = String.format("证照《%s》（编号对外脱敏）将于 %d 天后到期（有效至 %s），请及时续期/更新",
                cert.getCertName(), days, cert.getValidTo());
        CertAlertRecordDO record = new CertAlertRecordDO();
        record.setCertId(cert.getId());
        record.setAlertType(CertificateAlertTypeEnum.EXPIRY.name());
        record.setAlertLevel(level);
        record.setIdempotentKey(idempotentKey);
        record.setAlertMessage(message);
        record.setTriggeredAt(LocalDateTime.now());
        record.setNotifyChannel(writeJson(cfg.channels));
        record.setNotifyTarget(DEFAULT_RECEIVER);
        record.setNotifyStatus(CertNotifySendStatusEnum.PENDING.name());
        certAlertRecordMapper.insert(record);
        Map<String, Object> payload = Map.of(
                "bizType", CertNotifyBizTypeEnum.ALERT.name(),
                "alertLevel", level,
                "alertMessage", message,
                "certName", cert.getCertName() == null ? "" : cert.getCertName(),
                "certNo", cert.getCertNo() == null ? "" : cert.getCertNo(),
                "validTo", String.valueOf(cert.getValidTo()));
        for (String channel : cfg.channels) {
            CertNotifyOutboxDO outbox = new CertNotifyOutboxDO();
            outbox.setBizType(CertNotifyBizTypeEnum.ALERT.name());
            outbox.setBizId(record.getId());
            outbox.setChannel(channel);
            outbox.setReceiver(DEFAULT_RECEIVER);
            outbox.setPayload(writeJson(payload));
            outbox.setSendStatus(CertNotifySendStatusEnum.PENDING.name());
            outbox.setRetryCount(0);
            outbox.setMaxRetry(5);
            certNotifyOutboxMapper.insert(outbox);
        }
        return 1;
    }

    /** 档位映射（v3.3 §九）：阈值升序后 ≤t0→CRITICAL、≤t1→WARNING、否则 INFO。 */
    private String levelFor(long daysLeft, List<Integer> thresholdsAsc) {
        if (daysLeft <= thresholdsAsc.get(0)) {
            return CertificateAlertLevelEnum.CRITICAL.name();
        }
        if (thresholdsAsc.size() >= 2 && daysLeft <= thresholdsAsc.get(1)) {
            return CertificateAlertLevelEnum.WARNING.name();
        }
        return CertificateAlertLevelEnum.INFO.name();
    }

    private int backoffMinutes(int retried) {
        int idx = Math.min(retried - 1, BACKOFF_MINUTES.length - 1);
        return BACKOFF_MINUTES[Math.max(idx, 0)];
    }

    private List<Integer> parseInts(String json, List<Integer> fallback) {
        try {
            List<Integer> list = objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, Integer.class));
            List<Integer> sorted = new ArrayList<>(list);
            Collections.sort(sorted);
            return sorted;
        } catch (Exception e) {
            log.warn("[cert-alert] 阈值 JSON 解析失败，用默认：{}", json);
            return new ArrayList<>(fallback);
        }
    }

    private List<String> parseStrs(String json, List<String> fallback) {
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            log.warn("[cert-alert] 渠道 JSON 解析失败，用默认：{}", json);
            return new ArrayList<>(fallback);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    /** 配置视图（默认常量与租户行归一）。 */
    private record Config(boolean enabled, List<Integer> thresholds, List<String> channels) {
    }
}
