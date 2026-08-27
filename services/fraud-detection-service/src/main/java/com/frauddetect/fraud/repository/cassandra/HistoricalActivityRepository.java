package com.frauddetect.fraud.repository.cassandra;

import com.frauddetect.fraud.domain.cassandra.HistoricalActivity;
import com.frauddetect.fraud.domain.cassandra.VelocityKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Durable per-customer activity history for investigations. Range query returns a customer's recent
 * activity (newest-first) across the requested day buckets.
 */
public interface HistoricalActivityRepository extends CassandraRepository<HistoricalActivity, VelocityKey> {

    @Query("SELECT * FROM historical_activity "
            + "WHERE customer_id = :customerId AND day_bucket IN :buckets LIMIT :limit")
    List<HistoricalActivity> findRecent(@Param("customerId") String customerId,
                                        @Param("buckets") List<String> buckets,
                                        @Param("limit") int limit);
}
