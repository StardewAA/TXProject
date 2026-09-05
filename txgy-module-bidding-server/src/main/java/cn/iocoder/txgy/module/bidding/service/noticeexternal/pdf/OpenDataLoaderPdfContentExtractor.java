package cn.iocoder.txgy.module.bidding.service.noticeexternal.pdf;
// 规范依据：设计文档 v0.6 §7.3/§15.6（PDF 抽取走本地 OpenDataLoader，文本+表格；扫描件空结果由上层报 PDF_EXTRACT_EMPTY 失败）

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenDataLoader（本地库）PDF 抽取实现。
 * <p>适配说明：OpenDataLoader 的 Java API 坐标（groupId/artifactId）与模块当前引入方式尚未固化，
 * 此处按「反射 + 可配置 FQCN/方法名」绑定：loaderClass 配置为 OpenDataLoader 抽取器全限定类名，
 * 方法签名约定 {@code Object extract(File pdf)}，返回对象须暴露 {@code getText()} 与 {@code getTables()}。
 * 接入真实依赖后仅需通过配置指向对应类（或直接改为编译期引用，行为不变）。
 * <p>空结果处理：本实现只负责把「无可提取内容」如实返回空结果，是否判失败由 PdfExtractService 统一裁决。
 */
@Slf4j
public class OpenDataLoaderPdfContentExtractor implements PdfContentExtractor {

    /** 配置键前缀（yml：bidding.ext-notice.pdf.open-data-loader.*），接入时用 @ConfigurationProperties 绑定即可 */
    public static final String CONF_PREFIX = "bidding.ext-notice.pdf.open-data-loader";

    private final String loaderClass;
    private final String loaderMethod;

    private Object loader;
    private Method extractMethod;

    public OpenDataLoaderPdfContentExtractor() {
        // 默认空配置：未接入依赖/未配置 FQCN 时调用即给出明确失败信息（防静默跳过抽取）
        this(null, "extract");
    }

    public OpenDataLoaderPdfContentExtractor(String loaderClass, String loaderMethod) {
        this.loaderClass = loaderClass;
        this.loaderMethod = loaderMethod != null ? loaderMethod : "extract";
    }

    @Override
    public PdfExtractResult extract(File pdf) {
        if (loader == null) {
            initLoader();
        }
        try {
            Object raw = extractMethod.invoke(loader, pdf);
            return mapResult(raw);
        } catch (Exception e) {
            // 引擎异常：包装为非法状态上抛（由上层按 FAIL 处理，不入 FAIL_EMPTY）
            throw new IllegalStateException("OpenDataLoader PDF 抽取执行失败: " + e.getMessage(), e);
        }
    }

    private void initLoader() {
        if (loaderClass == null || loaderClass.isBlank()) {
            throw new IllegalStateException(
                    "OpenDataLoader 抽取器未接入：请引入本地 OpenDataLoader 依赖并在 " + CONF_PREFIX
                            + ".loader-class 配置其抽取器 FQCN（方法签名 Object extract(File)）");
        }
        try {
            Class<?> clazz = Class.forName(loaderClass);
            loader = clazz.getDeclaredConstructor().newInstance();
            extractMethod = clazz.getMethod(loaderMethod, File.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("OpenDataLoader 抽取器初始化失败: " + e.getMessage(), e);
        }
    }

    /** 反射读取 getText()/getTables()/getPageCount()（可选），缺失按 null 容错 */
    private PdfExtractResult mapResult(Object raw) {
        if (raw == null) {
            return new PdfExtractResult(null, null, null);
        }
        String text = readString(raw, "getText");
        Object tablesObj = readObject(raw, "getTables");
        Integer pageCount = readInt(raw, "getPageCount");
        return new PdfExtractResult(text, toTables(tablesObj), pageCount);
    }

    private String readString(Object raw, String getter) {
        Object v = readObject(raw, getter);
        return v == null ? null : String.valueOf(v);
    }

    private Integer readInt(Object raw, String getter) {
        Object v = readObject(raw, getter);
        return v instanceof Number n ? n.intValue() : null;
    }

    private Object readObject(Object raw, String getter) {
        try {
            Method m = raw.getClass().getMethod(getter);
            return m.invoke(raw);
        } catch (ReflectiveOperationException e) {
            log.debug("OpenDataLoader 结果对象无 {}() 方法，按空处理", getter);
            return null;
        }
    }

    /** tables 兼容 List<List<String>> / List<String[]>/二维数组等形态 → List<List<String>> */
    @SuppressWarnings("unchecked")
    private List<List<String>> toTables(Object tablesObj) {
        if (tablesObj == null) {
            return null;
        }
        if (tablesObj instanceof Iterable<?> rows) {
            List<List<String>> out = new ArrayList<>();
            for (Object row : rows) {
                out.add(rowToStrings(row));
            }
            return out;
        }
        if (tablesObj instanceof String[][] matrix) {
            List<List<String>> out = new ArrayList<>();
            for (String[] row : matrix) {
                out.add(List.of(row));
            }
            return out;
        }
        log.warn("OpenDataLoader getTables() 返回了未识别形态 {}，按空表格处理", tablesObj.getClass().getName());
        return null;
    }

    private List<String> rowToStrings(Object row) {
        if (row == null) {
            return List.of();
        }
        if (row instanceof Iterable<?> cells) {
            List<String> out = new ArrayList<>();
            for (Object cell : cells) {
                out.add(cell == null ? "" : String.valueOf(cell));
            }
            return out;
        }
        if (row instanceof String[]) {
            return List.of((String[]) row);
        }
        return List.of(String.valueOf(row));
    }

}
