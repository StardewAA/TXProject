package cn.iocoder.txgy.module.product.service.supplier;

import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.product.controller.app.supplier.SupplierProductSaveReqVO;
import cn.iocoder.txgy.module.product.controller.admin.spu.vo.*;
import cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.txgy.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.txgy.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.txgy.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.txgy.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import cn.iocoder.txgy.module.product.service.spu.ProductSpuService;
import cn.iocoder.txgy.module.product.service.category.ProductCategoryService;
import cn.iocoder.txgy.module.product.service.brand.ProductBrandService;
import cn.iocoder.txgy.module.trade.api.delivery.DeliveryTemplateApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import java.net.URI;
import java.util.*;
import static cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.invalid;

@Service
public class SupplierProductService {
    @Resource private EnterpriseMembershipService membership;
    @Resource private ProductSpuMapper spuMapper;
    @Resource private ProductSkuMapper skuMapper;
    @Resource private ProductSpuService spuService;
    @Resource private ProductCategoryService categoryService;
    @Resource private ProductBrandService brandService;
    @Resource private cn.iocoder.txgy.module.product.service.review.ProductSpuReviewService reviewService;
    @Resource private DeliveryTemplateApi deliveryApi;
    @Resource private cn.iocoder.txgy.module.product.service.property.ProductPropertyService propertyService;
    @Resource private cn.iocoder.txgy.module.product.service.property.ProductPropertyValueService valueService;

    public record Summary(Long id, String name, String picUrl, Integer status, Integer price, Integer stock,
                          Integer reviewStatus, Long contentVersion, Long approvedVersion, String submissionId, String reviewRemark) {}
    public record SkuView(Long id, Integer price, Integer marketPrice, Integer stock,
                          String barCode, Double weight, Double volume, String specification, boolean wmsManaged, String picUrl) {}
    public record Detail(Long id, Long shopId, String name, String keyword, String introduction,
                         Long categoryId, Long brandId, String picUrl, Long deliveryTemplateId,
                         Integer status, List<SkuView> skus, List<String> sliderPicUrls,
                         Integer reviewStatus, Long contentVersion, Long approvedVersion, String submissionId, String reviewRemark,
                         String description, Boolean specType) {}
    public record Option(Long id, String name) {}

