package cn.iocoder.txgy.tool.pdftoimage.api;

import java.io.File;

/**
 * 单页转换结果：页码（1 起）+ 实际像素尺寸 + 输出文件。
 */
public class PageImage {

    private final int pageIndex;
    private final int width;
    private final int height;
    private final File file;

    public PageImage(int pageIndex, int width, int height, File file) {
        this.pageIndex = pageIndex;
        this.width = width;
        this.height = height;
        this.file = file;
    }

    public int getPageIndex() { return pageIndex; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public File getFile() { return file; }

}
