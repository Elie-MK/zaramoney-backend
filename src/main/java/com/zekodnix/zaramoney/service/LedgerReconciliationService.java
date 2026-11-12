package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.service.dto.ReconciliationReportDTO;
import com.zekodnix.zaramoney.service.dto.ReconciliationReportDTO.BalanceDrift;
import com.zekodnix.zaramoney.service.dto.ReconciliationReportDTO.ChainBreak;
import com.zekodnix.zaramoney.service.dto.ReconciliationReportDTO.UnbalancedTransaction;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily ledger reconciliation (read-only). Checks:
 * <ol>
 *   <li><b>unbalanced transactions</b>: Σ DEBIT != Σ CREDIT over the entries of a transaction</li>
 *   <li><b>balance drift</b>: an account's balance != {@code balance_after} of its latest entry (a balance was changed
 *       without a ledger entry)</li>
 *   <li><b>chain breaks</b>: for two consecutive entries of an account (by id; ids grow under the account row lock),
 *       previous {@code balance_after} ± amount != {@code balance_after}</li>
 *   <li><b>completed transactions without ledger</b> since {@code balance_after} tracking started</li>
 * </ol>
 * Each check is ONE SQL statement, so it reads a consistent snapshot even while transfers run.
 *
 * <p>Information only: balances that predate the ledger (accounts funded before every flow wrote entries) make
 * Σ balances != Σ (credits − debits). That difference is reported as {@code legacyUnledgeredTotal} / {@code legacyAccounts},
 * not as an issue; checks 2 and 3 still catch any NEW drift on those accounts.
 *
 * <p>Runs every day at 02:15 Kinshasa time ({@code application.reconciliation.cron}); logs ERROR and sets the gauge
 * {@code zaramoney.ledger.reconciliation.issues} when something is wrong. On demand:
 * {@code GET /api/admin/backoffice/reports/reconciliation}. Single instance assumed (id order per account).
 */
@Service
public class LedgerReconciliationService {

    static final int LIMIT = 100;

    private static final Logger LOG = LoggerFactory.getLogger(LedgerReconciliationService.class);

    private static final String UNBALANCED_TRANSACTIONS =
        """
        select t.transaction_reference,
               coalesce(sum(case when l.entry_type = 'DEBIT' then l.amount end), 0) as debits,
               coalesce(sum(case when l.entry_type = 'CREDIT' then l.amount end), 0) as credits
          from ledger_entry l
          join transaction_record t on t.id = l.transaction_id
         group by t.id, t.transaction_reference
        having coalesce(sum(case when l.entry_type = 'DEBIT' then l.amount end), 0)
            <> coalesce(sum(case when l.entry_type = 'CREDIT' then l.amount end), 0)
         order by t.id
        """;

    private static final String BALANCE_DRIFTS =
        """
        select a.account_number, a.balance, last.balance_after, last.id
          from bank_account a
          join lateral (select l.id, l.balance_after from ledger_entry l
                         where l.account_id = a.id order by l.id desc limit 1) last on true
         where last.balance_after is not null
           and last.balance_after <> a.balance
         order by a.account_number
        """;

    private static final String CHAIN_BREAKS =
        """
        select a.account_number, x.id, x.previous_balance_after, x.signed_amount, x.balance_after
          from (select l.id, l.account_id, l.balance_after,
                       case when l.entry_type = 'CREDIT' then l.amount else -l.amount end as signed_amount,
                       lag(l.balance_after) over (partition by l.account_id order by l.id) as previous_balance_after
                  from ledger_entry l) x
          join bank_account a on a.id = x.account_id
         where x.balance_after is not null
           and x.previous_balance_after is not null
           and x.previous_balance_after + x.signed_amount <> x.balance_after
         order by x.id
        """;

    private static final String TRACKING_SINCE = "select min(created_at) from ledger_entry where balance_after is not null";

    private static final String COMPLETED_WITHOUT_LEDGER =
        """
        select t.transaction_reference
          from transaction_record t
         where t.transaction_status = 'COMPLETED'
           and t.transaction_date >= ?
           and not exists (select 1 from ledger_entry l where l.transaction_id = t.id)
         order by t.id
        """;

