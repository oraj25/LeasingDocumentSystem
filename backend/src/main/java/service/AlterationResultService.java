package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.AlterationResult;
import com.leasingdocument.backend.repository.AlterationResultRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AlterationResultService {

    private final AlterationResultRepository alterationResultRepository;

    public AlterationResultService(
            AlterationResultRepository alterationResultRepository
    ) {
        this.alterationResultRepository = alterationResultRepository;
    }


    public List<AlterationResult> getAllAlterationResults() {

        return alterationResultRepository.findAll();
    }


    public AlterationResult getAlterationResultById(
            Long id
    ) {

        return alterationResultRepository
                .findById(id)
                .orElse(null);
    }


    public AlterationResult saveAlterationResult(
            AlterationResult alterationResult
    ) {

        return alterationResultRepository
                .save(alterationResult);
    }


    public void deleteAlterationResult(
            Long id
    ) {

        alterationResultRepository
                .deleteById(id);
    }


    // Get alteration results only for allowed documents
    public List<AlterationResult> getResultsForDocuments(
            List<Long> documentIds
    ) {

        return alterationResultRepository
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