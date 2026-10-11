package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.MergeSelectInnerJoin;
import org.qed.RuleBuilder;

public class MergeSelectInnerJoinTest {

    public static void runTest() {
        var rule = new MergeSelectInnerJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(MergeSelectInnerJoin rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        b.scan(l).scan(r);
        var on = b.call(SqlStdOperatorTable.EQUALS, b.field(2, 0, 0), b.field(2, 1, 0));
        var above = b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 0, 0), b.literal(1));
        var before = b.join(JoinRelType.INNER, on).filter(above).build();

        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(l).scan(r).join(JoinRelType.INNER,
                plain.and(plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 0, 0), plain.field(2, 1, 0)),
                        plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(2, 0, 0), plain.literal(1)))).build();
        RuleChecks.rewrites(rule, before, after);

        var noFilter = RuleChecks.ruleBuilder(builder);
        noFilter.scan(l).scan(r);
        var joinOnly = noFilter.join(JoinRelType.INNER,
                noFilter.call(SqlStdOperatorTable.EQUALS, noFilter.field(2, 0, 0), noFilter.field(2, 1, 0))).build();
        RuleChecks.doesNotApply(rule, joinOnly);
    }

    public static void main(String[] args) {
        System.out.println("Running MergeSelectInnerJoin test...");
        runTest();
    }
}
