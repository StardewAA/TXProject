package cn.iocoder.txgy.module.infra.dal.mysql.file;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import cn.iocoder.txgy.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.txgy.module.infra.enums.file.FileAccessTypeEnum;
import cn.iocoder.txgy.module.infra.enums.file.FileBindStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 文件操作 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface FileMapper extends BaseMapperX<FileDO> {

    default PageResult<FileDO> selectPage(FilePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FileDO>()
                .likeIfPresent(FileDO::getPath, reqVO.getPath())
                .likeIfPresent(FileDO::getType, reqVO.getType())
                .betweenIfPresent(FileDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(FileDO::getId));
    }

    /**
     * 按 (configId, path) 查询**最早**的一条存活记录（原始登记者）
     *
     * 安全说明：历史数据里同一 (configId, path) 可能存在多条记录（例如同一天重复上传同名文件）。
     * 读取判定必须锚定**最早**的记录，避免「后插入的记录遮蔽原归属」——这是冒领漏洞的
     * 读取侧成因之一，因此这里刻意取 first 而非 last。
     *
     * @return 最早一条；不存在返回 null
     */
    default FileDO selectFirstByConfigIdAndPath(Long configId, String path) {
        List<FileDO> list = selectList(new LambdaQueryWrapperX<FileDO>()
                .eq(FileDO::getConfigId, configId)
                .eq(FileDO::getPath, path)
                .orderByAsc(FileDO::getId));
        return CollUtil.getFirst(list);
    }

    /**
     * 按 path 查询**最早**的一条存活记录（忽略 config_id）
     *
     * 用途：文件被服务端绑定确认为公开并提升到公开 OSS 后，其 {@code config_id} 会变为 OSS 配置，
     * 但业务实体仍持有上传期返回的网关 URL（内嵌原 master 的 config_id）。当按 (原 config_id, path)
     * 查不到时，用 path 兜底反查，使该 URL 仍能解析到已提升的记录。
     * path 含时间戳+随机数后缀，实际唯一，不会误命中其它文件。
     *
     * @return 最早一条；不存在返回 null
     */
    default FileDO selectFirstByPath(String path) {
        List<FileDO> list = selectList(new LambdaQueryWrapperX<FileDO>()
                .eq(FileDO::getPath, path)
                .orderByAsc(FileDO::getId));
        return CollUtil.getFirst(list);
    }

    /**
     * 查询「已确认公开但未进入公开 OSS」的文件（OSS 提升补传候选）。
     *
     * 口径：access_type=1（公开）且 bind_status=2（已公开绑定），但 config_id 不等于公开 OSS 配置。
     * 这类文件内容仍停留在 master（或历史存储），需要被补传到公开桶。
     *
     * 安全约束：
     * 1. 只选 PUBLIC 文件 —— 私有文件（access_type=2）绝不会被选中，因此补传机制
     *    不会把任何私有文件迁移到公共桶；
     * 2. 已在 OSS（config_id == ossConfigId）的文件被排除（由调用方幂等保证，这里再兜底一次）。
     *
     * @param ossConfigId 公开 OSS 配置编号（例如 36）
     * @param limit       最大条数
     */
    default List<FileDO> selectPublicUnpromoted(Long ossConfigId, int limit) {
        return selectList(new LambdaQueryWrapperX<FileDO>()
                .eq(FileDO::getAccessType, FileAccessTypeEnum.PUBLIC.getAccessType())
                .eq(FileDO::getBindStatus, FileBindStatusEnum.PUBLIC.getStatus())
                .ne(FileDO::getConfigId, ossConfigId)
                .orderByAsc(FileDO::getId)
                .last("LIMIT " + limit));
    }

    /**
     * 按访问地址批量查询文件记录（用于业务绑定）
     */
    default List<FileDO> selectListByUrls(Collection<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<FileDO>().in(FileDO::getUrl, urls));
    }

    /**
     * 查询已过期且仍未绑定业务单据的私有文件（孤儿文件清理候选）
     *
     * 安全约束（保守清理）：
     * 1. 仅"待绑定(UNBOUND)"：已绑定(BOUND)/公开(PUBLIC) 一律不在候选内；
     * 2. 必须有明确的过期时间：历史文件（bind_expire_time = NULL）永不进入候选；
     * 3. **必须有明确上传者**：系统/爬虫等无登录身份的上传（owner 为空）不参与清理，
     *    避免把平台自动生成、但业务表未逐条引用的文件误删。
     *
     * 注意：命中的候选在执行前还会经过 {@link #countBusinessReferences(String)} 二次护栏。
     *
     * @param now   当前时间
     * @param limit 最大条数
     */
    default List<FileDO> selectExpiredUnboundList(LocalDateTime now, Integer limit) {
        return selectList(new LambdaQueryWrapperX<FileDO>()
                .eq(FileDO::getBindStatus, cn.iocoder.txgy.module.infra.enums.file.FileBindStatusEnum.UNBOUND.getStatus())
                .isNotNull(FileDO::getBindExpireTime)
                .lt(FileDO::getBindExpireTime, now)
                .isNotNull(FileDO::getOwnerUserId)
                .isNotNull(FileDO::getOwnerUserType)
                .orderByAsc(FileDO::getId)
                .last("LIMIT " + (limit == null || limit <= 0 ? 100 : limit)));
    }

    /**
     * 统计该文件 URL 是否仍被业务数据引用（孤儿清理前的**安全护栏**）
     *
     * 背景：业务提交后若因网络等原因未能成功回绑（bindFile RPC 失败），文件会停留在
     * 「待绑定」状态。若清理只看时间，就会把**已落库的业务图片**一并删除，页面随即 404。
     * 因此清理前必须确认该 URL 未被任何业务数据引用。
     *
     * 实现说明：
     * 1. 覆盖"公开面"与"私有单据面"两类列（清单由 script/discover-url-columns.py 全库扫描得出，
     *    新增对外/单据图片列时需同步补充）；
     * 2. 刻意不做 LIKE 通配符转义：URL 中的 {@code _}/{@code %} 只会让匹配更宽，
     *    从而**更保守**（宁可漏删，绝不错删），符合护栏语义；
     * 3. 若业务表缺失导致查询异常，调用方按"疑似被引用"处理，跳过删除。
     *
     * @param url 文件访问地址
     * @return 引用条数；&gt;0 表示仍被业务数据引用
     */
    @Select("SELECT COUNT(*) FROM (" +
            "SELECT 1 FROM `product_spu` WHERE `pic_url` LIKE CONCAT('%', #{url}, '%') OR `slider_pic_urls` LIKE CONCAT('%', #{url}, '%') OR `description` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `product_sku` WHERE `pic_url` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `product_brand` WHERE `pic_url` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `product_category` WHERE `pic_url` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `product_shop` WHERE `logo_url` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `product_comment` WHERE `pic_urls` LIKE CONCAT('%', #{url}, '%') OR `sku_pic_url` LIKE CONCAT('%', #{url}, '%') OR `user_avatar` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `promotion_banner` WHERE `pic_url` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `promotion_article` WHERE `pic_url` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `promotion_article_category` WHERE `pic_url` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `promotion_seckill_config` WHERE `slider_pic_urls` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `promotion_diy_page` WHERE `preview_pic_urls` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `promotion_diy_template` WHERE `preview_pic_urls` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `promotion_combination_record` WHERE `pic_url` LIKE CONCAT('%', #{url}, '%') OR `avatar` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `promotion_kefu_message` WHERE `content` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `promotion_kefu_conversation` WHERE `last_message_content` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `promotion_kefu_chat_message` WHERE `card_json` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `member_level` WHERE `background_url` LIKE CONCAT('%', #{url}, '%') OR `icon` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `trade_order` WHERE `shop_logo_url` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `trade_order_item` WHERE `pic_url` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `trade_after_sale` WHERE `pic_url` LIKE CONCAT('%', #{url}, '%') OR `apply_pic_urls` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `trade_delivery_express` WHERE `logo` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `trade_delivery_pick_up_store` WHERE `logo` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `trade_platform_checkout_plan_item` WHERE `pic_url` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `trade_brokerage_withdraw` WHERE `qr_code_url` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `product_purchase_inquiry_item` WHERE `pic_url` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `product_enterprise` WHERE `license_url` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `member_user` WHERE `avatar` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `system_users` WHERE `avatar` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `ai_chat_role` WHERE `avatar` LIKE CONCAT('%', #{url}, '%') " +
            "UNION ALL SELECT 1 FROM `txgy_demo01_contact` WHERE `avatar` LIKE CONCAT('%', #{url}, '%') " +
            ") t")
    Long countBusinessReferences(@Param("url") String url);

}
