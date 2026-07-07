package com.marnickseidel.moneyleaks.banking.repository;

import com.marnickseidel.moneyleaks.banking.domain.BankConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankConnectionRepository extends JpaRepository<BankConnection, Long> {

    Optional<BankConnection> findByExternalConnectionId(String externalConnectionId);

    Optional<BankConnection> findByOauthState(String oauthState);
}
