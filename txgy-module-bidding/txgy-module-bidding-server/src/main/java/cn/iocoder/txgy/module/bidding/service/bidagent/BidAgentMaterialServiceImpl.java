package cn.iocoder.txgy.module.bidding.service.bidagent;

// [规范依据]《招投标模块开发规范》§4.2（业务在 service）、§4.5（写接口幂等由 Controller 承担）
// [业务目标] 材料登记即入库：台账落库 → 走云端检索链（百炼 embedding + Qdrant 向量库）入库 → 回写入库状态/片段数
// [为什么] 2026-09-16 口径：入库不再绕 Node 底座（底座并不提供 /bid/segment/index），改为在 Java 内直连
//          BidIndexService；"上传了但没入库"仍必须显式落在台账上（indexStatus=2 + 失败原因），禁止静默

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentMaterialVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentMaterialDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidAgentMaterialMapper;
import cn.iocoder.txgy.module.bidding.service.bidagent.index.BidIndexService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_DOC_TYPE_INVALID;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_FILE_TOO_LARGE;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_FILE_TYPE_INVALID;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_MATERIAL_FILE_EMPTY;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_MATERIAL_NOT_EXISTS;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_MATERIAL_SECTION_REQUIRED;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_PRICING_PARSE_FAILED;

@Slf4j
@Service
public class BidAgentMaterialServiceImpl implements BidAgentMaterialService {

    /** 允许的材料角色（TENDER 招标文件 / FACT 企业资料 / TEMPLATE 技术母本 / PRICING 报价数据） */
    private static final Set<String> DOC_TYPES = Set.of("TENDER", "FACT", "TEMPLATE", "PRICING");
    /** 报价数据角色：不走向量入库链，改为 Excel 解析 → bidding_bid_pricing 结构化落库（COPY_PRICING 通道） */
    private static final String DOC_TYPE_PRICING = "PRICING";
    /** 允许上传的文档扩展名（与前台工坊/底座侧白名单保持一致；后端兜底，脚本与第三方调用方同样受约束） */
    private static final Set<String> ALLOWED_EXTS = Set.of("pdf", "doc", "docx", "txt", "md", "xlsx", "pptx");
    /** 单文件大小上限（与底座侧 60MB 上限一致） */
    private static final long MAX_FILE_BYTES = 60L * 1024 * 1024;
    private static final String DOC_TYPE_TENDER = "TENDER";
    private static final int STATUS_INDEXED = 1;
    private static final int STATUS_FAILED = 2;

    @Resource
    private BidAgentMaterialMapper materialMapper;
    @Resource
    private BidIndexService indexService;
    @Resource
    private cn.iocoder.txgy.module.bidding.service.bidagent.pricing.BidPricingService pricingService;

    @Override
    public PageResult<BidAgentMaterialVO.RespVO> page(BidAgentMaterialVO.PageReqVO reqVO) {
        return BeanUtils.toBean(materialMapper.selectPage(reqVO), BidAgentMaterialVO.RespVO.class);
    }

    @Override
    public BidAgentMaterialVO.RespVO get(Long id) {
        BidAgentMaterialDO material = materialMapper.selectById(id);
        if (material == null) {
            throw exception(BID_AGENT_MATERIAL_NOT_EXISTS);
        }
        return BeanUtils.toBean(material, BidAgentMaterialVO.RespVO.class);
    }

    @Override
    public Long register(BidAgentMaterialVO.SaveReqVO reqVO) {
        if (!DOC_TYPES.contains(reqVO.getDocType())) {
            throw exception(BID_AGENT_DOC_TYPE_INVALID, reqVO.getDocType());
        }
        // 报价数据归属项目：与一键生成同口径（空=默认 0，用于无需选项目的一键生成场景）
        if (DOC_TYPE_PRICING.equals(reqVO.getDocType()) && reqVO.getProjectId() == null) {
            reqVO.setProjectId(0L);
        }
        // 文件类型与大小后端兜底：前台已拦截，脚本/第三方调用方同样受约束，避免脏台账
        String ext = StrUtil.subAfter(reqVO.getName(), '.', true);
        if (StrUtil.isNotBlank(ext) && !ALLOWED_EXTS.contains(ext.toLowerCase())) {
            throw exception(BID_AGENT_FILE_TYPE_INVALID, ext);
        }
        if (reqVO.getFileSize() != null && reqVO.getFileSize() > MAX_FILE_BYTES) {
            throw exception(BID_AGENT_FILE_TOO_LARGE, (reqVO.getFileSize() / 1024 / 1024) + " MB");
        }
        // 未选标段的一键生成：bidSectionCode 为空时默认 DEFAULT 标段兜底（不再强制绑定标段）
        if (StrUtil.isBlank(reqVO.getBidSectionCode())) {
            reqVO.setBidSectionCode("DEFAULT");
        }
        // 刻意不加 @Transactional：入库链路含外网下载 → Tika 解析 → 逐块 embedding → 写 Qdrant，
        // 可耗时数十秒；事务只应覆盖 DB 写入。此处三条 DB 语句各自单条提交，
        // 中途失败会以 indexStatus=2 显式落在台账上（可点「重建索引」修复），不会长时间占用连接与行锁。
        BidAgentMaterialDO material = BeanUtils.toBean(reqVO, BidAgentMaterialDO.class);
        material.setIndexStatus(0);
        material.setSegmentCount(0);
        materialMapper.insert(material);
        if (DOC_TYPE_PRICING.equals(material.getDocType())) {
            indexPricingInternal(material);
        } else {
            indexInternal(material);
        }
        materialMapper.updateById(material);
        return material.getId();
    }

