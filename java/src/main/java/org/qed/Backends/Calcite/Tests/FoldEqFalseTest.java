package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RuleBuilder;
import org.qed.RRuleInstances.FoldEqFalse;

public class FoldEqFalseTest {
    public static void runTest() {
        var rule = new FoldEqFalse();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlans();
        rejectsNotEqualToFalse();
    }

    private static void rewritesHandBuiltPlans() {
        var ruleBuilder = RuleBuilder.create();
        var table = RuleChecks.table(ruleBuilder, 1, false);
        var builder = RuleChecks.ruleBuilder(ruleBuilder);
        builder.scan(table);
        var source = builder.build();
        var rexBuilder = builder.getRexBuilder();
        RexNode field = rexBuilder.makeInputRef(source, 0);
        RelNode before = LogicalFilter.create(source, rexBuilder.makeCall(SqlStdOperatorTable.EQUALS,
                field, rexBuilder.makeLiteral(false)));
        RelNode expected = LogicalFilter.create(source, rexBuilder.makeCall(SqlStdOperatorTable.NOT, field));
        RuleChecks.rewrites(new FoldEqFalse(), before, expected);
    }

    private static void rejectsNotEqualToFalse() {
        var ruleBuilder = RuleBuilder.create();
        var table = RuleChecks.table(ruleBuilder, 1, false);
        var builder = RuleChecks.ruleBuilder(ruleBuilder);
        builder.scan(table);
        RexNode condition = builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), builder.literal(true));
        RelNode source = builder.build();
        RelNode before = LogicalFilter.create(source, condition);
        RuleChecks.doesNotApply(new FoldEqFalse(), before);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldEqFalse test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError("FoldEqFalse test failed");
        }
    }
}
