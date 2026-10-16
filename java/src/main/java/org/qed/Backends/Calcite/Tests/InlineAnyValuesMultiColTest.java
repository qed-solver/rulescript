package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.InlineAnyValuesMultiCol;
import org.qed.RuleBuilder;

import java.util.ArrayList;
import java.util.Arrays;

public class InlineAnyValuesMultiColTest {

    public static void runTest() {
        var rule = new InlineAnyValuesMultiCol();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(InlineAnyValuesMultiCol rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var names = new String[]{"c0", "c1"};
        var tupleEq = (java.util.function.Supplier<org.apache.calcite.rex.RexNode>) () -> b.and(
                RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)),
                RuleChecks.eq(b, b.field(2, 0, 1), b.field(2, 1, 1)));
        b.scan(t).push(union(b, names, 1, 2, 3, 4));
        var before = b.join(JoinRelType.SEMI, tupleEq.get()).build();
        b.scan(t);
        var after = b.filter(b.or(
                b.and(RuleChecks.eq(b, b.field(0), b.literal(1)), RuleChecks.eq(b, b.field(1), b.literal(2))),
                b.and(RuleChecks.eq(b, b.field(0), b.literal(3)), RuleChecks.eq(b, b.field(1), b.literal(4))))).build();
        RuleChecks.rewrites(rule, before, after);

        b.scan(t).push(union(b, names, 1, 2, 3, 5));
        RuleChecks.doesNotApply(rule, b.join(JoinRelType.SEMI, tupleEq.get()).build());
        b.scan(t).push(union(b, names, 1, 2, 3, 4));
        RuleChecks.doesNotApply(rule, b.join(JoinRelType.INNER, tupleEq.get()).build());
    }

    private static RelNode union(RelBuilder b, String[] names, Object... rows) {
        var inputs = new ArrayList<RelNode>();
        for (var i = 0; i < rows.length; i += names.length) {
            inputs.add(b.values(names, Arrays.copyOfRange(rows, i, i + names.length)).build());
        }
        return LogicalUnion.create(inputs, true);
    }

    public static void main(String[] args) {
        System.out.println("Running InlineAnyValuesMultiCol test...");
        runTest();
    }
}
