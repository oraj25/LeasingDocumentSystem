package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.Document;
import com.leasingdocument.backend.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    // Return only non-deleted documents
    public List<Document> getAllDocuments() {

        return documentRepository.findAll()
                .stream()
                .filter(document ->
                        !"DELETED".equalsIgnoreCase(document.getStatus())
                )
                .collect(Collectors.toList());
    }

    // Return document only if it is not deleted
    public Document getDocumentById(Long id) {

        Document document =
                documentRepository.findById(id).orElse(null);

        if (document == null) {
            return null;
        }

        if ("DELETED".equalsIgnoreCase(document.getStatus())) {
            return null;
        }

        return document;
    }

    public Document saveDocument(Document document) {
        return documentRepository.save(document);
    }

    // Soft Delete
    public void deleteDocument(Long id) {

        Document document =
                documentRepository.findById(id).orElse(null);

        if (document != null) {

            document.setStatus("DELETED");

            documentRepository.save(document);
        }
    }
}