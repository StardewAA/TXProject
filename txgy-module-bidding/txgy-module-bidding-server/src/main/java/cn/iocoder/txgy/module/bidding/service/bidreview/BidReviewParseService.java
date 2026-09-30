package cn.iocoder.txgy.module.bidding.service.bidreview;

/**
 * 智慧标书审查 · 文件解析服务（取流 → 抽文本）。
 *
 * <p>方案依据：§4.2 第 1 步 PARSE、§2.4（扫描件不做图片转写）。
 */
public interface BidReviewParseService {

    /**
     * 解析文件为纯文本。
     *
     * @param fileUrl  文件访问地址（上传接口返回的 url；仅作取流定位）
     * @param fileName 文件名（用于按扩展名选解析器）
     * @return 解析结果（text 为空即视为扫描件，调用方按 PARSE_EMPTY 失败）
     */
    ParseResult parse(String fileUrl, String fileName);

    /** 解析结果：mode=TEXT 有文字层 / EMPTY 无文字层（扫描件）。 */
    record ParseResult(String text, String mode) {

        public boolean isEmpty() {
            return text == null || text.isBlank();
        }
    }
}
