package com.marnickseidel.moneyleaks.repository;

import com.marnickseidel.moneyleaks.model.entity.Statement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StatementRepository extends JpaRepository<Statement, Long> {

    Optional<Statement> findByContentHash(String contentHash);
}
