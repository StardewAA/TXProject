package cn.iocoder.txgy.module.bidding.service.externalfetch;
// [依据] 解析器测试服务：管理端“先测解析器、后接下载器”开发顺序的接口支撑（2026-09）。

import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlParseTestRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlParserInfoRespVO;

import java.util.List;

/**
 * 爬虫解析器插件服务：解析器清单 + 解析器测试（HTML 文本/JSON 文本/文件上传）。
 */
public interface CrawlParseTestService {

    /** 解析器插件清单（已注册的全部策略，按 code 排序） */
    List<CrawlParserInfoRespVO> listParsers();

    /**
     * 解析器测试：按 parserCode 路由到策略并执行；payloadType=FILE 时按扩展名归一为 HTML/JSON。
     *
     * @param parserCode 解析器 code
     * @param payloadType HTML/JSON/FILE
     * @param content     HTML/JSON 文本（FILE 时忽略）
     * @param fileName    上传文件名（FILE 时必填）
     * @param url         来源 URL（可选，测试上下文/附件绝对化）
     * @param fileBytes   上传文件内容（FILE 时必填）
     * @return 解析测试结果（状态/公告字段/meta/告警/耗时）
     */
    CrawlParseTestRespVO testParse(String parserCode, String payloadType, String content,
                                   String fileName, String url, byte[] fileBytes);

}
