package com.siot.backend.rag.config;

import com.siot.backend.rag.ai.DocumentEmbedder;
import com.siot.backend.rag.document.BatchEmbedder;
import com.siot.backend.rag.document.DocumentChunker;
import com.siot.backend.rag.document.FileStorage;
import com.siot.backend.rag.document.LocalFileStorage;
import com.siot.backend.rag.document.PdfContentExtractor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/** 문서 적재 (ADM-02~04) 구성 요소. 파일 저장소는 지금은 로컬 폴더 (GCS 는 나중에) */
@Configuration
@EnableConfigurationProperties(DocumentProperties.class)
public class DocumentIngestionConfig {

    @Bean
    public FileStorage fileStorage(DocumentProperties props) {
        return new LocalFileStorage(Path.of(props.storageDir()));
    }

    @Bean
    public PdfContentExtractor pdfContentExtractor() {
        return new PdfContentExtractor();
    }

    @Bean
    public DocumentChunker documentChunker() {
        return new DocumentChunker();
    }

    @Bean
    public BatchEmbedder batchEmbedder(DocumentEmbedder documentEmbedder, DocumentProperties props) {
        return new BatchEmbedder(documentEmbedder, props, duration -> Thread.sleep(duration));
    }
}
