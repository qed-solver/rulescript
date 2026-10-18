package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PushFilterIntoJoinLeftAndRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushFilterIntoJoinLeftAndRight.json"));

    @Test
    void pushesEqualityOntoBothSidesAndKeepsTwoJoinKeys() {
        var left = Support.relation("L", 2, List.of(0, 1), new Random(1));
        var right = Support.relation("R", 2, List.of(0, 1), new Random(2));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        Expression keyOne = new EqualTo(l.get(0), r.get(0));
        Expression keyTwo = new EqualTo(l.get(1), r.get(1));
        Expression crossKey = new EqualTo(l.get(0), r.get(1));
        var plan = new Join(left, right, Inner$.MODULE$,
                Catalyst.some(new And(new And(keyOne, keyTwo), crossKey)), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var expected = new Join(
                new Filter(new EqualTo(l.get(0), l.get(1)), left),
                new Filter(new EqualTo(r.get(0), r.get(1)), right),
                Inner$.MODULE$,
                Catalyst.some(new And(keyOne, keyTwo)),
                JoinHint$.MODULE$.NONE());
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterJoinAlone() {
        var left = Support.relation("L", 2, List.of(0, 1), new Random(1));
        var right = Support.relation("R", 2, List.of(0, 1), new Random(2));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        var plan = new Join(left, right, LeftOuter$.MODULE$,
                Catalyst.some(new And(new And(new EqualTo(l.get(0), r.get(0)), new EqualTo(l.get(1), r.get(1))),
                        new EqualTo(l.get(0), r.get(1)))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAJoinWithoutTheCrossKeyAlone() {
        var left = Support.relation("L", 2, List.of(0, 1), new Random(1));
        var right = Support.relation("R", 2, List.of(0, 1), new Random(2));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        var plan = new Join(left, right, Inner$.MODULE$,
                Catalyst.some(new And(new EqualTo(l.get(0), r.get(0)), new EqualTo(l.get(1), r.get(1)))),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAJoinWithNullableInputAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("R", 2, List.of(0, 1), new Random(2));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        var plan = new Join(left, right, Inner$.MODULE$,
                Catalyst.some(new And(new And(new EqualTo(l.get(0), r.get(0)), new EqualTo(l.get(1), r.get(1))),
                        new EqualTo(l.get(0), r.get(1)))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
