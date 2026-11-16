package com.myassistant.config;

import com.myassistant.MongoPersistenceTest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test of the persistence foundation ({@link MongoPersistenceTest}) against a real MongoDB in Docker, with a
 * document that exists only here: the features of Phases 3-5 rely on each of these behaviours.
 */
@MongoPersistenceTest
class MongoPersistenceSmokeTest {

    @Document("smoke_notes")
    record SmokeNote(@Id String id, @Version Long version, @Indexed(unique = true) @NotBlank String name) {

        SmokeNote withName(String newName) {
            return new SmokeNote(id, version, newName);
        }
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    // Removes the documents but keeps the collection, so the unique index created at startup stays.
    @BeforeEach
    void emptyTheCollection() {
        mongoTemplate.remove(new Query(), SmokeNote.class);
    }

    @Test
    void savedDocumentIsReadBack() {
        SmokeNote saved = mongoTemplate.save(new SmokeNote("n1", null, "first"));

        SmokeNote found = mongoTemplate.findById("n1", SmokeNote.class);

        assertEquals("first", found.name());
        assertEquals(saved.version(), found.version());
    }

    @Test
    void invalidDocumentIsNotSaved() {
        assertThrows(ConstraintViolationException.class, () -> mongoTemplate.save(new SmokeNote("n1", null, " ")));

        assertTrue(mongoTemplate.findAll(SmokeNote.class).isEmpty());
    }

    @Test
    void duplicateUniqueValueIsRefused() {
        mongoTemplate.save(new SmokeNote("n1", null, "same"));

        assertThrows(DuplicateKeyException.class, () -> mongoTemplate.save(new SmokeNote("n2", null, "same")));

        assertEquals(1, mongoTemplate.findAll(SmokeNote.class).size());
    }

    @Test
    void savingAStaleCopyIsRefused() {
        SmokeNote stale = mongoTemplate.save(new SmokeNote("n1", null, "first"));
        mongoTemplate.save(stale.withName("second"));

        assertThrows(OptimisticLockingFailureException.class, () -> mongoTemplate.save(stale.withName("third")));

        assertEquals("second", mongoTemplate.findById("n1", SmokeNote.class).name());
    }
}
