package com.leasingdocument.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "integrity_result")
public class IntegrityResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "integrity_result_id")
    private Long integrityResultId;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "sha256_hash")
    private String sha256Hash;

    @Column(name = "verification_result")
    private String verificationResult;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;


    public Long getIntegrityResultId() {
        return integrityResultId;
    }

    public void setIntegrityResultId(Long integrityResultId) {
        this.integrityResultId = integrityResultId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash) {
        this.sha256Hash = sha256Hash;
    }

    public String getVerificationResult() {
        return verificationResult;
    }

    public void setVerificationResult(String verificationResult) {
        this.verificationResult = verificationResult;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}