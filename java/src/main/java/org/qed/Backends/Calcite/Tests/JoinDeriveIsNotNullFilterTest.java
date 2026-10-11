package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.JoinDeriveIsNotNullFilter;
import org.qed.RuleBuilder;

import java.util.List;

public class JoinDeriveIsNotNullFilterTest {

    public static void runTest() {
        var rule = new JoinDeriveIsNotNullFilter();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinDeriveIsNotNullFilter rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var cond = (java.util.function.Function<RelBuilder, RexNode>) q -> q.and(
                RuleChecks.eq(q, q.field(2, 0, 0), q.field(2, 1, 0)),
                q.call(SqlStdOperatorTable.LESS_THAN, q.field(2, 0, 0), q.field(2, 1, 0)));
        var before = b.scan(l).scan(r).join(JoinRelType.INNER, cond.apply(b)).build();
        b.scan(l).filter(b.isNotNull(b.field(0))).scan(r).filter(b.isNotNull(b.field(0)));
        var after = b.join(JoinRelType.INNER, cond.apply(b)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(l).scan(r).join(JoinRelType.INNER,
                b.and(b.call(SqlStdOperatorTable.LESS_THAN, b.field(2, 0, 0), b.field(2, 1, 0)),
                        b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 0, 0), b.field(2, 1, 0)))).build());
        RuleChecks.doesNotApply(rule, b.scan(l).scan(r).join(JoinRelType.LEFT, cond.apply(b)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running JoinDeriveIsNotNullFilter test...");
        runTest();
    }
}
