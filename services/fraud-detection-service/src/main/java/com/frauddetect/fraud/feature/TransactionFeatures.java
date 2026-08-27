package com.frauddetect.fraud.feature;

/**
 * Flat, immutable feature vector computed for a single transaction from Cassandra behaviour/velocity
 * plus the transaction snapshot. Deliberately primitive so it maps cleanly onto the gRPC
 * {@code TransactionRiskFeatures} message and is cheap to feed to every rule evaluator.
 *
 * @param amount                   transaction amount (major units)
 * @param currency                 ISO-4217
 * @param transactionType          PURCHASE|WITHDRAWAL|...
 * @param countryCode              ISO-3166 alpha-2 where the transaction originated
 * @param merchantCategory         merchant category (nullable-safe: empty string if unknown)
 * @param txCountLastHour          count of the customer's transactions in the last 1h (incl. this)
 * @param txCountLast24h           count in the last 24h
 * @param amountSumLast24h         summed amount in the last 24h
 * @param avgAmount30d             customer's ~30d average transaction amount (baseline)
 * @param distinctCountriesLast24h distinct origin countries in the last 24h
 * @param newDevice                device never seen before for this customer
 * @param newMerchant              merchant category never seen before for this customer
 * @param failedTxLastHour         failed/declined attempts in the last hour
 * @param kmFromLastTx             great-circle distance from the previous transaction (km)
 * @param secondsSinceLastTx       dwell time since the previous transaction (s)
 * @param countryChanged           origin country differs from the customer's last known country
 */
public record TransactionFeatures(
        double amount,
        String currency,
        String transactionType,
        String countryCode,
        String merchantCategory,
        long txCountLastHour,
        long txCountLast24h,
        double amountSumLast24h,
        double avgAmount30d,
        long distinctCountriesLast24h,
        boolean newDevice,
        boolean newMerchant,
        long failedTxLastHour,
        double kmFromLastTx,
        long secondsSinceLastTx,
        boolean countryChanged
) {
    /** Implied speed (km/h) between the previous and current transaction; 0 when dwell is unknown. */
    public double impliedSpeedKmh() {
        if (secondsSinceLastTx <= 0 || kmFromLastTx <= 0) {
            return 0.0;
        }
        return kmFromLastTx / (secondsSinceLastTx / 3600.0);
    }
}
