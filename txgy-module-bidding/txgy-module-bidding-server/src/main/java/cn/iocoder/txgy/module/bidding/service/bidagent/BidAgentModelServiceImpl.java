package cn.iocoder.txgy.module.bidding.service.bidagent;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentModelVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentModelDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidAgentModelMapper;
import cn.iocoder.txgy.module.bidding.framework.bidagent.LlmConfig;
import cn.iocoder.txgy.module.bidding.framework.bidagent.OpenAiCompatibleChatClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_MODEL_NAME_DUPLICATE;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_MODEL_NOT_EXISTS;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_RUNTIME_CONFIG_MISSING;

@Service
@Slf4j
public class BidAgentModelServiceImpl implements BidAgentModelService {

    @Resource
    private BidAgentModelMapper modelMapper;
    @Resource
    private OpenAiCompatibleChatClient chatClient;

    @Override
    public List<BidAgentModelVO.RespVO> list() {
        // 平台默认配置（tenant_id=0）与租户自建配置合并（租户优先），避免管理页看不到全局默认模型
        java.util.Map<String, BidAgentModelDO> merged = new java.util.LinkedHashMap<>();
        for (BidAgentModelDO item : cn.iocoder.txgy.framework.tenant.core.util.TenantUtils.execute(0L,
                modelMapper::selectListOrdered)) {
            merged.put(item.getName(), item);
        }
        Long tenantId = cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.getTenantId();
        if (tenantId != null && tenantId != 0L) {
            for (BidAgentModelDO item : modelMapper.selectListOrdered()) {
                merged.put(item.getName(), item);
            }
        }
        List<BidAgentModelVO.RespVO> result = new ArrayList<>();
        for (BidAgentModelDO item : merged.values()) {
            result.add(toRespVO(item));
        }
        return result;
    }

