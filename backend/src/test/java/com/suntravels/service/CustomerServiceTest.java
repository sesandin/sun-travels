package com.suntravels.service;

import com.suntravels.dto.CustomerRequest;
import com.suntravels.entity.Customer;
import com.suntravels.exception.CustomerNotFoundException;
import com.suntravels.exception.InvalidCustomerException;
import com.suntravels.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {
    @Mock CustomerRepository repository;

    @Test void createsPhoneOnlyEmailOnlyAndBothWhileNormalizing() {
        CustomerService service = serviceWithSavingIds();
        var phone = service.createCustomer(request(" Alice ", " +94 77 123 4567 ", " "));
        var email = service.createCustomer(request("Bob", null, " bob@example.com "));
        var both = service.createCustomer(request("Cara", "+44 (20) 1234-5678", "cara@example.com"));
        assertEquals("Alice", phone.name()); assertEquals("+94 77 123 4567", phone.phone()); assertNull(phone.email());
        assertNull(email.phone()); assertEquals("bob@example.com", email.email()); assertNotNull(both.phone());
        verify(repository, times(3)).save(any(Customer.class));
    }

    @Test void rejectsInvalidBusinessFieldsEvenOutsideMvcValidation() {
        CustomerService service = new CustomerService(repository);
        assertThrows(InvalidCustomerException.class, () -> service.createCustomer(request(" ", "+94771234567", null)));
        assertThrows(InvalidCustomerException.class, () -> service.createCustomer(request("Alice", " ", null)));
        assertThrows(InvalidCustomerException.class, () -> service.createCustomer(request("Alice", "letters", null)));
        assertThrows(InvalidCustomerException.class, () -> service.createCustomer(request("Alice", null, "bad-email")));
        assertThrows(InvalidCustomerException.class, () -> service.createCustomer(request("x".repeat(151), "+94771234567", null)));
        assertThrows(InvalidCustomerException.class, () -> service.createCustomer(request("Alice", "+" + "1".repeat(20), null)));
        verifyNoInteractions(repository);
    }

    @Test void retrievesUpdatesAndKeepsCustomerId() {
        Customer existing = customer(7, "Old", "+94111234567", null);
        when(repository.findById(7)).thenReturn(Optional.of(existing)); when(repository.save(existing)).thenReturn(existing);
        CustomerService service = new CustomerService(repository);
        assertEquals(7, service.getCustomer(7).customerId());
        var updated = service.updateCustomer(7, request(" New Name ", null, "new@example.com"));
        assertEquals(7, updated.customerId()); assertEquals("New Name", updated.name()); assertNull(updated.phone());
        verify(repository).save(existing);
    }

    @Test void unknownCustomerFailsWithoutInsert() {
        when(repository.findById(99)).thenReturn(Optional.empty());
        CustomerService service = new CustomerService(repository);
        assertThrows(CustomerNotFoundException.class, () -> service.getCustomer(99));
        assertThrows(CustomerNotFoundException.class, () -> service.updateCustomer(99, request("Name", "+94771234567", null)));
        verify(repository, never()).save(any());
    }

    @Test void searchesTextAndExactNumericIdWithBoundedPaging() {
        Customer row = customer(42, "Alice", "+94771234567", "alice@example.com");
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(1); return new PageImpl<>(List.of(row), pageable, 1);
        });
        CustomerService service = new CustomerService(repository);
        var result = service.searchCustomers(" 42 ", 0, 20);
        assertEquals(1, result.totalElements()); assertEquals(42, result.customers().get(0).customerId());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), page.capture()); assertEquals(20, page.getValue().getPageSize());
        assertThrows(InvalidCustomerException.class, () -> service.searchCustomers("", -1, 20));
        assertThrows(InvalidCustomerException.class, () -> service.searchCustomers("", 0, 51));
        assertThrows(InvalidCustomerException.class, () -> service.searchCustomers("x".repeat(151), 0, 20));
    }

    @Test void blankSearchUsesUnfilteredPagingAndSpecialCharactersRemainLiteral() {
        Customer row = customer(1, "100% Travel_Name", "+94771234567", null);
        when(repository.findAll(any(Pageable.class))).thenAnswer(invocation -> new PageImpl<>(List.of(row), invocation.getArgument(0), 1));
        CustomerService service = new CustomerService(repository);
        assertEquals(1, service.searchCustomers("  ", 0, 20).totalElements());
        verify(repository).findAll(any(Pageable.class));

        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(row)));
        assertEquals(1, service.searchCustomers("%_", 0, 20).totalElements());
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    private CustomerService serviceWithSavingIds() {
        when(repository.save(any(Customer.class))).thenAnswer(invocation -> { Customer c=invocation.getArgument(0); ReflectionTestUtils.setField(c,"customerId",1); return c; });
        return new CustomerService(repository);
    }
    private CustomerRequest request(String name,String phone,String email) { CustomerRequest r=new CustomerRequest();r.setName(name);r.setPhone(phone);r.setEmail(email);return r; }
    private Customer customer(int id,String name,String phone,String email) { Customer c=new Customer();ReflectionTestUtils.setField(c,"customerId",id);c.setName(name);c.setPhone(phone);c.setEmail(email);return c; }
}
