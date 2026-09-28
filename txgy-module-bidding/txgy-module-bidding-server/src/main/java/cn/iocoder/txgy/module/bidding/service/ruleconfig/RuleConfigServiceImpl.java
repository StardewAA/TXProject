package cn.iocoder.txgy.module.bidding.service.ruleconfig;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.ruleconfig.RuleConfigKeys;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.bidding.controller.admin.meta.vo.RuleConfigItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ruleconfig.vo.RuleConfigHistoryRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ruleconfig.vo.RuleConfigSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.ruleconfig.BiddingRuleConfigDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.ruleconfig.BiddingRuleConfigHistoryDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.ruleconfig.BiddingRuleConfigHistoryMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.ruleconfig.BiddingRuleConfigMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.RuleConfigStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.RuleConfigValueTypeEnum;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 规则配置服务实现（四级回落解析 + 类型/边界/一致性校验 + 版本与哈希链审计）。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§8.1（解析优先级、value_type、legal_floor、灰度与回滚）、
 * §8.2（审计与哈希链）、§3.2（法定下限拦截）；契约《电子证照模块-前端接口契约-v1.0.md》§1.2。
 * <p>设计遵循/针对：① 解析顺序"租户级 → 平台默认 → 代码兜底"，与既有 cert_alert_config 同范式；
 * ② 发布即"新增一行更高 version_no"，历史版本永不改写（回滚也以新版本复制旧值实现）——审计链因此不可断裂；
 * ③ 校验三道关：值类型格式 → legal_floor 边界 → 跨键一致性（如多级阈值必须严格递减），
 * 把"能配但会配坏"的情况挡在发布前；④ 本地缓存带 TTL，避免热路径每次查库。
 * <p>业务目标：数值参数集中受控、可灰度可回滚、变更可追责，业务代码零硬编码。
 */
@Slf4j
@Service
public class RuleConfigServiceImpl implements RuleConfigService {

    /** 平台默认行的租户编号（与 cert_alert_config 同口径）。 */
    private static final long PLATFORM_TENANT_ID = 0L;

    @Resource
    private BiddingRuleConfigMapper ruleConfigMapper;
    @Resource
    private BiddingRuleConfigHistoryMapper ruleConfigHistoryMapper;

    /** 本地缓存 TTL（秒）；配置为中心化参数，短 TTL + 发布即失效，兼顾性能与生效速度。 */
    @Value("${bidding.rule-config.cache-ttl-seconds:60}")
    private long cacheTtlSeconds;

    /** 解析缓存：key = tenantId:ruleKey，value = 带过期时间的解析结果。 */
    private final Map<String, CachedValue> cache = new ConcurrentHashMap<>();

    // ========== 读取 ==========

    @Override
    public RuleConfigItemRespVO getItem(String ruleKey) {
        Resolved resolved = resolve(ruleKey);
        RuleConfigItemRespVO vo = new RuleConfigItemRespVO();
        vo.setRuleKey(ruleKey);
        vo.setValueType(resolved.valueType);
        vo.setValue(parseByType(resolved.json, resolved.valueType));
        vo.setDescription(resolved.description);
        vo.setRegulationRef(resolved.regulationRef);
        vo.setLegalFloor(resolved.legalFloor);
        vo.setVersionNo(resolved.versionNo);
        vo.setRuleGroup(resolved.ruleGroup);
        vo.setSource(resolved.source);
        return vo;
    }

    @Override
    public List<RuleConfigItemRespVO> listByGroup(String group) {
        Long tenantId = currentTenantId();
        // 先放平台默认，再用租户级覆盖（后者优先级更高）；LinkedHashMap 保证输出顺序稳定便于前端 diff
        Map<String, BiddingRuleConfigDO> merged = new LinkedHashMap<>();
        for (BiddingRuleConfigDO row : ruleConfigMapper.selectByGroup(PLATFORM_TENANT_ID, group)) {
            merged.put(row.getRuleKey(), row);
        }
        if (tenantId != null && tenantId != PLATFORM_TENANT_ID) {
            for (BiddingRuleConfigDO row : ruleConfigMapper.selectByGroup(tenantId, group)) {
                merged.put(row.getRuleKey(), row);
            }
        }
        List<RuleConfigItemRespVO> result = new ArrayList<>();
        merged.values().forEach(row -> result.add(toItem(row)));
        // 兜底补齐：表里尚未初始化的键也要下发，否则前端配置页会缺项（值来自代码兜底，来源标记 FALLBACK）
        for (String fallbackKey : RuleConfigKeys.allFallbacks().keySet()) {
            if (merged.containsKey(fallbackKey)
                    || (group != null && !group.isBlank() && !groupMatches(fallbackKey, group))) {
                continue;
            }
            result.add(getItem(fallbackKey));
        }
        return result;
    }

