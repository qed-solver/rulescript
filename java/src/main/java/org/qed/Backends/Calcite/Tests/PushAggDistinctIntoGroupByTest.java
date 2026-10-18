package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.PushAggDistinctIntoGroupBy;
import org.qed.RuleBuilder;
import org.apache.calcite.rel.core.AggregateCall;
import java.util.List;

public class PushAggDistinctIntoGroupByTest {
    public static void main(String[] args) {
        System.out.println("Running PushAggDistinctIntoGroupBy test...");
        runTest();
    }

    public static void runTest() {
        var rule = new PushAggDistinctIntoGroupBy();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushAggDistinctIntoGroupBy rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, true);
        var input = builder.scan(table).build();
        var rex = builder.getRexBuilder();
        var base = rex.makeInputRef(input, 0);
        var project = LogicalProject.create(input, List.of(), List.of(base,
                rex.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.PLUS, base, rex.makeExactLiteral(java.math.BigDecimal.ONE)),
                rex.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.MULTIPLY, base, rex.makeExactLiteral(java.math.BigDecimal.valueOf(2)))), List.of("a", "b", "c"));
        var aggregateCall = AggregateCall.create(org.apache.calcite.sql.fun.SqlStdOperatorTable.COUNT, true, false,
                false, List.of(2), -1, null, RelCollations.EMPTY, 2, project, null, "f");
        var before = LogicalAggregate.create(project, List.of(), ImmutableBitSet.of(0, 1), null, List.of(aggregateCall));
        var plain = RuleChecks.ruleBuilder(builder);
        plain.push(input);
        plain.aggregate(plain.groupKey(project.getProjects()));
        var inner = plain.build();
        plain.push(inner);
        plain.aggregate(plain.groupKey(plain.field(0), plain.field(1)),
                plain.aggregateCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.COUNT, false, null, "f", plain.field(2)));
        var expected = plain.build();
        RuleChecks.rewrites(rule, before, expected);
        var otherTable = RuleChecks.table(builder, 2, false, true);
        var otherScan = builder.scan(otherTable).build();
        var otherRex = builder.getRexBuilder();
        var otherBase = otherRex.makeInputRef(otherScan, 0);
        var nullableInput = LogicalProject.create(otherScan, List.of(), List.of(otherBase,
                otherRex.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.PLUS, otherBase, otherRex.makeExactLiteral(java.math.BigDecimal.ONE)),
                otherRex.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.MULTIPLY, otherBase, otherRex.makeExactLiteral(java.math.BigDecimal.valueOf(2)))), List.of("a", "b", "c"));
        var nullableCall = AggregateCall.create(org.apache.calcite.sql.fun.SqlStdOperatorTable.COUNT, true, false,
                false, List.of(2), -1, null, RelCollations.EMPTY, 2, nullableInput, null, "f");
        RuleChecks.doesNotApply(rule, LogicalAggregate.create(nullableInput, List.of(), ImmutableBitSet.of(0, 1), null,
                List.of(nullableCall)));
    }
}
