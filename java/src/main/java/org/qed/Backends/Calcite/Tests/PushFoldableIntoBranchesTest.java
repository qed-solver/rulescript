package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RuleBuilder;
import org.qed.RRuleInstances.PushFoldableIntoBranches;

public final class PushFoldableIntoBranchesTest {
    private PushFoldableIntoBranchesTest() {}

    public static void runTest() {
        var rule = new PushFoldableIntoBranches();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushFoldableIntoBranches rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, false);
        var input = builder.scan(table);
        var condition = builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), builder.literal(0));
        var caseOp = new RuleBuilder.QedFunction("CASE", org.qed.RelType.fromString("INTEGER", true));
        var udf = new RuleBuilder.QedFunction("U", org.qed.RelType.fromString("INTEGER", true));
        var caseExpr = builder.call(caseOp, condition, builder.field(0), builder.field(1));
        var plan = input.project(builder.call(udf, caseExpr)).build();

        var plain = RuleChecks.ruleBuilder(builder);
        var expectedInput = plain.scan(table);
        var expectedCondition = plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), plain.literal(0));
        var expectedCase = plain.call(caseOp, expectedCondition, plain.call(udf, plain.field(0)),
                plain.call(udf, plain.field(1)));
        var expected = expectedInput.project(expectedCase).build();
        RuleChecks.rewrites(rule, plan, expected);

        var nearBuilder = RuleBuilder.create();
        var nearTable = RuleChecks.table(nearBuilder, 2, false, false);
        var otherOperator = nearBuilder.scan(nearTable).project(nearBuilder.call(udf,
                nearBuilder.call(SqlStdOperatorTable.COALESCE, nearBuilder.field(0), nearBuilder.field(1)))).build();
        RuleChecks.doesNotApply(rule, otherOperator);
    }

    public static void main(String[] args) {
        System.out.println("Running PushFoldableIntoBranches test...");
        runTest();
    }
}
