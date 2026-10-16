package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PruneInsertReturnCols;
import org.qed.RuleBuilder;

public class PruneInsertReturnColsTest {

    public static void runTest() {
        var rule = new PruneInsertReturnCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneInsertReturnCols rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var kept = (java.util.function.Supplier<RexNode>) () -> b.call(SqlStdOperatorTable.UNARY_MINUS, b.field(0));
        var dropped = (java.util.function.Supplier<RexNode>) () -> b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1));
        var before = b.scan(t).project(b.field(0), kept.get(), dropped.get())
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1))).build();
        var after = b.scan(t).project(b.field(0), kept.get())
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t).project(b.field(0), kept.get(), dropped.get())
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(2))).build());
        RuleChecks.doesNotApply(rule, b.scan(t).project(b.field(0), kept.get())
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running PruneInsertReturnCols test...");
        runTest();
    }
}
