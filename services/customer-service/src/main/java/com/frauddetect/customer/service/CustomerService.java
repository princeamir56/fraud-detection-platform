package com.frauddetect.customer.service;

import com.frauddetect.common.error.BusinessRuleException;
import com.frauddetect.common.error.ResourceNotFoundException;
import com.frauddetect.customer.domain.CustomerEntity;
import com.frauddetect.customer.domain.CustomerStatus;
import com.frauddetect.customer.dto.CustomerResponse;
import com.frauddetect.customer.dto.UpdateCustomerRequest;
import com.frauddetect.customer.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer profile management (Section 4 + Section 9). Registration itself lives in
 * {@link AuthService}; this service handles reads, profile edits and lifecycle status changes
 * (e.g. BLOCKED as a fraud response). {@code CLOSED} is terminal and cannot be reopened.
 */
@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CustomerResponse getById(String id) {
        return CustomerResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> list(CustomerStatus status, Pageable pageable) {
        Page<CustomerEntity> page = (status == null)
                ? repository.findAll(pageable)
                : repository.findByStatus(status, pageable);
        return page.map(CustomerResponse::from);
    }

    @Transactional
    public CustomerResponse update(String id, UpdateCustomerRequest request) {
        CustomerEntity customer = find(id);
        if (customer.getStatus() == CustomerStatus.CLOSED) {
            throw new BusinessRuleException("Customer " + id + " is CLOSED and cannot be modified");
        }
        customer.setFirstName(request.firstName());
        customer.setLastName(request.lastName());
        customer.setPhone(request.phone());
        log.info("Updated profile for customer {}", id);
        return CustomerResponse.from(customer);
    }

    /** Change the customer lifecycle status (e.g. BLOCKED as a fraud response, or CLOSED). */
    @Transactional
    public CustomerResponse changeStatus(String id, CustomerStatus status) {
        CustomerEntity customer = find(id);
        if (customer.getStatus() == CustomerStatus.CLOSED && status != CustomerStatus.CLOSED) {
            throw new BusinessRuleException("Customer " + id + " is CLOSED and cannot be reopened");
        }
        customer.setStatus(status);
        log.info("Customer {} status changed to {}", id, status);
        return CustomerResponse.from(customer);
    }

    private CustomerEntity find(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
    }
}
