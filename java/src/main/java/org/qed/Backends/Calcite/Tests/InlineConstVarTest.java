package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.InlineConstVar;
import org.qed.RuleBuilder;

public final class InlineConstVarTest {
    private InlineConstVarTest() {}

    public static void runTest() {
        var rule = new InlineConstVar();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        RuleChecks.rewrites(rule, concretePlan(false, true), concretePlan(true, true));
        RuleChecks.doesNotApply(rule, concretePlan(false, false));
    }

    private static RelNode concretePlan(boolean inline, boolean uniqueConstant) {
        var builder = RuleBuilder.create();
        var source = RuleChecks.table(builder, 1, false, true);
        var constant = RuleChecks.table(builder, 1, uniqueConstant, false);
        builder.scan(source).scan(constant).join(org.apache.calcite.rel.core.JoinRelType.INNER,
                builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)));
        var value = builder.field(1);
        if (inline) {
            builder.filter(builder.call(builder.genericPredicateOp("f", true), value));
        } else {
            builder.filter(builder.call(builder.genericPredicateOp("f", true), builder.field(0)));
        }
        return builder.build();
    }

    public static void main(String[] args) {
        System.out.println("Running InlineConstVar test...");
        runTest();
    }
}
