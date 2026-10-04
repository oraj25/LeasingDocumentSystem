package com.leasingdocument.backend.controller;

import com.leasingdocument.backend.entity.DocumentType;
import com.leasingdocument.backend.service.DocumentTypeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/document-types")
public class DocumentTypeController {

    private final DocumentTypeService documentTypeService;


    public DocumentTypeController(DocumentTypeService documentTypeService) {
        this.documentTypeService = documentTypeService;
    }


    // ADMIN + AGENT - View document types
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public List<DocumentType> getAllDocumentTypes() {
        return documentTypeService.getAllDocumentTypes();
    }


    // ADMIN + AGENT - View document type details
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public DocumentType getDocumentTypeById(
            @PathVariable Long id) {

        return documentTypeService.getDocumentTypeById(id);
    }


    // ADMIN ONLY - Create document type
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public DocumentType createDocumentType(
            @RequestBody DocumentType documentType) {

        return documentTypeService.saveDocumentType(documentType);
    }


    // ADMIN ONLY - Delete document type
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteDocumentType(
            @PathVariable Long id) {

        documentTypeService.deleteDocumentType(id);

        return "Document type deleted successfully";
    }
}