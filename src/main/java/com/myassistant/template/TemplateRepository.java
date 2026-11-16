package com.myassistant.template;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

// Only owner-scoped queries: a template is never looked up by its id alone.
public interface TemplateRepository extends MongoRepository<PromptTemplate, String> {

    List<PromptTemplate> findByOwnerIdOrderByNameAsc(String ownerId);

    Optional<PromptTemplate> findByIdAndOwnerId(String id, String ownerId);
}
