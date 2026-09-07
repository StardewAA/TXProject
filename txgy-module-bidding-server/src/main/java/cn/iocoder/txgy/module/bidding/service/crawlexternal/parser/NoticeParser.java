package cn.iocoder.txgy.module.bidding.service.crawlexternal.parser;
// [依据] 爬虫解析插件 SPI（与下载通道 DownloadChannelAdapter 对称）：采集源 parser_code_default 路由到解析器；
//       新增站点规则=新增一个 NoticeParser 实现并注册为 Bean（ParserRegistry 自动收集），不改主流程。

import java.util.List;

/**
 * 公告解析器插件（策略模式 SPI）。
 *
 * <p>职责：把原始数据（HTML/JSON 文本或文件）解析为公告规范化字段（NoticeParseResult）。
 * 下载器（channel 插件）产出原文 → 本解析器消费 → ingest 落库；当前解析器测试接口先于下载器落地。
 */
public interface NoticeParser {

    /** 解析器 code（与 bidding_crawl_source.parser_code_default / URL 审批 parser_code 对应） */
    String getParserCode();

    /** 解析器名称（管理界面展示） */
    String getParserName();

    /** 适用说明（站点/场景/规则版本） */
    String getDescription();

    /** 支持的载荷类型（CrawlParsePayloadTypeEnum.type：HTML/JSON） */
    List<String> supportedPayloadTypes();

    /** 执行解析（不得抛出业务异常：内部捕获，失败以 result.status=FAILED + error 表达） */
    NoticeParseResult parse(ParseInput input);

}
