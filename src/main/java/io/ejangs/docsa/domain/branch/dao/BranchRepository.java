package io.ejangs.docsa.domain.branch.dao;

import io.ejangs.docsa.domain.branch.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {}
