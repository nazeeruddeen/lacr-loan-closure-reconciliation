package com.employee.loan_system.lacr.repository;

import com.employee.loan_system.lacr.document.LoanClosureAuditEventDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface LoanClosureAuditEventMongoRepository extends MongoRepository<LoanClosureAuditEventDocument, String> {
    List<LoanClosureAuditEventDocument> findAllByOrderByCreatedAtDesc();
}
