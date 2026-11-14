package net.neuralcheck.domain.scoring

import net.neuralcheck.domain.model.ScanReport

/**
 * ScoringEngine interface as defined in ADR section 1.9 and 8.14.
 *   - RuleBasedScoringEngine: deterministic local scoring (datacollect flavor).
 *   - AiScoringEngine: remote analysis via the Firebase Functions callable SDK
 *     (launch flavor). Needs to suspend for the network call, which is why
 *     the contract is `suspend`. The rule-based implementation runs
 *     synchronously in its body — `suspend` adds no cost when the
 *     implementation does not actually suspend.
 */
interface ScoringEngine {
    suspend fun calculateScore(scanReport: ScanReport): ScoringResult
}
