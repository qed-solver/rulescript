package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.rex.RexNode;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.PushSelectIntoOrdinality;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public class PushSelectIntoOrdinalityTest {
    public static void runTest() {
        var rule = new PushSelectIntoOrdinality();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static RexNode ref(org.apache.calcite.tools.RelBuilder builder, org.apache.calcite.rel.RelNode input,
                               int index) {
        return builder.getRexBuilder().makeInputRef(input.getRowType().getFieldList().get(index).getType(), index);
    }

    private static void ordinaryTables(PushSelectIntoOrdinality rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        var ordinality = builder.genericProjectionOp("ordinality", RelType.fromString("INTEGER", true));
        var source = builder.scan(table).build();
        var beforeProject = LogicalProject.create(source, List.of(),
                List.of(ref(builder, source, 0), ref(builder, source, 1),
                        builder.call(ordinality, ref(builder, source, 0), ref(builder, source, 1))),
                List.of("c0", "c1", "ord"));
        var before = LogicalFilter.create(beforeProject,
                builder.call(SqlStdOperatorTable.GREATER_THAN, ref(builder, beforeProject, 0), builder.literal(10)));

        var plain = RuleChecks.ruleBuilder(builder);
        var expectedSource = plain.push(source).build();
        var expectedFilter = LogicalFilter.create(expectedSource,
                plain.call(SqlStdOperatorTable.GREATER_THAN, ref(plain, expectedSource, 0), plain.literal(10)));
        var expected = LogicalProject.create(expectedFilter, List.of(),
                List.of(ref(plain, expectedFilter, 0), ref(plain, expectedFilter, 1),
                        plain.call(ordinality, ref(plain, expectedFilter, 0), ref(plain, expectedFilter, 1))),
                List.of("c0", "c1", "ord"));
        RuleChecks.rewrites(rule, before, expected);

        var wider = RuleChecks.table(builder, 3, false);
        var widerSource = builder.scan(wider).build();
        var nearMissProject = LogicalProject.create(widerSource, List.of(),
                List.of(ref(builder, widerSource, 0), ref(builder, widerSource, 1),
                        builder.call(ordinality, ref(builder, widerSource, 0), ref(builder, widerSource, 1))),
                List.of("c0", "c1", "ord"));
        var nearMiss = LogicalFilter.create(nearMissProject,
                builder.call(SqlStdOperatorTable.GREATER_THAN, ref(builder, nearMissProject, 0), builder.literal(10)));
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running PushSelectIntoOrdinality test...");
        runTest();
    }
}