    public Map<String, Object> options(Long member, Long shop) {
        membership.requireShop(member, shop);
        return Map.of("brands", brandService.getBrandListByStatus(0).stream()
                        .map(b -> new Option(b.getId(), b.getName())).toList(),
                "templates", deliveryApi.list().getCheckedData(), "properties", propertyOptions());
    }
    public record PropertyOption(Long id,String name,List<Option> values) {}
    private List<PropertyOption> propertyOptions(){
        var properties=propertyService.getPropertyList().stream().filter(p->p.getId()>0).toList();
        if(properties.isEmpty())return List.of();
        var values=valueService.getPropertyValueListByPropertyId(properties.stream().map(p->p.getId()).toList());
        return properties.stream().map(p->new PropertyOption(p.getId(),p.getName(),values.stream()
                .filter(v->Objects.equals(v.getPropertyId(),p.getId())&&v.getId()>0).map(v->new Option(v.getId(),v.getName())).toList())).toList();
    }
    private List<ProductSkuSaveReqVO> createSkus(SupplierProductSaveReqVO req){
        boolean multi=Boolean.TRUE.equals(req.getSpecType());
        if(req.getSkus().isEmpty()||req.getSkus().size()>100||(!multi&&req.getSkus().size()!=1))throw invalid("单规格限1个SKU，多规格最多100个SKU");
        var result=new ArrayList<ProductSkuSaveReqVO>();var combinations=new HashSet<String>();Set<Long> dimensions=null;
        var options=multi?propertyOptions():List.<PropertyOption>of();
        for(var input:req.getSkus()){
            if(input.getId()!=null)throw invalid("新建商品不能指定既有SKU编号");
            var sku=new ProductSkuSaveReqVO().setName(req.getName()).setPrice(input.getPrice())
                    .setMarketPrice(input.getMarketPrice()).setCostPrice(0).setPicUrl(skuImage(input.getPicUrl(),req.getPicUrl()))
                    .setStock(Objects.requireNonNullElse(input.getInitialStock(),0)).setBarCode(input.getBarCode())
                    .setWeight(input.getWeight()).setVolume(input.getVolume()).setFirstBrokeragePrice(0).setSecondBrokeragePrice(0);
            if(multi){
                if(input.getProperties()==null||input.getProperties().isEmpty()||input.getProperties().size()>3)throw invalid("每个SKU须选择1至3个规格属性");
                var ids=new TreeSet<Long>();var resolved=new ArrayList<ProductSkuSaveReqVO.Property>();var keys=new TreeMap<Long,Long>();
                for(var selection:input.getProperties()){
                    var property=options.stream().filter(p->p.id().equals(selection.getPropertyId())).findFirst().orElseThrow(()->invalid("规格属性不存在，请刷新"));
                    var value=property.values().stream().filter(v->v.id().equals(selection.getValueId())).findFirst().orElseThrow(()->invalid("规格值不属于所选属性"));
                    if(!ids.add(property.id()))throw invalid("同一SKU不能重复选择属性");
                    resolved.add(new ProductSkuSaveReqVO.Property(property.id(),property.name(),value.id(),value.name()));keys.put(property.id(),value.id());
                }
                if(dimensions==null)dimensions=ids;else if(!dimensions.equals(ids))throw invalid("所有SKU必须使用相同规格属性");
                if(!combinations.add(keys.toString()))throw invalid("规格组合重复，请合并或修改");
                sku.setProperties(resolved);
            }else if(input.getProperties()!=null&&!input.getProperties().isEmpty())throw invalid("单规格商品不能传入多规格属性");
            result.add(sku);
        }
        return result;
    }
    public PageResult<Summary> page(Long member, Long shop, PageParam page, String name, Integer status, Integer reviewStatus) {
        membership.requireShop(member, shop);
        var q = new LambdaQueryWrapperX<ProductSpuDO>().eq(ProductSpuDO::getShopId, shop)
                .likeIfPresent(ProductSpuDO::getName, name).in(ProductSpuDO::getStatus, List.of(0, 1))
                .eqIfPresent(ProductSpuDO::getStatus, status).orderByDesc(ProductSpuDO::getId);
        ProductSpuMapper.appendReviewQuery(reviewStatus, q);
        var result = spuMapper.selectPage(page, q);
        return new PageResult<>(result.getList().stream().map(p -> new Summary(p.getId(), p.getName(),
                p.getPicUrl(), p.getStatus(), p.getPrice(), p.getStock(), p.getReviewStatus(),
                cn.iocoder.txgy.module.product.service.review.ProductSpuReviewService.version(p), p.getApprovedVersion(),
                p.getSubmissionId(), p.getReviewRemark())).toList(), result.getTotal());
    }
    private ProductSpuDO owned(Long member, Long shop, Long id, boolean lock) {
        membership.requireShop(member, shop);
        var q = new LambdaQueryWrapperX<ProductSpuDO>().eq(ProductSpuDO::getId, id)
                .eq(ProductSpuDO::getShopId, shop);
        if (lock) q.last("FOR UPDATE");
        var row = spuMapper.selectOne(q);
        if (row == null || !Set.of(0, 1).contains(row.getStatus())) throw invalid("商品不存在或无权管理");
        return row;
    }
    public Detail detail(Long member, Long shop, Long id) {
        var p = owned(member, shop, id, false);
        var skus = skuMapper.selectListBySpuId(id).stream().map(s -> new SkuView(s.getId(), s.getPrice(),
                s.getMarketPrice(), s.getStock(), s.getBarCode(), s.getWeight(), s.getVolume(),
                s.getProperties() == null ? "默认" : String.join(" / ", s.getProperties().stream()
                        .map(v -> v.getPropertyName() + ":" + v.getValueName()).toList()),
                s.getWmsSkuId() != null || s.getWmsWarehouseId() != null,s.getPicUrl())).toList();
        return new Detail(p.getId(), p.getShopId(), p.getName(), p.getKeyword(), p.getIntroduction(),
                p.getCategoryId(), p.getBrandId(), p.getPicUrl(), p.getDeliveryTemplateId(), p.getStatus(), skus,p.getSliderPicUrls(),
                p.getReviewStatus(), cn.iocoder.txgy.module.product.service.review.ProductSpuReviewService.version(p),
                p.getApprovedVersion(), p.getSubmissionId(), p.getReviewRemark(), p.getDescription(), p.getSpecType());
    }
    private void validateMetadata(SupplierProductSaveReqVO req) {
        categoryService.validateCategory(req.getCategoryId());
        if (categoryService.getCategoryLevel(req.getCategoryId()) < ProductCategoryDO.CATEGORY_LEVEL)
            throw invalid("请选择末级商品分类");
        brandService.validateProductBrand(req.getBrandId());
        validateImageUrl(req.getPicUrl());
        if(req.getSliderPicUrls()!=null){
            var images=req.getSliderPicUrls();
            if(images.isEmpty()||images.size()>10||!req.getPicUrl().equals(images.getFirst())||new HashSet<>(images).size()!=images.size())
                throw invalid("商品图片须为1至10张不重复图片，第一张为主图");
            images.forEach(SupplierProductService::validateImageUrl);
        }
        var template = deliveryApi.list().getCheckedData().stream()
                .filter(t -> t.id().equals(req.getDeliveryTemplateId())).findFirst()
                .orElseThrow(() -> invalid("运费模板不存在，请刷新选择"));
        for (var s : req.getSkus()) {
            if(s.getPicUrl()!=null&&!s.getPicUrl().isBlank())validateImageUrl(s.getPicUrl());
            if (template.chargeMode() == 2 && s.getWeight() <= 0) throw invalid("按重量计费的模板须填写正数重量");
            if (template.chargeMode() == 3 && s.getVolume() <= 0) throw invalid("按体积计费的模板须填写正数体积");
        }
    }
    public static void validateImageUrl(String url) {
        try {
            var uri = URI.create(url);
            if (!(Set.of("http", "https").contains(Objects.toString(uri.getScheme(), ""))
                    && uri.getHost() != null && uri.getUserInfo() == null)) throw invalid("商品图片须使用有效的HTTP(S)图片地址");
        } catch (IllegalArgumentException e) { throw invalid("商品图片地址格式不正确"); }
    }
    private String skuImage(String image,String main){return image==null||image.isBlank()?main:image;}
    @Transactional(rollbackFor = Exception.class)
    public Long save(Long member, SupplierProductSaveReqVO req) {
        membership.requireShop(member, req.getShopId());
        // 编辑时先锁定并校验归属，不能在后续保存中更换店铺。
        var existing = req.getId() == null ? null : owned(member, req.getShopId(), req.getId(), true);
        String oldSnapshot = existing == null ? null : reviewService.beforeEdit(existing, req.getExpectedVersion());
        validateMetadata(req);
        if (existing == null) {
            if (req.getDescriptionText() == null || req.getDescriptionText().isBlank()) throw invalid("请填写商品详情");
            var newSkus=createSkus(req);
            var create = new ProductSpuSaveReqVO().setShopId(req.getShopId()).setName(req.getName())
                    .setKeyword(req.getKeyword()).setIntroduction(req.getIntroduction())
                    .setDescription(HtmlUtils.htmlEscape(req.getDescriptionText()).replace("\n", "<br/>"))
                    .setCategoryId(req.getCategoryId()).setBrandId(req.getBrandId()).setPicUrl(req.getPicUrl())
                    .setSliderPicUrls(req.getSliderPicUrls()==null?List.of(req.getPicUrl()):req.getSliderPicUrls()).setSort(0).setSpecType(Boolean.TRUE.equals(req.getSpecType()))
                    .setDeliveryTypes(List.of(1)).setDeliveryTemplateId(req.getDeliveryTemplateId())
                    .setGiveIntegral(0).setSubCommissionType(false).setVirtualSalesCount(0).setSkus(newSkus);
            var id = spuService.createSpu(create);
            // 同事务先入仓，用户检查后单独上架。
            spuMapper.updateById(new ProductSpuDO().setId(id).setStatus(0));
            return id;
        }
        var actual = skuMapper.selectListBySpuId(existing.getId());
        var ids = new HashSet<Long>();
        for (var input : req.getSkus()) {
            if (input.getId() == null || !ids.add(input.getId()) || actual.stream().noneMatch(s -> s.getId().equals(input.getId())))
                throw invalid("SKU不属于当前商品或重复，请刷新重试");
            if (input.getInitialStock() != null) throw invalid("编辑商品不得覆盖实时库存");
            if(input.getProperties()!=null)throw invalid("已有商品不能修改SKU规格结构");
        }
        if (ids.size() != actual.size()) throw invalid("不得删除规格，请保留全部SKU");
        // 仅更新经营字段，避免覆盖并发扣减库存、WMS映射、销量和佣金。
        for (var input : req.getSkus()) {
            var skuPatch=new ProductSkuDO().setId(input.getId()).setPrice(input.getPrice())
                    .setMarketPrice(input.getMarketPrice()).setWeight(input.getWeight()).setVolume(input.getVolume());
            if(input.getPicUrl()!=null)skuPatch.setPicUrl(skuImage(input.getPicUrl(),req.getPicUrl()));
            skuMapper.updateById(skuPatch);
        }
        var patch = new ProductSpuDO().setId(existing.getId()).setName(req.getName()).setKeyword(req.getKeyword())
                .setIntroduction(req.getIntroduction()).setCategoryId(req.getCategoryId()).setBrandId(req.getBrandId())
                .setPicUrl(req.getPicUrl()).setDeliveryTemplateId(req.getDeliveryTemplateId())
                .setPrice(req.getSkus().stream().mapToInt(SupplierProductSaveReqVO.Sku::getPrice).min().orElseThrow())
                .setMarketPrice(req.getSkus().stream().mapToInt(SupplierProductSaveReqVO.Sku::getMarketPrice).min().orElseThrow());
        if (req.getDescriptionText() != null && !req.getDescriptionText().isBlank())
            patch.setDescription(HtmlUtils.htmlEscape(req.getDescriptionText()).replace("\n", "<br/>"));
        if(req.getSliderPicUrls()!=null)patch.setSliderPicUrls(req.getSliderPicUrls());
        spuMapper.updateById(patch);
        reviewService.finishEdit(existing, oldSnapshot);
        return existing.getId();
    }
    @Transactional(rollbackFor = Exception.class)
    public void status(Long member, Long shop, Long id, Integer status, Long expectedVersion) {
        if (status == null || !Set.of(0, 1).contains(status)) throw invalid("仅支持上架或下架");
        var p = owned(member, shop, id, true);
        cn.iocoder.txgy.module.product.service.review.ProductSpuReviewService.expect(p, expectedVersion);
        if (status == 1) {
            reviewService.requireApprovedForListing(p);
            categoryService.validateCategory(p.getCategoryId());
            brandService.validateProductBrand(p.getBrandId());
            if (p.getDeliveryTypes() == null || !p.getDeliveryTypes().contains(1)
                    || deliveryApi.list().getCheckedData().stream().noneMatch(t -> t.id().equals(p.getDeliveryTemplateId())))
                throw invalid("请先由后台配置有效快递配送方式及运费模板");
        }
        spuMapper.updateById(new ProductSpuDO().setId(id).setStatus(status));
    }
}
