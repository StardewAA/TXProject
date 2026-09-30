package cn.iocoder.txgy.module.bidding.controller.admin.bidagent.contract;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.contract.vo.BidSegmentIndexReqVO;
import cn.iocoder.txgy.module.bidding.service.bidagent.index.BidIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_SEGMENT_SOURCE_EMPTY;

/**
 * 投标检索索引接口：文档上传 → 切分 → 向量化 → 入库（检索的前置链路）。
 */
@Tag(name = "管理后台 - 投标检索索引")
@RestController
@RequestMapping("/bidding/bid-agent/contract/segment")
@Validated
// 契约端由 Node 侧机对机调用，不携带 tenant-id 请求头；租户由请求参数显式传入，
// 服务层再用 TenantUtils.execute(tenantId, ...) 开启租户过滤，故此处需跳过租户校验过滤器。
@TenantIgnore
public class BidSegmentController {

    @Resource
    private BidIndexService indexService;

    @PostMapping("/index")
    @Operation(summary = "索引文档：url（Tika 解析 PDF/docx）或 content（纯文本）二选一，切分+向量化+入库")
    @PermitAll // 机对机契约端：免登（鉴权由 BidAgentKeyFilter 校验 X-Agent-Key）
    @Idempotent // 写方法幂等：同租户同文档同内容在 1s 内重复提交直接拦截
    public CommonResult<Integer> index(@Valid @RequestBody BidSegmentIndexReqVO reqVO) {
        if (StrUtil.isBlank(reqVO.getUrl()) && StrUtil.isBlank(reqVO.getContent())) {
            throw exception(BID_SEGMENT_SOURCE_EMPTY);
        }
        Map<String, Object> extraMeta = new HashMap<>();
        if (reqVO.getChapterPath() != null) {
            extraMeta.put("chapterPath", reqVO.getChapterPath());
        }
        if (reqVO.getPageNo() != null) {
            extraMeta.put("pageNo", reqVO.getPageNo());
        }
        // url 优先：下载 → Tika 抽取全文 → 入库；否则走纯文本直传（契约端无材料编号，按文档名口径）
        int count;
        if (StrUtil.isNotBlank(reqVO.getUrl())) {
            count = indexService.indexUrl(reqVO.getTenantId(), reqVO.getProjectId(), null, reqVO.getDocType(),
                    reqVO.getSourceDocName(), reqVO.getBidSectionCode(), reqVO.getUrl(), extraMeta);
        } else {
            count = indexService.indexDocument(reqVO.getTenantId(), reqVO.getProjectId(), null, reqVO.getDocType(),
                    reqVO.getSourceDocName(), reqVO.getBidSectionCode(), reqVO.getContent(), extraMeta);
        }
        return success(count);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除某文档的全部片段")
    @PermitAll
    @Idempotent
    public CommonResult<Boolean> delete(@RequestParam("tenantId") Long tenantId,
                                        @RequestParam("sourceDocName") String sourceDocName) {
        indexService.deleteBySourceDocName(tenantId, sourceDocName);
        return success(true);
    }
}
