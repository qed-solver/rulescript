package org.qed.Backends.Calcite.Tests;

import com.google.common.collect.ImmutableList;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RRuleInstances.PruneExplainCols;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class PruneExplainColsTest {
    public static void runTest() {
        var rule = new PruneExplainCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RRule rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        var input = builder.scan(table);
        var needed = input.field(0);
        SqlOperator output = builder.genericProjectionOp("OrdinaryExplainOutput",
                new RelType.BaseType(org.apache.calcite.sql.type.SqlTypeName.INTEGER, true));
        var before = input.project(builder.call(output, needed)).build();
        var source = builder.scan(table).build();
        var pruned = LogicalProject.create(source, ImmutableList.of(),
                ImmutableList.of(builder.getRexBuilder().makeInputRef(source, 0)), ImmutableList.of("Table_Column_0"));
        var after = LogicalProject.create(pruned, ImmutableList.of(),
                ImmutableList.of(builder.getRexBuilder().makeCall(output, builder.getRexBuilder().makeInputRef(pruned, 0))),
                ImmutableList.of("$f0"));
        RuleChecks.rewrites(rule, before, after);
        var usedDiscarded = builder.scan(table).project(builder.call(output, builder.field(1))).build();
        RuleChecks.doesNotApply(rule, usedDiscarded);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
