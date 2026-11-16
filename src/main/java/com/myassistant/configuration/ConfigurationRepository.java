package com.myassistant.configuration;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

// Only owner-scoped queries: a configuration is never looked up by its id alone.
public interface ConfigurationRepository extends MongoRepository<LlmConfiguration, String> {

    // Sorted by ConfigurationService (the order ignores letter case, which MongoDB's default order does not).
    List<LlmConfiguration> findByOwnerId(String ownerId);

    Optional<LlmConfiguration> findByIdAndOwnerId(String id, String ownerId);

    List<LlmConfiguration> findByOwnerIdAndTemplateId(String ownerId, String templateId);
}
