package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.Document;
import com.leasingdocument.backend.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentService {

    private static final String DELETED_STATUS =
            "DELETED";


    private final DocumentRepository documentRepository;


    public DocumentService(
            DocumentRepository documentRepository
    ) {

        this.documentRepository =
                documentRepository;
    }


    // =========================================================
    // GET ALL ACTIVE DOCUMENTS
    // =========================================================

    public List<Document> getAllDocuments() {

        return documentRepository
                .findByStatusNotIgnoreCase(
                        DELETED_STATUS
                );
    }


    // =========================================================
    // GET DOCUMENT BY ID
    // =========================================================

    public Document getDocumentById(
            Long id
    ) {

        Document document =
                documentRepository
                        .findById(id)
                        .orElse(null);


        if (document == null) {

            return null;
        }


        if (
                DELETED_STATUS.equalsIgnoreCase(
                        document.getStatus()
                )
        ) {

            return null;
        }


        return document;
    }


    // =========================================================
    // GET DOCUMENTS BELONGING TO AGENT
    // =========================================================

    public List<Document> getDocumentsByAgentId(
            Long agentId
    ) {

        return documentRepository
                .findByAgentIdAndStatusNotIgnoreCase(
                        agentId,
                        DELETED_STATUS
                );
    }


    // =========================================================
    // CHECK DOCUMENT OWNERSHIP
    // =========================================================

    public boolean isDocumentOwnedByAgent(
            Long documentId,
            Long agentId
    ) {

        Document document =
                getDocumentById(
                        documentId
                );


        return document != null
                &&
                agentId != null
                &&
                agentId.equals(
                        document.getAgentId()
                );
    }


    // =========================================================
    // SAVE DOCUMENT
    // =========================================================

    public Document saveDocument(
            Document document
    ) {

        return documentRepository
                .save(document);
    }


    // =========================================================
    // SOFT DELETE DOCUMENT
    // =========================================================

    public void deleteDocument(
            Long id
    ) {

        Document document =
                documentRepository
                        .findById(id)
                        .orElse(null);


        if (document != null) {

            document.setStatus(
                    DELETED_STATUS
            );

            documentRepository
                    .save(document);
        }
    }
}