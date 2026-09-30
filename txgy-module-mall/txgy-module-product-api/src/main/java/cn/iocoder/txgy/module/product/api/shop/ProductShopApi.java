package cn.iocoder.txgy.module.product.api.shop;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.product.api.shop.dto.ProductShopRespDTO;
import cn.iocoder.txgy.module.product.enums.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 企业店铺 API。
 *
 * @author DAMU
 */
@FeignClient(name = ApiConstants.NAME)
public interface ProductShopApi {

    String PREFIX = ApiConstants.PREFIX + "/shop";

    /**
     * 获得店铺信息。
     *
     * @param id 店铺编号
     * @return 店铺信息，不存在时返回 null
     */
    @GetMapping(PREFIX + "/get")
    CommonResult<ProductShopRespDTO> getShop(@RequestParam("id") Long id);

    /**
     * 根据后台负责人获得企业店铺。
     *
     * @param managerUserId 后台系统用户编号
     * @return 店铺信息，未绑定时返回 null
     */
    @GetMapping(PREFIX + "/get-by-manager-user-id")
    CommonResult<ProductShopRespDTO> getShopByManagerUserId(@RequestParam("managerUserId") Long managerUserId);

    /**
     * 获得该店铺**全部合法后台处置人**的后台用户编号（负责人 + 被授权到该店铺的账号/客服）。
     *
     * 用于把「谁有权读取本店铺的业务私有文件（如售后凭证）」落成显式的 participant，
     * 而不是依赖「同租户任意后台可读」这种粗放放行。
     */
    @GetMapping(PREFIX + "/authorized-admin-user-ids")
    CommonResult<List<Long>> getAuthorizedAdminUserIds(@RequestParam("shopId") Long shopId);

}

