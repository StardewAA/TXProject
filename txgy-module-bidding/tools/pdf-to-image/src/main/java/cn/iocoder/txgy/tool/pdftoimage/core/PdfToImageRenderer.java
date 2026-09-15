package cn.iocoder.txgy.tool.pdftoimage.core;

import cn.iocoder.txgy.tool.pdftoimage.api.PageImage;
import cn.iocoder.txgy.tool.pdftoimage.api.PdfToImageOptions;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * PDF 转图片渲染器：单文件逐页转图。
 */
public interface PdfToImageRenderer {

    /**
     * 将 pdf 的每一页转换为一张图片并写入 outDir。
     *
     * @param pdf     待转换 PDF
     * @param outDir  输出目录（不存在则创建）
     * @param options 分辨率/格式/尺寸限制等
     * @return 每页一张的结果；文件不存在/无法打开时抛 IOException
     */
    List<PageImage> convert(File pdf, File outDir, PdfToImageOptions options) throws IOException;

}
