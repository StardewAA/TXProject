package cn.iocoder.txgy.module.cms.service.review;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.pdfbox.pdmodel.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import static cn.iocoder.txgy.module.cms.service.review.ReviewModels.*;
import static org.assertj.core.api.Assertions.*;

class ReviewParserTest {
    ReviewParser parser=new ReviewParser();
    @Test void rejectsInvalidEncodingAndFakeFormat() {
        assertThat(parser.parse(new MockMultipartFile("file","bad.txt","text/plain",new byte[]{(byte)0xff})).issues()).extracting(Issue::code).contains("INVALID_UTF8");
        assertThat(parser.parse(new MockMultipartFile("file","fake.pdf","application/pdf","not a pdf".getBytes())).issues()).extracting(Issue::code).contains("PARSE_FAILED");
    }
    @Test void detectsScanPages() throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(PDDocument doc=new PDDocument()) {doc.addPage(new PDPage());doc.save(out);}
        assertThat(parser.parse(new MockMultipartFile("file","scan.pdf","application/pdf",out.toByteArray())).issues()).extracting(Issue::code).contains("OCR_REQUIRED");
    }
    @Test void readsDocxAndBlocksExternalEntities() throws Exception {
        String xml="<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>第一条 正文</w:t></w:r></w:p></w:body></w:document>";
        assertThat(parser.parse(docx(xml)).text()).contains("第一条 正文");
        assertThat(parser.parse(docx("<!DOCTYPE doc [<!ENTITY xxe SYSTEM 'file:///not-allowed'>]>"+xml)).issues()).extracting(Issue::code).contains("PARSE_FAILED");
    }
    private MockMultipartFile docx(String xml) throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(bytes)) {zip.putNextEntry(new ZipEntry("word/document.xml"));zip.write(xml.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
        return new MockMultipartFile("file","test.docx","application/octet-stream",bytes.toByteArray());
    }
}
