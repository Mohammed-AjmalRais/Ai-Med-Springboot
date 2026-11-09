package com.medibot.service;

import com.medibot.dto.DiagnosisResponse;
import com.medibot.dto.MlServiceResponse;
import com.medibot.dto.PredictionItem;
import com.medibot.model.DiseaseInfo;
import com.medibot.repository.DiseaseRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Core Orchestration Service:
 * 1. Calls Python ML microservice for XGBoost inference.
 * 2. Enriches prediction with clinical knowledge from Spring Data JPA repository.
 */
@Service
public class DiagnosisService {

    private final MlClientService mlClientService;
    private final DiseaseRepository diseaseRepository;

    public DiagnosisService(MlClientService mlClientService, DiseaseRepository diseaseRepository) {
        this.mlClientService = mlClientService;
        this.diseaseRepository = diseaseRepository;
    }

    public DiagnosisResponse diagnose(List<String> symptoms) {
        // 1. Call ML microservice for disease inference
        MlServiceResponse mlResponse = mlClientService.predict(symptoms);
        String topDisease = mlResponse.getTopDisease();
        Double confidence = mlResponse.getConfidence();
        List<PredictionItem> allPredictions = mlResponse.getPredictions();

        // 2. Query Database (Spring Data JPA) for clinical knowledge
        Optional<DiseaseInfo> diseaseOpt = diseaseRepository.findByDiseaseNameIgnoreCase(topDisease);

        String specialist;
        String severity;
        String remedies;
        String precautions;

        if (diseaseOpt.isPresent()) {
            DiseaseInfo info = diseaseOpt.get();
            specialist = info.getSpecialistToConsult();
            severity = info.getSeverityLevel();
            remedies = info.getHomeRemedies();
            precautions = info.getPrecautions();
        } else {
            // Default fallback if disease is not yet seeded
            specialist = "General Physician";
            severity = "Moderate";
            remedies = "Get plenty of rest, maintain hydration with warm fluids or ORS, and monitor symptoms.";
            precautions = "Seek medical evaluation if symptoms worsen or fever persists.";

            DiseaseInfo newEntry = new DiseaseInfo(topDisease, specialist, severity, remedies, precautions);
            diseaseRepository.save(newEntry);
        }

        return new DiagnosisResponse(
                topDisease,
                confidence,
                specialist,
                severity,
                remedies,
                precautions,
                false,
                allPredictions
        );
    }
}
