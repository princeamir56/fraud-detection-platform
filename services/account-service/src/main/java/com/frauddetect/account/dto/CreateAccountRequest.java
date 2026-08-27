package com.frauddetect.account.dto;

import com.frauddetect.account.domain.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body for opening an account (Section 4). Bean Validation runs before any business logic. The
 * account number is optional — if omitted the service generates one — but when supplied must be a
 * plausible alphanumeric identifier.
 *
 * @param customerId      owning customer id
 * @param accountNumber   optional external account number (generated if blank)
 * @param type            account type
 * @param currency        ISO-4217 currency code (upper-case)
 * @param initialBalance  opening balance (>= 0)
 * @param creditLimit     overdraft allowance for CREDIT accounts (>= 0; ignored for others)
 */
public record CreateAccountRequest(

        @NotBlank
        @Size(max = 64)
        String customerId,

        @Size(max = 34)
        @Pattern(regexp = "[A-Za-z0-9-]*", message = "accountNumber may contain only letters, digits and hyphens")
        String accountNumber,

        @NotNull
        AccountType type,

        @NotBlank
        @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter ISO-4217 code")
        String currency,

        @NotNull
        @DecimalMin(value = "0.00", message = "initialBalance cannot be negative")
        @Digits(integer = 15, fraction = 4)
        BigDecimal initialBalance,

        @DecimalMin(value = "0.00", message = "creditLimit cannot be negative")
        @Digits(integer = 15, fraction = 4)
        BigDecimal creditLimit
) {
}
