package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.NormalizeInConst;
import org.qed.RuleBuilder;

public class NormalizeInConstTest {

    public static void runTest() {
        var rule = new NormalizeInConst();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(NormalizeInConst rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 3, false);
        var plain = RuleChecks.ruleBuilder(builder);
        var rx = plain.getRexBuilder();
        var scanned = plain.scan(table).build();
        var eqA = rx.makeCall(SqlStdOperatorTable.EQUALS, rx.makeInputRef(scanned, 0), rx.makeInputRef(scanned, 1));
        var eqB = rx.makeCall(SqlStdOperatorTable.EQUALS, rx.makeInputRef(scanned, 0), rx.makeInputRef(scanned, 2));
        var before = LogicalFilter.create(scanned, rx.makeCall(SqlStdOperatorTable.OR, eqA, eqB, eqA, eqB));
        var after = plain.scan(table)
                .filter(plain.or(eqA, eqB))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var pair = plain.scan(table)
                .filter(plain.or(eqA, eqB))
                .build();
        RuleChecks.doesNotApply(rule, pair);
    }

    public static void main(String[] args) {
        System.out.println("Running NormalizeInConst test...");
        runTest();
    }
}
