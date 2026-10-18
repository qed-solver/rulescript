package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SimplifyEqualsAnyTuple;
import org.qed.RuleBuilder;

import java.util.List;

import static org.qed.Backends.Calcite.Tests.RuleChecks.*;

public final class SimplifyEqualsAnyTupleTest {
    public static void runTest() {
        var rule = new SimplifyEqualsAnyTuple();
        rewritesPattern(rule);
        rewritesConcretePredicates(rule);
        rejectsNearMisses(rule);
        handBuiltMatchAndExpected();
        rejectsInnerJoin();
    }

    private static void handBuiltMatchAndExpected() {
        var schema = RuleBuilder.create();
        var sourceName = table(schema, 1, false, true);
        var tupleName = table(schema, 1, false, true);
        var b = schema.transform(CalciteRuntime::configure);
        RelNode source = b.scan(sourceName).build();
        RelNode tuple = b.scan(tupleName).build();
        RexNode lhs = b.getRexBuilder().makeInputRef(source, 0);
        RelNode input = org.apache.calcite.rel.logical.LogicalJoin.create(source, tuple,
                List.of(), b.call(SqlStdOperatorTable.EQUALS, lhs,
                        b.getRexBuilder().makeInputRef(tuple.getRowType().getFieldList().get(0).getType(), 1)),
                java.util.Set.of(), JoinRelType.SEMI);

        RexNode expectedCondition = b.in(tuple, List.of(b.getRexBuilder().makeInputRef(source, 0)));
        RelNode expected = LogicalFilter.create(source, expectedCondition);
        rewrites(new SimplifyEqualsAnyTuple(), input, expected);
    }

    private static void rejectsInnerJoin() {
        var schema = RuleBuilder.create();
        var sourceName = table(schema, 1, false, true);
        var tupleName = table(schema, 1, false, true);
        var b = schema.transform(CalciteRuntime::configure);
        b.scan(sourceName).scan(tupleName).join(JoinRelType.INNER,
                b.call(SqlStdOperatorTable.EQUALS, b.field(2, 0, 0), b.field(2, 1, 0)));
        doesNotApply(new SimplifyEqualsAnyTuple(), b.build());
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyEqualsAnyTuple test...");
        runTest();
    }
}
