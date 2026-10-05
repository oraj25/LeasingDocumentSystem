package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.IntegrityResult;
import com.leasingdocument.backend.repository.IntegrityResultRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class IntegrityResultService {

    private final IntegrityResultRepository integrityResultRepository;


    public IntegrityResultService(
            IntegrityResultRepository integrityResultRepository
    ) {

        this.integrityResultRepository =
                integrityResultRepository;
    }


    public List<IntegrityResult> getAllIntegrityResults() {

        return integrityResultRepository
                .findAll();
    }


    public IntegrityResult getIntegrityResultById(
            Long id
    ) {

        return integrityResultRepository
                .findById(id)
                .orElse(null);
    }


    // =========================================================
    // GET LATEST INTEGRITY RESULT FOR DOCUMENT
    // =========================================================

    public IntegrityResult getLatestResultForDocument(
            Long documentId
    ) {

        return integrityResultRepository
                .findAll()
                .stream()
                .filter(result ->
                        documentId.equals(
                                result.getDocumentId()
                        )
                )
                .max(
                        Comparator.comparing(
                                IntegrityResult::getIntegrityResultId
                        )
                )
                .orElse(null);
    }


    public IntegrityResult saveIntegrityResult(
            IntegrityResult integrityResult
    ) {

        return integrityResultRepository
                .save(
                        integrityResult
                );
    }


    public void deleteIntegrityResult(
            Long id
    ) {

        integrityResultRepository
                .deleteById(id);
    }


    public List<IntegrityResult> getResultsForDocuments(
            List<Long> documentIds
    ) {

        return integrityResultRepository
                .findAll()
                .stream()
                .filter(result ->
                        documentIds.contains(
                                result.getDocumentId()
                        )
                )
                .toList();
    }
}