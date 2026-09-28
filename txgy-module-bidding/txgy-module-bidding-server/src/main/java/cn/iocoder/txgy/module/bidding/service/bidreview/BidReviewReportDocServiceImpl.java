package cn.iocoder.txgy.module.bidding.service.bidreview;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewReportDocRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewIssueDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewReportDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewTaskDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewIssueMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewReportMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewTaskMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.infra.api.file.FileApi;
import cn.iocoder.txgy.module.infra.api.file.dto.FileMetadataRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 智慧标书审查 · 报告 Word 文档生成实现（手写最小 OOXML，零新增依赖）。
 *
 * <p>为什么不用 POI/iText：模块当前只依赖 PDFBox；为了 PDF 里嵌中文字体还要额外带字体文件（10MB+），
 * 而 Word 文档天然支持中文，且用户通常还要二次编辑整改意见 —— 因此先做 docx，PDF 留作后续（方案 §8 P1）。
 */
@Slf4j
@Service
public class BidReviewReportDocServiceImpl implements BidReviewReportDocService {

    /** 报告文档归档目录（便于运维按业务清理） */
    private static final String DIRECTORY = "bidding/bid-review/report";

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Resource
    private BidReviewTaskMapper taskMapper;
    // 钉住 bean 名：字段名 reportMapper 若走 @Resource 按名解析会命中 report 域的 ReportMapper（类型不符→启动失败）
    @Resource(name = "bidReviewReportMapper")
    private BidReviewReportMapper reportMapper;
    @Resource
    private BidReviewIssueMapper issueMapper;
    @Resource
    private BidReviewReportService reportService;
    @Resource
    private BidReviewTaskService taskService;
    @Resource
    private FileApi fileApi;

    @Override
    public BidReviewReportDocRespVO getOrCreateDocx(Long taskId, boolean checkOwner) {
        // 个人端：归属校验 + 切到任务所属租户（报告/问题项/文件归档都跟着任务租户，
        // 个人端提交到企业的任务才不会"生成报告时取不到报告行"·2026-09-20）
        if (checkOwner) {
            return taskService.executeInMyTaskTenant(taskId, () -> doGetOrCreateDocx(taskId, true));
        }
        return doGetOrCreateDocx(taskId, false);
    }

