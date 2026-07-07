package com.marnickseidel.moneyleaks.service;

import com.marnickseidel.moneyleaks.model.dto.StatementUploadResponse;
import com.marnickseidel.moneyleaks.model.entity.Statement;
import com.marnickseidel.moneyleaks.model.enums.StatementStatus;
import com.marnickseidel.moneyleaks.repository.StatementRepository;
import com.marnickseidel.moneyleaks.util.ContentHashUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

@Service
public class StatementService {

    private final StatementRepository statementRepository;

    public StatementService(StatementRepository statementRepository) {
        this.statementRepository = statementRepository;
    }

    @Transactional
    public StatementUploadResponse upload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required");
        }

        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("statement.csv");
        if (!filename.toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException("Only CSV files are supported");
        }

        byte[] content = file.getBytes();
        String contentHash = ContentHashUtil.sha256(content);

        Optional<Statement> existing = statementRepository.findByContentHash(contentHash);
        if (existing.isPresent()) {
            Statement statement = existing.get();
            return new StatementUploadResponse(
                    statement.getId(),
                    statement.getFilename(),
                    statement.getContentHash(),
                    statement.getStatus(),
                    statement.getUploadedAt(),
                    true
            );
        }

        Statement statement = new Statement();
        statement.setFilename(filename);
        statement.setContentHash(contentHash);
        statement.setContent(content);
        statement.setStatus(StatementStatus.UPLOADED);
        statement.setUploadedAt(Instant.now());

        Statement saved = statementRepository.save(statement);
        return new StatementUploadResponse(
                saved.getId(),
                saved.getFilename(),
                saved.getContentHash(),
                saved.getStatus(),
                saved.getUploadedAt(),
                false
        );
    }

    @Transactional(readOnly = true)
    public Statement getRequired(Long id) {
        return statementRepository.findById(id)
                .orElseThrow(() -> new StatementNotFoundException(id));
    }
}
