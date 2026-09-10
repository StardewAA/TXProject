package cn.iocoder.txgy.module.bidding.service.externalfetch;

// 规范依据：外部信息获取 v1.0 §五（MySQL 持久队列 + Redis Stream 轻信号）+ 2026-09-10 用户口径：
//           列表行全文生产端即归档 FileApi（源文件），task.payload 只存引用（rawFileUrl/hash/size），
//           不把大文本落 DB/Redis；消费端按引用拉原文执行（直采/PDF/AI 同源）。

import cn.iocoder.txgy.framework.mq.redis.core.RedisMQTemplate;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper;
import cn.iocoder.txgy.module.bidding.service.externalfetch.mq.CrawlNoticeFetchMessage;
import cn.iocoder.txgy.module.bidding.service.externalfetch.parser.NoticeParseResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 信息获取任务投递实现：条目 -> FileApi 源文件归档（有全文时）-> PENDING task（防重）-> payload 引用 -> Redis 轻信号。
 */
@Slf4j
@Service
public class CrawlTaskDispatchServiceImpl implements CrawlTaskDispatchService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private CrawlTaskService crawlTaskService;
    @Resource
    private CrawlTaskMapper crawlTaskMapper;
    @Resource
    private RedisMQTemplate redisMQTemplate;
    @Resource
    private cn.iocoder.txgy.module.infra.api.file.FileApi fileApi;
    @Resource
    private cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper noticeExternalMapper;

    @Override
    public Long enqueue(Long jobId, String sourceCode, String parserCode, NoticeParseResult.ListEntry entry) {
        if (entry == null || !StringUtils.hasText(entry.getUrl())) {
            return null;
        }
        String url = entry.getUrl().trim();
        String urlHash = sha256Hex(url);
        // 2026-09-09 修复：已入库公告（orig_url_hash 命中）不再重复投递——历史 URL 入队会占满队列拖死调度（幂等跳过也要排队）
        Long existing = noticeExternalMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO>()
                .eq(cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO::getOrigUrlHash, urlHash));
        if (existing != null && existing > 0) {
            log.debug("[enqueue][urlHash={}] 已在公告库（幂等），跳过投递 url={}", urlHash, url);
            return null; // 不占队列：已抓过的不重投（内容更新检测由增量窗口重扫+contentHash 另议）
        }
        Long taskId = crawlTaskService.createTaskIfAbsent(jobId, sourceCode, url, urlHash,
                "GENERIC", parserCode, 0);
        if (taskId == null) {
            return null;
        }
        // payload：仅新任务（无载荷）写入；全文先归档 FileApi 再存引用（大文本不进 DB/Redis）
        boolean fresh = writePayloadIfPending(taskId, entry);
        if (fresh) {
            sendSignal(taskId, jobId, sourceCode);
        }
        return taskId;
    }

    private boolean writePayloadIfPending(Long taskId, NoticeParseResult.ListEntry entry) {
        try {
            CrawlTaskDO task = crawlTaskMapper.selectById(taskId);
            if (task == null || StringUtils.hasText(task.getPayloadJson())) {
                return false; // 复用既有任务：不覆盖
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("title", entry.getTitle());
            payload.put("url", entry.getUrl());
            payload.put("date", entry.getDate());
            payload.put("category", entry.getCategory());
            // 全文文件化：有 contentHtml 先归档 FileApi，payload 只存引用（v1.0 §2.2 原文文件化）
            if (StringUtils.hasText(entry.getContentHtml())) {
                FileRef ref = archiveHtml(entry.getContentHtml(), entry.getTitle());
                if (ref != null) {
                    payload.put("rawFileUrl", ref.url);
                    payload.put("rawSha256", ref.sha256);
                    payload.put("rawSize", ref.size);
                } else {
                    // 归档失败：任务照常入队（消费端无引用则报缺素材，走重试/人工）
                    log.warn("[enqueue][taskId={}] HTML 源文件归档失败（任务照常入队，消费端将缺素材）", taskId);
                }
            }
            task.setPayloadJson(MAPPER.writeValueAsString(payload));
            crawlTaskMapper.updateById(task);
            return true;
        } catch (Exception e) {
            log.warn("[enqueue][taskId={}] payloadJson 写入失败（任务仍在队，可后续补）: {}", taskId, e.getMessage());
            return false;
        }
    }

    /** HTML 原文归档 FileApi（目录 bidding/notice-external/raw-html；返回 URL/SHA-256/大小 三件套） */
    private FileRef archiveHtml(String contentHtml, String title) {
        try {
            byte[] bytes = contentHtml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String safeName = (title == null ? "notice" : title.replaceAll("[/:*?\"<>|]", "_")).trim();
            if (safeName.length() > 60) {
                safeName = safeName.substring(0, 60);
            }
            String url = fileApi.createFile(bytes, safeName + ".html", "bidding/notice-external/raw-html", "text/html;charset=utf-8");
            if (!StringUtils.hasText(url)) {
                return null;
            }
            FileRef ref = new FileRef();
            ref.url = url;
            ref.sha256 = sha256HexBytes(bytes);
            ref.size = (long) bytes.length;
            log.info("[archiveHtml][url={}] 源文件归档 size={}", url, ref.size);
            return ref;
        } catch (Exception e) {
            log.warn("[archiveHtml] 归档失败: {}", e.getMessage());
            return null;
        }
    }

    private void sendSignal(Long taskId, Long jobId, String sourceCode) {
        try {
            CrawlNoticeFetchMessage msg = new CrawlNoticeFetchMessage();
            msg.setTaskId(taskId);
            msg.setJobId(jobId);
            msg.setSourceCode(sourceCode);
            redisMQTemplate.send(msg);
            log.info("[enqueue][taskId={} jobId={} source={}] 轻信号已投递", taskId, jobId, sourceCode);
        } catch (Exception e) {
            // 发消息失败不回滚 task（DB 兜底，启动自愈可补发）
            log.error("[enqueue][taskId={}] 轻信号投递失败（task 仍 PENDING，可自愈补发）: {}", taskId, e.getMessage());
        }
    }

    private static String sha256Hex(String input) {
        return sha256HexBytes(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String sha256HexBytes(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }

    private static class FileRef {
        private String url;
        private String sha256;
        private Long size;
    }
}
