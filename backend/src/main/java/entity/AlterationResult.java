package com.leasingdocument.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "alteration_result")
public class AlterationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alteration_result_id")
    private Long alterationResultId;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "analysis_status")
    private String analysisStatus;

    @Column(name = "suspicious_region_count")
    private Integer suspiciousRegionCount;

    @Column(name = "algorithm_version")
    private String algorithmVersion;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;


    public Long getAlterationResultId() {
        return alterationResultId;
    }

    public void setAlterationResultId(Long alterationResultId) {
        this.alterationResultId = alterationResultId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getAnalysisStatus() {
        return analysisStatus;
    }

    public void setAnalysisStatus(String analysisStatus) {
        this.analysisStatus = analysisStatus;
    }

    public Integer getSuspiciousRegionCount() {
        return suspiciousRegionCount;
    }

    public void setSuspiciousRegionCount(Integer suspiciousRegionCount) {
        this.suspiciousRegionCount = suspiciousRegionCount;
    }

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public void setAlgorithmVersion(String algorithmVersion) {
        this.algorithmVersion = algorithmVersion;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}