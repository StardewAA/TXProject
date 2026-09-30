package cn.iocoder.txgy.module.bidding.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 禁止硬编码数值参数门禁（登记制）。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§8.1（"所有数值参数集中受控，业务代码零硬编码"）与 §8.4
 * （"禁止硬编码：扫描 service/** 与 job/**，命中魔法数字（白名单外）即失败"）；
 * 用户硬约束"所有数值参数必须支持动态配置，严禁硬编码"。
 *
 * <p>为什么不是"扫所有数字"：那种门禁会命中循环下标、分页默认值、列宽等技术常量，噪音大到只能靠
 * 巨型白名单压住，最终没人看、也没人维护——就是"假门禁"。本门禁只盯**长得像业务阈值**的常量
 * （名字含 DAYS/WINDOW/THRESHOLD/LIMIT/RATIO/…），并强制它们**登记**：要么证明是工程性护栏（TECHNICAL），
 * 要么承认是尚未接入配置中心的存量欠账（BUSINESS_DEBT，写明欠什么）。
 *
 * <p>真实教训（本门禁的三条规则都对应一个已经发生的问题）：
 * ① 人员到期窗口 {@code personnel.cert.expiring.window.days} **早已在配置中心就位**（描述就是
 * "≤ 该值由预警扫描置 EXPIRING_SOON"），但服务里仍硬编码 30 —— 运维改配置**不生效**（假配置），
 * 且同语义窗口在证书/资质/材料三处各写一遍，改一处漏两处；
 * ② 26 个配置键中有 8 个在 Java 侧**从未被读取**（含"复审提醒天数"这类看起来可配、改了没反应的键）；
 * ③ 登记表会腐烂：修好一处若不从欠账表移除，门禁就会替旧问题"背书"。
 *
 * <p>业务目标：阈值只能从配置中心来；已经声称可配的键必须真的有人读。
 */
class NoHardcodedParamTest {

    private static final Path MAIN_ROOT = Path.of("src", "main", "java", "cn", "iocoder", "txgy", "module", "bidding");
    private static final Path KEYS_FILE = MAIN_ROOT.resolve("framework/ruleconfig/RuleConfigKeys.java");

    /** 阈值形状的常量声明：static final 数值 + 名字含阈值词。 */
    private static final Pattern THRESHOLD_CONSTANT = Pattern.compile(
            "\\bstatic final\\s+(?:int|long|double|Integer|Long|BigDecimal|short)\\s+([A-Z][A-Z0-9_]{2,})\\s*=\\s*([^;]{1,60});");

    private static final Pattern THRESHOLD_NAME = Pattern.compile(
            "(DAYS|WINDOW|THRESHOLD|LIMIT|MAX|MIN|RATIO|RATE|PERCENT|SCORE|COUNT|TIMES|HOURS|MONTHS?|YEARS?|AMOUNT|DURATION|SIZE|QUOTA|WEIGHT|COEFF|FACTOR|EXPIR|GRACE|TOLERANCE|BATCH|RETRY|TIMEOUT|TOTAL|CAP)");

    /**
     * 同值三元：{@code cond ? X : X}——两个分支同值，条件形同虚设。
     *
     * <p>为什么值得单独做一条规则：这类写法**看起来像映射、实际是常量**，是最难靠肉眼发现的一类硬编码。
     * 实例：{@code SAFETY_OFFICER.equals(post) ? "C" : "C"}——"矩阵待业务确认后配置化"写在注释里，
     * 而代码里所有岗位都返回 C 类，业务确认矩阵时必须改代码发版；数值型门禁（阈值常量）抓不到它，
     * 因为它压根不是 static final 常量。
     */
    private static final Pattern SAME_BRANCH_TERNARY = Pattern.compile("\\?\\s*([^:?;\\n{}]{1,60}?)\\s*:\\s*\\1\\s*[;),]");

    /** 规则键常量声明：{@code String NAME = "a.b.c"}。 */
    private static final Pattern KEY_CONSTANT = Pattern.compile("String\\s+([A-Z][A-Z0-9_]+)\\s*=\\s*\"([a-z][a-z0-9.]*\\.[a-z0-9.]+)\"");

