package cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal;
// 规范依据：《招投标模块开发规范》§4.2（Mapper/BaseMapperX）；设计文档 v0.6 §7

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalAttachmentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外部公告附件 Mapper（G 轨）。
 */
@Mapper
public interface NoticeExternalAttachmentMapper extends BaseMapperX<NoticeExternalAttachmentDO> {
}