    @Override
    public void delete(Long id) {
        BidAgentMaterialDO material = materialMapper.selectById(id);
        if (material == null) {
            throw exception(BID_AGENT_MATERIAL_NOT_EXISTS);
        }
        if (DOC_TYPE_PRICING.equals(material.getDocType())) {
            // 报价数据：只清结构化行，不碰向量库
            pricingService.deleteByMaterial(material.getId());
        } else {
            // 先删向量与切片台账（按材料编号精确级联，避免同名文件互相误删），再删材料
            indexService.deleteByMaterialId(TenantContextHolder.getTenantId(), material.getId());
        }
        materialMapper.deleteById(id);
    }

    @Override
    public void reindex(Long id) {
        BidAgentMaterialDO material = materialMapper.selectById(id);
        if (material == null) {
            throw exception(BID_AGENT_MATERIAL_NOT_EXISTS);
        }
        // 同 register：远程入库链移出事务，避免重建索引期间长时间持有 DB 连接
        if (DOC_TYPE_PRICING.equals(material.getDocType())) {
            indexPricingInternal(material);
        } else {
            indexInternal(material);
        }
        materialMapper.updateById(material);
    }

    @Override
    public Map<String, Object> pageSegment(Long materialId, Integer pageNo, Integer pageSize) {
        BidAgentMaterialDO material = materialMapper.selectById(materialId);
        if (material == null) {
            throw exception(BID_AGENT_MATERIAL_NOT_EXISTS);
        }
        // 切片台账与向量库写入同源（BidIndexService）；按材料编号精确分页，同名文件不混页
        return indexService.pageSegmentsByMaterialId(TenantContextHolder.getTenantId(),
                materialId, pageNo, pageSize);
    }

    /**
     * 走云端检索链入库并回写台账状态。
     *
     * <p>业务目标：入库结果对专员可见——成功记片段数，失败记原因；云端未配置/调用失败时也要落到台账上。
     * <p>为什么吞异常：材料已登记，专员应能"看到失败 → 修好配置 → 点重建"，而不是重填一遍表单。
     */
    private void indexInternal(BidAgentMaterialDO material) {
        if (StrUtil.isBlank(material.getFileUrl())) {
            material.setIndexStatus(STATUS_FAILED);
            material.setSegmentCount(0);
            material.setIndexMessage(exception(BID_AGENT_MATERIAL_FILE_EMPTY).getMessage());
            return;
        }
        try {
            int count = indexService.indexUrl(TenantContextHolder.getTenantId(), null, material.getId(),
                    material.getDocType(), material.getName(), material.getBidSectionCode(), material.getFileUrl(), Map.of());
            if (count <= 0) {
                material.setIndexStatus(STATUS_FAILED);
                material.setSegmentCount(0);
                material.setIndexMessage("未入库：检索未启用（bidding.bid-agent.ai-enabled=false）或向量库不可用，"
                        + "请配置百炼 key 后点「重建索引」");
                return;
            }
            material.setIndexStatus(STATUS_INDEXED);
            material.setSegmentCount(count);
            material.setIndexMessage("入库成功，片段数 " + count);
        } catch (Exception e) {
            log.warn("[BidAgentMaterialServiceImpl][indexInternal] 入库失败 materialId={} msg={}",
                    material.getId(), e.getMessage());
            material.setIndexStatus(STATUS_FAILED);
            material.setSegmentCount(0);
            material.setIndexMessage(StrUtil.maxLength("入库失败：" + e.getMessage(), 480));
        }
    }

    /**
     * 报价数据（PRICING）入库：不走向量检索链，改为 Excel 解析 → bidding_bid_pricing 结构化落库。
     * 失败原因同样显式落在台账上（indexStatus=2），专员可修正 Excel 后点「重建索引」。
     */
    private void indexPricingInternal(BidAgentMaterialDO material) {
        try {
            int rows = pricingService.parseAndStore(material);
            material.setIndexStatus(STATUS_INDEXED);
            material.setSegmentCount(rows);
            material.setIndexMessage("报价数据解析入库成功，共 " + rows + " 行");
        } catch (Exception e) {
            log.warn("[BidAgentMaterialServiceImpl][indexPricingInternal] 报价解析失败 materialId={} msg={}",
                    material.getId(), e.getMessage());
            material.setIndexStatus(STATUS_FAILED);
            material.setSegmentCount(0);
            material.setIndexMessage(StrUtil.maxLength(exception(BID_AGENT_PRICING_PARSE_FAILED,
                    StrUtil.maxLength(e.getMessage(), 300)).getMessage(), 480));
        }
    }
}
