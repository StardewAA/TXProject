package cn.iocoder.txgy.module.cms.service.review;

import org.springframework.stereotype.Component;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import static cn.iocoder.txgy.module.cms.service.review.ReviewModels.*;

/** 确定性检查，不访问用户URL，不让LLM决定法律效力。 */
@Component
public class ReviewEngine {
    private final Clock clock;
    public ReviewEngine() { this(Clock.systemUTC()); }
    public ReviewEngine(Clock clock) { this.clock = clock; }
    public LocalDate today() { return LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai"))); }
    public static String hash(String text) {
        try {
            // Only line endings and outer whitespace are normalized; do not erase clause boundaries.
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    text.replace("\r\n", "\n").replace('\r','\n').strip().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static boolean officialUrl(String value) {
        try {
            URI uri = URI.create(value);
            String host = Objects.toString(uri.getHost(), "").toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo()==null
                    && (uri.getPort()==-1 || uri.getPort()==443) && host.endsWith(".gov.cn");
        } catch (Exception e) { return false; }
    }

    public Report review(String id, String createdAt, Metadata m, String text, List<Issue> parsing, Evidence evidence) {
        List<Issue> issues = new ArrayList<>(parsing);
        String[] lines = text.split("\\R", -1);
        for (int i=0; i<lines.length; i++) {
            if (lines[i].codePoints().anyMatch(c -> c==0xFFFD || c==0xFFFE || c==0xFFFF ||
                    (Character.isISOControl(c) && c!='\t') || Character.getType(c)==Character.PRIVATE_USE)) {
                add(issues, Dimension.ENCODING, Severity.ERROR, "GARBLED_CHARACTERS", "第"+(i+1)+"行",
                        "发现替换字符、私用区或异常控制字符", "对照原文重新编码或校对OCR");
                if (issues.size()>100) break;
            }
        }
        if (Pattern.compile("[ÃÂ]{2,}|锟斤拷").matcher(text).find())
            add(issues,Dimension.ENCODING,Severity.ERROR,"MOJIBAKE","全文","发现疑似编码损坏文本","用正确编码重新导出原文");
        if (text.strip().length()<80) add(issues,Dimension.QUALITY,Severity.ERROR,"TOO_SHORT","全文","正文不足80字符，无法作为完整法规资料","提交完整原文，不能只提交名称或摘要");
        if (text.length()>ReviewParser.MAX_CHARS) add(issues,Dimension.QUALITY,Severity.ERROR,"TEXT_LIMIT","全文","正文超过100万字符","分卷提交");
        if (!officialUrl(m.sourceUrl())) add(issues,Dimension.AUTHENTICITY,Severity.ERROR,"SOURCE_DOMAIN","来源链接","需要HTTPS政府官网原文链接","填写官方发布页；官网域名仅是初筛，不代表已经核实");
        if (evidence==null) {
            add(issues,Dimension.AUTHENTICITY,Severity.ERROR,"SOURCE_UNVERIFIED","来源与效力","尚无授权核验人员提交的官方依据","由核验人员对照官方原文、版本沿革并登记核验记录");
        } else {
            if (!officialUrl(evidence.validitySourceUrl()) || !officialUrl(evidence.officialMetadata().sourceUrl()))
                add(issues,Dimension.AUTHENTICITY,Severity.ERROR,"EVIDENCE_URL","核验依据","核验依据不是支持的官方链接","补充有效的HTTPS政府官网依据");
            if (evidence.checkedOn()==null || evidence.checkedOn().isBefore(today().minusDays(30)) || evidence.checkedOn().isAfter(today()))
                add(issues,Dimension.AUTHENTICITY,Severity.ERROR,"STALE_EVIDENCE","核验日期","核验记录已过期或日期异常","重新核对官方版本沿革；核验有效期30天");
            if (!hash(text).equals(evidence.officialTextHash()))
                add(issues,Dimension.AUTHENTICITY,Severity.ERROR,"TEXT_MISMATCH","正文","提交正文与核验的官方全文不一致","使用官方完整正文；不要省略、改写或混入摘要");
            Metadata official = evidence.officialMetadata();
            if (!Objects.equals(m.title(),official.title()) || !Objects.equals(m.documentNo(),official.documentNo())
                    || !Objects.equals(m.issuer(),official.issuer()) || !Objects.equals(m.sourceUrl(),official.sourceUrl())
                    || m.kind()!=official.kind() || m.validity()!=official.validity()
                    || !Objects.equals(m.effectiveDate(),official.effectiveDate()) || !Objects.equals(m.expiryDate(),official.expiryDate()))
                add(issues,Dimension.AUTHENTICITY,Severity.ERROR,"METADATA_MISMATCH","元数据","标题、文号、机关、链接或效力信息与官方核验记录冲突","按报告中的官方元数据修改后重新提交");
        }
        if (m.validity()==Validity.UNKNOWN) add(issues,Dimension.AUTHENTICITY,Severity.ERROR,"VALIDITY_UNKNOWN","法规效力","当前效力待核实","核实实施、废止或替代依据后再入库");
        if (m.effectiveDate()==null) add(issues,Dimension.FORMAT,Severity.ERROR,"MISSING_EFFECTIVE_DATE","实施日期","缺少实施日期","根据官方原文补充日期");
        if (m.expiryDate()!=null && m.effectiveDate()!=null && m.expiryDate().isBefore(m.effectiveDate()))
            add(issues,Dimension.FORMAT,Severity.ERROR,"DATE_ORDER","日期","失效日期早于实施日期","核对版本日期");
        boolean ended = m.validity()==Validity.REPEALED || m.validity()==Validity.EXPIRED;
        if (ended && (m.expiryDate()==null || m.expiryDate().isAfter(today())))
            add(issues,Dimension.AUTHENTICITY,Severity.ERROR,"END_DATE","失效日期","历史版本缺少有效的失效日期","按官方依据填写最后有效日期");
        if (ended) add(issues,Dimension.AUTHENTICITY,m.historical()?Severity.WARNING:Severity.ERROR,"HISTORICAL","法规效力",
                "该版本已经废止或失效", "仅能勾选历史资料后归档，不能标为现行依据");
        if (m.validity()==Validity.EFFECTIVE && ((m.effectiveDate()!=null && m.effectiveDate().isAfter(today())) || (m.expiryDate()!=null && m.expiryDate().isBefore(today()))))
            add(issues,Dimension.AUTHENTICITY,Severity.ERROR,"EFFECTIVE_CONFLICT","法规效力","现行状态与实施或失效日期冲突","修正效力状态");
        if (m.validity()==Validity.NOT_YET_EFFECTIVE) {
            if (m.effectiveDate()==null || !m.effectiveDate().isAfter(today())) add(issues,Dimension.AUTHENTICITY,Severity.ERROR,"FUTURE_DATE","实施日期","尚未生效状态与日期冲突","填写未来的实施日期");
            add(issues,Dimension.AUTHENTICITY,Severity.WARNING,"FUTURE_VERSION","法规效力","文件尚未生效","入库后保留尚未生效标记，不作为当前依据");
        }
        if (m.kind()==Kind.LAW) checkArticles(lines,issues);
        if (m.kind()==Kind.STANDARD && !m.documentNo().matches("(?i)GB(?:/T)?\\s*\\d+(?:\\.\\d+)?[-—]\\d{4}"))
            add(issues,Dimension.FORMAT,Severity.ERROR,"STANDARD_NUMBER","标准编号","国标编号格式不完整","例如 GB/T 23257-2017");
        Set<String> paragraphs = new HashSet<>();
        for (int i=0;i<lines.length;i++) if (lines[i].strip().length()>60 && !paragraphs.add(lines[i].strip())) {
            add(issues,Dimension.QUALITY,Severity.WARNING,"DUPLICATE_PARAGRAPH","第"+(i+1)+"行","发现重复长段落","核对是否重复粘贴；与官方原文一致的重复可以保留"); break;
        }
        EnumMap<Dimension,Integer> scores = new EnumMap<>(Dimension.class);
        for (Dimension d:Dimension.values()) scores.put(d,Math.max(0,100-issues.stream().filter(x->x.dimension()==d).mapToInt(x->x.severity()==Severity.ERROR?50:10).sum()));
        int total=scores.values().stream().mapToInt(Integer::intValue).sum()/4;
        Decision decision=issues.stream().anyMatch(x->x.severity()==Severity.ERROR)?Decision.FAIL:issues.isEmpty()?Decision.PASS:Decision.WARNING;
        return new Report(id,createdAt,m,hash(text),decision,total,Map.copyOf(scores),List.copyOf(issues),evidence,false,0,
                WorkflowStatus.PENDING_MANUAL,"compliance-rule-v1",Instant.now(clock).toString(),null,null,null);
    }
    private void checkArticles(String[] lines,List<Issue> issues) {
        // Official Chinese texts use ideographic, non-breaking and narrow spaces around article numbers.
        var pattern=Pattern.compile("^\\s*第([^\\s条]{1,12})\\s*条", Pattern.UNICODE_CHARACTER_CLASS);
        int previous=0;
        for (int i=0;i<lines.length;i++) {
            var match=pattern.matcher(lines[i]);
            if (match.find()) {
                int current=chineseNumber(match.group(1));
                if(current!=previous+1) add(issues,Dimension.FORMAT,Severity.ERROR,"ARTICLE_SEQUENCE","第"+(i+1)+"行","条号缺失、重复或乱序：预期第"+(previous+1)+"条，实际第"+current+"条","对照完整原文补齐条款和段落；仅支持整部法律审查");
                previous=current;
                if(issues.size()>100) break;
            }
        }
        if(previous==0) add(issues,Dimension.FORMAT,Severity.ERROR,"NO_ARTICLES","全文","未识别到法律条号","按原文保留“第一条”等条款起始行");
    }
    static int chineseNumber(String text) {
        int sum=0,n=0;
        for(char c:text.toCharArray()) {
            int d="零一二三四五六七八九".indexOf(c);
            if(d>=0 || c=='〇') n=Math.max(0,d);
            else { sum+=(n==0?1:n)*switch(c){case '十'->10;case '百'->100;case '千'->1000;default->10000;}; n=0; }
        }
        return sum+n;
    }
    private void add(List<Issue> list,Dimension d,Severity s,String code,String location,String message,String suggestion) {
        list.add(new Issue(d,s,code,location,message,suggestion));
    }
    public static List<Chunk> chunk(String text) {
        List<Chunk> chunks=new ArrayList<>();
        for(int start=0;start<text.length();) {
            int end=Math.min(start+800,text.length());
            if(end<text.length()) {
                int line=text.lastIndexOf('\n',end-1);
                if(line>start+400) end=line+1;
                if(Character.isHighSurrogate(text.charAt(end-1))) end--;
            }
            chunks.add(new Chunk(chunks.size()+1,start,end,text.substring(start,end)));
            start=end;
        }
        return List.copyOf(chunks);
    }
}
