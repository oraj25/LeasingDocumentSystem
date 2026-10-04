package com.leasingdocument.backend.repository;

import com.leasingdocument.backend.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
}