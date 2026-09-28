package cn.iocoder.txgy.module.bidding.service.attachment;
// 规范依据：《招投标模块开发规范》§5 三-5（文件经 FileApi，库内只记 file_id+sha256+size，URL 非一致性依据）
//   + 红线 8（附件三件套）+ api/vo/AttachmentSetVO（附件集结构）+ 用户裁定（一对 N 附件表/URL/对象存储）

import cn.iocoder.txgy.module.bidding.controller.admin.attachment.vo.AttachmentRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.attachment.vo.AttachmentSaveReqVO;

import java.util.List;

/**
 * bidding 通用附件服务（项目/招标项目/标段/公告 一对 N）。
 *
 * <p>文件本体由 {@code POST /bidding/file/upload} 上传并换取 fileId（一致性依据），本服务只维护
 * 「业务实体 ↔ 附件行」的绑定关系（biz_type + biz_code 一对 N）。
 */
public interface AttachmentService {

    /**
     * 绑定附件（幂等：同一 (bizType, bizCode, fileId) 重复绑定返回原行）。
     *
     * @param reqVO 附件绑定入参
     * @return 附件行
     */
    AttachmentRespVO save(AttachmentSaveReqVO reqVO);

    /**
     * 解绑附件（逻辑删除；不存在抛 ATTACHMENT_NOT_EXISTS）。
     *
     * @param id 附件行主键
     */
    void remove(Long id);

    /**
     * 按业务实体查附件列表（一对 N；按 seq 排序）。
     *
     * @param bizType 归属实体类型
     * @param bizCode 归属实体业务编号
     * @return 附件行列表
     */
    List<AttachmentRespVO> list(String bizType, String bizCode);

}