    /** 把规则键写成字面量（应当用 RuleConfigKeys 常量）。 */
    private static final Pattern KEY_LITERAL = Pattern.compile(
            "ruleConfigService\\.(?:get\\w+|resolveWithAudit)\\(\\s*\"");

    /**
     * 阈值常量登记表：{@code 相对路径::常量名 → 分类:理由}。
     *
     * <p>分类：{@code TECHNICAL} 工程性护栏（内存/存储/上游承载、爬取礼貌、结果集上限）；
     * {@code BUSINESS_DEBT} 业务阈值但尚未接入配置中心——**必须写明欠什么**，接线后请从本表删除。
     */
    private static final Map<String, String> THRESHOLD_REGISTRY = new LinkedHashMap<>();

    /**
     * 配置键读取归属登记表：{@code 常量名 → 分类:理由}（仅未被 Java 代码读取的键需要登记）。
     *
     * <p>分类：{@code FRONTEND} 由前端读取（后端只负责下发，如上传前校验）；
     * {@code PRESET} 功能尚未实现、键为预置；{@code DEBT} 后端本应读取但尚未接线。
     */
    private static final Map<String, String> CONFIG_KEY_REGISTRY = new LinkedHashMap<>();

    static {
        // 已划账：CertificateAlertServiceImpl::EXPIRING_SOON_DAYS 已删除，改为解析新键 cert.expiring.status.days
        // （默认 30 = 原硬编码值，行为不变；不与展示口径 cert.expiring.window.days 合并，理由见该键 javadoc 与 RuleConfigKeys）
        THRESHOLD_REGISTRY.put("service/externalfetch/CrawlRunServiceImpl.java::FAIL_BACKOFF_MAX_SECONDS",
                "TECHNICAL:爬虫失败退避上限（运维保护，不是业务口径）");
        THRESHOLD_REGISTRY.put("service/externalfetch/CrawlSourceServiceImpl.java::MIN_FETCH_INTERVAL_SECONDS",
                "TECHNICAL:站点最小抓取间隔（爬取礼貌/反封禁）");
        THRESHOLD_REGISTRY.put("service/externalfetch/CrawlTaskServiceImpl.java::MAX_ATTEMPTS",
                "TECHNICAL:抓取任务重试次数（可靠性参数）");
        THRESHOLD_REGISTRY.put("service/externalfetch/fetch/CrawlSourceRunner.java::DEFAULT_LOOKBACK_MAX_PAGES",
                "TECHNICAL:回溯翻页上限（防止一次拉爆源站）");
        THRESHOLD_REGISTRY.put("service/externalfetch/fetch/CrawlSourceRunner.java::SINGLE_PAGE_LIMIT",
                "TECHNICAL:单页抓取条数（分页聚合用）");
        THRESHOLD_REGISTRY.put("service/externalfetch/parser/HtmlNoticeExtractor.java::MAX_BODY_TEXT",
                "TECHNICAL:正文抽取上限（内存护栏）");
        THRESHOLD_REGISTRY.put("service/externalfetch/parser/HtmlNoticeExtractor.java::MAX_HTML",
                "TECHNICAL:HTML 载入上限（内存护栏）");
        THRESHOLD_REGISTRY.put("service/externalfetch/parser/PdfTextNoticeExtractor.java::MAX_TEXT",
                "TECHNICAL:PDF 文本抽取上限（内存护栏）");
        THRESHOLD_REGISTRY.put("service/externalfetch/throttle/CrawlDownloadGate.java::MIN_INTERVAL_SECONDS",
                "TECHNICAL:下载节流最小间隔（同站点保护）");
        THRESHOLD_REGISTRY.put("service/externalfetch/validate/DownloadIntegrityValidator.java::MIN_TEXT_CHARS",
                "TECHNICAL:正文完整性最小长度（抓取质量判定，非业务阈值）");
        THRESHOLD_REGISTRY.put("service/externalfetch/validate/DownloadIntegrityValidator.java::MOJIBAKE_RATIO",
                "TECHNICAL:乱码比例容差（编码质量判定）");
        // 已划账（2026-09-14）：AiChatNoticeExtractor::MAX_INLINE_TEXT 由写死 60000 改为可配置
        // bidding.ai.max-inline-text-chars（默认 8000，成本主旋钮）——接线到配置中心后必须从本表删除，
        // 否则等于替旧硬编码背书（本表语义见 configKeys_mustBeReadOrRegistered）。
        THRESHOLD_REGISTRY.put("service/noticeexternal/body/AiBodyStructurer.java::MAX_INLINE_TEXT",
                "TECHNICAL:送入模型的内联文本上限（token/成本护栏）");
        THRESHOLD_REGISTRY.put("service/noticeexternal/indexer/EsExternalNoticeIndexNotifier.java::MAX_TEXT_CHARS",
                "TECHNICAL:ES 单字段文本上限（索引承载）");
        THRESHOLD_REGISTRY.put("service/noticeexternal/ingest/ExternalNoticeIngestServiceImpl.java::MAX_TITLE",
                "TECHNICAL:入库列长护栏（对应 DDL 列宽）");
        THRESHOLD_REGISTRY.put("service/noticeexternal/ingest/ExternalNoticeIngestServiceImpl.java::MAX_ORIG_URL",
                "TECHNICAL:入库列长护栏（对应 DDL 列宽）");
        THRESHOLD_REGISTRY.put("service/noticeexternal/ingest/ExternalNoticeIngestServiceImpl.java::MAX_HTML",
                "TECHNICAL:原始 HTML 留存上限（存储护栏）");
        THRESHOLD_REGISTRY.put("service/noticeexternal/ingest/ExternalNoticeIngestServiceImpl.java::MAX_META_JSON",
                "TECHNICAL:元数据 JSON 留存上限（存储护栏）");
        THRESHOLD_REGISTRY.put("service/noticeexternal/ingest/ExternalNoticeIngestServiceImpl.java::MAX_EXTRA_RAW",
                "TECHNICAL:扩展原文留存上限（存储护栏）");
        THRESHOLD_REGISTRY.put("service/noticeexternal/NoticeExternalServiceImpl.java::KEYWORD_MATCH_LIMIT",
                "TECHNICAL:关键字匹配结果条数上限（性能护栏，非业务口径）");
        // 2026-09-18 划账：CrawlTaskDispatchServiceImpl::MAX_INLINE_HTML_CHARS 已从源码删除（内联正文上限语义并入 CrawlFetchStore 存储护栏），登记随删
        THRESHOLD_REGISTRY.put("service/project/ProjectTraceServiceImpl.java::DOMAIN_LIMIT",
                "TECHNICAL:域名聚合结果条数上限（性能护栏）");
        THRESHOLD_REGISTRY.put("service/biznum/BiddingBizNoGeneratorImpl.java::MAX_SEQ",
                "TECHNICAL:独立式序列最大值 36^6-1（编码位数护栏，防溢出，非业务阈值）");
        THRESHOLD_REGISTRY.put("service/biznum/BiddingBizNoGeneratorImpl.java::MAX_CHILD_SEQ",
                "TECHNICAL:层级式子序列最大值 36^3-1（父编号下容量护栏，防溢出，非业务阈值）");
        THRESHOLD_REGISTRY.put("service/meta/BiddingOptionServiceImpl.java::DEFAULT_LIMIT",
                "TECHNICAL:字典/选项下拉默认返回条数（界面容量护栏，非业务阈值）");
        THRESHOLD_REGISTRY.put("service/meta/BiddingOptionServiceImpl.java::MAX_LIMIT",
                "TECHNICAL:字典/选项下拉最大返回条数（界面容量护栏，超限应改精确关键字）");
        // 标书审查域（bidreview，另一工作线）登记（2026-09-18）
        THRESHOLD_REGISTRY.put("service/bidreview/BidReviewAiReviewServiceImpl.java::BATCH_SIZE",
                "TECHNICAL:AI 单批比对条数（模型能力护栏：太多漏判、太少浪费调用轮次）");
        THRESHOLD_REGISTRY.put("service/bidreview/BidReviewAiReviewServiceImpl.java::TENDER_TEXT_LIMIT_FACTOR",
                "TECHNICAL:招标文件正文截断倍率（chunkSize 乘以该因子后送模型，长文模型能力护栏）");
        THRESHOLD_REGISTRY.put("service/bidreview/BidReviewParseServiceImpl.java::MAX_DOWNLOAD_BYTES",
                "TECHNICAL:单文件下载上限 30MB（防误传大包打满内存，非业务阈值）");
        THRESHOLD_REGISTRY.put("service/bidreview/BidReviewTaskServiceImpl.java::MAX_RETRY",
                "TECHNICAL:审查任务重试次数（可靠性参数，非业务阈值）");
        // 已划账（2026-09-18）：AiChatNoticeExtractor::MAX_INLINE_TEXT 仍为写死 60000，
        // 注释声明应改为可配置 bidding.ai.max-inline-text-chars（默认 8000）——接线到配置中心后必须从本表删除
        THRESHOLD_REGISTRY.put("service/noticeexternal/ai/AiChatNoticeExtractor.java::MAX_INLINE_TEXT",
                "DEBT:送入 AI 的内联正文上限仍硬编码 60000；应接线 bidding.ai.max-inline-text-chars（成本主旋钮）");
        // 已划账：EnterpriseMaterialServiceImpl::EXPIRING_WINDOW_DAYS 已删除，改为解析新键
        // enterprise.material.expiring.window.days（默认 60 = 原硬编码值，行为不变）。理由同上：
        // 与展示口径 cert.expiring.window.days 数值相同但语义不同（资格自查提前期 vs 列表角标），不合并。
        // 本次已接线（2026-09-13）：personnel.cert.expiring.window.days 已由 PersonnelAlertServiceImpl 读取，
        // 故其硬编码 EXPIRING_SOON_DAYS 已删除——修好一处就要从欠账表里划掉一处。
        // 注：本轮新增的 CERT_EXPIRING_STATUS_DAYS **不进本表**——本表登记的是"未被后端读取"的键
        // （本表语义由 configKeys_mustBeReadOrRegistered 定义：已登记却真被读了 → 失败，必须移除）。
        // 该键由 CertificateAlertServiceImpl 经 resolveWithAudit 真读，故只在 RuleConfigKeys 登记常量与兜底。
        // ===== 2026-09-25 merge 后补齐登记（bidagent/contract/deposit 等域常量，来源主分支合入） =====
        THRESHOLD_REGISTRY.put("service/bidagent/BidAgentMaterialServiceImpl.java::MAX_FILE_BYTES",
                "TECHNICAL:标书材料单文件上传上限 60MB（存储/传输护栏，非业务阈值）");
        THRESHOLD_REGISTRY.put("service/bidagent/index/BidIndexServiceImpl.java::CHUNK_SIZE",
                "TECHNICAL:标书索引分块大小（模型上下文护栏）");
        THRESHOLD_REGISTRY.put("service/bidagent/index/BidTextCleaner.java::WRAP_MERGE_MIN_LINE_LEN",
                "TECHNICAL:正文清洗-折行合并最小行长（版面规则，非业务阈值）");
        THRESHOLD_REGISTRY.put("service/bidagent/index/BidTextCleaner.java::WRAP_MERGE_MAX_LINE_LEN",
                "TECHNICAL:正文清洗-折行合并最大行长（版面规则，非业务阈值）");
        THRESHOLD_REGISTRY.put("service/bidagent/index/BidTextCleaner.java::RUNNING_LINE_MAX_LEN",
                "TECHNICAL:正文清洗-连续行最大长度（版面规则，非业务阈值）");
        THRESHOLD_REGISTRY.put("service/bidagent/index/BidTextCleaner.java::RUNNING_LINE_MIN_COUNT",
                "TECHNICAL:正文清洗-连续行判定最小数量（版面规则，非业务阈值）");
        THRESHOLD_REGISTRY.put("service/bidagent/index/BidTextCleaner.java::RUNNING_LINE_MIN_RATIO",
                "TECHNICAL:正文清洗-连续行占比阈值（版面规则，非业务阈值）");
        THRESHOLD_REGISTRY.put("service/bidagent/index/BidTextCleaner.java::PAGE_SEQ_MIN_SIZE",
                "TECHNICAL:正文清洗-页码序列最小长度（版面规则，非业务阈值）");
        THRESHOLD_REGISTRY.put("service/bidagent/index/BidTextCleaner.java::PAGE_SEQ_MAX_STEP",
                "TECHNICAL:正文清洗-页码最大步长（版面规则，非业务阈值）");
        THRESHOLD_REGISTRY.put("service/bidagent/index/BidTextCleaner.java::PAGE_SEQ_MIN_ASCENT_RATIO",
                "TECHNICAL:正文清洗-页码递增比例阈值（版面规则，非业务阈值）");
        THRESHOLD_REGISTRY.put("service/bidagent/index/BidTextCleaner.java::TABLE_TOKEN_MAX_LEN",
                "TECHNICAL:正文清洗-表格 token 上限（模型能力护栏）");
        THRESHOLD_REGISTRY.put("service/bidagent/index/BidTextCleaner.java::SAMPLE_LIMIT",
                "TECHNICAL:正文清洗-抽样行数上限（性能护栏）");
        THRESHOLD_REGISTRY.put("service/ca/CaSealServiceImpl.java::DEMO_SEED_MAX",
                "TECHNICAL:本地演示签章密钥种子上限（测试账号容量护栏）");
        THRESHOLD_REGISTRY.put("service/ca/CaSealServiceImpl.java::MAX_SEAL_FILE_BYTES",
                "TECHNICAL:签章单文件上限 15MB（infra 上传链路实测护栏，2026-09-24 由 16MB 下调）");
        THRESHOLD_REGISTRY.put("service/contract/ContractServiceImpl.java::PERFORMANCE_BOND_RATE_LIMIT",
                "LEGAL:履约保证金 ≤ 合同价 10%（实施条例第57条，法定上限）");
        THRESHOLD_REGISTRY.put("service/contract/ContractServiceImpl.java::SIGN_PERIOD_DAYS_LIMIT",
                "LEGAL:中标通知书发出 30 日内签约（招标投标法第46条，法定期限）");
        THRESHOLD_REGISTRY.put("service/contract/ContractServiceImpl.java::RETENTION_YEARS",
                "LEGAL:合同归档保存 ≥15 年（电子办法技术规范）");
        THRESHOLD_REGISTRY.put("service/contract/ContractServiceImpl.java::EXPIRING_SOON_DAYS",
                "TECHNICAL:合同保存期临期提醒阈值 90 天（运维提醒口径）");
        THRESHOLD_REGISTRY.put("service/deposit/BidDepositServiceImpl.java::DEPOSIT_RATE_LIMIT",
                "LEGAL:投标保证金 ≤ 项目估算价 2%（招标投标法实施条例第26条，法定上限）");
        THRESHOLD_REGISTRY.put("service/deposit/BidDepositServiceImpl.java::REFUND_PERIOD_DAYS_LIMIT",
                "LEGAL:未中标人保证金退还 ≤5 日、中标人 ≤30 日（条例第57条，法定期限）");
        THRESHOLD_REGISTRY.put("service/noticeexternal/NoticeExternalServiceImpl.java::MAX_KEYWORD_MATCH_LIMIT",
                "TECHNICAL:外部公告关键字匹配结果条数上限（性能护栏，非业务口径）");
        THRESHOLD_REGISTRY.put("service/noticeexternal/search/ExternalNoticeIndexMapping.java::MIN_GRAM",
                "TECHNICAL:外部公告 ES 索引最小分词粒度（检索质量参数）");
        THRESHOLD_REGISTRY.put("service/noticeexternal/search/ExternalNoticeIndexMapping.java::MAX_GRAM",
                "TECHNICAL:外部公告 ES 索引最大分词粒度（检索质量参数）");
        THRESHOLD_REGISTRY.put("service/noticefollow/FollowServiceImpl.java::DIMENSION_SEED_LIMIT",
                "TECHNICAL:关注维度种子条数上限（防账号批量关注刷屏护栏）");
        THRESHOLD_REGISTRY.put("service/objection/ObjectionServiceImpl.java::PREQUAL_OBJECTION_DAYS",
                "LEGAL:资格预审异议期 2 日（招标投标法实施条例第22条，法定期限）");
        THRESHOLD_REGISTRY.put("service/objection/ObjectionServiceImpl.java::TENDER_OBJECTION_DAYS",
                "LEGAL:招标文件异议期 10 日（招标投标法实施条例第22条，法定期限）");
        THRESHOLD_REGISTRY.put("service/objection/ObjectionServiceImpl.java::REPLY_PERIOD_DAYS",
                "LEGAL:异议答复期 3 日（招标投标法实施条例第22条，法定期限）");
        THRESHOLD_REGISTRY.put("service/prequal/PrequalCommitteeServiceImpl.java::MIN_MEMBER_COUNT",
                "LEGAL:资格审查委员会成员 ≥5 人（招标投标法第37条，法定下限）");
        THRESHOLD_REGISTRY.put("service/prequal/PrequalServiceImpl.java::MIN_APPLY_PERIOD_DAYS",
                "LEGAL:资格预审申请期 ≥5 日（招标投标法实施条例第16条，法定期限）");
        THRESHOLD_REGISTRY.put("service/report/ReportServiceImpl.java::REPORT_PERIOD_DAYS_LIMIT",
                "TECHNICAL:报告查询周期上限（报表性能护栏，非业务口径）");
        CONFIG_KEY_REGISTRY.put("CERT_EXPIRING_WINDOW_DAYS",
                "FRONTEND:列表角标与筛选口径，契约《企业资质页-页面级契约》§3.11 由前端把窗口天数传进来，后端不读");
        CONFIG_KEY_REGISTRY.put("CERT_FILE_MAX_SIZE_MB", "FRONTEND:上传前校验（前端读 /bidding/meta/configs）");
        CONFIG_KEY_REGISTRY.put("CERT_FILE_ALLOW_FORMATS", "FRONTEND:上传前校验（前端读 /bidding/meta/configs）");
        CONFIG_KEY_REGISTRY.put("CERT_ANNOTATION_MAX_DAYS", "PRESET:加注件功能未实现（技术方案 §8.3 序 5）");
        CONFIG_KEY_REGISTRY.put("CERT_ANNOTATION_CASCADE_ON_REVOKE", "PRESET:加注件功能未实现（技术方案 §8.3 序 5）");
        CONFIG_KEY_REGISTRY.put("PERSONNEL_CERT_REVIEW_WARN_DAYS",
                "DEBT:复审提醒当前走 cert_alert_config.thresholds 档位，与本键(60)的关系未定义，擅自接线会改变复审预警档位");
    }

