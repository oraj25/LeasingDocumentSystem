package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.DocumentType;
import com.leasingdocument.backend.repository.DocumentTypeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentTypeService {

    private final DocumentTypeRepository documentTypeRepository;


    public DocumentTypeService(DocumentTypeRepository documentTypeRepository) {
        this.documentTypeRepository = documentTypeRepository;
    }


    public List<DocumentType> getAllDocumentTypes() {
        return documentTypeRepository.findAll();
    }


    public DocumentType getDocumentTypeById(Long id) {
        return documentTypeRepository.findById(id).orElse(null);
    }


    public DocumentType saveDocumentType(DocumentType documentType) {
        return documentTypeRepository.save(documentType);
    }


    public void deleteDocumentType(Long id) {
        documentTypeRepository.deleteById(id);
    }
}