    private static final String TOTALS =
        """
        select (select coalesce(sum(balance), 0) from bank_account) as total_balances,
               (select coalesce(sum(case when entry_type = 'CREDIT' then amount else -amount end), 0) from ledger_entry) as ledger_net,
               (select count(*) from bank_account a
                 where a.balance <> coalesce((select sum(case when l.entry_type = 'CREDIT' then l.amount else -l.amount end)
                                                from ledger_entry l where l.account_id = a.id), 0)) as legacy_accounts
        """;

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong lastIssueCount = new AtomicLong(0);

    public LedgerReconciliationService(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        Gauge.builder("zaramoney.ledger.reconciliation.issues", lastIssueCount, AtomicLong::get)
            .description("Issues found by the last ledger reconciliation (0 = books balance)")
            .register(meterRegistry);
    }

    @Scheduled(cron = "${application.reconciliation.cron:0 15 2 * * *}", zone = "Africa/Kinshasa")
    public void scheduledReconciliation() {
        try {
            reconcile();
        } catch (RuntimeException e) {
            LOG.error("RECONCILIATION could not run", e);
        }
    }

    @Transactional(readOnly = true)
    public ReconciliationReportDTO reconcile() {
        List<UnbalancedTransaction> unbalanced = jdbcTemplate.query(UNBALANCED_TRANSACTIONS, (rs, i) ->
            new UnbalancedTransaction(rs.getString(1), rs.getBigDecimal(2), rs.getBigDecimal(3))
        );
        List<BalanceDrift> drifts = jdbcTemplate.query(BALANCE_DRIFTS, (rs, i) ->
            new BalanceDrift(rs.getString(1), rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getLong(4))
        );
        List<ChainBreak> breaks = jdbcTemplate.query(CHAIN_BREAKS, (rs, i) ->
            new ChainBreak(rs.getString(1), rs.getLong(2), rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getBigDecimal(5))
        );
        Timestamp since = jdbcTemplate.queryForObject(TRACKING_SINCE, Timestamp.class);
        List<String> withoutLedger = since == null
            ? List.of()
            : jdbcTemplate.query(COMPLETED_WITHOUT_LEDGER, (rs, i) -> rs.getString(1), since);
        var totals = jdbcTemplate.queryForMap(TOTALS);
        BigDecimal totalBalances = (BigDecimal) totals.get("total_balances");
        BigDecimal ledgerNet = (BigDecimal) totals.get("ledger_net");
        long legacyAccounts = ((Number) totals.get("legacy_accounts")).longValue();

        long issues = (long) unbalanced.size() + drifts.size() + breaks.size() + withoutLedger.size();
        lastIssueCount.set(issues);
        var report = new ReconciliationReportDTO(
            Instant.now(),
            issues == 0,
            issues,
            cap(unbalanced),
            cap(drifts),
            cap(breaks),
            cap(withoutLedger),
            since == null ? null : since.toInstant(),
            totalBalances,
            ledgerNet,
            totalBalances.subtract(ledgerNet),
            legacyAccounts
        );
        if (issues == 0) {
            LOG.info(
                "RECONCILIATION ok (legacy unledgered total {} on {} accounts)",
                report.legacyUnledgeredTotal(),
                report.legacyAccounts()
            );
        } else {
            LOG.error(
                "RECONCILIATION FAILED: {} issue(s): unbalanced={} drifts={} chainBreaks={} withoutLedger={} — first: {} {} {} {}",
                issues,
                unbalanced.size(),
                drifts.size(),
                breaks.size(),
                withoutLedger.size(),
                first(unbalanced),
                first(drifts),
                first(breaks),
                first(withoutLedger)
            );
        }
        return report;
    }

    private static <T> List<T> cap(List<T> list) {
        return list.size() <= LIMIT ? list : List.copyOf(list.subList(0, LIMIT));
    }

    private static Object first(List<?> list) {
        return list.isEmpty() ? "-" : list.get(0);
    }
}
