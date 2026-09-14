package cn.iocoder.txgy.module.bidding.service.externalfetch.fetch;

// 规范依据：《外部信息获取 v1.0》§合规（按站点 robots 约定抓取）；用户口径 2026-09-10「反爬策略不足、合规性」。
// 业务目标：抓取前检查目标站点 robots.txt 的 Disallow 规则，避免"能抓到就抓"的合规风险
//   （先前的瓶颈是：源表有 robots_ok 字段，但代码里从未真正检查过 robots）。
// 为什么可失败放行：robots.txt 不可达（超时/404/5xx）不构成"明确禁止"，
//   合规实践通常允许继续抓取；本实现按"放行 + WARN 一次 + 可审计"处理，并在配置里可整体关闭。

import cn.iocoder.txgy.module.bidding.infra.config.CrawlHttpProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * robots.txt 合规检查（按 host 缓存；默认 6 小时）。
 *
 * <p>实现范围：支持最常见形态——`User-agent` 分组下的 `Disallow`/`Allow` 路径前缀规则，
 * 以最长匹配优先（Allow 与 Disallow 同长时 Allow 优先，与主流爬虫一致）；通配符 `*`/`$` 暂不支持，
 * 遇到含通配符的规则按"字面前缀"处理（偏保守：可能多禁，不会少禁）。
 */
@Slf4j
@Component
public class RobotsGuard {

    @Resource
    private HttpFetchClient httpFetchClient;
    @Resource
    private CrawlHttpProperties props;

    private final Map<String, CachedRules> cache = new ConcurrentHashMap<>();

    /** 是否允许抓取该 URL（配置关闭 robots 检查、或非 http(s)、或 robots 不可达 → 放行） */
    public boolean isAllowed(String url) {
        if (props != null && !props.isRobotsEnabled()) {
            return true;
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            return true; // 非法 URL 交由上层白名单/下载校验处理
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (host == null || scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            return true; // 非 http(s)（如平台内部文件服务）不适用 robots
        }
        Rules rules = rulesOf(scheme + "://" + host);
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        boolean allowed = rules.allowed(path);
        if (!allowed) {
            log.warn("[robots] 目标路径被 robots.txt 禁止抓取，跳过：host={} path={}", host, path);
        }
        return allowed;
    }

    private Rules rulesOf(String origin) {
        CachedRules cached = cache.get(origin);
        long ttlMs = (props == null ? 360 : Math.max(1, props.getRobotsCacheMinutes())) * 60_000L;
        if (cached != null && System.currentTimeMillis() - cached.loadedAtMs < ttlMs) {
            return cached.rules;
        }
        Rules rules = load(origin);
        cache.put(origin, new CachedRules(rules, System.currentTimeMillis()));
        return rules;
    }

    private Rules load(String origin) {
        String robotsUrl = origin + "/robots.txt";
        try {
            String text = httpFetchClient.getText(robotsUrl);
            return Rules.parse(text);
        } catch (Exception e) {
            log.warn("[robots] robots.txt 获取失败（按放行处理，并记本次结果）：{} 原因={}", robotsUrl, e.getMessage());
            return Rules.empty();
        }
    }

    private static class CachedRules {
        final Rules rules;
        final long loadedAtMs;

        CachedRules(Rules rules, long loadedAtMs) {
            this.rules = rules;
            this.loadedAtMs = loadedAtMs;
        }
    }

    /** 解析结果（路径前缀规则集） */
    static class Rules {
        final List<String> disallow = new ArrayList<>();
        final List<String> allow = new ArrayList<>();

        static Rules empty() {
            return new Rules();
        }

        static Rules parse(String text) {
            Rules rules = new Rules();
            if (text == null) {
                return rules;
            }
            boolean inStarGroup = false;
            for (String raw : text.split("\\r?\\n")) {
                String line = raw.split("#")[0].trim();
                if (line.isEmpty()) {
                    continue;
                }
                int idx = line.indexOf(':');
                if (idx <= 0) {
                    continue;
                }
                String key = line.substring(0, idx).trim().toLowerCase();
                String value = line.substring(idx + 1).trim();
                if (key.equals("user-agent")) {
                    // 只采集 "*" 组（我们以匿名爬虫身份抓取）；显式点名本爬虫 UA 的组同样生效
                    inStarGroup = value.equals("*") || value.toLowerCase().contains("txgy");
                    continue;
                }
                if (!inStarGroup) {
                    continue;
                }
                if (key.equals("disallow") && !value.isEmpty()) {
                    rules.disallow.add(normalize(value));
                } else if (key.equals("allow") && !value.isEmpty()) {
                    rules.allow.add(normalize(value));
                }
            }
            return rules;
        }

        /** 通配符保守处理：截断到第一个 '*' 之前（宁可多禁，不少禁） */
        private static String normalize(String pattern) {
            int star = pattern.indexOf('*');
            String p = star >= 0 ? pattern.substring(0, star) : pattern;
            int dollar = p.indexOf('$');
            if (dollar >= 0) {
                p = p.substring(0, dollar);
            }
            return p.isEmpty() ? "/" : p;
        }

        /** 最长匹配优先（Allow 与 Disallow 等长时 Allow 优先） */
        boolean allowed(String path) {
            String bestDisallow = longestMatch(disallow, path);
            String bestAllow = longestMatch(allow, path);
            if (bestDisallow == null) {
                return true;
            }
            return bestAllow != null && bestAllow.length() >= bestDisallow.length();
        }

        private static String longestMatch(List<String> patterns, String path) {
            String best = null;
            for (String p : patterns) {
                if (path.startsWith(p) && (best == null || p.length() > best.length())) {
                    best = p;
                }
            }
            return best;
        }
    }

}
