package cn.iocoder.txgy.module.product.service.enterprise;

import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.member.api.user.MemberUserApi;
import cn.iocoder.txgy.module.product.controller.app.enterprise.EnterpriseApplyReqVO;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.*;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.*;
import cn.iocoder.txgy.module.product.service.shop.ProductShopService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

@Service
public class EnterpriseMembershipService {
    @Resource private EnterpriseApplicationMapper mapper;
    @Resource private ProductEnterpriseMapper enterpriseMapper;
    @Resource private MemberUserApi memberApi;
    @Resource private ProductShopService shopService;

    public static ServiceException invalid(String message) { return new ServiceException(400, message); }
    public static Long memberId() {
        var user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || !Objects.equals(user.getUserType(), UserTypeEnum.MEMBER.getValue()))
            throw new ServiceException(403, "请使用商城会员账号");
        return user.getId();
    }
    public EnterpriseApplicationDO mine(Long memberId) {
        return mapper.selectOne(new LambdaQueryWrapperX<EnterpriseApplicationDO>()
                .eq(EnterpriseApplicationDO::getMemberId, memberId));
    }
    private EnterpriseApplicationDO locked(Long id) {
        var row = mapper.selectOne(new LambdaQueryWrapperX<EnterpriseApplicationDO>()
                .eq(EnterpriseApplicationDO::getId, id).last("FOR UPDATE"));
        if (row == null) throw invalid("申请不存在");
        return row;
    }
    public EnterpriseApplicationDO requireBuyer(Long memberId) {
        var row = mine(memberId);
        if (row == null || !Integer.valueOf(1).equals(row.getStatus()) || row.getEnterpriseId() == null)
            throw new ServiceException(403, "请先完善企业资料并等待后台审核通过");
        var company = enterpriseMapper.selectById(row.getEnterpriseId());
        if (company == null || !Integer.valueOf(1).equals(company.getAuditStatus()))
            throw new ServiceException(403, "企业认证已失效，请联系平台");
        return row;
    }
    public List<ProductShopDO> salesShops(Long memberId) {
        var row = requireBuyer(memberId);
        if (!"supplier".equals(row.getIdentity()) || row.getShopId() == null) return List.of();
        return shopService.getShopList().stream()
                .filter(s -> Objects.equals(s.getId(), row.getShopId())
                        && Objects.equals(s.getEnterpriseId(), row.getEnterpriseId())
                        && Integer.valueOf(0).equals(s.getStatus())).toList();
    }
    public void requireShop(Long memberId, Long shopId) {
        if (shopId == null || salesShops(memberId).stream().noneMatch(s -> s.getId().equals(shopId)))
            throw new ServiceException(403, "仅审核通过并关联店铺的供应商可管理该店铺");
    }
    @Transactional(rollbackFor=Exception.class)
    public void bindShop(Long applicationId, Long shopId) {
        var row = locked(applicationId);
        requireBuyer(row.getMemberId());
        if (!"supplier".equals(row.getIdentity())) throw invalid("采购商不能分配销售店铺");
        var shop = shopService.getShop(shopId);
        if (shop == null || !Objects.equals(shop.getEnterpriseId(), row.getEnterpriseId())
                || !Integer.valueOf(0).equals(shop.getStatus()))
            throw invalid("仅可关联该企业下的启用店铺");
        row.setShopId(shopId);
        mapper.updateById(row);
    }
    @Transactional(rollbackFor=Exception.class)
    public Long apply(Long memberId, EnterpriseApplyReqVO req) {
        var user = memberApi.getUser(memberId).getCheckedData();
        if (user == null || !Set.of("buyer", "supplier").contains(
                Objects.toString(user.getMemberIdentity(), "")))
            throw invalid("当前账号尚未申请企业身份，请联系平台办理，原有购物功能不受影响");
        validateLicense(req.getLicenseData());
        var current = mine(memberId);
        if (current != null) {
            current = locked(current.getId());
            if (!Integer.valueOf(2).equals(current.getStatus()))
                throw invalid("仅驳回申请可修改重提");
        }
        var row = BeanUtils.toBean(req, EnterpriseApplicationDO.class);
        row.setMemberId(memberId);
        row.setIdentity(user.getMemberIdentity());
        row.setStatus(0);
        row.setAuditRemark("");
        if (current == null) mapper.insert(row);
        else { row.setId(current.getId()); mapper.updateById(row); }
        return row.getId();
    }
    private void validateLicense(String data) {
        if (data == null || data.length() > 1500000 ||
                !(data.startsWith("data:image/png;base64,") || data.startsWith("data:image/jpeg;base64,")))
            throw invalid("营业执照仅支持1MB以内的PNG或JPEG图片");
        try {
            byte[] bytes = Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1));
            if (bytes.length > 1048576) throw invalid("营业执照不能超过1MB");
            try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw invalid("营业执照图片无效");
                var reader = readers.next();
                try {
                    reader.setInput(stream);
                    if ((long)reader.getWidth(0) * reader.getHeight(0) > 25000000L)
                        throw invalid("营业执照图片像素过大");
                } finally { reader.dispose(); }
            }
        } catch (ServiceException e) { throw e; }
        catch (Exception e) { throw invalid("营业执照图片无法读取"); }
    }
    public PageResult<EnterpriseApplicationDO> page(PageParam page, Integer status) {
        var result = mapper.selectPage(page, new LambdaQueryWrapperX<EnterpriseApplicationDO>()
                .eqIfPresent(EnterpriseApplicationDO::getStatus, status).orderByDesc(EnterpriseApplicationDO::getId));
        result.getList().forEach(r -> r.setLicenseData(null));
        return result;
    }
    public EnterpriseApplicationDO get(Long id) { return mapper.selectById(id); }

    @Transactional(rollbackFor=Exception.class)
    public void audit(Long id, Integer status, String remark, Long existingEnterpriseId, Long auditor) {
        if (!Set.of(1, 2).contains(status)) throw invalid("审核结果不正确");
        if (remark == null || remark.isBlank() || remark.length() > 500) throw invalid("请填写500字以内审核意见");
        var row = locked(id);
        if (!Integer.valueOf(0).equals(row.getStatus())) throw invalid("申请已经审核，请刷新");
        if (status == 1) {
            ProductEnterpriseDO company;
            if (existingEnterpriseId != null) {
                company = enterpriseMapper.selectById(existingEnterpriseId);
                if (company == null || !row.getCreditCode().equals(company.getCreditCode())
                        || !Integer.valueOf(1).equals(company.getAuditStatus()))
                    throw invalid("仅可关联信用代码一致且审核通过的企业");
            } else {
                if (enterpriseMapper.selectOne(new LambdaQueryWrapperX<ProductEnterpriseDO>()
                        .eq(ProductEnterpriseDO::getCreditCode, row.getCreditCode()).last("FOR UPDATE")) != null)
                    throw invalid("企业已存在，请核实申请人身份后填写已有企业编号关联，不会自动认领企业");
                company = new ProductEnterpriseDO();
                company.setName(row.getName()); company.setCreditCode(row.getCreditCode());
                company.setContactName(row.getContactName()); company.setContactMobile(row.getContactMobile());
                company.setAddress(row.getAddress()); company.setIntroduction(row.getIntroduction());
                // 私有资质保留在申请记录中，不能复制进公开店铺资料。
                company.setAuditStatus(1); company.setAuditRemark(remark);
                enterpriseMapper.insert(company);
            }
            row.setEnterpriseId(company.getId());
        }
        row.setStatus(status); row.setAuditRemark(remark); row.setAuditorId(auditor);
        row.setAuditTime(LocalDateTime.now()); mapper.updateById(row);
    }
}
