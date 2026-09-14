package cn.iocoder.txgy.tool.pdftoimage.api;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量转换汇总（含失败清单），并写入输出目录 report.json。
 */
public class BatchSummary {

    private String tool = "txgy-tool-pdf-to-image";
    private final String input;
    private final String outputDir;
    private int pdfFiles = 0;
    private int okFiles = 0;
    private int failedFiles = 0;
    private int totalPages = 0;
    private final List<String[]> failures = new ArrayList<String[]>();
    private final List<String[]> pages = new ArrayList<String[]>();

    public BatchSummary(File input, File outputDir) {
        this.input = input == null ? "" : input.getAbsolutePath();
        this.outputDir = outputDir == null ? "" : outputDir.getAbsolutePath();
    }

    public void addPage(String fileName, int page, int w, int h) {
        pages.add(new String[]{fileName, String.valueOf(page), String.valueOf(w), String.valueOf(h)});
    }

    public void addFailure(String fileName, String error) {
        failures.add(new String[]{fileName, error == null ? "unknown" : error});
    }

    public int getPdfFiles() { return pdfFiles; }
    public void setPdfFiles(int pdfFiles) { this.pdfFiles = pdfFiles; }
    public int getOkFiles() { return okFiles; }
    public void setOkFiles(int okFiles) { this.okFiles = okFiles; }
    public int getFailedFiles() { return failedFiles; }
    public void setFailedFiles(int failedFiles) { this.failedFiles = failedFiles; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    /** 单行 JSON（写 report.json / 供上游断言），不做格式化 */
    public String toJson() {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        sb.append("\"tool\":\"").append(JsonUtil.escape(tool)).append('"');
        sb.append(",\"input\":\"").append(JsonUtil.escape(input)).append('"');
        sb.append(",\"outputDir\":\"").append(JsonUtil.escape(outputDir)).append('"');
        sb.append(",\"pdfFiles\":").append(pdfFiles);
        sb.append(",\"okFiles\":").append(okFiles);
        sb.append(",\"failedFiles\":").append(failedFiles);
        sb.append(",\"totalPages\":").append(totalPages);
        sb.append(",\"pages\":[");
        for (int i = 0; i < pages.size(); i++) {
            if (i > 0) { sb.append(','); }
            sb.append("[\"").append(JsonUtil.escape(pages.get(i)[0])).append("\",")
              .append(pages.get(i)[1]).append(',')
              .append(pages.get(i)[2]).append(',')
              .append(pages.get(i)[3]).append(']');
        }
        sb.append("],\"failures\":[");
        for (int i = 0; i < failures.size(); i++) {
            if (i > 0) { sb.append(','); }
            sb.append("[\"").append(JsonUtil.escape(failures.get(i)[0])).append("\",\"")
              .append(JsonUtil.escape(failures.get(i)[1])).append("\"]");
        }
        sb.append("]}");
        return sb.toString();
    }

    public boolean isAllOk() {
        return failedFiles == 0;
    }

}
