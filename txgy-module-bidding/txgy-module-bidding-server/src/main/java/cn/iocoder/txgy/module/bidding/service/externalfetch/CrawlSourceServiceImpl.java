package cn.iocoder.txgy.module.bidding.service.externalfetch;
// 规范依据：设计文档 v0.6 §四/§六/§九（白名单源配置、fail-closed、外链域名白名单=启用源）；《招投标模块开发规范》§4.2/红线 11

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlSourcePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlSourceRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlSourceSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlSourceMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.enums.CrawlTaskStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalStatusEnum;
import cn.iocoder.txgy.module.bidding.service.externalfetch.channel.ChannelRegistry;
import cn.iocoder.txgy.module.bidding.service.externalfetch.parser.ParserRegistry;
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
import java.util.LinkedHashMap;
import java.util.Map;

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

    /** 获取方式：爬虫（列表 HTML/详情逐条抓） */
    private static final String FETCH_TYPE_CRAWLER = "CRAWLER";
    /** 获取方式：API 接口直取（JSON 列表，api_config_json 配置请求/分页/字段映射） */
    private static final String FETCH_TYPE_API = "API";


    /** 由结构化 API 字段组装 api_config_json（fetchType=API 且给了任一 api* 字段时）；否则返回 null 走直填兼容 */
    private String assembleApiConfig(CrawlSourceSaveReqVO reqVO) {
        boolean structured = StringUtils.hasText(reqVO.getApiUrl()) || StringUtils.hasText(reqVO.getApiRowsPath())
                || StringUtils.hasText(reqVO.getApiMethod()) || StringUtils.hasText(reqVO.getApiHeadersJson())
                || StringUtils.hasText(reqVO.getApiBodyTemplate()) || StringUtils.hasText(reqVO.getApiMappingJson())
                || StringUtils.hasText(reqVO.getApiTotalPath()) || StringUtils.hasText(reqVO.getApiUrlPrefix())
                || StringUtils.hasText(reqVO.getApiPageParam()) || StringUtils.hasText(reqVO.getApiSizeParam())
                || reqVO.getApiPageSize() != null;
        if (!structured) {
            return null;
        }
        try {
            Map<String, Object> cfg = new LinkedHashMap<>();
            cfg.put("method", StringUtils.hasText(reqVO.getApiMethod()) ? reqVO.getApiMethod().trim().toUpperCase() : "GET");
            cfg.put("url", reqVO.getApiUrl());
            if (StringUtils.hasText(reqVO.getApiHeadersJson())) {
                cfg.put("headers", OBJECT_MAPPER.readValue(reqVO.getApiHeadersJson(), Map.class));
            }
            if (StringUtils.hasText(reqVO.getApiBodyTemplate())) { cfg.put("bodyTemplate", reqVO.getApiBodyTemplate()); }
            if (StringUtils.hasText(reqVO.getApiPageParam())) { cfg.put("pageParam", reqVO.getApiPageParam()); }
            if (StringUtils.hasText(reqVO.getApiSizeParam())) { cfg.put("sizeParam", reqVO.getApiSizeParam()); }
            if (reqVO.getApiPageSize() != null) { cfg.put("pageSize", reqVO.getApiPageSize()); }
            cfg.put("rowsPath", StringUtils.hasText(reqVO.getApiRowsPath()) ? reqVO.getApiRowsPath() : "rows");
            if (StringUtils.hasText(reqVO.getApiTotalPath())) { cfg.put("totalPath", reqVO.getApiTotalPath()); }
            if (StringUtils.hasText(reqVO.getApiMappingJson())) {
                cfg.put("mapping", OBJECT_MAPPER.readValue(reqVO.getApiMappingJson(), Map.class));
            }
            if (StringUtils.hasText(reqVO.getApiUrlPrefix())) { cfg.put("urlPrefix", reqVO.getApiUrlPrefix()); }
            return OBJECT_MAPPER.writeValueAsString(cfg);
        } catch (Exception e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_PAYLOAD_INVALID);
        }
    }

    /** 配置合法性：URL 协议/通道已注册/entryUrls JSON 为 http(s) 数组（保存即校验，防脏配置静默生效）。 */
    private void validatePayload(CrawlSourceSaveReqVO reqVO) {
        String fetchType = StringUtils.hasText(reqVO.getFetchType()) ? reqVO.getFetchType().trim().toUpperCase() : FETCH_TYPE_CRAWLER;
        if (!FETCH_TYPE_CRAWLER.equals(fetchType) && !FETCH_TYPE_API.equals(fetchType)) {
            // 获取方式只允许 CRAWLER/API（外部信息获取模块，见设计 v1.0 §4.2）
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_PAYLOAD_INVALID);
        }
        if (FETCH_TYPE_API.equals(fetchType)) {
            // API 源：结构化字段（apiUrl）或直填 apiConfigJson 二选一必须有（防漏配静默空跑）
            boolean structured = StringUtils.hasText(reqVO.getApiUrl());
            if (!structured && !StringUtils.hasText(reqVO.getApiConfigJson())) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_PAYLOAD_INVALID);
            }
            if (structured && !isHttpUrl(reqVO.getApiUrl())) {
                // 接口地址必须是 http/https（结构化必填校验）
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_PAYLOAD_INVALID);
            }
        }
        if (StringUtils.hasText(reqVO.getApiConfigJson())) {
            try {
                JsonNode node = OBJECT_MAPPER.readTree(reqVO.getApiConfigJson());
                if (!node.isObject()) {
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_PAYLOAD_INVALID);
                }
            } catch (Exception e) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_PAYLOAD_INVALID);
            }
        }
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
                    // 兼容三种栏目形态（2026-09-09 实测校准）：
                    // ① 纯 URL 字符串数组（旧版契约）→ 须为 http(s)
                    // ② 栏目对象 {name,parser,listUrl,...}（HTML 列表驱动，Runner parseConfigs）→ 校验 listUrl 为 http(s)
                    // ③ 栏目对象 {name,parser,siteId,categoryId,...}（JSON 接口驱动如 QYZB，无 listUrl）→ 靠 baseUrl，不强制 listUrl
                    if (entry.isTextual()) {
                        if (!isHttpUrl(entry.asText())) {
                            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_PAYLOAD_INVALID);
                        }
                        continue;
                    }
                    if (!entry.isObject()) {
                        throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_PAYLOAD_INVALID);
                    }
                    JsonNode lu = entry.get("listUrl");
                    if (lu != null && lu.isTextual() && !isHttpUrl(lu.asText())) {
                        throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_PAYLOAD_INVALID);
                    }
                    if (lu != null && !lu.isNull() && !lu.isTextual()) {
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
        source.setFetchType(StringUtils.hasText(reqVO.getFetchType()) ? reqVO.getFetchType().trim().toUpperCase() : FETCH_TYPE_CRAWLER);
        // API 配置：结构化字段优先（前端友好表单 → 服务端组装）；直填 apiConfigJson（高级/兼容）兜底
        String assembled = assembleApiConfig(reqVO);
        source.setApiConfigJson(assembled != null ? assembled : reqVO.getApiConfigJson());
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
        resp.setFetchType(source.getFetchType());
        resp.setApiConfigJson(source.getApiConfigJson());
        fillStructuredApiFields(resp, source.getApiConfigJson());
        resp.setParserCodeDefault(source.getParserCodeDefault());
        resp.setFetchIntervalSeconds(source.getFetchIntervalSeconds());
        resp.setRobotsOk(source.getRobotsOk());
        resp.setEnabled(source.getEnabled());
        resp.setRemark(source.getRemark());
        resp.setCreateTime(source.getCreateTime());
        resp.setUpdateTime(source.getUpdateTime());
        return resp;
    }


    /** 把 api_config_json 拆成结构化字段（前端表单回显；解析失败静默——直填 JSON 的高级模式仍可从 apiConfigJson 看） */
    private void fillStructuredApiFields(CrawlSourceRespVO resp, String apiConfigJson) {
        if (!StringUtils.hasText(apiConfigJson)) {
            return;
        }
        try {
            JsonNode cfg = OBJECT_MAPPER.readTree(apiConfigJson);
            if (!cfg.isObject()) {
                return;
            }
            resp.setApiUrl(textNode(cfg, "url"));
            resp.setApiMethod(textNode(cfg, "method"));
            JsonNode headers = cfg.get("headers");
            if (headers != null && headers.isObject() && !headers.isEmpty()) {
                resp.setApiHeadersJson(headers.toString());
            }
            resp.setApiBodyTemplate(textNode(cfg, "bodyTemplate"));
            resp.setApiPageParam(textNode(cfg, "pageParam"));
            resp.setApiSizeParam(textNode(cfg, "sizeParam"));
            JsonNode pageSize = cfg.get("pageSize");
            if (pageSize != null && pageSize.isNumber()) { resp.setApiPageSize(pageSize.asInt()); }
            resp.setApiRowsPath(textNode(cfg, "rowsPath"));
            resp.setApiTotalPath(textNode(cfg, "totalPath"));
            JsonNode mapping = cfg.get("mapping");
            if (mapping != null && mapping.isObject() && !mapping.isEmpty()) {
                resp.setApiMappingJson(mapping.toString());
            }
            resp.setApiUrlPrefix(textNode(cfg, "urlPrefix"));
        } catch (Exception ignore) {
            // 解析失败保持 apiConfigJson 原样可查
        }
    }

    private static String textNode(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null || v.isNull() || v.isMissingNode() || !v.isValueNode()) {
            return null;
        }
        String s = v.asText();
        return s == null || s.isBlank() ? null : s.trim();
    }

}
