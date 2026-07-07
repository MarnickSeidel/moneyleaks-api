package com.marnickseidel.moneyleaks;

import com.marnickseidel.moneyleaks.banking.provider.enablebanking.EnableBankingProperties;
import com.marnickseidel.moneyleaks.banking.provider.gocardless.GoCardlessProperties;
import com.marnickseidel.moneyleaks.config.RecurringDetectionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        RecurringDetectionProperties.class,
        GoCardlessProperties.class,
        EnableBankingProperties.class
})
public class MoneyLeaksApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoneyLeaksApplication.class, args);
    }
}
