package com.smartlamp.agent.actions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// Agent 写操作审计仓库（阶段19）：与项目其他仓库同一套 Spring Data JPA 体系
public interface AgentActionAuditRepository extends JpaRepository<AgentActionAudit, Long> {

    Optional<AgentActionAudit> findByActionId(String actionId);

    List<AgentActionAudit> findAllByOrderByRequestedAtDesc();
}
