package cn.iocoder.txgy.module.bidding.service.noticeexternal.url;
// 规范依据：设计文档 v0.6 §九（开放重定向/外链协议白名单：下载目标 http(s)+IP 校验）；安全评审意见 2（外链中转+域名白名单）

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlSourceDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal.CrawlSourceMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 外部链接中转守卫：仅放行 http(s)，且目标域名 ∈ 启用采集源的 base_url 域名（含子域名）。
 *
 * <p>公开接口的 origUrl / 附件 origDownloadUrl 不应被前端直接外链（开放重定向/钓鱼），
 * 统一经 /bidding/notice-external/go 中转；白名单数据来自源配置（加载失败按“拒绝”兜底，fail-closed）。
 */
@Slf4j
@Service
public class ExternalRedirectService {

    @Resource
    private CrawlSourceMapper crawlSourceMapper;

    /**
     * 校验并放行跳转目标。
     *
     * @param rawUrl 待跳转 URL（origUrl 或附件溯源链接）
     * @return 校验通过的目标 URL
     * @throws cn.iocoder.txgy.framework.common.exception.ServiceException 非法协议/非法域名 → 106_014
     */
    public String resolve(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_REDIRECT_FORBIDDEN);
        }
        URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (URISyntaxException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_REDIRECT_FORBIDDEN);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            // 协议白名单：拒绝 javascript:/data:/file: 等伪协议（安全评审意见 2/3）
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_REDIRECT_FORBIDDEN);
        }
        String host = uri.getHost();
        if (host == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_REDIRECT_FORBIDDEN);
        }
        String hostLower = host.toLowerCase();
        if (!isAllowed(hostLower)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_REDIRECT_FORBIDDEN);
        }
        return rawUrl;
    }

    private boolean isAllowed(String hostLower) {
        try {
            List<CrawlSourceDO> sources = crawlSourceMapper.selectList(new LambdaQueryWrapper<CrawlSourceDO>()
                    .eq(CrawlSourceDO::getEnabled, Boolean.TRUE));
            Set<String> hosts = new HashSet<>();
            for (CrawlSourceDO source : sources) {
                if (!StringUtils.hasText(source.getBaseUrl())) {
                    continue;
                }
                try {
                    String h = new URI(source.getBaseUrl()).getHost();
                    if (StringUtils.hasText(h)) {
                        hosts.add(h.toLowerCase());
                    }
                } catch (URISyntaxException ignore) {
                    // 配置脏数据不阻断其它源
                }
            }
            return hosts.stream().anyMatch(host -> host.equals(hostLower) || hostLower.endsWith("." + host));
        } catch (Exception e) {
            // 源表不可读（如 DDL 未执行）→ fail-closed：不跳转
            log.error("[isAllowed][host={}] 读取采集源白名单失败，按拒绝处理", hostLower, e);
            return false;
        }
    }

}
