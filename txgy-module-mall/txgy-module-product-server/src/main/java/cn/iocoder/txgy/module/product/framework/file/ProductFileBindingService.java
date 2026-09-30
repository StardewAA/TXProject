package cn.iocoder.txgy.module.product.framework.file;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.infra.api.file.FileApi;
import cn.iocoder.txgy.module.infra.api.file.dto.FileBindReqDTO;
import cn.iocoder.txgy.module.infra.enums.file.FileAccessTypeEnum;
import cn.iocoder.txgy.module.infra.enums.file.FileBizTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 商品域「文件归属绑定」统一入口。
 *
 * 背景：文件上传后一律先落为「待绑定（私有）」状态，只有上传者本人在短窗口内可读，
 * 超期后连上传者本人也无法读取，且可能被孤儿文件清理任务回收。
 * 因此**业务单据落库成功**后必须调用 {@link FileApi#bindFile} 明确文件归属：
 *
 * <ul>
 *     <li>商品图片 / 店铺 Logo 等前台展示资源 → 绑定为公开资源，匿名可读；</li>
 *     <li>私有资源（售后凭证 / 询价参考图等）由 trade、member 模块各自绑定。</li>
 * </ul>
 *
 * 安全约定：是否公开由 infra 侧按 {@link FileBizTypeEnum#isPublicCapable()} 复核，
 * 本类只声明业务意图；绑定失败会抛错并让业务事务回滚，避免"单据已保存但图片谁也读不到"。
 *
 * @author OPENLAB BS
 */
@Slf4j
@Component
public class ProductFileBindingService {

    @Resource
    private FileApi fileApi;

    /**
     * 把商品图片（主图 / 轮播图 / SKU 图）确认为商品所属的公开资源。
     *
     * @param spuId 商品编号，为空时不做任何处理
     * @param urls  图片地址，允许包含空值与重复项
     */
    public void bindProductImages(Long spuId, Collection<String> urls) {
        bind(spuId == null ? null : String.valueOf(spuId), FileBizTypeEnum.PRODUCT_IMAGE, urls);
    }

    /**
     * 把店铺 Logo 确认为店铺所属的公开资源。
     */
    public void bindShopLogo(Long shopId, String logoUrl) {
        bind(shopId == null ? null : String.valueOf(shopId), FileBizTypeEnum.SHOP_LOGO,
                StrUtil.isBlank(logoUrl) ? List.of() : List.of(logoUrl));
    }

    /**
     * 把商品评价图片确认为评价所属的公开资源。
     *
     * 评价图片会在商品详情页对匿名访客展示（{@code product_comment.pic_urls}），
     * 若不确认归属，评价保存后图片会一直读不到。
     */
    public void bindCommentImages(Long commentId, Collection<String> urls) {
        bind(commentId == null ? null : String.valueOf(commentId), FileBizTypeEnum.COMMENT_IMAGE, urls);
    }

    private void bind(String bizId, FileBizTypeEnum bizType, Collection<String> urls) {
        if (bizId == null || CollUtil.isEmpty(urls)) {
            return;
        }
        List<String> normalized = new ArrayList<>();
        for (String url : urls) {
            if (StrUtil.isNotBlank(url) && !normalized.contains(url.trim())) {
                normalized.add(url.trim());
            }
        }
        if (normalized.isEmpty()) {
            return;
        }
        // 归属声明：infra 侧会用它去比对文件记录里的上传者，**不一致直接拒绝**。
        // 商品图（供应商后台上传）/ 评价图（会员上传）都由当前登录身份上传，
        // 因此这里必须把登录身份作为「声明上传者」传下去，否则会触发 FILE_BIND_OWNER_MISMATCH。
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        FileBindReqDTO reqDTO = new FileBindReqDTO()
                .setUrls(normalized)
                .setBizType(bizType.name())
                .setBizId(bizId)
                .setAccessType(FileAccessTypeEnum.PUBLIC.getAccessType())
                .setTenantId(TenantContextHolder.getRequiredTenantId())
                .setOwnerUserType(loginUser != null ? loginUser.getUserType() : null)
                .setOwnerUserId(loginUser != null ? loginUser.getId() : null);
        // getCheckedData：绑定失败（上传者不一致 / 业务类型不允许公开 / 跨租户 / 重绑他人单据）
        // 必须让业务事务回滚，避免"单据已保存但图片谁也读不到"。
        fileApi.bindFile(reqDTO).getCheckedData();
    }

}
