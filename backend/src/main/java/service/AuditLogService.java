package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.AuditLog;
import com.leasingdocument.backend.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public List<AuditLog> getAllAuditLogs() {
        return auditLogRepository.findAll();
    }

    public AuditLog getAuditLogById(Long id) {
        return auditLogRepository.findById(id).orElse(null);
    }

    public AuditLog saveAuditLog(AuditLog auditLog) {
        return auditLogRepository.save(auditLog);
    }

    public void deleteAuditLog(Long id) {
        auditLogRepository.deleteById(id);
    }

    // Create audit log for login
    public AuditLog createLoginAuditLog(
            String role,
            Long userId,
            String ipAddress
    ) {

        AuditLog auditLog = new AuditLog();

        if ("ADMIN".equals(role)) {
            auditLog.setAdminId(userId);

        } else if ("AGENT".equals(role)) {
            auditLog.setAgentId(userId);
        }

        auditLog.setAction("LOGIN");
        auditLog.setDescription(
                role + " logged in successfully"
        );
        auditLog.setIpAddress(ipAddress);
        auditLog.setCreatedAt(
                LocalDateTime.now()
        );

        return auditLogRepository.save(
                auditLog
        );
    }

    // Create audit log for logout
    public AuditLog createLogoutAuditLog(
            String role,
            Long userId,
            String ipAddress
    ) {

        AuditLog auditLog = new AuditLog();

        if ("ADMIN".equals(role)) {
            auditLog.setAdminId(userId);

        } else if ("AGENT".equals(role)) {
            auditLog.setAgentId(userId);
        }

        auditLog.setAction("LOGOUT");
        auditLog.setDescription(
                role + " logged out successfully"
        );
        auditLog.setIpAddress(ipAddress);
        auditLog.setCreatedAt(
                LocalDateTime.now()
        );

        return auditLogRepository.save(
                auditLog
        );
    }
}