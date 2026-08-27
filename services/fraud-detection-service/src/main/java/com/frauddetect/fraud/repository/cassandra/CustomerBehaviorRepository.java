package com.frauddetect.fraud.repository.cassandra;

import com.frauddetect.fraud.domain.cassandra.CustomerBehavior;
import org.springframework.data.cassandra.repository.CassandraRepository;

/**
 * Point-read/upsert access to the per-customer behavioural profile. Keyed by {@code customer_id}.
 */
public interface CustomerBehaviorRepository extends CassandraRepository<CustomerBehavior, String> {
}
