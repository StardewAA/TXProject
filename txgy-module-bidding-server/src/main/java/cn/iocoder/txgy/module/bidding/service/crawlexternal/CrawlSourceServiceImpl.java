package cn.iocoder.txgy.module.bidding.service.crawlexternal;
// 规范依据：设计文档 v0.6 §四/§六/§九（白名单源配置、fail-closed、外链域名白名单=启用源）；《招投标模块开发规范》§4.2/红线 11

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo.CrawlSourcePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo.CrawlSourceRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo.CrawlSourceSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlSourceDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlTaskDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal.CrawlSourceMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal.CrawlTaskMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.enums.CrawlTaskStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalStatusEnum;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.channel.ChannelRegistry;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.parser.ParserRegistry;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 采集源配置管理实现（G 轨，平台级）。
 */
@Slf4j
@Service
public class CrawlSourceServiceImpl implements CrawlSourceService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Resource
    private CrawlSourceMapper crawlSourceMapper;
    @Resource
    private CrawlTaskMapper crawlTaskMapper;
    @Resource
    private NoticeExternalMapper noticeExternalMapper;
    @Resource
    private ChannelRegistry channelRegistry;
    @Resource
    private ParserRegistry parserRegistry;

    @Override
    public PageResult<CrawlSourceRespVO> getSourcePage(CrawlSourcePageReqVO reqVO) {
        LambdaQueryWrapper<CrawlSourceDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(reqVO.getSourceCode())) {
            wrapper.eq(CrawlSourceDO::getSourceCode, reqVO.getSourceCode());
        }
        if (StringUtils.hasText(reqVO.getSourceName())) {
            wrapper.like(CrawlSourceDO::getSourceName, reqVO.getSourceName());
        }
        if (StringUtils.hasText(reqVO.getChannelCode())) {
            wrapper.eq(CrawlSourceDO::getChannelCode, reqVO.getChannelCode());
        }
        if (reqVO.getEnabled() != null) {
            wrapper.eq(CrawlSourceDO::getEnabled, reqVO.getEnabled());
        }
        wrapper.orderByDesc(CrawlSourceDO::getId);
        IPage<CrawlSourceDO> page = crawlSourceMapper.selectPage(Page.of(reqVO.getPageNo(), reqVO.getPageSize()), wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::toRespVO).toList(), page.getTotal());
    }

    @Override
    public CrawlSourceRespVO getSource(Long id) {
        return toRespVO(require(id));
    }

    @Override
    public Long createSource(CrawlSourceSaveReqVO reqVO) {
        validatePayload(reqVO);
        CrawlSourceDO source = new CrawlSourceDO();
        applyFields(source, reqVO);
        try {
            crawlSourceMapper.insert(source);
        } catch (DuplicateKeyException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_CODE_DUPLICATE);
        }
        log.info("[createSource][sourceCode={}] 采集源创建（默认不启用，启用后方进入 /go 白名单）", reqVO.getSourceCode());
        return source.getId();
    }

    @Override
    public void updateSource(CrawlSourceSaveReqVO reqVO) {
        CrawlSourceDO existing = require(reqVO.getId());
        if (!existing.getSourceCode().equals(reqVO.getSourceCode())) {
            // 代码=唯一身份（外部公告 source_code 快照/任务/白名单引用），禁止修改
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_CODE_IMMUTABLE);
        }
        validatePayload(reqVO);
        applyFields(existing, reqVO);
        crawlSourceMapper.updateById(existing);
    }

    @Override
    public void setSourceEnabled(Long id, Boolean enabled) {
        CrawlSourceDO source = require(id);
        boolean target = Boolean.TRUE.equals(enabled);
        if (source.getEnabled() != null && source.getEnabled().equals(target)) {
            return; // 幂等
        }
        if (!target) {
            // 停用=移出外链 /go 白名单：若仍有 ACTIVE 公告引用，其“查看原文”会立刻被拒（fail-closed 副作用）→ 先下线公告再停用
            assertNotInUse(source.getSourceCode());
        }
        source.setEnabled(target);
        crawlSourceMapper.updateById(source);
    }

    @Override
    public void deleteSource(Long id) {
        CrawlSourceDO source = require(id);
        assertNotInUse(source.getSourceCode());
        // 物理删除配置行：任务/公告行仍保留 source_code 文本快照可追溯；物理删避免与 uk_source_code(source_code, deleted) 逻辑删墓碑冲突
        crawlSourceMapper.deleteById(source.getId());
        log.info("[deleteSource][sourceCode={}] 采集源已删除（物理删配置行）", source.getSourceCode());
    }

    // ========== 私有工具 ==========

    private CrawlSourceDO require(Long id) {
        CrawlSourceDO source = id == null ? null : crawlSourceMapper.selectById(id);
        if (source == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_NOT_EXISTS);
        }
        return source;
    }

    /** 停用/删除前置守卫：存在未终态采集任务或 ACTIVE 外部公告引用该源时拒绝。 */
    private void assertNotInUse(String sourceCode) {
        Long running = crawlTaskMapper.selectCount(new LambdaQueryWrapper<CrawlTaskDO>()
                .eq(CrawlTaskDO::getSourceCode, sourceCode)
                .in(CrawlTaskDO::getStatus, CrawlTaskStatusEnum.PENDING.getStatus(),
                        CrawlTaskStatusEnum.RUNNING.getStatus()));
        if (running != null && running > 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_IN_USE);
        }
        Long activeNotices = noticeExternalMapper.selectCount(new LambdaQueryWrapper<NoticeExternalDO>()
                .eq(NoticeExternalDO::getSourceCode, sourceCode)
                .eq(NoticeExternalDO::getStatus, NoticeExternalStatusEnum.ACTIVE.getStatus()));
        if (activeNotices != null && activeNotices > 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_IN_USE);
        }
    }

    /** 频控下限（秒）：任何采集源轮询间隔不得小于 2 分钟（防目标站封禁；下载器调度同样执行该下限） */
    private static final int MIN_FETCH_INTERVAL_SECONDS = 120;

    /** 配置合法性：URL 协议/通道已注册/entryUrls JSON 为 http(s) 数组（保存即校验，防脏配置静默生效）。 */
    private void validatePayload(CrawlSourceSaveReqVO reqVO) {
        if (!isHttpUrl(reqVO.getBaseUrl())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_PAYLOAD_INVALID);
        }
        if (!channelRegistry.has(reqVO.getChannelCode())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_PAYLOAD_INVALID);
        }
        if (StringUtils.hasText(reqVO.getParserCodeDefault()) && !parserRegistry.has(reqVO.getParserCodeDefault())) {
            // 解析器插件校验：未知 parser_code_default 不允许保存（防脏配置静默生效；见 ParserRegistry）
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_PAYLOAD_INVALID);
        }
        if (reqVO.getFetchIntervalSeconds() != null && reqVO.getFetchIntervalSeconds() < MIN_FETCH_INTERVAL_SECONDS) {
            // 频控下限：爬取间隔 ≥2 分钟（用户口径：小于 2 分钟可能被目标站封禁）
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_INTERVAL_TOO_FREQUENT);
        }
        if (StringUtils.hasText(reqVO.getEntryUrlsJson())) {
            try {
                JsonNode node = OBJECT_MAPPER.readTree(reqVO.getEntryUrlsJson());
                if (!node.isArray()) {
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_PAYLOAD_INVALID);
                }
                for (JsonNode entry : node) {
                    // 兼容两种形态（2026-09-09 实测修复）：① 栏目对象 {name,parser,listUrl,...}（Runner parseConfigs 使用的现行格式）；
                    // ② 纯 URL 字符串数组（旧版契约）。两种都必须校验最终 http(s) 地址合法。
                    String url = null;
                    if (entry.isTextual()) {
                        url = entry.asText();
                    } else if (entry.isObject()) {
                        JsonNode lu = entry.get("listUrl");
                        if (lu == null || !lu.isTextual()) {
                            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_PAYLOAD_INVALID);
                        }
                        url = lu.asText();
                    } else {
                        throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_PAYLOAD_INVALID);
                    }
                    if (!isHttpUrl(url)) {
                        throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_PAYLOAD_INVALID);
                    }
                }
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_PAYLOAD_INVALID);
            }
        }
    }

    private void applyFields(CrawlSourceDO source, CrawlSourceSaveReqVO reqVO) {
        source.setSourceCode(reqVO.getSourceCode().trim());
        source.setSourceName(reqVO.getSourceName().trim());
        source.setBaseUrl(reqVO.getBaseUrl().trim());
        source.setAllowedHosts(StringUtils.hasText(reqVO.getAllowedHosts()) ? reqVO.getAllowedHosts().trim() : null);
        source.setEntryUrlsJson(StringUtils.hasText(reqVO.getEntryUrlsJson()) ? reqVO.getEntryUrlsJson() : null);
        source.setChannelCode(reqVO.getChannelCode());
        source.setParserCodeDefault(reqVO.getParserCodeDefault());
        source.setFetchIntervalSeconds(reqVO.getFetchIntervalSeconds() == null ? 3600 : reqVO.getFetchIntervalSeconds());
        source.setRobotsOk(Boolean.TRUE.equals(reqVO.getRobotsOk()));
        source.setRemark(reqVO.getRemark());
    }

    private boolean isHttpUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        try {
            URI uri = new URI(url);
            return uri.getHost() != null
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private CrawlSourceRespVO toRespVO(CrawlSourceDO source) {
        CrawlSourceRespVO resp = new CrawlSourceRespVO();
        resp.setId(source.getId());
        resp.setSourceCode(source.getSourceCode());
        resp.setSourceName(source.getSourceName());
        resp.setBaseUrl(source.getBaseUrl());
        resp.setAllowedHosts(source.getAllowedHosts());
        resp.setEntryUrlsJson(source.getEntryUrlsJson());
        resp.setChannelCode(source.getChannelCode());
        resp.setParserCodeDefault(source.getParserCodeDefault());
        resp.setFetchIntervalSeconds(source.getFetchIntervalSeconds());
        resp.setRobotsOk(source.getRobotsOk());
        resp.setEnabled(source.getEnabled());
        resp.setRemark(source.getRemark());
        resp.setCreateTime(source.getCreateTime());
        resp.setUpdateTime(source.getUpdateTime());
        return resp;
    }

}
