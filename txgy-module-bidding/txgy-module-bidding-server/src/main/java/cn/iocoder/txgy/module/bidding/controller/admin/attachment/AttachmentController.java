package cn.iocoder.txgy.module.bidding.controller.admin.attachment;
// 规范依据：《招投标模块开发规范》§4.2（Controller 位于 controller/admin/<域>，名 XxxController）
//   + §4.4/§4.5（映射方法带 @PreAuthorize；写方法带 @Idempotent 幂等）
//   + §5 三-5 文件存储口径（FileApi + file_id/sha256/size）+ 红线 8
//   + 用户裁定 2026-09-24：针对主键的专门附件表（一对 N）；URL/对象存储承载。
// [业务目标] 项目/招标项目/标段/公告的通用附件绑定：文件本体走 /bidding/file/upload 换 fileId，
//   本控制器只维护「业务实体 ↔ 附件行」的一对 N 关系（biz_type + biz_code 关联，不侵入四实体表）。
// [为什么] 四实体 VO/DO 由各域代理维护，附件做成独立绑定式可零侵入接入；
//   数据集规范 AttachmentSetVO 的 ATTACHMENT_NAME/TYPE/FILE_NAME/URL 字段在此逐一对齐。

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.attachment.vo.AttachmentRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.attachment.vo.AttachmentSaveReqVO;
import cn.iocoder.txgy.module.bidding.service.attachment.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - bidding 通用附件")
@RestController
@RequestMapping("/bidding/attachment")
@Validated
public class AttachmentController {

    @Resource
    private AttachmentService attachmentService;

    @PostMapping("/save")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "绑定附件（一对 N；同一 (bizType,bizCode,fileId) 幂等返回原行）",
            description = "文件本体请先走 POST /bidding/file/upload 拿到 fileId/url/sha256 后再绑定"
            + "；bizType: PROJECT/TENDER_PROJECT/BID_SECTION/ANNOUNCEMENT/BID_TRACK/PREQUAL_APPLY（后两者见契约《投标与资格预审多文件化-2026-10-02》R1）"
            + "，bizCode 为对应业务编号。")
    public CommonResult<AttachmentRespVO> save(@Valid @RequestBody AttachmentSaveReqVO reqVO) {
        return success(attachmentService.save(reqVO));
    }

    @DeleteMapping("/delete")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "解绑附件（逻辑删除）")
    public CommonResult<Boolean> remove(@RequestParam("id") Long id) {
        attachmentService.remove(id);
        return success(true);
    }

    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "按业务实体查附件列表（一对 N；按 seq 排序）", description = "bizType + bizCode 必传；INFECTED 行不下发下载 URL（virusStatus 标识）")
    public CommonResult<List<AttachmentRespVO>> list(
            @RequestParam("bizType") String bizType,
            @RequestParam("bizCode") String bizCode) {
        return success(attachmentService.list(bizType, bizCode));
    }

}