    @Test
    void thresholdConstants_mustBeRegistered() throws IOException {
        Map<String, String> found = new LinkedHashMap<>();
        for (String relative : sourcesUnder("service", "job")) {
            String text = Files.readString(MAIN_ROOT.resolve(relative));
            Matcher matcher = THRESHOLD_CONSTANT.matcher(text);
            while (matcher.find()) {
                if (THRESHOLD_NAME.matcher(matcher.group(1)).find()) {
                    found.put(relative + "::" + matcher.group(1), matcher.group(2).trim());
                }
            }
        }

        Set<String> unregistered = new LinkedHashSet<>(found.keySet());
        unregistered.removeAll(THRESHOLD_REGISTRY.keySet());
        Set<String> stale = new LinkedHashSet<>(THRESHOLD_REGISTRY.keySet());
        stale.removeAll(found.keySet());

        assertTrue(unregistered.isEmpty(),
                ("发现未登记的业务阈值形状常量：%s。要么改走规则配置中心（RuleConfigService#resolveWithAudit，"
                        + "阈值必须可配）；确属工程性护栏的，登记进 THRESHOLD_REGISTRY 并写明理由。当前值：%s")
                        .formatted(unregistered, unregistered.stream().map(found::get).toList()));
        assertTrue(stale.isEmpty(),
                "以下登记项已不存在（多半已接线到配置中心），请从 THRESHOLD_REGISTRY 删除以免替旧问题背书：" + stale);
    }

