package com.marnickseidel.moneyleaks.repository;

import com.marnickseidel.moneyleaks.model.entity.BankTransaction;
import com.marnickseidel.moneyleaks.model.enums.TransactionSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    List<BankTransaction> findByStatementId(Long statementId);

    List<BankTransaction> findAllByOrderByTransactionDateAsc();

    @Modifying
    @Query("delete from BankTransaction bt where bt.statement.id = :statementId")
    void deleteByStatementId(@Param("statementId") Long statementId);

    @Modifying
    @Query("delete from BankTransaction bt where bt.source = :source")
    void deleteBySource(@Param("source") TransactionSource source);
}
