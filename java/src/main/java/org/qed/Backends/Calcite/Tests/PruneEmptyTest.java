package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.PruneEmpty;
import org.qed.RuleBuilder;

import java.util.List;

public class PruneEmptyTest {

    public static void runTest() {
        var rule = new PruneEmpty();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneEmpty rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = org.apache.calcite.rel.logical.LogicalFilter.create(b.scan(t).empty().build(),
                RuleChecks.gt(b, b.scan(t).field(0), 5));
        var after = b.scan(t).empty().build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t).filter(RuleChecks.gt(b, b.field(0), 5)).build());
        RuleChecks.doesNotApply(rule, b.scan(t).empty().project(b.call(SqlStdOperatorTable.PLUS, b.field(0),
                b.literal(1))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running PruneEmpty test...");
        runTest();
    }
}
