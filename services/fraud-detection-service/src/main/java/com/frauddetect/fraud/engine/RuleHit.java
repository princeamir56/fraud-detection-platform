package com.frauddetect.fraud.engine;

/**
 * A single fired rule and the points it contributed.
 *
 * @param ruleCode    stable code of the rule (e.g. {@code LARGE_AMOUNT})
 * @param description human-readable reason (surfaced to analysts and in the alert)
 * @param weight      the rule's configured maximum weight
 * @param points      points actually awarded for this transaction ({@code 0 < points <= weight})
 */
public record RuleHit(String ruleCode, String description, int weight, int points) {
}
