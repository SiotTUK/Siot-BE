package com.siot.backend.rag.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "document")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "source_url", columnDefinition = "text")
    private String sourceUrl;

    @Column(name = "published_on")
    private LocalDate publishedOn;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** ADM-02: 새 문서 (사용 중) */
    public static Document create(String name, String sourceUrl, LocalDate publishedOn) {
        Document document = new Document();
        document.name = name;
        document.sourceUrl = sourceUrl;
        document.publishedOn = publishedOn;
        return document;
    }

    /** ADM-03: null 인 값은 바꾸지 않는다 */
    public void update(String name, String sourceUrl, LocalDate publishedOn, Boolean active) {
        if (name != null) {
            this.name = name;
        }
        if (sourceUrl != null) {
            this.sourceUrl = sourceUrl;
        }
        if (publishedOn != null) {
            this.publishedOn = publishedOn;
        }
        if (active != null) {
            this.active = active;
        }
    }
}