    private BidReviewReportDocRespVO doGetOrCreateDocx(Long taskId, boolean checkOwner) {
        // 复用报告读取（个人端在此完成归属校验；报告不存在会抛 BID_REVIEW_REPORT_NOT_READY）
        reportService.getReportByTaskId(taskId, checkOwner);
        BidReviewReportDO report = reportMapper.selectByTaskId(taskId);
        BidReviewTaskDO task = taskMapper.selectById(taskId);
        BidReviewReportDocRespVO resp = new BidReviewReportDocRespVO();
        String fileName = buildFileName(task, report);
        // 已生成过 → 直接复用（同一任务只生成一次，避免重复占存储）
        if (report.getDocxFileId() != null) {
            resp.setFileId(report.getDocxFileId());
            resp.setFileName(fileName);
            // 复用已生成文件：回传生成时快照的下载地址（否则前端只拿到 fileId 无法直接下载）
            resp.setUrl(report.getDocxFileUrl());
            resp.setGenerated(false);
            return resp;
        }
        List<BidReviewIssueDO> issues = issueMapper.selectListByTaskId(taskId);
        byte[] docx = buildDocx(task, report, issues);
        // 【2026-09-16 修复】存储名唯一化：报告文件名固定（任务名+报告号），重复生成会写出多条相同 URL，
        // 之后 infra 按 URL 回查元数据用 selectOne → TooManyResultsException → fileId 拿不到、
        // docx_file_id 存不下来 → 每次点下载都在重新生成。这里只给「存储名」加时间戳，展示名仍是正常文件名。
        String storageName = buildUniqueStorageName(fileName);
        String url = fileApi.createFile(docx, storageName, DIRECTORY, DOCX_CONTENT_TYPE);
        if (StrUtil.isBlank(url)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FILE_REGISTER_FAILED);
        }
        Long fileId = null;
        try {
            var metaResult = fileApi.getMetadataByUrl(url);
            FileMetadataRespDTO meta = metaResult == null ? null : metaResult.getCheckedData();
            if (meta != null) {
                fileId = meta.getId();
            }
        } catch (Exception e) {
            log.warn("[getOrCreateDocx][报告文档已生成但元数据回查失败 url={}]", url, e);
        }
        if (fileId != null) {
            report.setDocxFileId(fileId);
        }
        report.setDocxFileUrl(url);
        reportMapper.updateById(report);
        resp.setFileId(fileId);
        resp.setUrl(url);
        resp.setFileName(fileName);
        resp.setGenerated(true);
        log.info("[getOrCreateDocx][报告文档生成成功 taskId={} fileId={} url={}]", taskId, fileId, url);
        return resp;
    }

    /**
     * 取「结论」部分用于文档渲染。
     *
     * <p>为什么需要：2026-09-20 之前的实现把「## 问题清单」拼进了 report.conclusion，而本文档在结论之后
     * 还会渲染一次逐条清单，导致**同一份清单在 Word 里出现两遍**（用户反馈"生成审查报告的文档里面有重复内容"）。
     * 流水线侧已改为 conclusion 不再含清单（BidReviewPipelineServiceImpl.buildReport），
     * 但**历史报告行里存的 conclusion 仍带旧格式**，所以这里再做一层截断兼容：
     * 砍掉清单段和结尾那句免责声明（免责声明由本类在文末统一输出）。
     * 对新数据是无副作用的空操作。
     */
    private String conclusionForDocx(String conclusion) {
        String text = StrUtil.nullToEmpty(conclusion);
        int cut = text.indexOf("## 问题清单");
        if (cut >= 0) {
            text = text.substring(0, cut);
        }
        text = text.replace("> 本报告由 AI 生成，需人工复核后使用。", "");
        return text.stripTrailing();
    }

    private String buildFileName(BidReviewTaskDO task, BidReviewReportDO report) {
        String base = StrUtil.blankToDefault(task == null ? null : task.getTaskName(), "标书审查报告");
        String safe = base.replaceAll("[\\\\/:*?\"<>|]", "_");
        return StrUtil.sub(safe, 0, 60) + "-审查报告-" + StrUtil.blankToDefault(report.getReportNo(), "") + ".docx";
    }

    /** 存储名唯一化（yyyyMMddHHmmssSSS-随机4位-原名），保证 infra 文件 URL 唯一、元数据可回查。 */
    private String buildUniqueStorageName(String originalName) {
        String stamp = cn.hutool.core.date.DateUtil.format(new java.util.Date(), "yyyyMMddHHmmssSSS");
        String random = cn.hutool.core.util.RandomUtil.randomNumbers(4);
        return stamp + "-" + random + "-" + StrUtil.blankToDefault(originalName, "report.docx");
    }

    // ==================== 最小 OOXML 生成 ====================

    private byte[] buildDocx(BidReviewTaskDO task, BidReviewReportDO report, List<BidReviewIssueDO> issues) {
        List<String> paragraphs = new ArrayList<>();
        paragraphs.add("# 智慧标书审查报告");
        paragraphs.add("");
        paragraphs.add("报告编号：" + StrUtil.blankToDefault(report.getReportNo(), "-"));
        paragraphs.add("任务名称：" + StrUtil.blankToDefault(task == null ? null : task.getTaskName(), "-"));
        paragraphs.add("标书文件：" + StrUtil.blankToDefault(task == null ? null : task.getBidFileName(), "-"));
        // 抬头只留"这是哪次审查"的标识信息：结论 / 问题统计 / 自评得分都已在下方「审查结论」段出现，
        // 抬头再写一遍就是同一份文档里同样的话说两遍（2026-09-20 用户反馈"文档里有重复内容"）。
        paragraphs.add("");
        // conclusion 自带 "## 审查结论" 小标题；这里不再另加"结论与建议"标题（2026-09-20 修文档内容重复）
        paragraphs.addAll(splitLines(conclusionForDocx(report.getConclusion())));
        paragraphs.add("");
        paragraphs.add("## 问题清单（逐条）");
        paragraphs.add("");
        int index = 1;
        for (BidReviewIssueDO issue : issues) {
            paragraphs.add("### " + index++ + ". [" + issue.getSeverity() + "] " + StrUtil.nullToEmpty(issue.getIssueTitle()));
            if (StrUtil.isNotBlank(issue.getIssueDesc())) {
                paragraphs.add("说明：" + issue.getIssueDesc());
            }
            paragraphs.add("证据（" + StrUtil.blankToDefault(issue.getHitLocation(), "未定位") + "）："
                    + (StrUtil.isBlank(issue.getEvidenceText()) ? "标书没有对应内容" : issue.getEvidenceText()));
            paragraphs.add("整改建议：" + StrUtil.nullToEmpty(issue.getSuggestion()));
            paragraphs.add("复核状态：" + StrUtil.nullToEmpty(issue.getStatus()));
            paragraphs.add("");
        }
        paragraphs.add("说明：本报告由 AI 生成，需人工复核后使用；不作为对外正式文件。");
        try {
            return zip(paragraphs);
        } catch (Exception e) {
            log.error("[buildDocx][生成 docx 失败 taskId={}]", task == null ? null : task.getId(), e);
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_REVIEW_REPORT_NOT_READY);
        }
    }

    private byte[] zip(List<String> paragraphs) throws Exception {
        StringBuilder body = new StringBuilder();
        for (String p : paragraphs) {
            body.append(buildParagraph(p));
        }
        String documentXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:body>" + body + "<w:sectPr/></w:body></w:document>";
        String contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\""
                + DOCX_CONTENT_TYPE + ".main+xml\"/></Types>";
        String rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\""
                + " Target=\"word/document.xml\"/></Relationships>";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            putEntry(zip, "[Content_Types].xml", contentTypes);
            putEntry(zip, "_rels/.rels", rels);
            putEntry(zip, "word/document.xml", documentXml);
        }
        return out.toByteArray();
    }

    private void putEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** 极简 Markdown → Word 段落：按 # 级别加粗放大，其余按普通段落；空行保留为空段落。 */
    private String buildParagraph(String line) {
        String text = line == null ? "" : line;
        boolean h1 = text.startsWith("# ");
        boolean h2 = text.startsWith("## ");
        boolean h3 = text.startsWith("### ");
        if (h1 || h2 || h3) {
            text = text.replaceFirst("^#+ ", "");
        }
        if (text.startsWith("- ")) {
            text = "• " + text.substring(2);
        }
        if (text.startsWith("> ")) {
            text = text.substring(2);
        }
        String rPr = h1 ? "<w:rPr><w:b/><w:sz w:val=\"36\"/></w:rPr>"
                : h2 ? "<w:rPr><w:b/><w:sz w:val=\"30\"/></w:rPr>"
                : h3 ? "<w:rPr><w:b/><w:sz w:val=\"26\"/></w:rPr>"
                : "";
        return "<w:p><w:pPr><w:spacing w:after=\"120\"/></w:pPr><w:r>" + rPr
                + "<w:t xml:space=\"preserve\">" + escapeXml(text) + "</w:t></w:r></w:p>";
    }

    private String escapeXml(String text) {
        return StrUtil.nullToEmpty(text)
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : StrUtil.nullToEmpty(text).split("\n")) {
            lines.add(line.stripTrailing());
        }
        return lines;
    }

    private long count(List<BidReviewIssueDO> issues, String severity) {
        return issues.stream().filter(i -> severity.equals(i.getSeverity())).count();
    }

    private String verdictText(String verdict) {
        if ("FAIL".equals(verdict)) {
            return "不通过（命中高危问题，需整改后重投）";
        }
        if ("RISK".equals(verdict)) {
            return "存在风险（有中低风险问题，建议整改）";
        }
        return "通过（未发现明确问题）";
    }
}
