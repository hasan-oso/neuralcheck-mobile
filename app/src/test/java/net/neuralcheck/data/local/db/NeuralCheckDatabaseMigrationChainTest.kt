package net.neuralcheck.data.local.db

import net.neuralcheck.data.local.db.NeuralCheckDatabase.Companion.ALL_MIGRATIONS
import net.neuralcheck.data.local.db.NeuralCheckDatabase.Companion.SCHEMA_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The migration chain registered with Room must reach the current schema
 * version without a gap. DatabaseModule keeps
 * `fallbackToDestructiveMigration()` as a safety net — a schema bump whose
 * migration is missing from [ALL_MIGRATIONS] would silently reach it and WIPE
 * a technician's saved reports on upgrade (the 2026-09-24 incident behind
 * DestructiveMigrationTracker). This test turns that into a build failure.
 */
class NeuralCheckDatabaseMigrationChainTest {

    @Test
    fun `migrations form a contiguous chain from 1 to SCHEMA_VERSION`() {
        val steps = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals(
            "one step per version, each ending where the next begins",
            (1 until SCHEMA_VERSION).map { it to it + 1 },
            steps
        )
    }

    @Test
    fun `schema version is 8 for fieldsVersion 68 and the 7 to 8 step is registered`() {
        assertEquals(8, SCHEMA_VERSION)
        assertTrue(ALL_MIGRATIONS.any { it.startVersion == 7 && it.endVersion == 8 })
    }

    @Test
    fun `the 7 to 8 object in the list is the documented MIGRATION_7_8 constant`() {
        assertTrue(ALL_MIGRATIONS.contains(NeuralCheckDatabase.MIGRATION_7_8))
        assertEquals(
            listOf("ALTER TABLE functional_results ADD COLUMN measurements TEXT DEFAULT NULL"),
            NeuralCheckDatabase.MIGRATION_7_8_SQL
        )
    }
}
