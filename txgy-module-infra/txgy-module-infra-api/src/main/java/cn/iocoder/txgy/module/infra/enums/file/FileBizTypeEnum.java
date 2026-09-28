package cn.iocoder.txgy.module.infra.enums.file;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件业务类型枚举
 *
 * 说明：
 * 1. {@link #directory} 是**服务端维护**的目录约定，用于把客户端传入的 {@code directory} 归一化成一个受控的业务类型；
 * 2. {@link #publicCapable} 只表示"该业务类型**允许**被服务端确认为公开资源"，**不代表上传即公开**；
 * 3. 未匹配到任何业务类型的文件归为 {@link #OTHER}，一律按私有处理（安全默认）。
 *
 * 该枚举位于 infra-api，业务模块在调用 {@code FileApi#bindFile} 时按 {@link #name()} 声明业务类型，
 * 由 infra 侧重新解析并复核 {@link #publicCapable()}，因此**跨模块改名会造成编译期错误**而不是静默降级。
 *
 * @author OPENLAB BS
 */
@AllArgsConstructor
@Getter
public enum FileBizTypeEnum {

    // ========== 允许公开的业务资源（仍需服务端绑定确认）==========
    PRODUCT_IMAGE("mall/product", true, "商品图片"),
    CATALOG_IMAGE("mall/catalog", true, "商品目录图片"),
    SHOP_LOGO("mall/shop-logo", true, "店铺 Logo"),
    TENANT_PRODUCT_IMAGE("mall/tianxin", true, "租户商品图片"),
    DELIVERY_LOGO("mall/delivery", true, "配送公司 Logo"),
    BANNER_IMAGE("mall/banner", true, "运营横幅"),
    BRAND_IMAGE("mall/brand", true, "品牌图片"),
    CATEGORY_IMAGE("mall/category", true, "商品分类图片"),
    COMMENT_IMAGE("mall/comment", true, "商品评价图片"),
    DEMO_IMAGE("mall-demo", true, "演示图片"),
    /**
     * 会员头像。
     *
     * 头像会出现在商品评价（{@code product_comment.user_avatar}）、后台客服会话等**公开面**，
     * 而 {@code <img>} 无法携带登录态，因此与 {@code 009-file-public-recompute.sql} 的口径一致，
     * 按公开资源处理；若改为私有，评论区的头像会全部变成 403 破图。
     */
    MEMBER_AVATAR("member/avatar", true, "会员头像"),

    // ========== 私有业务资源 ==========
    AFTER_SALE_IMAGE("mall/aftersale", false, "售后凭证"),
    INQUIRY_IMAGE("mall/inquiry", false, "询价附件"),
    CUSTOMER_SERVICE_IMAGE("mall/customer-service", false, "客服附件"),
    ENTERPRISE_CERT("mall/enterprise", false, "企业证照"),

    // ========== 兜底 ==========
    OTHER("", false, "其他（默认私有）");

    /**
     * 服务端维护的目录约定
     */
    private final String directory;
    /**
     * 是否允许被服务端确认为公开资源
     */
    private final boolean publicCapable;
    /**
     * 业务类型名
     */
    private final String name;

    /**
     * 按客户端传入的 directory 归一化业务类型。
     *
     * 匹配策略：取 directory 的前 1~2 段与枚举的 directory 做前缀匹配；匹配不到返回 {@link #OTHER}。
     * 注意：这里**只做归一化**，不产生任何授权效果；是否公开仍由服务端绑定决定。
     *
     * @param directory 客户端目录
     * @return 业务类型，非空
     */
    public static FileBizTypeEnum getByDirectory(String directory) {
        if (StrUtil.isBlank(directory)) {
            return OTHER;
        }
        String normalized = StrUtil.trim(directory).replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        FileBizTypeEnum matched = null;
        int matchedLength = -1;
        for (FileBizTypeEnum item : values()) {
            if (StrUtil.isEmpty(item.directory)) {
                continue;
            }
            if (!normalized.equals(item.directory) && !normalized.startsWith(item.directory + "/")) {
                continue;
            }
            // 取最长前缀，避免 mall/product 与 mall/product-x 之类的歧义
            if (item.directory.length() > matchedLength) {
                matched = item;
                matchedLength = item.directory.length();
            }
        }
        return matched != null ? matched : OTHER;
    }

    /**
     * 按业务类型名匹配（服务端绑定 / 历史数据补录使用）
     */
    public static FileBizTypeEnum valueOfName(String name) {
        return ArrayUtil.firstMatch(item -> item.name().equalsIgnoreCase(name), FileBizTypeEnum.values());
    }

    /**
     * 该业务类型是否允许公开
     */
    public boolean isPublicCapable() {
        return publicCapable;
    }

}