    @Test
    void sameBranchTernary_mustBeRealMappingOrRemoved() throws IOException {
        Set<String> offenders = new LinkedHashSet<>();
        for (String relative : sourcesUnder("service", "job", "framework")) {
            // 只去注释、**保留字符串字面量**：字符串是业务取值，必须参与判定；
            // 若连字符串一起去掉，"A 与 B 不同"会被抹成"两个空串相同"而产生假阳性。
            String text = stripComments(Files.readString(MAIN_ROOT.resolve(relative)));
            Matcher matcher = SAME_BRANCH_TERNARY.matcher(text);
            while (matcher.find()) {
                offenders.add(relative + " → " + matcher.group().replaceAll("\\s+", " "));
            }
        }
        assertTrue(offenders.isEmpty(),
                ("发现同值三元（两个分支返回同一值，条件形同虚设，通常意味着「想做成映射却写成常量」）：%s。"
                        + "请改为从规则配置中心读取（可配口径），或直接删掉多余条件。").formatted(offenders));
    }

    /** 去掉注释（保留字符串与代码本体）。 */
    private static String stripComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    @Test
    void ruleKeys_mustUseConstantsNotLiterals() throws IOException {
        Set<String> offenders = new LinkedHashSet<>();
        for (String relative : sourcesUnder("service", "job")) {
            String text = Files.readString(MAIN_ROOT.resolve(relative));
            if (KEY_LITERAL.matcher(text).find()) {
                offenders.add(relative);
            }
        }
        assertTrue(offenders.isEmpty(),
                "以下文件把规则键写成了字符串字面量：%s。规则键必须用 RuleConfigKeys 常量——字面量拼错不会编译失败，"
                        .formatted(offenders) + "只会在运行期静默回落到代码兜底（配置改了不生效）");
    }

