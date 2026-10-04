package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.IntegrityResult;
import com.leasingdocument.backend.repository.IntegrityResultRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class IntegrityResultService {

    private final IntegrityResultRepository integrityResultRepository;

    public IntegrityResultService(
            IntegrityResultRepository integrityResultRepository
    ) {
        this.integrityResultRepository = integrityResultRepository;
    }


    public List<IntegrityResult> getAllIntegrityResults() {

        return integrityResultRepository.findAll();
    }


    public IntegrityResult getIntegrityResultById(
            Long id
    ) {

        return integrityResultRepository
                .findById(id)
                .orElse(null);
    }


    public IntegrityResult saveIntegrityResult(
            IntegrityResult integrityResult
    ) {

        return integrityResultRepository
                .save(integrityResult);
    }


    public void deleteIntegrityResult(
            Long id
    ) {

        integrityResultRepository
                .deleteById(id);
    }


    // Get integrity results only for allowed documents
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
                .collect(Collectors.toList());
    }
}