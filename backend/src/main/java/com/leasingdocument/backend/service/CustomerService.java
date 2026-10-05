package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.Customer;
import com.leasingdocument.backend.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;


    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }


    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }


    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id).orElse(null);
    }


    public Customer saveCustomer(Customer customer) {
        return customerRepository.save(customer);
    }


    public void deleteCustomer(Long id) {
        customerRepository.deleteById(id);
    }
}