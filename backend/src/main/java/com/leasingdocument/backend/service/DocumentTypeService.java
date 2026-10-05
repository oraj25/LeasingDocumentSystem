package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.DocumentType;
import com.leasingdocument.backend.repository.DocumentTypeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class DocumentTypeService {

    private final DocumentTypeRepository documentTypeRepository;


    public DocumentTypeService(
            DocumentTypeRepository documentTypeRepository
    ) {

        this.documentTypeRepository =
                documentTypeRepository;
    }


    // =========================================================
    // GET ALL DOCUMENT TYPES
    // =========================================================

    public List<DocumentType> getAllDocumentTypes() {

        return documentTypeRepository
                .findAll();
    }


    // =========================================================
    // GET ACTIVE DOCUMENT TYPES
    // =========================================================

    public List<DocumentType> getActiveDocumentTypes() {

        return documentTypeRepository
                .findAll()
                .stream()
                .filter(type ->
                        type.getStatus() == null
                                ||
                                "ACTIVE".equalsIgnoreCase(
                                        type.getStatus()
                                )
                )
                .toList();
    }


    // =========================================================
    // GET DOCUMENT TYPE BY ID
    // =========================================================

    public DocumentType getDocumentTypeById(
            Long id
    ) {

        return documentTypeRepository
                .findById(id)
                .orElse(null);
    }


    // =========================================================
    // GET DOCUMENT TYPE BY STABLE TYPE CODE
    // =========================================================

    public DocumentType getDocumentTypeByCode(
            String typeCode
    ) {

        if (
                typeCode == null ||
                        typeCode.isBlank()
        ) {

            return null;
        }


        String normalizedCode =
                typeCode
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );


        return documentTypeRepository
                .findAll()
                .stream()
                .filter(type ->
                        type.getTypeCode() != null
                                &&
                                normalizedCode.equals(
                                        type.getTypeCode()
                                                .trim()
                                                .toUpperCase(
                                                        Locale.ROOT
                                                )
                                )
                )
                .findFirst()
                .orElse(null);
    }


    // =========================================================
    // SAVE DOCUMENT TYPE
    // =========================================================

    public DocumentType saveDocumentType(
            DocumentType documentType
    ) {

        if (
                documentType.getTypeCode() != null
        ) {

            documentType.setTypeCode(
                    documentType
                            .getTypeCode()
                            .trim()
                            .toUpperCase(
                                    Locale.ROOT
                            )
            );
        }


        return documentTypeRepository
                .save(documentType);
    }


    // =========================================================
    // DELETE DOCUMENT TYPE
    //
    // Kept for compatibility with the current Admin UI.
    // Later this becomes deactivate-only.
    // =========================================================

    public void deleteDocumentType(
            Long id
    ) {

        documentTypeRepository
                .deleteById(id);
    }
}