package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapFilterIntoJoinLeftTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("MapFilterIntoJoinLeft.json"));

    @Test
    void rebindsFilterToTheLeftKey() {
        var left = Support.relation("L", 2, List.of(0, 1), new Random(1));
        var right = Support.relation("R", 1, List.of(0), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var l1 = Catalyst.<Attribute>list(left.output()).get(1);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var keyEquality = new EqualTo(l0, r0);
        var filterOnRight = new GreaterThan(r0, l1);
        var plan = new Join(left, right, Inner$.MODULE$, Option(new And(keyEquality, filterOnRight)), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var expected = new Join(left, right, Inner$.MODULE$,
                Option(new And(keyEquality, new GreaterThan(l0, l1))), JoinHint$.MODULE$.NONE());
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAnOuterJoinAlone() {
        var left = Support.relation("L", 2, List.of(0, 1), new Random(1));
        var right = Support.relation("R", 1, List.of(0), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var l1 = Catalyst.<Attribute>list(left.output()).get(1);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new And(new EqualTo(l0, r0), new GreaterThan(r0, l1));
        var plan = new Join(left, right, LeftOuter$.MODULE$, Option(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesANullableKeyAlone() {
        var left = Support.relation("L", 2, List.of(1), new Random(1));
        var right = Support.relation("R", 1, List.of(0), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var l1 = Catalyst.<Attribute>list(left.output()).get(1);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new And(new EqualTo(l0, r0), new GreaterThan(r0, l1));
        var plan = new Join(left, right, Inner$.MODULE$, Option(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesANonEqualityKeyAlone() {
        var left = Support.relation("L", 2, List.of(0, 1), new Random(1));
        var right = Support.relation("R", 1, List.of(0), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var l1 = Catalyst.<Attribute>list(left.output()).get(1);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new And(new LessThan(l0, r0), new GreaterThan(r0, l1));
        var plan = new Join(left, right, Inner$.MODULE$, Option(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    private static scala.Option<Expression> Option(Expression e) {
        return scala.Option.apply(e);
    }
}
