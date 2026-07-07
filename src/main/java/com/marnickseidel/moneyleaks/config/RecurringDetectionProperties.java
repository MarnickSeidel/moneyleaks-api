package com.marnickseidel.moneyleaks.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moneyleaks.recurring")
public class RecurringDetectionProperties {

    private int minOccurrences = 2;
    private int amountTolerancePercent = 5;
    private int monthlyMinDays = 26;
    private int monthlyMaxDays = 35;
    private int yearlyMinDays = 350;
    private int yearlyMaxDays = 380;

    public int getMinOccurrences() {
        return minOccurrences;
    }

    public void setMinOccurrences(int minOccurrences) {
        this.minOccurrences = minOccurrences;
    }

    public int getAmountTolerancePercent() {
        return amountTolerancePercent;
    }

    public void setAmountTolerancePercent(int amountTolerancePercent) {
        this.amountTolerancePercent = amountTolerancePercent;
    }

    public int getMonthlyMinDays() {
        return monthlyMinDays;
    }

    public void setMonthlyMinDays(int monthlyMinDays) {
        this.monthlyMinDays = monthlyMinDays;
    }

    public int getMonthlyMaxDays() {
        return monthlyMaxDays;
    }

    public void setMonthlyMaxDays(int monthlyMaxDays) {
        this.monthlyMaxDays = monthlyMaxDays;
    }

    public int getYearlyMinDays() {
        return yearlyMinDays;
    }

    public void setYearlyMinDays(int yearlyMinDays) {
        this.yearlyMinDays = yearlyMinDays;
    }

    public int getYearlyMaxDays() {
        return yearlyMaxDays;
    }

    public void setYearlyMaxDays(int yearlyMaxDays) {
        this.yearlyMaxDays = yearlyMaxDays;
    }
}
