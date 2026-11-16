package com.myassistant.template;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

// Only owner-scoped queries: a template is never looked up by its id alone.
public interface TemplateRepository extends MongoRepository<PromptTemplate, String> {

    // Sorted by TemplateService (the order ignores letter case, which MongoDB's default order does not).
    List<PromptTemplate> findByOwnerId(String ownerId);

    Optional<PromptTemplate> findByIdAndOwnerId(String id, String ownerId);
}
