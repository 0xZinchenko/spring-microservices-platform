package com.zim4ik.fraud.repository;

import com.zim4ik.fraud.entity.BlockedEmailDomain;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockedEmailDomainRepository extends JpaRepository<BlockedEmailDomain, String> {
}
