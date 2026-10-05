package com.leasingdocument.backend.service;

import com.leasingdocument.backend.contract.WorkflowConstants;
import com.leasingdocument.backend.entity.AuditLog;
import com.leasingdocument.backend.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;


    public AuditLogService(
            AuditLogRepository auditLogRepository
    ) {

        this.auditLogRepository =
                auditLogRepository;
    }


    public List<AuditLog> getAllAuditLogs() {

        return auditLogRepository
                .findAll();
    }


    public AuditLog getAuditLogById(
            Long id
    ) {

        return auditLogRepository
                .findById(id)
                .orElse(null);
    }


    public AuditLog saveAuditLog(
            AuditLog auditLog
    ) {

        return auditLogRepository
                .save(auditLog);
    }


    public void deleteAuditLog(
            Long id
    ) {

        auditLogRepository
                .deleteById(id);
    }


    // =========================================================
    // LOGIN EVENT
    // =========================================================

    public AuditLog createLoginAuditLog(
            String role,
            Long userId,
            String ipAddress
    ) {

        AuditLog auditLog =
                new AuditLog();


        applyUser(
                auditLog,
                role,
                userId
        );


        auditLog.setAction(
                WorkflowConstants
                        .AuditAction
                        .LOGIN
        );

        auditLog.setEventStatus(
                WorkflowConstants
                        .AuditEventStatus
                        .SUCCESS
        );

        auditLog.setDescription(
                role +
                        " logged in successfully"
        );

        auditLog.setIpAddress(
                ipAddress
        );

        auditLog.setCreatedAt(
                LocalDateTime.now()
        );


        return auditLogRepository
                .save(auditLog);
    }


    // =========================================================
    // LOGOUT EVENT
    // =========================================================

    public AuditLog createLogoutAuditLog(
            String role,
            Long userId,
            String ipAddress
    ) {

        AuditLog auditLog =
                new AuditLog();


        applyUser(
                auditLog,
                role,
                userId
        );


        auditLog.setAction(
                WorkflowConstants
                        .AuditAction
                        .LOGOUT
        );

        auditLog.setEventStatus(
                WorkflowConstants
                        .AuditEventStatus
                        .SUCCESS
        );

        auditLog.setDescription(
                role +
                        " logged out successfully"
        );

        auditLog.setIpAddress(
                ipAddress
        );

        auditLog.setCreatedAt(
                LocalDateTime.now()
        );


        return auditLogRepository
                .save(auditLog);
    }


    // =========================================================
    // DOCUMENT / SECURITY EVENT
    //
    // This method will be used by:
    //
    // camera capture
    // quality validation
    // integrity verification
    // encryption
    // alteration analysis
    // admin review
    // =========================================================

    public AuditLog createDocumentAuditLog(
            String role,
            Long userId,
            Long documentId,
            Long deviceId,
            String action,
            String eventStatus,
            String description,
            String ipAddress
    ) {

        AuditLog auditLog =
                new AuditLog();


        applyUser(
                auditLog,
                role,
                userId
        );


        auditLog.setDocumentId(
                documentId
        );

        auditLog.setDeviceId(
                deviceId
        );

        auditLog.setAction(
                action
        );

        auditLog.setEventStatus(
                eventStatus
        );

        auditLog.setDescription(
                description
        );

        auditLog.setIpAddress(
                ipAddress
        );

        auditLog.setCreatedAt(
                LocalDateTime.now()
        );


        return auditLogRepository
                .save(auditLog);
    }


    // =========================================================
    // ASSIGN ADMIN / AGENT TO AUDIT EVENT
    // =========================================================

    private void applyUser(
            AuditLog auditLog,
            String role,
            Long userId
    ) {

        if (
                role == null ||
                        userId == null
        ) {

            return;
        }


        if (
                "ADMIN".equalsIgnoreCase(
                        role
                )
        ) {

            auditLog.setAdminId(
                    userId
            );


        } else if (
                "AGENT".equalsIgnoreCase(
                        role
                )
        ) {

            auditLog.setAgentId(
                    userId
            );
        }
    }
}