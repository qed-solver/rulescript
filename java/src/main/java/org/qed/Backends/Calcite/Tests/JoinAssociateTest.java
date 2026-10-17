package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.JoinAssociate;
import org.qed.RuleBuilder;

public class JoinAssociateTest {
    public static void runTest() {
        var rule = new JoinAssociate();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        RuleChecks.rewrites(rule, handBuiltInput(false), handBuiltExpected());
        RuleChecks.doesNotApply(rule, handBuiltInput(true));
    }

    private static RelNode handBuiltInput(boolean wrongPc) {
        var schema = RuleBuilder.create();
        var a = RuleChecks.table(schema, 1, false);
        var b = RuleChecks.table(schema, 1, false);
        var c = RuleChecks.table(schema, 1, false);
        var builder = RuleChecks.ruleBuilder(schema);
        builder.scan(a).scan(b).join(JoinRelType.INNER,
                builder.and(predicate(builder, "pab", 0, 1), predicate(builder, "pb", 1)));
        builder.scan(c).join(JoinRelType.INNER,
                builder.and(predicate(builder, "pabc", 0, 1, 2), predicate(builder, "pbc", 1, 2),
                        predicate(builder, "pc", 2),
                        wrongPc ? predicate(builder, "extra", 2) : predicate(builder, "pc", 2)));
        return builder.build();
    }

    private static RelNode handBuiltExpected() {
        var schema = RuleBuilder.create();
        var a = RuleChecks.table(schema, 1, false);
        var b = RuleChecks.table(schema, 1, false);
        var c = RuleChecks.table(schema, 1, false);
        var builder = RuleChecks.ruleBuilder(schema);
        builder.scan(a).scan(b).scan(c).join(JoinRelType.INNER,
                builder.and(predicate(builder, "pb", 0), predicate(builder, "pbc", 0, 1),
                        predicate(builder, "pc", 1)));
        builder.join(JoinRelType.INNER,
                builder.and(predicate(builder, "pab", 0, 1), predicate(builder, "pabc", 0, 1, 2)));
        return builder.build();
    }

    private static RexNode predicate(RelBuilder builder, String name, int... indexes) {
        SqlOperator operator = RuleBuilder.create().genericPredicateOp(name, true);
        var type = builder.peek().getRowType().getFieldList().get(0).getType();
        var args = new RexNode[indexes.length];
        for (var i = 0; i < indexes.length; i++) {
            args[i] = builder.getRexBuilder().makeInputRef(type, indexes[i]);
        }
        return builder.call(operator, args);
    }

    public static void main(String[] args) {
        System.out.println("Running JoinAssociate test...");
        runTest();
    }
}
