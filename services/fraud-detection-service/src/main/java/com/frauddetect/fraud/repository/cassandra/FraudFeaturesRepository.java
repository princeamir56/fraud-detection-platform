package com.frauddetect.fraud.repository.cassandra;

import com.frauddetect.fraud.domain.cassandra.FraudFeatures;
import com.frauddetect.fraud.domain.cassandra.VelocityKey;
import org.springframework.data.cassandra.repository.CassandraRepository;

/**
 * Append/read access to the persisted feature vectors. Insert-only on the hot path; reads are for
 * offline training/back-testing and decision explainability.
 */
public interface FraudFeaturesRepository extends CassandraRepository<FraudFeatures, VelocityKey> {
}
