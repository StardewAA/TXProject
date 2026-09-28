package cn.iocoder.txgy.module.bidding.service.bidagent.pricing;

// [规范依据]《招投标模块开发规范》§4.2（业务在 service）、§4.5（写接口幂等由 Controller 承担）
// [业务目标] 报价数据（投标人编制报价的产物）结构化落库：PRICING 材料上传 → Excel 解析 → 按表名+行号+列值 JSON 落库
// [为什么] 计价表约 1229 个书签的数据来源只能是投标人报价，无法从招标文件/企业事实抽取；
//          Excel 转 JSON 行由 Java 完成（与材料台账/租户隔离一致），书签名映射由 Node 侧按字典完成

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.bidding.api.dto.bidagent.BidPricingRespDTO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentMaterialDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidPricingRowDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidPricingRowMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报价数据服务实现
 */
@Slf4j
@Service
public class BidPricingServiceImpl implements BidPricingService {

    /** 标准报价 Excel 允许的表名（与 Node 侧 bookmark-dict.json 的 table 字段一致） */
    private static final Set<String> KNOWN_TABLES = Set.of(
            "bill", "measure", "other", "daywork", "service", "fee", "analysis", "material",
            "project_cost", "unit_cost", "cost_breakdown", "shared");
    /** shared 表：第 0 列变量名、第 1 列取值 */
    private static final String SHARED_TABLE = "shared";

    @Resource
    private BidPricingRowMapper pricingRowMapper;

