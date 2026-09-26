package com.zim4ik.fraud.repository;

import com.zim4ik.fraud.entity.BlockedEmail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockedEmailRepository extends JpaRepository<BlockedEmail, String> {
}
