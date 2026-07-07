package com.marnickseidel.moneyleaks.controller;

import com.marnickseidel.moneyleaks.model.dto.StatementProcessResponse;
import com.marnickseidel.moneyleaks.model.dto.StatementUploadResponse;
import com.marnickseidel.moneyleaks.service.StatementProcessingService;
import com.marnickseidel.moneyleaks.service.StatementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/statements")
public class StatementController {

    private final StatementService statementService;
    private final StatementProcessingService statementProcessingService;

    public StatementController(
            StatementService statementService,
            StatementProcessingService statementProcessingService
    ) {
        this.statementService = statementService;
        this.statementProcessingService = statementProcessingService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public StatementUploadResponse upload(@RequestPart("file") MultipartFile file) throws IOException {
        return statementService.upload(file);
    }

    @PostMapping("/{id}/process")
    public StatementProcessResponse process(@PathVariable Long id) throws IOException {
        return statementProcessingService.process(id);
    }
}