    @Override
    public int parseAndStore(BidAgentMaterialDO material) {
        if (material.getProjectId() == null) {
            throw new IllegalArgumentException("报价数据未绑定项目（projectId 为空），无法落库");
        }
        byte[] bytes = download(material);
        List<BidPricingRowDO> rows;
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            rows = parseWorkbook(workbook, material);
        } catch (IOException ex) {
            throw new IllegalArgumentException("报价 Excel 解析失败：" + ex.getMessage(), ex);
        }
        // 按材料级联：先删后插（重建索引/重复上传 = 覆盖式更新，避免旧行残留）
        pricingRowMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BidPricingRowDO>()
                .eq(BidPricingRowDO::getMaterialId, material.getId()));
        for (BidPricingRowDO row : rows) {
            pricingRowMapper.insert(row);
        }
        log.info("[BidPricingServiceImpl][parseAndStore] 报价数据落库 materialId={} rows={}",
                material.getId(), rows.size());
        return rows.size();
    }

    @Override
    public void deleteByMaterial(Long materialId) {
        pricingRowMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BidPricingRowDO>()
                .eq(BidPricingRowDO::getMaterialId, materialId));
    }

    @Override
    public BidPricingRespDTO getPricing(Long projectId, Long tenantId) {
        if (projectId == null || tenantId == null) {
            return empty();
        }
        return TenantUtils.execute(tenantId, () -> {
            List<BidPricingRowDO> rows = pricingRowMapper.selectListByProject(projectId);
            if (CollUtil.isEmpty(rows)) {
                return empty();
            }
            BidPricingRespDTO resp = new BidPricingRespDTO();
            Map<String, String> shared = new LinkedHashMap<>();
            Map<String, List<Map<String, String>>> tables = new LinkedHashMap<>();
            for (BidPricingRowDO row : rows) {
                Map<String, String> values = StrUtil.isBlank(row.getDataJson())
                        ? Map.of() : JSONUtil.toBean(row.getDataJson(), Map.class);
                if (SHARED_TABLE.equals(row.getTableName())) {
                    String key = row.getDataJson() == null ? null : values.keySet().stream().findFirst().orElse(null);
                    if (key != null) {
                        shared.put(key, values.get(key));
                    }
                    continue;
                }
                tables.computeIfAbsent(row.getTableName(), k -> new ArrayList<>()).add(values);
            }
            resp.setShared(shared);
            resp.setTables(tables);
            return resp;
        });
    }

    private static BidPricingRespDTO empty() {
        BidPricingRespDTO resp = new BidPricingRespDTO();
        resp.setShared(Map.of());
        resp.setTables(Map.of());
        return resp;
    }

    /** 下载材料文件（复用索引链的下载机制：fileUrl → bytes；URL 不可达时抛出专员可见原因） */
    private byte[] download(BidAgentMaterialDO material) {
        if (StrUtil.isBlank(material.getFileUrl())) {
            throw new IllegalArgumentException("材料缺少文件地址（fileUrl），无法解析");
        }
        try {
            return HttpUtil.downloadBytes(material.getFileUrl());
        } catch (Exception ex) {
            throw new IllegalArgumentException("报价 Excel 下载失败：" + StrUtil.maxLength(ex.getMessage(), 200), ex);
        }
    }

    /**
     * 解析工作簿：每个已知表 → 首非空行为表头、其后非空行为数据行；
     * shared 表按「变量名/取值」两列处理。未知表跳过并在日志告警。
     */
    private List<BidPricingRowDO> parseWorkbook(Workbook workbook, BidAgentMaterialDO material) {
        DataFormatter formatter = new DataFormatter();
        List<BidPricingRowDO> rows = new ArrayList<>();
        int knownSheets = 0;
        for (int s = 0; s < workbook.getNumberOfSheets(); s += 1) {
            Sheet sheet = workbook.getSheetAt(s);
            String tableName = sheet.getSheetName().trim();
            if (!KNOWN_TABLES.contains(tableName)) {
                log.warn("[BidPricingServiceImpl][parseWorkbook] 跳过未知表名：{}", tableName);
                continue;
            }
            knownSheets += 1;
            List<String> headers = null;
            int rowNo = 0;
            for (Row row : sheet) {
                List<String> cells = readRow(row, formatter);
                if (cells.stream().allMatch(StrUtil::isBlank)) {
                    continue; // 空行跳过
                }
                if (headers == null) {
                    headers = cells; // 首非空行 = 表头
                    continue;
                }
                if (SHARED_TABLE.equals(tableName)) {
                    // shared：每行两列（变量名 / 取值）
                    String key = cells.size() > 0 ? cells.get(0).trim() : "";
                    String value = cells.size() > 1 ? cells.get(1).trim() : "";
                    if (StrUtil.isBlank(key)) {
                        continue;
                    }
                    rowNo += 1;
                    rows.add(buildRow(material, tableName, rowNo, Map.of(key, value)));
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c += 1) {
                    String header = headers.get(c);
                    if (StrUtil.isBlank(header)) {
                        continue;
                    }
                    values.put(header.trim(), c < cells.size() ? cells.get(c).trim() : "");
                }
                if (values.values().stream().allMatch(StrUtil::isBlank)) {
                    continue;
                }
                rowNo += 1;
                rows.add(buildRow(material, tableName, rowNo, values));
            }
        }
        if (knownSheets == 0) {
            throw new IllegalArgumentException("未找到任何已知计价表（表名应为 bill/measure/other/daywork/service/fee/"
                    + "analysis/material/project_cost/unit_cost/cost_breakdown/shared）");
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Excel 中没有数据行（表头下一行开始填数据）");
        }
        return rows;
    }

    private BidPricingRowDO buildRow(BidAgentMaterialDO material, String tableName, int rowNo, Map<String, String> values) {
        BidPricingRowDO row = new BidPricingRowDO();
        row.setMaterialId(material.getId());
        row.setProjectId(material.getProjectId());
        row.setTableName(tableName);
        row.setRowNo(rowNo);
        row.setDataJson(JSONUtil.toJsonStr(values));
        return row;
    }

    /** 读一行全部单元格的展示文本（数字按显示值、公式取公式结果），空单元格为空串 */
    private List<String> readRow(Row row, DataFormatter formatter) {
        List<String> cells = new ArrayList<>();
        short last = row.getLastCellNum();
        if (last < 0) {
            return cells;
        }
        for (int c = 0; c < last; c += 1) {
            Cell cell = row.getCell(c);
            cells.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
        }
        // 去掉行尾连续空串
        while (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) {
            cells.remove(cells.size() - 1);
        }
        return cells;
    }
}
