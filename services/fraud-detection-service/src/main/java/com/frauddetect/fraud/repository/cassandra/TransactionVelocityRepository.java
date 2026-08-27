package com.frauddetect.fraud.repository.cassandra;

import com.frauddetect.fraud.domain.cassandra.TransactionVelocity;
import com.frauddetect.fraud.domain.cassandra.VelocityKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Reads the recent velocity window for a customer. The explicit CQL keeps the query legal for
 * Cassandra: {@code customer_id} fixes the first partition component, {@code day_bucket IN (...)}
 * targets the (typically two) buckets covering the window, and {@code event_time >=} is a clustering
 * range scan returning rows newest-first. In-memory the caller derives counts/sums/distincts.
 */
public interface TransactionVelocityRepository extends CassandraRepository<TransactionVelocity, VelocityKey> {

    @Query("SELECT * FROM transaction_velocity "
            + "WHERE customer_id = :customerId AND day_bucket IN :buckets AND event_time >= :since")
    List<TransactionVelocity> findRecent(@Param("customerId") String customerId,
                                         @Param("buckets") List<String> buckets,
                                         @Param("since") Instant since);
}
