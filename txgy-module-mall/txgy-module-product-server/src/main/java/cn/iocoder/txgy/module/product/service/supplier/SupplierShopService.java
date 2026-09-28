package cn.iocoder.txgy.module.product.service.supplier;

import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import cn.iocoder.txgy.module.product.framework.file.ProductFileBindingService;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.*;
import cn.iocoder.txgy.module.product.dal.mysql.shop.*;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.product.api.supplier.ProductSupplierApi.ReturnAddress;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import static cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.invalid;

@Service
public class SupplierShopService {
    @Resource private EnterpriseMembershipService membership;
    @Resource private ProductShopMapper shops;
    @Resource private ShopReturnAddressMapper addresses;
    @Resource private ProductFileBindingService productFileBinding;
    public record Address(@NotBlank @Size(max=32) String receiverName,
            @NotBlank @Pattern(regexp="[0-9+() -]{5,32}") String receiverMobile,
            @NotBlank @Size(max=512) String address) {}
    public record Save(@NotNull Long shopId, @NotBlank @Size(max=64) String name,
            @Size(max=512) String logoUrl, @Size(max=32) String contactName,
            @Size(max=32) String contactMobile, @Size(max=1024) String introduction,
            @NotNull @Valid Address returnAddress) {}
    public record Detail(Long shopId, String name, String logoUrl, String contactName,
                         String contactMobile, String introduction, ReturnAddress returnAddress) {}
    public Detail get(Long member, Long shop) {
        membership.requireShop(member, shop);
        var s=shops.selectById(shop);
        if(s==null) throw invalid("店铺不存在");
        return new Detail(s.getId(),s.getName(),s.getLogoUrl(),s.getContactName(),s.getContactMobile(),s.getIntroduction(),address(shop));
    }
    private ReturnAddress address(Long shop) {
        var a=addresses.selectById(shop);
        return a==null?null:new ReturnAddress(a.getReceiverName(),a.getReceiverMobile(),a.getAddress());
    }
    public ReturnAddress requiredAddress(Long member,Long shop) {
        membership.requireShop(member,shop);
        var a=address(shop);
        if(a==null||a.receiverName().isBlank()||a.receiverMobile().isBlank()||a.address().isBlank())
            throw invalid("请先在店铺资料中维护完整退货地址");
        return a;
    }
    @Transactional(rollbackFor=Exception.class)
    public void save(Long member,Save req) {
        membership.requireShop(member,req.shopId());
        var shop=shops.selectOne(new LambdaQueryWrapperX<ProductShopDO>().eq(ProductShopDO::getId,req.shopId()).last("FOR UPDATE"));
        if(shop==null) throw invalid("店铺不存在");
        String logo=req.logoUrl()==null?"":req.logoUrl().trim();
        if(!logo.isEmpty()) {
            try {var uri=URI.create(logo);if(!("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException();}
            catch(IllegalArgumentException e){throw invalid("Logo仅支持有效HTTP(S)图片地址");}
        }
        // 白名单更新，不允许修改企业、后台负责人或启用状态。
        shops.updateById(new ProductShopDO().setId(req.shopId()).setName(req.name().trim()).setLogoUrl(logo)
                .setContactName(req.contactName()==null?"":req.contactName().trim())
                .setContactMobile(req.contactMobile()==null?"":req.contactMobile().trim())
                .setIntroduction(req.introduction()==null?"":req.introduction().trim()));
        var a=new ShopReturnAddressDO().setShopId(req.shopId()).setReceiverName(req.returnAddress().receiverName().trim())
                .setReceiverMobile(req.returnAddress().receiverMobile().trim()).setAddress(req.returnAddress().address().trim());
        if(addresses.selectById(req.shopId())==null)addresses.insert(a);else addresses.updateById(a);
        // 店铺 Logo 属于前台公开资源（店铺列表、询价机会列表都会展示给其他租户），
        // 店铺资料落库后由服务端确认归属，否则匿名访客读到的是一张 403 的破图。
        productFileBinding.bindShopLogo(req.shopId(), logo);
    }
}
