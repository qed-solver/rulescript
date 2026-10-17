package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SimplifyOrFalse;

import static org.qed.Backends.Calcite.Tests.RuleChecks.*;

public class SimplifyOrFalseTest {
    public static void runTest() {
        var rule = new SimplifyOrFalse();
        rewritesPattern(rule);
        rewritesConcretePredicates(rule);
        rejectsNearMisses(rule);

        // Independent hand-built plan: OR(a > 7, FALSE) should retain exactly a > 7.
        var rb = org.qed.RuleBuilder.create();
        var table = table(rb, 1, false, true);
        RelBuilder b = ruleBuilder(rb);
        b.scan(table);
        RexNode a = b.field(0);
        RexNode predicate = gt(b, a, 7);
        RexNode condition = b.call(SqlStdOperatorTable.OR, predicate, b.literal(false));
        RelNode input = LogicalFilter.create(b.build(), condition);
        var expectedBuilder = org.qed.RuleBuilder.create();
        var expectedTable = table(expectedBuilder, 1, false, true);
        RelBuilder e = ruleBuilder(expectedBuilder);
        e.scan(expectedTable);
        e.filter(gt(e, e.field(0), 7));
        RelNode expected = e.build();
        rewrites(rule, input, expected);

        // OR(a > 7, TRUE) is a distinct predicate and must not match.
        var missBuilder = org.qed.RuleBuilder.create();
        var missTable = table(missBuilder, 1, false, true);
        RelBuilder m = ruleBuilder(missBuilder);
        m.scan(missTable);
        RexNode missPred = gt(m, m.field(0), 7);
        RelNode trueDisjunct = LogicalFilter.create(m.build(), m.call(SqlStdOperatorTable.OR,
                missPred, m.literal(true)));
        doesNotApply(rule, trueDisjunct);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyOrFalse test...");
        runTest();
    }
}