    @Override
    public int getInt(String ruleKey) {
        Object value = getItem(ruleKey).getValue();
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_VALUE_FORMAT_INVALID, ruleKey);
    }

    @Override
    public long getLong(String ruleKey) {
        return getInt(ruleKey);
    }

    @Override
    public BigDecimal getDecimal(String ruleKey) {
        Object value = getItem(ruleKey).getValue();
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text && !text.isBlank()) {
            return new BigDecimal(text);
        }
        throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_VALUE_FORMAT_INVALID, ruleKey);
    }

    @Override
    public boolean getBool(String ruleKey) {
        Object value = getItem(ruleKey).getValue();
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_VALUE_FORMAT_INVALID, ruleKey);
    }

    @Override
    public String getString(String ruleKey) {
        Object value = getItem(ruleKey).getValue();
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public List<Integer> getIntList(String ruleKey) {
        // 直接读原始 JSON 文本再按目标类型解析：避免先转 Object 再转 List 的二次开销与类型擦除问题
        List<Integer> list = JsonUtils.parseArray(resolve(ruleKey).json(), Integer.class);
        return list == null ? Collections.emptyList() : list;
    }

    @Override
    public Map<String, Integer> getIntMap(String ruleKey) {
        Map<?, ?> raw = JsonUtils.parseObject(resolve(ruleKey).json(), Map.class);
        return raw == null ? Collections.emptyMap() : castIntMap(raw);
    }

    // ========== 写入 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveOrPublish(RuleConfigSaveReqVO reqVO) {
        if (reqVO.getChangeReason() == null || reqVO.getChangeReason().isBlank()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_CHANGE_REASON_REQUIRED);
        }
        if (!RuleConfigValueTypeEnum.isValid(reqVO.getValueType())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_VALUE_TYPE_INVALID, reqVO.getValueType());
        }
        String targetStatus = reqVO.getStatus() == null || reqVO.getStatus().isBlank()
                ? RuleConfigStatusEnum.PUBLISHED.name() : reqVO.getStatus();
        if (!RuleConfigStatusEnum.valueOf(targetStatus).isResolvable()
                && !RuleConfigStatusEnum.DRAFT.name().equals(targetStatus)) {
            // 只允许 DRAFT / PUBLISHED / GRAY 作为写入目标；ROLLED_BACK 由回滚接口产生
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_STATUS_INVALID, "-", targetStatus);
        }

        Long tenantId = Objects.requireNonNullElse(currentTenantId(), PLATFORM_TENANT_ID);
        BiddingRuleConfigDO latest = ruleConfigMapper.selectLatest(tenantId, reqVO.getRuleKey());
        if (latest != null && !RuleConfigStatusEnum.isTransitionAllowed(latest.getStatus(), targetStatus)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_STATUS_INVALID,
                    latest.getStatus(), targetStatus);
        }

        // 三道校验：格式 → 边界 → 跨键一致性（顺序固定，先报最小颗粒度的问题）
        validateFormat(reqVO.getRuleKey(), reqVO.getValueType(), reqVO.getConfigValue());
        validateBound(reqVO.getRuleKey(), reqVO.getValueType(), reqVO.getConfigValue(), reqVO.getLegalFloor());
        validateConsistency(reqVO.getRuleKey(), reqVO.getValueType(), reqVO.getConfigValue());

        int nextVersion = latest == null ? 1 : latest.getVersionNo() + 1;
        BiddingRuleConfigDO row = new BiddingRuleConfigDO();
        row.setId(IdWorker.getId());
        row.setTenantId(tenantId);
        row.setRuleKey(reqVO.getRuleKey());
        row.setRuleGroup(reqVO.getRuleGroup() != null ? reqVO.getRuleGroup()
                : (latest != null ? latest.getRuleGroup() : null));
        row.setValueType(reqVO.getValueType());
        row.setConfigValue(reqVO.getConfigValue());
        row.setLegalFloor(reqVO.getLegalFloor() != null ? reqVO.getLegalFloor()
                : (latest != null ? latest.getLegalFloor() : null));
        row.setDescription(reqVO.getDescription() != null ? reqVO.getDescription()
                : (latest != null ? latest.getDescription() : null));
        row.setRegulationRef(reqVO.getRegulationRef() != null ? reqVO.getRegulationRef()
                : (latest != null ? latest.getRegulationRef() : null));
        row.setVersionNo(nextVersion);
        row.setStatus(targetStatus);
        row.setGrayScope(reqVO.getGrayScope());
        row.setEffectiveFrom(reqVO.getEffectiveFrom());
        row.setEffectiveTo(reqVO.getEffectiveTo());
        row.setVersion(0);
        ruleConfigMapper.insert(row);

        writeHistory(row, latest, RuleConfigStatusEnum.GRAY.name().equals(targetStatus) ? "GRAY"
                : (latest == null ? "CREATE" : "PUBLISH"), reqVO.getChangeReason());
        evictCache();
        log.info("[rule-config][save] key={} tenant={} version={} status={}",
                row.getRuleKey(), tenantId, nextVersion, targetStatus);
        return row.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long rollback(String ruleKey, Integer targetVersion, String changeReason) {
        if (changeReason == null || changeReason.isBlank()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_CHANGE_REASON_REQUIRED);
        }
        Long tenantId = Objects.requireNonNullElse(currentTenantId(), PLATFORM_TENANT_ID);
        List<BiddingRuleConfigDO> versions = ruleConfigMapper.selectVersions(tenantId, ruleKey);
        BiddingRuleConfigDO target = versions.stream()
                .filter(v -> Objects.equals(v.getVersionNo(), targetVersion))
                .findFirst()
                .orElseThrow(() -> ServiceExceptionUtil.exception(
                        ErrorCodeConstants.RULE_CONFIG_NOT_EXISTS, ruleKey + "@v" + targetVersion));
        BiddingRuleConfigDO latest = versions.isEmpty() ? null : versions.get(0);

        // 回滚 = 以"新版本复制旧值"，历史版本保持只读（审计链不可改写）
        BiddingRuleConfigDO row = new BiddingRuleConfigDO();
        row.setId(IdWorker.getId());
        row.setTenantId(tenantId);
        row.setRuleKey(ruleKey);
        row.setRuleGroup(target.getRuleGroup());
        row.setValueType(target.getValueType());
        row.setConfigValue(target.getConfigValue());
        row.setLegalFloor(target.getLegalFloor());
        row.setDescription(target.getDescription());
        row.setRegulationRef(target.getRegulationRef());
        row.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1);
        row.setStatus(RuleConfigStatusEnum.PUBLISHED.name());
        row.setVersion(0);
        ruleConfigMapper.insert(row);

        writeHistory(row, latest, "ROLLBACK",
                changeReason + "（回滚至 v" + targetVersion + "）");
        evictCache();
        log.warn("[rule-config][rollback] key={} tenant={} to=v{} new=v{}",
                ruleKey, tenantId, targetVersion, row.getVersionNo());
        return row.getId();
    }

    @Override
    public List<RuleConfigHistoryRespVO> history(String ruleKey) {
        return ruleConfigHistoryMapper.selectByRuleKey(ruleKey).stream().map(row -> {
            RuleConfigHistoryRespVO vo = new RuleConfigHistoryRespVO();
            vo.setId(row.getId());
            vo.setRuleKey(row.getRuleKey());
            vo.setFromVersion(row.getFromVersion());
            vo.setToVersion(row.getToVersion());
            vo.setFromValue(row.getFromValue());
            vo.setToValue(row.getToValue());
            vo.setChangeAction(row.getChangeAction());
            vo.setChangedBy(row.getChangedBy());
            vo.setChangedAt(row.getChangedAt());
            vo.setChangeReason(row.getChangeReason());
            vo.setRecordHash(row.getRecordHash());
            vo.setPrevHash(row.getPrevHash());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void evictCache() {
        cache.clear();
    }

    @Override
    public void validateAlertThresholds(String certCategory, List<Integer> thresholds) {
        if (thresholds == null || thresholds.isEmpty()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_VALUE_FORMAT_INVALID,
                    RuleConfigKeys.CERT_ALERT_THRESHOLDS);
        }
        Map<String, Integer> floors = getIntMap(RuleConfigKeys.CERT_ALERT_LEGAL_FLOOR_DAYS);
        Integer floor = floors.get(certCategory);
        if (floor == null) {
            return;
        }
        int maxThreshold = thresholds.stream().filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(0);
        if (maxThreshold < floor) {
            RuleConfigItemRespVO item = getItem(RuleConfigKeys.CERT_ALERT_LEGAL_FLOOR_DAYS);
            String ref = item.getRegulationRef() == null ? "法定办理提前期" : item.getRegulationRef();
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.LEGAL_FLOOR_VIOLATED, floor, ref);
        }
    }

    // ========== 内部方法 ==========

    /** 四级回落解析：租户级 → 平台默认 → 代码兜底。 */
    private Resolved resolve(String ruleKey) {
        Long tenantId = currentTenantId();
        String cacheKey = tenantId + ":" + ruleKey;
        CachedValue cached = cache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expireAt > now) {
            return cached.value;
        }
        Resolved resolved = null;
        if (tenantId != null && tenantId != PLATFORM_TENANT_ID) {
            resolved = fromRow(ruleConfigMapper.selectLatest(tenantId, ruleKey), "TENANT");
        }
        if (resolved == null) {
            resolved = fromRow(ruleConfigMapper.selectLatest(PLATFORM_TENANT_ID, ruleKey), "PLATFORM");
        }
        if (resolved == null) {
            String fallback = RuleConfigKeys.fallback(ruleKey);
            if (fallback == null) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_NOT_EXISTS, ruleKey);
            }
            resolved = new Resolved(fallback, inferValueType(fallback), null, null, null, null, null,
                    "FALLBACK");
            log.warn("[rule-config][fallback] key={} 未配置，使用代码兜底值（请尽快初始化配置表）", ruleKey);
        }
        cache.put(cacheKey, new CachedValue(resolved, now + cacheTtlSeconds * 1000));
        return resolved;
    }

    /**
     * 兜底值类型推断（兜底只有 JSON 文本，没有 value_type 元数据）。
     *
     * <p>为什么不能统一按 JSON 处理：兜底 "60" 若不判为 INT，getInt() 会因拿到 String 而抛"值类型不匹配"，
     * 于是"表未初始化"这种最常见的过渡场景反而直接报错——兜底的意义就没了。
     */
    private String inferValueType(String json) {
        String text = json.trim();
        if (text.matches("-?\\d+")) {
            return RuleConfigValueTypeEnum.INT.name();
        }
        if (text.matches("-?\\d+(\\.\\d+)?")) {
            return RuleConfigValueTypeEnum.DECIMAL.name();
        }
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return RuleConfigValueTypeEnum.BOOL.name();
        }
        if (text.startsWith("[") || text.startsWith("{")) {
            return RuleConfigValueTypeEnum.JSON.name();
        }
        return RuleConfigValueTypeEnum.STRING.name();
    }

    /** 行 → 解析结果（过滤未生效/不可解析状态，保证灰度与生效窗口被尊重）。 */
    private Resolved fromRow(BiddingRuleConfigDO row, String source) {
        if (row == null) {
            return null;
        }
        RuleConfigStatusEnum status;
        try {
            status = RuleConfigStatusEnum.valueOf(row.getStatus());
        } catch (IllegalArgumentException ex) {
            return null;
        }
        if (!status.isResolvable()) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (row.getEffectiveFrom() != null && now.isBefore(row.getEffectiveFrom())) {
            return null;
        }
        if (row.getEffectiveTo() != null && now.isAfter(row.getEffectiveTo())) {
            return null;
        }
        return new Resolved(row.getConfigValue(), row.getValueType(), row.getDescription(), row.getRegulationRef(),
                row.getLegalFloor(), row.getVersionNo(), row.getRuleGroup(), source);
    }

    private RuleConfigItemRespVO toItem(BiddingRuleConfigDO row) {
        Resolved resolved = new Resolved(row.getConfigValue(), row.getValueType(), row.getDescription(),
                row.getRegulationRef(), row.getLegalFloor(), row.getVersionNo(), row.getRuleGroup(), "PLATFORM");
        RuleConfigItemRespVO vo = new RuleConfigItemRespVO();
        vo.setRuleKey(row.getRuleKey());
        vo.setValueType(resolved.valueType);
        vo.setValue(parseByType(resolved.json, resolved.valueType));
        vo.setDescription(resolved.description);
        vo.setRegulationRef(resolved.regulationRef);
        vo.setLegalFloor(resolved.legalFloor);
        vo.setVersionNo(resolved.versionNo);
        vo.setRuleGroup(resolved.ruleGroup);
        vo.setSource(resolved.source);
        return vo;
    }

    /** 按声明的类型反序列化（前端拿到的就是数字/布尔/数组，而非字符串）。 */
    private Object parseByType(String json, String valueType) {
        if (json == null) {
            return null;
        }
        RuleConfigValueTypeEnum type;
        try {
            type = RuleConfigValueTypeEnum.valueOf(valueType);
        } catch (IllegalArgumentException ex) {
            return json;
        }
        return switch (type) {
            case INT -> JsonUtils.parseObject(json, Integer.class);
            case DECIMAL -> JsonUtils.parseObject(json, BigDecimal.class);
            case BOOL -> JsonUtils.parseObject(json, Boolean.class);
            case STRING -> {
                String text = json.trim();
                yield text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2
                        ? text.substring(1, text.length() - 1) : text;
            }
            case JSON -> JsonUtils.isJson(json) ? JsonUtils.parseObject(json, Object.class) : json;
        };
    }

    /** 校验第一关：值类型与格式是否匹配（防止把阈值配成字符串导致比较失效）。 */
    private void validateFormat(String ruleKey, String valueType, String configValue) {
        RuleConfigValueTypeEnum type;
        try {
            type = RuleConfigValueTypeEnum.valueOf(valueType);
        } catch (IllegalArgumentException ex) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_VALUE_TYPE_INVALID, valueType);
        }
        boolean ok = switch (type) {
            case INT -> configValue.trim().matches("-?\\d+");
            case DECIMAL -> configValue.trim().matches("-?\\d+(\\.\\d+)?");
            case BOOL -> "true".equalsIgnoreCase(configValue.trim()) || "false".equalsIgnoreCase(configValue.trim());
            case STRING -> !configValue.isBlank();
            case JSON -> JsonUtils.isJson(configValue);
        };
        if (!ok) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_VALUE_FORMAT_INVALID,
                    ruleKey + "=" + configValue + "（声明类型 " + valueType + "）");
        }
    }

    /** 校验第二关：legal_floor 边界（标量看 min/max，数组看 itemMin/itemMax）。 */
    private void validateBound(String ruleKey, String valueType, String configValue, String legalFloor) {
        if (legalFloor == null || legalFloor.isBlank()) {
            return;
        }
        Map<String, Object> bound = JsonUtils.parseObject(legalFloor, Map.class);
        if (bound == null) {
            return;
        }
        RuleConfigValueTypeEnum type = RuleConfigValueTypeEnum.valueOf(valueType);
        if (type.isNumeric()) {
            BigDecimal value = new BigDecimal(configValue.trim());
            checkRange(ruleKey, value, bound.get("min"), bound.get("max"));
            return;
        }
        if (type == RuleConfigValueTypeEnum.JSON) {
            List<Integer> items = JsonUtils.parseArray(configValue, Integer.class);
            if (items == null) {
                return;
            }
            for (Integer item : items) {
                if (item == null) {
                    continue;
                }
                checkRange(ruleKey, new BigDecimal(item), bound.get("itemMin"), bound.get("itemMax"));
            }
        }
    }

    private void checkRange(String ruleKey, BigDecimal value, Object min, Object max) {
        if (min != null && value.compareTo(new BigDecimal(String.valueOf(min))) < 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_BOUND_VIOLATED,
                    ruleKey + "=" + value, "最小值 " + min);
        }
        if (max != null && value.compareTo(new BigDecimal(String.valueOf(max))) > 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_BOUND_VIOLATED,
                    ruleKey + "=" + value, "最大值 " + max);
        }
    }

    /** 校验第三关：跨键/自身一致性（多级阈值必须严格递减，否则档位映射会取错）。 */
    private void validateConsistency(String ruleKey, String valueType, String configValue) {
        if (!RuleConfigKeys.CERT_ALERT_THRESHOLDS.equals(ruleKey)
                && !RuleConfigKeys.CERT_ANNOTATION_WARN_DAYS.equals(ruleKey)) {
            return;
        }
        List<Integer> items = JsonUtils.parseArray(configValue, Integer.class);
        if (items == null || items.isEmpty()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_CONSISTENCY_VIOLATED,
                    "多级预警阈值不得为空");
        }
        for (int i = 1; i < items.size(); i++) {
            Integer prev = items.get(i - 1);
            Integer curr = items.get(i);
            if (prev == null || curr == null || prev <= curr) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_CONSISTENCY_VIOLATED,
                        "多级预警阈值必须严格递减（当前 " + items + "），否则档位映射会取到错误档");
            }
        }
    }

    /** 写审计历史（哈希链：record_hash = SHA-256(业务字段 + prev_hash)）。 */
    private void writeHistory(BiddingRuleConfigDO row, BiddingRuleConfigDO previous, String action, String reason) {
        BiddingRuleConfigHistoryDO prev = ruleConfigHistoryMapper.selectLatest(row.getRuleKey());
        BiddingRuleConfigHistoryDO history = new BiddingRuleConfigHistoryDO();
        history.setId(IdWorker.getId());
        history.setTenantId(row.getTenantId());
        history.setRuleKey(row.getRuleKey());
        history.setFromVersion(previous == null ? null : previous.getVersionNo());
        history.setToVersion(row.getVersionNo());
        history.setFromValue(previous == null ? null : previous.getConfigValue());
        history.setToValue(row.getConfigValue());
        history.setChangeAction(action);
        history.setChangedBy(String.valueOf(currentUserId()));
        history.setChangedAt(LocalDateTime.now());
        history.setChangeReason(reason);
        history.setPrevHash(prev == null ? null : prev.getRecordHash());
        history.setRecordHash(sha256(String.join("|",
                String.valueOf(history.getRuleKey()), String.valueOf(history.getFromVersion()),
                String.valueOf(history.getToVersion()), String.valueOf(history.getFromValue()),
                String.valueOf(history.getToValue()), history.getChangeAction(), history.getChangedBy(),
                String.valueOf(history.getChangedAt()), String.valueOf(history.getChangeReason()),
                String.valueOf(history.getPrevHash()))));
        ruleConfigHistoryMapper.insert(history);
    }

    /** 纯 JDK SHA-256（本模块无 hutool 依赖，不为一个哈希引入第三方包）。 */
    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            log.warn("[rule-config][hash] 计算哈希失败，审计链该条留空: {}", ex.getMessage());
            return null;
        }
    }

    private Long currentTenantId() {
        return TenantContextHolder.getTenantId();
    }

    private Long currentUserId() {
        try {
            return cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
        } catch (Exception ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> castIntMap(Map<?, ?> raw) {
        Map<String, Integer> result = new LinkedHashMap<>();
        raw.forEach((k, v) -> result.put(String.valueOf(k), v == null ? null : Integer.valueOf(String.valueOf(v))));
        return result;
    }

    /** 兜底项是否属于某分组（按键前缀粗判，避免为兜底值再维护一份分组表）。 */
    private boolean groupMatches(String ruleKey, String group) {
        return switch (group) {
            case "CERT" -> ruleKey.startsWith("cert.") && !ruleKey.startsWith("cert.alert.");
            case "ALERT" -> ruleKey.startsWith("cert.alert.") || ruleKey.startsWith("cert.annotation.warn");
            case "PERSONNEL" -> ruleKey.startsWith("personnel.");
            default -> ruleKey.startsWith(group.toLowerCase() + ".");
        };
    }

    /** 解析结果内部载体。 */
    private record Resolved(String json, String valueType, String description, String regulationRef,
                            String legalFloor, Integer versionNo, String ruleGroup, String source) {
    }

    /** 缓存条目（含过期时间戳）。 */
    private record CachedValue(Resolved value, long expireAt) {
    }
}
