package cn.iocoder.txgy.module.bidding.enums;

import java.util.Map;
import java.util.Set;

/**
 * 规则配置状态（bidding_rule_config.status 的 code 取值）。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§8.1（DRAFT/PUBLISHED/GRAY/ROLLED_BACK + 版本与灰度）；
 * 《招投标模块开发规范》§4.5（状态流转接口化 + 乐观锁）。
 * <p>为什么这样写：配置直接改行为，必须"DRAFT 先存、PUBLISHED 才生效、GRAY 先小范围验证、ROLLED_BACK 可回退"，
 * 否则运维误改一次阈值就会全租户生效，且没有回退路径——这是配置中心最容易造成生产事故的地方。
 * <p>业务目标：改配置像发版一样可控：可预演（草稿）、可灰度、可回滚，且每次变更留痕可追责。
 */
public enum RuleConfigStatusEnum {

    /** 草稿（不参与解析，仅暂存） */
    DRAFT,
    /** 已发布（对全租户生效） */
    PUBLISHED,
    /** 灰度（仅对 gray_scope 命中的租户生效） */
    GRAY,
    /** 已回滚（保留历史，不参与解析） */
    ROLLED_BACK;

    /**
     * 允许的状态迁移表（key=当前状态，value=可达状态）；同状态重复提交按幂等放行。
     *
     * <p>为什么限制迁移：已回滚的版本不允许再被"发布"（否则历史版本语义会被改写），应改为复制成新版本再发布。
     */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            DRAFT.name(), Set.of(PUBLISHED.name(), GRAY.name(), ROLLED_BACK.name()),
            PUBLISHED.name(), Set.of(GRAY.name(), ROLLED_BACK.name()),
            GRAY.name(), Set.of(PUBLISHED.name(), ROLLED_BACK.name()),
            ROLLED_BACK.name(), Set.of());

    public static boolean isTransitionAllowed(String from, String to) {
        if (from == null || to == null || from.equals(to)) {
            return true;
        }
        Set<String> targets = ALLOWED.get(from);
        return targets != null && targets.contains(to);
    }

    /** 是否为可参与解析的状态（只有这两个会被读到线上）。 */
    public boolean isResolvable() {
        return this == PUBLISHED || this == GRAY;
    }
}
