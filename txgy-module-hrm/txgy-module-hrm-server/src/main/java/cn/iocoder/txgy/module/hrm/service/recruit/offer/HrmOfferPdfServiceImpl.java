package cn.iocoder.txgy.module.hrm.service.recruit.offer;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.hrm.dal.dataobject.company.HrmCompanyProfileDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitOfferDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.candidate.HrmRecruitCandidateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.OFFER_PDF_FONT_MISSING;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.OFFER_PDF_RENDER_FAILED;

/**
 * HRM Offer 通知 PDF Service 实现类
 *
 * <p>基于 Apache PDFBox 直接绘制 PDF（Apache-2.0，仓库内 bidding/training/cms 模块已在用同款依赖）。
 * 中文必须内嵌字体，否则 PDF 里的汉字会变成空白，因此按以下顺序探测字体文件：</p>
 * <ol>
 *   <li>配置项 {@code hrm.offer.pdf.font-path} 指定的字体文件</li>
 *   <li>挂载目录 {@code /app/fonts} 下的常见中文字体</li>
 *   <li>开发机 Windows 字体目录（仅本地开发兜底）</li>
 * </ol>
 *
 * @author OPENLAB BS
 */
@Service
@Validated
@Slf4j
public class HrmOfferPdfServiceImpl implements HrmOfferPdfService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy 年 MM 月 dd 日");

    /**
     * 兜底探测的字体路径（按顺序）
     */
    private static final String[] FALLBACK_FONT_PATHS = {
            "/app/fonts/simhei.ttf",
            "/app/fonts/NotoSansSC-Regular.ttf",
            "/app/fonts/SourceHanSansCN-Regular.ttf",
            "C:/Windows/Fonts/simhei.ttf",
    };

    @Value("${hrm.offer.pdf.font-path:}")
    private String fontPath;

    @Override
    public byte[] renderOfferPdf(HrmRecruitOfferDO offer, HrmRecruitCandidateDO candidate,
                                 HrmRecruitPostDO post, HrmCompanyProfileDO company) {
        File fontFile = resolveFontFile();
        if (fontFile == null) {
            throw exception(OFFER_PDF_FONT_MISSING);
        }
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            // PDFBox 2.x 没有 File 重载，需要以输入流加载（并做子集内嵌，避免 PDF 体积过大）
            PDType0Font font;
            try (InputStream fontStream = new FileInputStream(fontFile)) {
                font = PDType0Font.load(document, fontStream, true);
            }
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                drawContent(cs, font, offer, candidate, post, company, page);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            log.error("[renderOfferPdf][Offer({}) 生成 PDF 失败]", offer.getId(), ex);
            throw exception(OFFER_PDF_RENDER_FAILED);
        }
    }

    @Override
    public String buildFileName(String candidateName, String offerNo) {
        return StrUtil.format("录用通知书-{}-{}.pdf", StrUtil.blankToDefault(candidateName, "候选人"),
                StrUtil.blankToDefault(offerNo, String.valueOf(System.currentTimeMillis())));
    }

    /**
     * 绘制《录用通知书》正文
     */
    private void drawContent(PDPageContentStream cs, PDType0Font font, HrmRecruitOfferDO offer,
                             HrmRecruitCandidateDO candidate, HrmRecruitPostDO post,
                             HrmCompanyProfileDO company, PDPage page) throws IOException {
        float margin = 56f;
        float width = page.getMediaBox().getWidth() - margin * 2;
        float y = page.getMediaBox().getHeight() - 90f;

        // 1. 抬头：公司名 + 标题
        String companyName = company != null ? company.getName() : "用工单位";
        write(cs, font, 11, margin, y, companyName);
        y -= 40f;
        write(cs, font, 20, margin + width / 2 - 60, y, "录 用 通 知 书");
        y -= 24f;
        write(cs, font, 10, margin, y, StrUtil.format("Offer 编号：{}", StrUtil.blankToDefault(offer.getOfferNo(), "-")));
        write(cs, font, 10, margin + width - 150, y,
                StrUtil.format("发出日期：{}", java.time.LocalDate.now().format(DATE_FORMATTER)));
        y -= 30f;

        // 2. 正文
        String candidateName = candidate != null ? candidate.getName() : "候选人";
        List<String> lines = new ArrayList<>();
        lines.add(StrUtil.format("尊敬的 {} 先生/女士：", candidateName));
        lines.add("");
        lines.add(StrUtil.format("您好！经面试评估，{} 决定录用您加入我司，现将录用条件说明如下：", companyName));
        lines.add("");
        lines.add(StrUtil.format("一、录用岗位：{}", post != null ? StrUtil.blankToDefault(post.getPostName(), "-") : "-"));
        lines.add(StrUtil.format("二、计划入职日期：{}",
                offer.getPlannedOnboardDate() != null ? offer.getPlannedOnboardDate().format(DATE_FORMATTER) : "以双方约定为准"));
        lines.add(StrUtil.format("三、薪酬标准：{}", formatSalary(offer)));
        lines.add(StrUtil.format("四、Offer 有效期：截至 {}，逾期未确认视为放弃",
                offer.getValidUntil() != null ? offer.getValidUntil().format(DATE_FORMATTER) : "-"));
        lines.add("五、入职需携带：身份证原件、特种作业操作证及岗位要求的资格证书、体检报告、");
        lines.add("    本人银行卡与近期免冠照片；现场岗位另需完成三级安全教育后上岗。");
        if (StrUtil.isNotBlank(offer.getRemark())) {
            lines.add(StrUtil.format("六、其他说明：{}", offer.getRemark()));
        }
        lines.add("");
        lines.add("请您在有效期内登录招聘平台确认接受本录用通知；如有疑问，请与招聘负责人联系。");
        lines.add("");
        lines.add("期待与您共同完成项目履约，携手共进！");

        for (String line : lines) {
            if (StrUtil.isBlank(line)) {
                y -= 10f;
                continue;
            }
            write(cs, font, 11, margin, y, line);
            y -= 22f;
        }

        // 3. 落款
        y -= 24f;
        write(cs, font, 11, margin + width - 200, y, StrUtil.format("{} （盖章）", companyName));
        y -= 22f;
        write(cs, font, 11, margin + width - 200, y,
                java.time.LocalDate.now().format(DATE_FORMATTER));
        // 4. 页脚
        write(cs, font, 8, margin, 60, "本通知由系统自动生成，仅用于录用沟通，不构成劳动合同；正式劳动关系以双方签署的书面合同为准。");
    }

    private String formatSalary(HrmRecruitOfferDO offer) {
        BigDecimal amount = offer.getSalaryAmount();
        if (amount == null) {
            return "面议";
        }
        String unit = offer.getSalaryUnit() != null && offer.getSalaryUnit() == 2 ? "元/年" : "元/月";
        return amount.stripTrailingZeros().toPlainString() + " " + unit;
    }

    /**
     * 写一行文字
     */
    private void write(PDPageContentStream cs, PDType0Font font, float size, float x, float y, String text)
            throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(StrUtil.blankToDefault(text, ""));
        cs.endText();
    }

    /**
     * 按「配置 > 挂载目录 > 开发机」顺序探测中文字体，找不到时返回 null（由上层给出可操作的错误提示）
     */
    private File resolveFontFile() {
        if (StrUtil.isNotBlank(fontPath)) {
            File file = new File(fontPath);
            if (file.isFile()) {
                return file;
            }
            log.warn("[resolveFontFile][配置的字体路径不存在({})，继续探测默认路径]", fontPath);
        }
        for (String path : FALLBACK_FONT_PATHS) {
            File file = new File(path);
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }

}
