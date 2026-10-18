package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.InlineSelectConstants;
import org.qed.RuleBuilder;

public final class InlineSelectConstantsTest {
    private InlineSelectConstantsTest() {}

    public static void runTest() {
        var rule = new InlineSelectConstants();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(InlineSelectConstants rule) {
        var before = concretePlan(false, true, JoinRelType.INNER);
        var after = concretePlan(true, true, JoinRelType.INNER);
        RuleChecks.rewrites(rule, before, after);
        RuleChecks.doesNotApply(rule, concretePlan(false, false, JoinRelType.INNER));
    }

    private static RelNode concretePlan(boolean inline, boolean uniqueConstant, JoinRelType joinType) {
        var builder = RuleBuilder.create();
        var source = RuleChecks.table(builder, 1, false, true);
        var constant = RuleChecks.table(builder, 1, uniqueConstant, false);
        builder.scan(source).scan(constant).join(joinType,
                builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)));
        var predicate = builder.genericPredicateOp("g", true);
        builder.filter(builder.call(predicate, builder.field(inline ? 1 : 0)));
        return builder.build();
    }

    public static void main(String[] args) {
        System.out.println("Running InlineSelectConstants test...");
        runTest();
    }
}
