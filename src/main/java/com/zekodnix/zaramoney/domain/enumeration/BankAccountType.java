package com.zekodnix.zaramoney.domain.enumeration;

/**
 * Kind of bank account.
 * <ul>
 *   <li>{@code CUSTOMER}: a user's wallet. Its balance can never go below 0 (DB check constraint).</li>
 *   <li>{@code SYSTEM}: an internal ZaraMoney account (e.g. promo/bonus). It has no user, can go negative
 *   (it represents money the company issued) and can never be a transfer receiver or sender for customers.</li>
 * </ul>
 */
public enum BankAccountType {
    CUSTOMER,
    SYSTEM,
}
