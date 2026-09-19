package com.orangehrm.assessment.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public record EmployeeData(
        String firstName,
        String lastName,
        String username,
        String password,
        String employeeNumber,
    String customFieldValue) {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    /**
     * Builds an employee whose identifying fields are unique across concurrent runs.
     *
     * <p>A second-resolution timestamp alone is not enough. The CI matrix executes the smoke,
     * role-based and regression suites at the same time, and the regression suite runs classes
     * in parallel, so two calls landing in the same second is routine rather than unlikely.
     * Identical data collides on OrangeHRM's unique Employee Id and username, which surfaces as
     * a save rejection that looks like a product bug, and it also makes username-based row
     * lookup during cleanup ambiguous enough to delete another run's record.
     *
     * <p>The counter guarantees uniqueness within one JVM; the random block makes a clash
     * between separately started JVMs improbable.
     */
    public static EmployeeData unique() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String entropy = String.format("%02d%03d",
                SEQUENCE.getAndIncrement() % 100,
                ThreadLocalRandom.current().nextInt(1000));
        String uniqueSuffix = timestamp + entropy;

        return new EmployeeData(
                "Auto" + uniqueSuffix,
                "User" + uniqueSuffix,
                "autouser" + uniqueSuffix,
                "Orange@1234",
                // OrangeHRM pre-fills Employee Id with the next sequential number. On the shared
                // public demo that value frequently already exists, so the save is rejected with
                // "Employee Id already exists". Supplying our own keeps runs independent.
                uniqueSuffix.substring(uniqueSuffix.length() - 9),
            "CF" + uniqueSuffix);
    }

    public String fullName() {
        return firstName + " " + lastName;
    }
}