    @Test
    void configKeys_mustBeReadOrRegistered() throws IOException {
        String keysSource = Files.readString(KEYS_FILE);
        Map<String, String> declared = new LinkedHashMap<>();
        Matcher matcher = KEY_CONSTANT.matcher(keysSource);
        while (matcher.find()) {
            declared.put(matcher.group(1), matcher.group(2));
        }
        assertTrue(declared.size() >= 20, "未解析到规则键常量（RuleConfigKeys 结构变了？解析到 " + declared.size() + " 个）");

        StringBuilder allSources = new StringBuilder();
        for (String relative : sourcesUnder("service", "job", "controller", "dal")) {
            allSources.append(Files.readString(MAIN_ROOT.resolve(relative))).append('\n');
        }
        String body = allSources.toString();

        Set<String> unread = new LinkedHashSet<>();
        declared.forEach((name, key) -> {
            if (!Pattern.compile("RuleConfigKeys\\." + name + "\\b").matcher(body).find()) {
                unread.add(name);
            }
        });
        Set<String> unregistered = new LinkedHashSet<>(unread);
        unregistered.removeAll(CONFIG_KEY_REGISTRY.keySet());
        Set<String> stale = new LinkedHashSet<>(CONFIG_KEY_REGISTRY.keySet());
        stale.removeAll(unread);

        assertTrue(unregistered.isEmpty(),
                ("以下配置键在 Java 侧从未被读取、也未登记归属：%s（对应键值 %s）。"
                        + "配置键「看起来可配、改了没反应」比硬编码更危险——请接线，或在 CONFIG_KEY_REGISTRY 写明"
                        + "FRONTEND/PRESET/DEBT 及理由").formatted(unregistered,
                        unregistered.stream().map(declared::get).toList()));
        assertTrue(stale.isEmpty(),
                "以下配置键已被代码读取（或常量已删），请从 CONFIG_KEY_REGISTRY 移除：" + stale);
    }

    /** 列出指定顶层包下的全部 .java 相对路径。 */
    private Set<String> sourcesUnder(String... topPackages) throws IOException {
        Set<String> result = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(MAIN_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = MAIN_ROOT.relativize(file).toString().replace('\\', '/');
                for (String top : topPackages) {
                    if (relative.startsWith(top + "/")) {
                        result.add(relative);
                    }
                }
            }
        }
        return result;
    }
}
