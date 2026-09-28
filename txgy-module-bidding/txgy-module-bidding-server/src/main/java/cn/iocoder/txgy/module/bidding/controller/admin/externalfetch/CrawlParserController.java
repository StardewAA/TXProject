package cn.iocoder.txgy.module.bidding.controller.admin.externalfetch;
// [依据] 爬虫解析器插件测试接口（2026-09，先解析后下载的开发顺序）：
//       GET /list 解析器插件清单；POST /test 用 HTML/JSON 文本框或文件上传测试指定解析器。
//       权限复用 bidding:crawl-source:query/update（菜单 60002100~；如需独立按钮后续可注册 60002107）。

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlParseTestRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlParserInfoRespVO;
import cn.iocoder.txgy.module.bidding.service.externalfetch.CrawlParseTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 爬虫解析器（插件/策略测试）。
 */
@Tag(name = "管理后台 - bidding 爬虫解析器（插件测试）")
@RestController
@RequestMapping("/bidding/crawl-parser")
@Validated
public class CrawlParserController {

    @Resource
    private CrawlParseTestService crawlParseTestService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('bidding:crawl-source:query')")
    @Operation(summary = "解析器插件清单（code/名称/说明/支持类型）")
    public CommonResult<List<CrawlParserInfoRespVO>> list() {
        return success(crawlParseTestService.listParsers());
    }

    @PostMapping("/test")
    @PreAuthorize("@ss.hasPermission('bidding:crawl-source:update')")
    @Idempotent
    @Operation(summary = "解析器测试（HTML/JSON 文本框或文件上传；multipart/form-data）")
    public CommonResult<CrawlParseTestRespVO> test(
            @RequestParam("parserCode") @NotEmpty(message = "解析器 code 不能为空") String parserCode,
            @RequestParam("payloadType") @NotEmpty(message = "载荷类型不能为空（HTML/JSON/FILE）") String payloadType,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "fileName", required = false) String fileName,
            @RequestParam(value = "url", required = false) String url,
            @RequestPart(value = "file", required = false) MultipartFile file) throws IOException {
        byte[] fileBytes = file == null || file.isEmpty() ? null : file.getBytes();
        return success(crawlParseTestService.testParse(parserCode, payloadType, content, fileName, url, fileBytes));
    }

}
