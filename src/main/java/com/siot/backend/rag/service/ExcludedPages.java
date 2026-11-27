package com.siot.backend.rag.service;

import com.siot.backend.global.exception.BusinessException;
import com.siot.backend.global.exception.ErrorCode;
import com.siot.backend.rag.document.PageRanges;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.util.OptionalInt;
import java.util.SortedSet;

/** ADM-04: 관리자가 보낸 "적재에서 뺄 쪽" 을 확인하고 저장 형태("51-52,100")로 바꾼다. 자동 감지는 하지 않는다 */
@Slf4j
final class ExcludedPages {

    /** 요청 값의 최대 길이 */
    static final int MAX_LENGTH = 1000;

    private ExcludedPages() {
    }

    /**
     * @param spec "51-52, 100". null 이거나 비어 있으면 빼는 쪽 없음
     * @param pdf  그 버전의 PDF. 쪽 수를 넘는 번호를 막는다 (열 수 없으면 쪽 수 확인은 건너뛰고 적재 [EXTRACT] 에서 실패한다)
     * @return 저장할 값. 빼는 쪽이 없으면 null
     * @throws BusinessException 형식이 틀림, 쪽 수를 넘음, 모든 쪽을 뺌, {@value #MAX_LENGTH}자 초과
     */
    static String normalize(String spec, byte[] pdf) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        if (spec.length() > MAX_LENGTH) {
            throw invalid();
        }
        SortedSet<Integer> pages;
        try {
            pages = PageRanges.parse(spec);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        if (pages.isEmpty()) {
            throw invalid();
        }
        OptionalInt pageCount = pageCount(pdf);
        if (pageCount.isPresent()
                && (pages.last() > pageCount.getAsInt() || pages.size() >= pageCount.getAsInt())) {
            throw invalid();
        }
        return PageRanges.format(pages);
    }

    private static OptionalInt pageCount(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return OptionalInt.of(document.getNumberOfPages());
        } catch (IOException e) {
            log.warn("Could not count pages to check excluded pages: {}", e.getMessage());
            return OptionalInt.empty();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.DOCUMENT_EXCLUDED_PAGES_INVALID);
    }
}
