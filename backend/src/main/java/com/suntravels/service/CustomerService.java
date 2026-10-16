package com.suntravels.service;

import com.suntravels.dto.*;
import com.suntravels.entity.Customer;
import com.suntravels.exception.CustomerNotFoundException;
import com.suntravels.exception.InvalidCustomerException;
import com.suntravels.repository.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class CustomerService {
    public static final int MAX_PAGE_SIZE = 50;
    // Choose the first interior domain dot deterministically. Possessive quantifiers
    // preserve the existing accepted characters without retrying every possible split.
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]++@[^\\s@][^\\s@.]*+\\.[^\\s@]++$");
    private static final Pattern PHONE = Pattern.compile("^(?=.*\\d)[+0-9(). \\-]{5,20}$");

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public CustomerPageResponse searchCustomers(String rawQuery, int page, int size) {
        if (page < 0) throw new InvalidCustomerException("Page must be zero or greater");
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidCustomerException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        String query = normalize(rawQuery);
        if (query != null && query.length() > 150) {
            throw new InvalidCustomerException("Search text must be at most 150 characters");
        }
        Integer exactId = parsePositiveInteger(query);
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "name").and(Sort.by("customerId")));
        Page<Customer> result = query == null ? repository.findAll(pageable) :
                repository.findAll(searchSpecification(query, exactId), pageable);
        if (page > 0 && result.isEmpty()) {
            pageable = PageRequest.of(Math.max(0, result.getTotalPages() - 1), size, pageable.getSort());
            result = query == null ? repository.findAll(pageable) :
                    repository.findAll(searchSpecification(query, exactId), pageable);
        }
        return new CustomerPageResponse(result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public CustomerResponse getCustomer(Integer id) {
        return toResponse(find(id));
    }

    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        Customer customer = new Customer();
        apply(customer, request);
        return toResponse(repository.save(customer));
    }

    @Transactional
    public CustomerResponse updateCustomer(Integer id, CustomerRequest request) {
        Customer customer = find(id);
        apply(customer, request);
        return toResponse(repository.save(customer));
    }

    private Customer find(Integer id) {
        return repository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }

    private void apply(Customer customer, CustomerRequest request) {
        if (request == null) throw new InvalidCustomerException("Customer details are required");
        String name = normalize(request.getName());
        String phone = normalize(request.getPhone());
        String email = normalize(request.getEmail());
        if (name == null) throw new InvalidCustomerException("Customer name is required");
        if (name.length() > 150) throw new InvalidCustomerException("Name must be at most 150 characters");
        if (phone == null && email == null) {
            throw new InvalidCustomerException("Provide at least a phone number or email address");
        }
        if (phone != null && !PHONE.matcher(phone).matches()) {
            throw new InvalidCustomerException(
                    "Phone must use 5 to 20 international phone characters and include a digit");
        }
        if (email != null && (email.length() > 150 || !EMAIL.matcher(email).matches())) {
            throw new InvalidCustomerException("Provide a valid email address of at most 150 characters");
        }
        customer.setName(name);
        customer.setPhone(phone);
        customer.setEmail(email);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Integer parsePositiveInteger(String value) {
        if (value == null || !value.matches("[1-9]\\d*")) return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Specification<Customer> searchSpecification(String query, Integer exactId) {
        String pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
        return (root, criteria, builder) -> {
            List<jakarta.persistence.criteria.Predicate> matches = new ArrayList<>();
            if (exactId != null) matches.add(builder.equal(root.get("customerId"), exactId));
            matches.add(builder.like(builder.lower(root.get("name")), pattern, '\\'));
            matches.add(builder.like(builder.lower(root.get("phone")), pattern, '\\'));
            matches.add(builder.like(builder.lower(root.get("email")), pattern, '\\'));
            return builder.or(matches.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getCustomerId(), c.getName(), c.getPhone(), c.getEmail());
    }
}
