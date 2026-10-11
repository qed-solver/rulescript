package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.RejectNullsProject;
import org.qed.RuleBuilder;

import java.util.List;

public class RejectNullsProjectTest {

    public static void runTest() {
        var rule = new RejectNullsProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RejectNullsProject rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var before = builder.scan(table)
                .project(List.<RexNode>of(
                        builder.call(SqlStdOperatorTable.UNARY_MINUS, builder.field(0)),
                        builder.field(0)), List.of(), true)
                .filter(builder.call(SqlStdOperatorTable.IS_NOT_NULL, builder.field(0)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table)
                .filter(plain.call(SqlStdOperatorTable.IS_NOT_NULL, plain.field(0)))
                .project(List.<RexNode>of(
                        plain.call(SqlStdOperatorTable.UNARY_MINUS, plain.field(0)),
                        plain.field(0)))
                .filter(plain.call(SqlStdOperatorTable.IS_NOT_NULL, plain.field(0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nullFilter = builder.scan(table)
                .project(List.<RexNode>of(
                        builder.call(SqlStdOperatorTable.UNARY_MINUS, builder.field(0)),
                        builder.field(0)), List.of(), true)
                .filter(builder.call(SqlStdOperatorTable.IS_NULL, builder.field(0)))
                .build();
        RuleChecks.doesNotApply(rule, nullFilter);
    }

    public static void main(String[] args) {
        System.out.println("Running RejectNullsProject test...");
        runTest();
    }
}
