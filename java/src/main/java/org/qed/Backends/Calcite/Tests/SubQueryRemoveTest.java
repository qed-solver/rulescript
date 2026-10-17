package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.logical.LogicalTableScan;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SubQueryRemove;
import org.qed.RuleBuilder;

import java.util.List;

public class SubQueryRemoveTest {
    public static void runTest() {
        var rule = new SubQueryRemove();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        handBuiltExistsRewrite(rule);
        rejectsNonPositiveExists();
    }

    private static void handBuiltExistsRewrite(SubQueryRemove rule) {
        var qedBuilder = RuleBuilder.create();
        var outerName = RuleChecks.table(qedBuilder, 1, false, false);
        var innerName = RuleChecks.table(qedBuilder, 1, false, false);
        var builder = RuleChecks.ruleBuilder(qedBuilder);
        RelNode outer = builder.scan(outerName).build();
        RelNode inner = builder.scan(innerName).build();
        var subquery = RexSubQuery.exists(inner);
        RelNode source = LogicalFilter.create(outer, subquery);

        var expectedQBuilder = RuleBuilder.create();
        RuleChecks.table(expectedQBuilder, 1, false, false);
        RuleChecks.table(expectedQBuilder, 1, false, false);
        var expectedBuilder = RuleChecks.ruleBuilder(expectedQBuilder);
        // Build the expected semijoin-style existence test without using the rule's RelRN.after().
        RelNode expectedInner = expectedBuilder.scan(innerName).build();
        RelNode trueProjection = LogicalProject.create(expectedInner, List.of(),
                List.of(expectedBuilder.getRexBuilder().makeLiteral(true)),
                expectedBuilder.getTypeFactory().builder().add("$f0", org.apache.calcite.sql.type.SqlTypeName.BOOLEAN)
                        .build());
        RelNode distinct = LogicalAggregate.create(trueProjection, List.of(), ImmutableBitSet.of(0), null, List.of());
        RelNode expectedOuter = expectedBuilder.scan(outerName).build();
        RelNode joined = org.apache.calcite.rel.logical.LogicalJoin.create(expectedOuter, distinct,
                List.of(), expectedBuilder.getRexBuilder().makeLiteral(true), java.util.Set.of(),
                org.apache.calcite.rel.core.JoinRelType.INNER);
        RelNode expected = LogicalProject.create(joined, List.of(),
                List.of(expectedBuilder.getRexBuilder().makeInputRef(joined, 0)),
                expectedOuter.getRowType());
        RuleChecks.rewrites(rule, source, expected);
    }

    private static void rejectsNonPositiveExists() {
        var qedBuilder = RuleBuilder.create();
        var outerName = RuleChecks.table(qedBuilder, 1, false, false);
        var innerName = RuleChecks.table(qedBuilder, 1, false, false);
        var builder = RuleChecks.ruleBuilder(qedBuilder);
        RelNode outer = builder.scan(outerName).build();
        RelNode inner = builder.scan(innerName).build();
        var exists = RexSubQuery.exists(inner);
        var notExists = builder.getRexBuilder().makeCall(SqlStdOperatorTable.NOT, exists);
        RuleChecks.doesNotApply(new SubQueryRemove(), LogicalFilter.create(outer, notExists));
    }

    public static void main(String[] args) {
        System.out.println("Running SubQueryRemove test...");
        runTest();
    }
}
