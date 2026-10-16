package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.InlineAnyValuesSingleCol;
import org.qed.RuleBuilder;

import java.util.ArrayList;
import java.util.Arrays;

public class InlineAnyValuesSingleColTest {

    public static void runTest() {
        var rule = new InlineAnyValuesSingleCol();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(InlineAnyValuesSingleCol rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var names = new String[]{"c0"};
        b.scan(t).push(union(b, names, 1, 3));
        var before = b.join(JoinRelType.SEMI, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).build();
        b.scan(t);
        var condition = b.or(RuleChecks.eq(b, b.field(0), b.literal(1)), RuleChecks.eq(b, b.field(0), b.literal(3)));
        var after = LogicalFilter.create(b.build(), condition);
        RuleChecks.rewrites(rule, before, after);

        b.scan(t).push(union(b, names, 1, 4));
        RuleChecks.doesNotApply(rule, b.join(JoinRelType.SEMI, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).build());
        b.scan(t).push(union(b, names, 1, 3));
        RuleChecks.doesNotApply(rule, b.join(JoinRelType.ANTI, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).build());
    }

    private static RelNode union(RelBuilder b, String[] names, Object... rows) {
        var inputs = new ArrayList<RelNode>();
        for (var i = 0; i < rows.length; i += names.length) {
            inputs.add(b.values(names, Arrays.copyOfRange(rows, i, i + names.length)).build());
        }
        return LogicalUnion.create(inputs, true);
    }

    public static void main(String[] args) {
        System.out.println("Running InlineAnyValuesSingleCol test...");
        runTest();
    }
}
