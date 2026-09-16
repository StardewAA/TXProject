package cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal;
// 规范依据：《招投标模块开发规范》§4.2（Mapper/BaseMapperX）；设计文档 v0.6 §7

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Objects;

/**
 * 外部公告主档 Mapper（G 轨，不拼租户条件）。
 */
@Mapper
public interface NoticeExternalMapper extends BaseMapperX<NoticeExternalDO> {

    /**
     * 去重读取公告类型原文（管理端「公告类型」下拉选项）。
     *
     * <p>notice_type_raw 是外站自述类型原文（自由文本，无枚举/字典），本查询仅取去重后的值作下拉选项，
     * 不参与检索——检索仍走 eq 精确匹配 / ES 全文（红线：不开放业务表 LIKE）。
     * 空值/空串已在 SQL 与 Java 侧双重过滤，保证下拉不含脏项。
     */
    default List<String> selectDistinctNoticeTypeRaw() {
        return selectObjs(new QueryWrapper<NoticeExternalDO>()
                .select("DISTINCT notice_type_raw AS t")
                .isNotNull("notice_type_raw")
                .ne("notice_type_raw", "")
                .orderByAsc("notice_type_raw"))
                .stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
