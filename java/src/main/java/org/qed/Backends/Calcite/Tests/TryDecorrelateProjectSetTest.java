package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.TryDecorrelateProjectSet;
import org.qed.RuleBuilder;

public final class TryDecorrelateProjectSetTest {
    public static void runTest() {
        var rule = new TryDecorrelateProjectSet();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var catalog = RuleBuilder.create();
        var l = RuleChecks.table(catalog, 1, false);
        var i = RuleChecks.table(catalog, 1, false);
        var e = RuleChecks.table(catalog, 1, false);
        var b = RuleChecks.ruleBuilder(catalog);
        var left = b.scan(l).build();
        var input = b.scan(i).build();
        var emitted = b.scan(e).build();
        b.push(input).push(emitted);
        var membership = b.call(catalog.genericPredicateOp("M", true), ref(b, left, 0), ref(b, left, 1));
        var zipped = b.join(JoinRelType.INNER, membership).build();
        b.push(left).push(zipped);
        var condition = b.call(catalog.genericPredicateOp("on", true),
                ref(b, left, 0), ref(b, left, 1), ref(b, left, 2));
        var before = b.join(JoinRelType.INNER, condition).build();

        var cross = b.push(left).push(input).join(JoinRelType.INNER, b.literal(true)).build();
        b.push(cross).push(emitted);
        var movedMembership = b.call(catalog.genericPredicateOp("M", true), ref(b, left, 1), ref(b, left, 2));
        var movedZip = b.join(JoinRelType.INNER, movedMembership).build();
        b.push(movedZip);
        var movedCondition = b.call(catalog.genericPredicateOp("on", true),
                ref(b, left, 0), ref(b, left, 1), ref(b, left, 2));
        var expected = b.filter(movedCondition).build();
        RuleChecks.rewrites(rule, before, expected);

        RuleChecks.doesNotApply(rule, b.scan(l).build());
    }

    public static void main(String[] args) {
        System.out.println("Running TryDecorrelateProjectSet test...");
        runTest();
    }

    private static org.apache.calcite.rex.RexNode ref(org.apache.calcite.tools.RelBuilder b,
            org.apache.calcite.rel.RelNode typeSource, int index) {
        return b.getRexBuilder().makeInputRef(typeSource.getRowType().getFieldList().get(0).getType(), index);
    }
}
