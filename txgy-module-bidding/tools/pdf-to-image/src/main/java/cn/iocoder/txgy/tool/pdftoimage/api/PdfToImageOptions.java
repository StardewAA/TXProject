package cn.iocoder.txgy.tool.pdftoimage.api;

/**
 * PDF 转图片配置项（含分辨率/尺寸限制）。
 */
public class PdfToImageOptions {

    public static final String FORMAT_PNG = "PNG";
    public static final String FORMAT_JPG = "JPG";

    /** 默认渲染 DPI：A4@200dpi ≈ 1654×2339，证书清晰且体积适中 */
    public static final int DEFAULT_DPI = 200;
    /** 默认最大宽/高（A4@300dpi），0 表示不限制 */
    public static final int DEFAULT_MAX_WIDTH = 2480;
    public static final int DEFAULT_MAX_HEIGHT = 3508;
    public static final double DEFAULT_JPEG_QUALITY = 0.9;

    /** 渲染 DPI（72~600） */
    private int dpi = DEFAULT_DPI;
    /** 输出格式：PNG / JPG */
    private String format = FORMAT_PNG;
    /** JPG 质量 0.1~1.0（仅 JPG 生效） */
    private double jpegQuality = DEFAULT_JPEG_QUALITY;
    /** 最大输出宽（像素），超出等比缩小；0=不限制 */
    private int maxWidth = DEFAULT_MAX_WIDTH;
    /** 最大输出高（像素），超出等比缩小；0=不限制 */
    private int maxHeight = DEFAULT_MAX_HEIGHT;
    /** 白底合成（JPG 强制白底）；false 时 PNG 保留透明（ARGB） */
    private boolean whiteBackground = true;
    /** 目录输入时是否递归子目录 */
    private boolean recursive = true;
    /** 批量并发线程数 */
    private int threads = 2;
    /** 已存在同名输出时是否覆盖；false=跳过 */
    private boolean overwrite = false;

    public int getDpi() { return dpi; }
    public PdfToImageOptions setDpi(int dpi) {
        this.dpi = Math.max(36, Math.min(dpi, 1200));
        return this;
    }

    public String getFormat() { return format; }
    /** 返回小写扩展名 jpg/png */
    public String getFormatExt() {
        return FORMAT_JPG.equalsIgnoreCase(format) ? "jpg" : "png";
    }
    public PdfToImageOptions setFormat(String format) {
        if (FORMAT_JPG.equalsIgnoreCase(format)) {
            this.format = FORMAT_JPG;
        } else {
            this.format = FORMAT_PNG;
        }
        return this;
    }

    public double getJpegQuality() { return jpegQuality; }
    public PdfToImageOptions setJpegQuality(double jpegQuality) {
        this.jpegQuality = Math.max(0.1, Math.min(jpegQuality, 1.0));
        return this;
    }

    public int getMaxWidth() { return maxWidth; }
    public PdfToImageOptions setMaxWidth(int maxWidth) { this.maxWidth = Math.max(0, maxWidth); return this; }

    public int getMaxHeight() { return maxHeight; }
    public PdfToImageOptions setMaxHeight(int maxHeight) { this.maxHeight = Math.max(0, maxHeight); return this; }

    public boolean isWhiteBackground() { return whiteBackground; }
    public PdfToImageOptions setWhiteBackground(boolean whiteBackground) { this.whiteBackground = whiteBackground; return this; }

    public boolean isRecursive() { return recursive; }
    public PdfToImageOptions setRecursive(boolean recursive) { this.recursive = recursive; return this; }

    public int getThreads() { return threads; }
    public PdfToImageOptions setThreads(int threads) { this.threads = Math.max(1, threads); return this; }

    public boolean isOverwrite() { return overwrite; }
    public PdfToImageOptions setOverwrite(boolean overwrite) { this.overwrite = overwrite; return this; }

}
