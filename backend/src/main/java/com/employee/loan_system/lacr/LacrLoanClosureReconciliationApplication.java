package com.employee.loan_system.lacr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class LacrLoanClosureReconciliationApplication {

    public static void main(String[] args) {
        SpringApplication.run(LacrLoanClosureReconciliationApplication.class, args);
    }
}
