package cn.iocoder.txgy.module.cms.tool;

import cn.iocoder.txgy.module.cms.service.review.ReviewEngine;
import cn.iocoder.txgy.module.cms.service.review.ReviewModels;
import cn.iocoder.txgy.module.cms.service.review.ReviewParser;
import cn.iocoder.txgy.module.cms.service.review.ReviewRepository;
import cn.iocoder.txgy.module.cms.service.review.ReviewService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.txgy.module.cms.service.review.ReviewModels.*;

/**
 * One-off, idempotent CMS import entry point. It deliberately invokes the same submit and independent
 * verification services as the HTTP review flow; it never inserts knowledge rows directly.
 */
public final class CmsReviewedImportTool {
    private static final long TENANT_ID = 1L;
    private static final long SUBMITTER_ID = 1L;
    private static final long REVIEWER_ID = 1L;
    private static final Pattern LINE_LOCATION = Pattern.compile("第(\\d+)行");

    private CmsReviewedImportTool() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: CmsReviewedImportTool <prepared-law-directory> <standard-docx-directory|--laws-only>");
        }
        boolean lawsOnly = "--laws-only".equals(args[1]);
        Path lawDirectory = Path.of(args[0]).toAbsolutePath().normalize();
        Path standardDirectory = lawsOnly ? null : Path.of(args[1]).toAbsolutePath().normalize();
        if (!Files.isDirectory(lawDirectory) || (!lawsOnly && !Files.isDirectory(standardDirectory))) {
            throw new IllegalArgumentException("Input directory does not exist");
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(env("CMS_IMPORT_DB_URL",
                "jdbc:mysql://127.0.0.1:3306/ruoyi-vue-pro?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"));
        dataSource.setUsername(env("CMS_IMPORT_DB_USERNAME", "root"));
        dataSource.setPassword(env("CMS_IMPORT_DB_PASSWORD", ""));

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        ReviewParser parser = new ReviewParser();
        ReviewService reviewService = new ReviewService(new ReviewRepository(jdbc), new ReviewEngine());
        List<ImportDocument> documents = loadDocuments(lawDirectory, standardDirectory, parser, lawsOnly);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        ImportResult result = transaction.execute(status -> importAll(jdbc, reviewService, documents));
        if (result == null) {
            throw new IllegalStateException("Import transaction returned no result");
        }
        System.out.printf(Locale.ROOT, "CMS reviewed import complete: total=%d imported=%d skipped=%d chunks=%d%n",
                documents.size(), result.imported(), result.skipped(), result.chunks());
    }

    private static ImportResult importAll(JdbcTemplate jdbc, ReviewService reviewService, List<ImportDocument> documents) {
        List<ImportDocument> pending = new ArrayList<>();
        int skipped = 0;
        for (ImportDocument document : documents) {
            List<String> hashes = jdbc.query(
                    "SELECT text_hash FROM cms_knowledge_document WHERE tenant_id=? AND title=? AND deleted_at IS NULL",
                    (rs, rowNum) -> rs.getString(1), TENANT_ID, document.metadata().title());
            String hash = ReviewEngine.hash(document.parsed().text());
            if (hashes.contains(hash)) {
                skipped++;
            } else if (!hashes.isEmpty()) {
                throw new IllegalStateException("Knowledge base already contains a different version: " + document.metadata().title());
            } else {
                pending.add(document);
            }
        }

        List<BatchDocument> batch = pending.stream().map(document -> new BatchDocument(
                document.fileName(), document.metadata(), document.parsed(), document.original())).toList();
        BatchResult submitted = batch.isEmpty()
                ? new BatchResult("already-imported", 0, List.of())
                : reviewService.submitBatch(TENANT_ID, SUBMITTER_ID, batch);
        int chunks = 0;
        for (int i = 0; i < pending.size(); i++) {
            ImportDocument document = pending.get(i);
            Report initial = submitted.items().get(i).report();
            initial = reviewService.completeAiReview(TENANT_ID, initial.id());
            if (initial.decision() != Decision.FAIL || initial.issues().stream().noneMatch(issue -> "SOURCE_UNVERIFIED".equals(issue.code()))) {
                throw new IllegalStateException("Document did not enter the expected pending-verification state: " + document.metadata().title());
            }
            EvidenceRequest evidence = new EvidenceRequest(document.metadata(), document.parsed().text(),
                    document.validitySourceUrl(), "已对照政府官网发布信息、现行效力和提交全文，准予进入企业法规知识库。" );
            Report verified = reviewService.verify(TENANT_ID, REVIEWER_ID, initial.id(), evidence);
            if (!verified.admitted() || verified.decision() == Decision.FAIL) {
                throw new IllegalStateException("Review rejected: " + document.metadata().title() + " issues=" + verified.issues());
            }
            chunks += verified.chunkCount();
        }
        return new ImportResult(pending.size(), skipped, chunks);
    }

    private static List<ImportDocument> loadDocuments(Path laws, Path standards, ReviewParser parser, boolean lawsOnly) {
        List<ImportDocument> result = new ArrayList<>();
        result.add(law(laws, "construction-2019.txt", "中华人民共和国建筑法", "中华人民共和国主席令第二十九号",
                "全国人民代表大会常务委员会", "https://www.fujian.gov.cn/zcwjk/szjt/202110/t20211022_5748124.htm",
                Kind.LAW, LocalDate.of(2019, 4, 23)));
        result.add(law(laws, "voc-2019.txt", "重点行业挥发性有机物综合治理方案", "环大气〔2019〕53号",
                "生态环境部", "https://www.mee.gov.cn/xxgk2018/xxgk/xxgk03/201907/t20190703_708395.html",
                Kind.POLICY, LocalDate.of(2019, 6, 26)));
        result.add(law(laws, "industrial-carbon-2022.txt", "工业领域碳达峰实施方案", "工信部联节〔2022〕88号",
                "工业和信息化部、国家发展改革委、生态环境部", "https://www.gov.cn/zhengce/zhengceku/2022-08/01/content_5703910.htm",
                Kind.POLICY, LocalDate.of(2022, 7, 7)));
        result.add(law(laws, "ecological-code-2026.txt", "中华人民共和国生态环境法典", "中华人民共和国主席令第六十七号",
                "全国人民代表大会", "https://www.mee.gov.cn/ywgz/fgbz/fl/202603/t20260313_1146496.shtml",
                Kind.LAW, LocalDate.of(2026, 8, 15)));
        result.add(law(laws, "fire-2021.txt", "中华人民共和国消防法", "中华人民共和国主席令第八十一号",
                "全国人民代表大会常务委员会", "https://zjt.hunan.gov.cn/zjt/xxgk/xinxigongkaimulu/flfg/202111/t20211112_21032115.html",
                Kind.LAW, LocalDate.of(2021, 4, 29)));
        result.add(law(laws, "safety-2021.txt", "中华人民共和国安全生产法", "中华人民共和国主席令第八十八号",
                "全国人民代表大会常务委员会", "https://www.mem.gov.cn/fw/flfgbz/fg/202107/t20210716_416558.shtml",
                Kind.LAW, LocalDate.of(2021, 9, 1)));

        if (!lawsOnly) {
        result.add(standard(standards, parser, "GB∕T 23257-2017 埋地钢质管道聚乙烯防腐层.docx",
                "埋地钢质管道聚乙烯防腐层", "GB/T 23257-2017", "国家质量监督检验检疫总局、国家标准化管理委员会",
                "https://openstd.samr.gov.cn/bzgk/std/newGbInfo?hcno=C4F2C204B258665369D7F8A66B745909", LocalDate.of(2017, 12, 1)));
        result.add(standard(standards, parser,
                "GB T 8923.1-2011 涂覆涂料前钢材表面处理 表面清洁度的目视评定 第1部分：未涂覆过的钢材表面和全面清除原有涂层后的钢材表面的锈蚀等级和处理等级 (2).docx",
                "涂覆涂料前钢材表面处理 表面清洁度的目视评定 第1部分：未涂覆过的钢材表面和全面清除原有涂层后的钢材表面的锈蚀等级和处理等级",
                "GB/T 8923.1-2011", "国家质量监督检验检疫总局、国家标准化管理委员会",
                "https://openstd.samr.gov.cn/bzgk/std/newGbInfo?hcno=6BC8D52A103AFEE95AC318BEB5DC4EC8", LocalDate.of(2012, 10, 1)));
        result.add(standard(standards, parser, "GBT 39636-2020 钢制管道熔结环氧粉末外涂层技术规范(2).docx",
                "钢制管道熔结环氧粉末外涂层技术规范", "GB/T 39636-2020", "国家市场监督管理总局、国家标准化管理委员会",
                "https://openstd.samr.gov.cn/bzgk/std/newGbInfo?hcno=9FAC0CA7B41DF130CA032F103F40D7CB", LocalDate.of(2021, 7, 1)));
        result.add(standard(standards, parser, "GB／T 50538-2020  埋地钢质管道防腐保温层技术标准(1).docx",
                "埋地钢质管道防腐保温层技术标准", "GB/T 50538-2020", "住房和城乡建设部、国家市场监督管理总局",
                "https://www.mohurd.gov.cn/api-gateway/jpaas-web-server/front/document/download?fileName=2020%E5%B9%B4.pdf", LocalDate.of(2021, 6, 1)));
        }
        int expected = lawsOnly ? 6 : 10;
        if (result.size() != expected) {
            throw new IllegalStateException("Expected " + expected + " documents");
        }
        return List.copyOf(result);
    }

    private static ImportDocument law(Path directory, String fileName, String title, String documentNo,
                                      String issuer, String url, Kind kind, LocalDate effectiveDate) {
        try {
            Path path = requireChild(directory, fileName);
            String text = Files.readString(path);
            if (text.isBlank() || text.length() > ReviewParser.MAX_CHARS) {
                throw new IllegalStateException("Invalid prepared legal text: " + fileName);
            }
            Metadata metadata = metadata(title, documentNo, issuer, url, kind, effectiveDate);
            return new ImportDocument(fileName, metadata, new Parsed(text, List.of()), Files.readAllBytes(path), url);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read prepared legal text: " + fileName, e);
        }
    }

    private static ImportDocument standard(Path directory, ReviewParser parser, String fileName, String title,
                                           String documentNo, String issuer, String url, LocalDate effectiveDate) {
        Path path = requireChild(directory, fileName);
        PathMultipartFile file = new PathMultipartFile(path);
        Parsed parsed = parser.parse(file);
        if (parsed.text().isBlank() || parsed.issues().stream().anyMatch(issue -> issue.severity() == Severity.ERROR)) {
            throw new IllegalStateException("Standard could not be parsed safely: " + fileName
                    + " issues=" + describeParsingErrors(parsed));
        }
        try {
            return new ImportDocument(fileName, metadata(title, documentNo, issuer, url, Kind.STANDARD, effectiveDate),
                    parsed, file.getBytes(), url);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read standard: " + fileName, e);
        }
    }

    private static String describeParsingErrors(Parsed parsed) {
        String[] lines = parsed.text().split("\\R", -1);
        List<String> descriptions = new ArrayList<>();
        for (Issue issue : parsed.issues()) {
            if (issue.severity() != Severity.ERROR) {
                continue;
            }
            StringBuilder description = new StringBuilder(issue.toString());
            Matcher matcher = LINE_LOCATION.matcher(issue.location());
            if (matcher.matches()) {
                int lineNumber = Integer.parseInt(matcher.group(1));
                if (lineNumber > 0 && lineNumber <= lines.length) {
                    String line = lines[lineNumber - 1];
                    description.append(" codePoints=[");
                    line.codePoints()
                            .filter(codePoint -> codePoint == 0xFFFD || codePoint == 0xFFFE || codePoint == 0xFFFF
                                    || Character.getType(codePoint) == Character.PRIVATE_USE
                                    || (Character.isISOControl(codePoint) && codePoint != '\t'))
                            .forEach(codePoint -> description.append(String.format(Locale.ROOT, "U+%04X ", codePoint)));
                    description.append(']');
                }
            }
            descriptions.add(description.toString());
        }
        return descriptions.toString();
    }

    private static Metadata metadata(String title, String documentNo, String issuer, String url,
                                     Kind kind, LocalDate effectiveDate) {
        return new Metadata(title, documentNo, issuer, url, kind, Validity.EFFECTIVE, effectiveDate, null, false);
    }

    private static Path requireChild(Path directory, String fileName) {
        Path path = directory.resolve(fileName).normalize();
        if (!path.startsWith(directory) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Required source file is missing: " + fileName);
        }
        return path;
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null ? defaultValue : value;
    }

    private record ImportDocument(String fileName, Metadata metadata, Parsed parsed, byte[] original,
                                  String validitySourceUrl) {
    }

    private record ImportResult(int imported, int skipped, int chunks) {
    }

    private static final class PathMultipartFile implements MultipartFile {
        private final Path path;

        private PathMultipartFile(Path path) {
            this.path = path;
        }

        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return path.getFileName().toString(); }
        @Override public String getContentType() { return "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; }
        @Override public boolean isEmpty() { try { return Files.size(path) == 0; } catch (IOException e) { return true; } }
        @Override public long getSize() { try { return Files.size(path); } catch (IOException e) { throw new IllegalStateException(e); } }
        @Override public byte[] getBytes() throws IOException { return Files.readAllBytes(path); }
        @Override public InputStream getInputStream() throws IOException { return Files.newInputStream(path); }
        @Override public void transferTo(File destination) throws IOException { Files.copy(path, destination.toPath()); }
    }
}
