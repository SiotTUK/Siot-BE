package com.siot.backend.translation.bill;

import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * RUL-07: PDF 고지서는 첫 쪽만 PNG 로 바꿔 AI 로 보낸다 (나머지 쪽은 외부로 보내지 않음).
 * 읽기·그리기·PNG 저장 모두 메모리에서만 한다 (임시 파일 없음).
 */
final class PdfFirstPage {

    /** 고지서 글자를 읽을 만한 해상도 */
    private static final float DPI = 150f;
    /** 긴 변 최대 픽셀 (아주 큰 쪽 크기 대비) */
    private static final int MAX_PIXELS = 2500;

    record Rendered(byte[] png, int pageCount) {
    }

    private PdfFirstPage() {
    }

    static Rendered render(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf, "", null, null, IOUtils.createMemoryOnlyStreamCache())) {
            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                throw new BusinessException(ErrorCode.BILL_FILE_INVALID);
            }
            PDRectangle box = document.getPage(0).getCropBox();
            float longest = Math.max(box.getWidth(), box.getHeight());
            float scale = Math.min(DPI / 72f, longest <= 0 ? DPI / 72f : MAX_PIXELS / longest);
            BufferedImage image = new PDFRenderer(document).renderImage(0, scale, ImageType.RGB);
            return new Rendered(png(image), pageCount);
        } catch (IOException e) {
            // 깨진 PDF, 암호 PDF
            throw new BusinessException(ErrorCode.BILL_FILE_INVALID);
        }
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            if (!ImageIO.write(image, "png", stream)) {
                throw new IOException("No PNG writer");
            }
        }
        return out.toByteArray();
    }
}
