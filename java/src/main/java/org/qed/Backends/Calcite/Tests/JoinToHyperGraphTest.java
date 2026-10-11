package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.JoinToHyperGraph;
import org.qed.RuleBuilder;

import java.util.List;

public class JoinToHyperGraphTest {

    public static void runTest() {
        var rule = new JoinToHyperGraph();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinToHyperGraph rule) {
        var base = RuleBuilder.create();
        var ta = RuleChecks.table(base, 1, false);
        var tb = RuleChecks.table(base, 1, false);
        var tc = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var leftDeep = (java.util.function.Function<JoinRelType, RelBuilder>) top -> {
            b.scan(ta).scan(tb).join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
            return b.scan(tc).join(top, b.and(
                    b.call(SqlStdOperatorTable.LESS_THAN, b.field(2, 0, 0), b.field(2, 1, 0)),
                    b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 0, 1), b.field(2, 1, 0))));
        };
        var before = leftDeep.apply(JoinRelType.INNER).build();

        b.scan(ta).scan(tb).scan(tc).join(JoinRelType.INNER,
                b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 0, 0), b.field(2, 1, 0)));
        var after = b.join(JoinRelType.INNER, b.and(RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)),
                b.call(SqlStdOperatorTable.LESS_THAN, b.field(2, 0, 0), b.field(2, 1, 1)))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, leftDeep.apply(JoinRelType.LEFT).build());
    }

    public static void main(String[] args) {
        System.out.println("Running JoinToHyperGraph test...");
        runTest();
    }
}
