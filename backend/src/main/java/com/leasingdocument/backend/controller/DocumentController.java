package com.leasingdocument.backend.controller;

import com.leasingdocument.backend.entity.Document;
import com.leasingdocument.backend.service.DocumentService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    private static final long MAX_FILE_SIZE =
            20L * 1024L * 1024L;

    public DocumentController(
            DocumentService documentService
    ) {
        this.documentService = documentService;
    }


    // =========================================================
    // ADMIN ONLY - View all documents
    // =========================================================

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Document> getAllDocuments() {

        return documentService.getAllDocuments();
    }


    // =========================================================
    // AGENT - View own documents
    // ADMIN - View all documents
    // =========================================================

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public List<Document> getMyDocuments(
            Authentication authentication
    ) {

        String role = authentication
                .getAuthorities()
                .iterator()
                .next()
                .getAuthority();

        if ("ROLE_ADMIN".equals(role)) {

            return documentService
                    .getAllDocuments();
        }

        Long agentId = Long.parseLong(
                authentication
                        .getPrincipal()
                        .toString()
        );

        return documentService
                .getAllDocuments()
                .stream()
                .filter(document ->
                        agentId.equals(
                                document.getAgentId()
                        )
                )
                .collect(Collectors.toList());
    }


    // =========================================================
    // ADMIN - Any document
    // AGENT - Own document only
    // =========================================================

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public Document getDocumentById(
            @PathVariable Long id,
            Authentication authentication
    ) {

        Document document =
                documentService.getDocumentById(id);

        if (document == null) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document not found"
            );
        }

        String role = authentication
                .getAuthorities()
                .iterator()
                .next()
                .getAuthority();

        if ("ROLE_ADMIN".equals(role)) {

            return document;
        }

        Long agentId = Long.parseLong(
                authentication
                        .getPrincipal()
                        .toString()
        );

        if (!agentId.equals(
                document.getAgentId()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Agents can only view their own documents"
            );
        }

        return document;
    }


    // =========================================================
    // ADMIN - Any file
    // AGENT - Own document file only
    // =========================================================

    @GetMapping("/image/{fileName}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<Resource> getImage(
            @PathVariable String fileName,
            Authentication authentication
    ) throws IOException {

        String safeFileName =
                Paths.get(fileName)
                        .getFileName()
                        .toString();

        Document document =
                documentService
                        .getAllDocuments()
                        .stream()
                        .filter(doc ->
                                safeFileName.equals(
                                        doc.getFileName()
                                )
                        )
                        .findFirst()
                        .orElse(null);

        if (document == null) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document file not found"
            );
        }

        String role = authentication
                .getAuthorities()
                .iterator()
                .next()
                .getAuthority();

        if ("ROLE_AGENT".equals(role)) {

            Long agentId =
                    Long.parseLong(
                            authentication
                                    .getPrincipal()
                                    .toString()
                    );

            if (!agentId.equals(
                    document.getAgentId()
            )) {

                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Agents can only view their own document files"
                );
            }
        }

        Path uploadDirectory =
                Paths.get("uploads")
                        .toAbsolutePath()
                        .normalize();

        Path path =
                uploadDirectory
                        .resolve(safeFileName)
                        .normalize();

        if (!path.startsWith(
                uploadDirectory
        )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid file path"
            );
        }

        Resource resource =
                new UrlResource(
                        path.toUri()
                );

        if (
                !resource.exists() ||
                        !resource.isReadable()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document file not found"
            );
        }

        String contentType =
                Files.probeContentType(path);

        MediaType mediaType =
                MediaType.APPLICATION_OCTET_STREAM;

        if (contentType != null) {

            try {

                mediaType =
                        MediaType.parseMediaType(
                                contentType
                        );

            } catch (Exception ignored) {

                // Keep default type
            }
        }

        return ResponseEntity
                .ok()
                .contentType(mediaType)
                .body(resource);
    }


    // =========================================================
    // ADMIN ONLY - Create document manually
    // =========================================================

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Document createDocument(
            @RequestBody Document document
    ) {

        return documentService
                .saveDocument(document);
    }


    // =========================================================
    // ADMIN + AGENT - SECURE DOCUMENT UPLOAD
    // =========================================================

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public String uploadDocument(
            @RequestParam("file")
            MultipartFile file,

            @RequestParam
            Long customerId,

            @RequestParam(required = false)
            Long agentId,

            @RequestParam
            Long documentTypeId,

            Authentication authentication

    ) throws IOException {

        // -----------------------------------------
        // 1. Reject empty files
        // -----------------------------------------

        if (
                file == null ||
                        file.isEmpty()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File cannot be empty"
            );
        }


        // -----------------------------------------
        // 2. File size validation
        // -----------------------------------------

        if (file.getSize() > MAX_FILE_SIZE) {

            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Maximum allowed file size is 20 MB"
            );
        }


        // -----------------------------------------
        // 3. Get safe original filename
        // -----------------------------------------

        String originalFileName =
                file.getOriginalFilename();

        if (
                originalFileName == null ||
                        originalFileName.isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid file name"
            );
        }

        originalFileName =
                Paths.get(originalFileName)
                        .getFileName()
                        .toString();


        // -----------------------------------------
        // 4. Validate extension
        // -----------------------------------------

        String extension =
                getFileExtension(
                        originalFileName
                );

        if (
                !extension.equals("jpg") &&
                        !extension.equals("jpeg") &&
                        !extension.equals("png") &&
                        !extension.equals("pdf")
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only JPG, JPEG, PNG and PDF files are allowed"
            );
        }


        // -----------------------------------------
        // 5. Validate actual file signature
        // -----------------------------------------

        if (!isValidFileSignature(
                file,
                extension
        )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File content does not match its file type"
            );
        }


        // -----------------------------------------
        // 6. Identify logged-in role
        // -----------------------------------------

        String role = authentication
                .getAuthorities()
                .iterator()
                .next()
                .getAuthority();


        // Agent ID must come from JWT
        if ("ROLE_AGENT".equals(role)) {

            agentId =
                    Long.parseLong(
                            authentication
                                    .getPrincipal()
                                    .toString()
                    );
        }


        // Admin must provide agent ID
        if (
                "ROLE_ADMIN".equals(role) &&
                        agentId == null
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Agent ID is required for admin uploads"
            );
        }


        // -----------------------------------------
        // 7. Create secure upload directory
        // -----------------------------------------

        Path uploadDirectory =
                Paths.get("uploads")
                        .toAbsolutePath()
                        .normalize();

        Files.createDirectories(
                uploadDirectory
        );


        // -----------------------------------------
        // 8. Generate unique server filename
        // -----------------------------------------

        String safeOriginalName =
                originalFileName
                        .replaceAll(
                                "[^a-zA-Z0-9._-]",
                                "_"
                        );

        String storedFileName =
                UUID.randomUUID()
                        .toString()
                        + "_"
                        + safeOriginalName;


        // -----------------------------------------
        // 9. Build secure file path
        // -----------------------------------------

        Path filePath =
                uploadDirectory
                        .resolve(
                                storedFileName
                        )
                        .normalize();

        if (!filePath.startsWith(
                uploadDirectory
        )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid upload path"
            );
        }


        // -----------------------------------------
        // 10. Save file without overwriting
        // -----------------------------------------

        Files.copy(
                file.getInputStream(),
                filePath
        );


        // -----------------------------------------
        // 11. Create database document record
        // -----------------------------------------

        Document document =
                new Document();

        document.setCustomerId(
                customerId
        );

        document.setAgentId(
                agentId
        );

        document.setDocumentTypeId(
                documentTypeId
        );

        document.setFileName(
                storedFileName
        );

        document.setFilePath(
                Paths.get(
                        "uploads",
                        storedFileName
                ).toString()
        );

        document.setStatus(
                "ACTIVE"
        );

        document.setCaptureStatus(
                "CAPTURED"
        );

        document.setVerificationStatus(
                "PENDING"
        );


        // -----------------------------------------
        // 12. Save DB record
        // Roll back file if DB save fails
        // -----------------------------------------

        try {

            documentService
                    .saveDocument(document);

        } catch (Exception e) {

            Files.deleteIfExists(
                    filePath
            );

            throw e;
        }


        return "File uploaded securely and document record created: "
                + storedFileName;
    }


    // =========================================================
    // ADMIN ONLY - Soft delete document
    // =========================================================

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteDocument(
            @PathVariable Long id
    ) {

        documentService
                .deleteDocument(id);

        return "Document deleted successfully";
    }


    // =========================================================
    // HELPER - Extract extension
    // =========================================================

    private String getFileExtension(
            String fileName
    ) {

        int dotIndex =
                fileName
                        .lastIndexOf('.');

        if (
                dotIndex < 0 ||
                        dotIndex ==
                                fileName.length() - 1
        ) {

            return "";
        }

        return fileName
                .substring(dotIndex + 1)
                .toLowerCase(
                        Locale.ROOT
                );
    }


    // =========================================================
    // HELPER - Validate real file content
    // =========================================================

    private boolean isValidFileSignature(
            MultipartFile file,
            String extension
    ) throws IOException {

        byte[] header;

        try (
                InputStream inputStream =
                        file.getInputStream()
        ) {

            header =
                    inputStream.readNBytes(8);
        }

        if (
                extension.equals("jpg") ||
                        extension.equals("jpeg")
        ) {

            return header.length >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF;
        }

        if (extension.equals("png")) {

            return header.length >= 8
                    && (header[0] & 0xFF) == 0x89
                    && header[1] == 0x50
                    && header[2] == 0x4E
                    && header[3] == 0x47
                    && header[4] == 0x0D
                    && header[5] == 0x0A
                    && header[6] == 0x1A
                    && header[7] == 0x0A;
        }

        if (extension.equals("pdf")) {

            return header.length >= 4
                    && header[0] == 0x25
                    && header[1] == 0x50
                    && header[2] == 0x44
                    && header[3] == 0x46;
        }

        return false;
    }
}