    @Override
    public BidAgentModelVO.RespVO get(Long id) {
        BidAgentModelDO item = selectByIdWithPlatformFallback(id);
        if (item == null) {
            throw exception(BID_AGENT_MODEL_NOT_EXISTS);
        }
        return toRespVO(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(BidAgentModelVO.SaveReqVO reqVO) {
        Long tenantId = TenantContextHolder.getTenantId();
        BidAgentModelDO item;
        boolean isNewRow = reqVO.getId() == null;
        if (isNewRow) {
            item = new BidAgentModelDO();
        } else {
            item = modelMapper.selectById(reqVO.getId());
            if (item == null) {
                // 企业租户编辑平台默认配置（tenant_id=0）时，复制为租户自建行（列表按名合并、租户优先），
                // 不修改平台行，避免一个租户的改动影响全局默认配置。
                BidAgentModelDO platform = TenantUtils.execute(0L, () -> modelMapper.selectById(reqVO.getId()));
                if (platform == null) {
                    throw exception(BID_AGENT_MODEL_NOT_EXISTS);
                }
                // 租户已自建同名行（界面过期仍提交平台行 id）时，直接改租户行，避免撞同名唯一键
                BidAgentModelDO tenantOwn = selectTenantRowByName(tenantId, platform.getName());
                if (tenantOwn != null) {
                    item = tenantOwn;
                } else {
                    item = copyForTenant(platform);
                    isNewRow = true;
                }
            }
        }
        item.setName(reqVO.getName());
        item.setProvider(StrUtil.blankToDefault(reqVO.getProvider(), "openai-compat"));
        item.setBaseUrl(reqVO.getBaseUrl());
        item.setModel(reqVO.getModel());
        // 留空=不修改（避免前端脱敏值回写覆盖真 key）；复制自平台行时保留平台 key
        if (StrUtil.isNotBlank(reqVO.getApiKey()) && !reqVO.getApiKey().contains("****")) {
            item.setApiKey(reqVO.getApiKey().trim());
        }
        item.setTemperature(reqVO.getTemperature());
        item.setMaxTokens(reqVO.getMaxTokens());
        item.setTimeoutSeconds(reqVO.getTimeoutSeconds());
        item.setMaxRetries(reqVO.getMaxRetries());
        item.setEnabled(reqVO.getEnabled() == null ? 1 : reqVO.getEnabled());
        item.setRemark(reqVO.getRemark());
        if (isNewRow) {
            item.setTenantId(tenantId == null ? 0L : tenantId);
            // 新增（含复制平台行为租户行）前，物理清理同名已逻辑删除的历史行，避免撞唯一键 (tenant_id, name, deleted)
            modelMapper.purgeDeletedByName(item.getName());
            try {
                modelMapper.insert(item);
            } catch (DuplicateKeyException ex) {
                // 并发/异常输入兜底：同名活行已存在时返回业务错误而非 500
                throw exception(BID_AGENT_MODEL_NAME_DUPLICATE, item.getName());
            }
        } else {
            modelMapper.updateById(item);
        }
        return item.getId();
    }

    @Override
    public BidAgentModelVO.TestRespVO test(BidAgentModelVO.TestReqVO reqVO) {
        BidAgentModelVO.TestRespVO resp = new BidAgentModelVO.TestRespVO();
        try {
            LlmConfig config;
            if (reqVO.getId() != null) {
                BidAgentModelDO item = selectByIdWithPlatformFallback(reqVO.getId());
                if (item == null) {
                    throw exception(BID_AGENT_MODEL_NOT_EXISTS);
                }
                config = toConfig(item);
                if (StrUtil.isNotBlank(reqVO.getModel())) {
                    config.setModel(reqVO.getModel());
                }
            } else {
                config = new LlmConfig(reqVO.getBaseUrl(), reqVO.getModel(), reqVO.getApiKey(), null, null,
                        reqVO.getTimeoutSeconds(), null);
            }
            resp.setMessage("连通正常，模型回复：" + chatClient.testConnection(config));
            resp.setOk(true);
        } catch (Exception ex) {
            log.warn("[BidAgentModelServiceImpl][test] 自检失败：{}", ex.getMessage());
            resp.setOk(false);
            resp.setMessage(StrUtil.maxLength(ex.getMessage(), 300));
        }
        return resp;
    }

    @Override
    public LlmConfig resolveEnabledConfig() {
        LlmConfig config = resolveEnabledConfigOrNull();
        if (config == null) {
            throw exception(BID_AGENT_RUNTIME_CONFIG_MISSING,
                    "没有启用的云端模型配置（智能体管理 → 云端模型）");
        }
        if (StrUtil.isBlank(config.getApiKey())) {
            throw exception(BID_AGENT_RUNTIME_CONFIG_MISSING, "云端模型未配置 API Key（智能体管理 → 云端模型）");
        }
        return config;
    }

    @Override
    public BidAgentModelDO resolveEnabledModelOrNull() {
        // 租户优先：租户自建的启用配置存在时用租户的；否则回落到平台默认配置（tenant_id=0）
        BidAgentModelDO item = modelMapper.selectEnabledOne();
        if (item == null) {
            item = TenantUtils.execute(0L, modelMapper::selectEnabledOne);
        }
        return item;
    }

    private LlmConfig resolveEnabledConfigOrNull() {
        BidAgentModelDO item = resolveEnabledModelOrNull();
        return item == null ? null : toConfig(item);
    }

    /** 按 id 读配置：当前租户优先，平台（tenant_id=0）兜底 */
    private BidAgentModelDO selectByIdWithPlatformFallback(Long id) {
        BidAgentModelDO item = modelMapper.selectById(id);
        if (item == null) {
            item = TenantUtils.execute(0L, () -> modelMapper.selectById(id));
        }
        return item;
    }

    /** 复制平台行为租户自建行（保留 apiKey 等完整配置，后续由入参覆盖） */
    private BidAgentModelDO copyForTenant(BidAgentModelDO platform) {
        BidAgentModelDO copy = new BidAgentModelDO();
        copy.setName(platform.getName());
        copy.setProvider(platform.getProvider());
        copy.setBaseUrl(platform.getBaseUrl());
        copy.setModel(platform.getModel());
        copy.setApiKey(platform.getApiKey());
        copy.setTemperature(platform.getTemperature());
        copy.setMaxTokens(platform.getMaxTokens());
        copy.setTimeoutSeconds(platform.getTimeoutSeconds());
        copy.setMaxRetries(platform.getMaxRetries());
        copy.setEnabled(platform.getEnabled());
        copy.setRemark(platform.getRemark());
        return copy;
    }

    /** 当前租户名下按名称查自建行（不含平台行） */
    private BidAgentModelDO selectTenantRowByName(Long tenantId, String name) {
        if (tenantId == null || tenantId == 0L) {
            return null;
        }
        for (BidAgentModelDO row : modelMapper.selectListOrdered()) {
            if (StrUtil.equals(row.getName(), name)) {
                return row;
            }
        }
        return null;
    }

    private LlmConfig toConfig(BidAgentModelDO item) {
        return new LlmConfig(item.getBaseUrl(), item.getModel(), item.getApiKey(),
                item.getTemperature() == null ? BigDecimal.valueOf(0.2) : item.getTemperature(),
                item.getMaxTokens(), item.getTimeoutSeconds(), item.getMaxRetries());
    }

    private BidAgentModelVO.RespVO toRespVO(BidAgentModelDO item) {
        BidAgentModelVO.RespVO vo = BeanUtils.toBean(item, BidAgentModelVO.RespVO.class);
        boolean configured = StrUtil.isNotBlank(item.getApiKey());
        vo.setApiKeyConfigured(configured);
        // 短 Key 防护：长度不足 8 位时整体打码，不返回后缀（Hutool subSuf 对短串会返回原文，等于明文泄露）
        String apiKey = item.getApiKey();
        vo.setApiKeyMask(configured ? (apiKey.length() < 8 ? "****" : "****" + StrUtil.subSuf(apiKey, 4)) : null);
        return vo;
    }
}
