package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.PullOutVariantExtractions;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public class PullOutVariantExtractionsTest {
    public static void runTest() {
        var rule = new PullOutVariantExtractions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PullOutVariantExtractions rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        var input = builder.scan(table).build();
        var valueType = RelType.fromString("INTEGER", true);
        SqlOperator extraction = builder.genericProjectionOp("ve", valueType);
        RexNode value = builder.call(extraction, builder.getRexBuilder().makeInputRef(input, 1));
        RelNode project = org.apache.calcite.rel.logical.LogicalProject.create(input, List.of(),
                List.of(builder.getRexBuilder().makeInputRef(input, 0), value), List.of("key", "extracted"));
        SqlAggFunction aggregate = builder.genericAggregateOp("f", valueType);
        RelNode plan = LogicalAggregate.create(project, List.of(), ImmutableBitSet.of(0), null,
                List.of(org.apache.calcite.rel.core.AggregateCall.create(aggregate, false, false, false,
                        List.of(1), -1, null, org.apache.calcite.rel.RelCollations.EMPTY, 1, project, null, "f")));

        var plain = RuleChecks.ruleBuilder(builder);
        var plainInput = plain.scan(table).build();
        RexNode plainValue = plain.getRexBuilder().makeCall(extraction,
                plain.getRexBuilder().makeInputRef(plainInput, 1));
        RelNode plainProject = org.apache.calcite.rel.logical.LogicalProject.create(plainInput, List.of(),
                List.of(plain.getRexBuilder().makeInputRef(plainInput, 0), plainValue), List.of("key", "_ve0"));
        RelNode expected = LogicalAggregate.create(plainProject, List.of(), ImmutableBitSet.of(0), null,
                List.of(org.apache.calcite.rel.core.AggregateCall.create(aggregate, false, false, false,
                        List.of(1), -1, null, org.apache.calcite.rel.RelCollations.EMPTY, 1, plainProject, null, "f")));
        RuleChecks.rewrites(rule, plan, expected);

        RelNode nearMiss = LogicalAggregate.create(project, List.of(), ImmutableBitSet.of(0, 1), null,
                List.of(org.apache.calcite.rel.core.AggregateCall.create(aggregate, false, false, false,
                        List.of(1), -1, null, org.apache.calcite.rel.RelCollations.EMPTY, 2, project, null, "f")));
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running PullOutVariantExtractions test...");
        runTest();
    }
}
