package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class JoinDeriveIsNotNullFilterTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("JoinDeriveIsNotNullFilter.json"));

    @Test
    void derivesNotNullFiltersBelowAnEquiJoin() {
        var left = Support.relation("l", 1, List.of(), new Random(7));
        var right = Support.relation("r", 1, List.of(), new Random(8));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new And(new EqualTo(l, r), new Not(new GreaterThan(l, r)));
        var plan = new Join(left, right, Inner$.MODULE$, Catalyst.<Expression>some(cond), JoinHint.NONE());
        var expected = new Join(new Filter(new IsNotNull(l), left), new Filter(new IsNotNull(r), right),
                Inner$.MODULE$, Catalyst.<Expression>some(cond), JoinHint.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(7));
        var right = Support.relation("r", 1, List.of(), new Random(8));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new And(new EqualTo(l, r), new Not(new GreaterThan(l, r)));
        var plan = new Join(left, right, LeftOuter$.MODULE$, Catalyst.<Expression>some(cond), JoinHint.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAJoinWithoutAnEqualityConjunctAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(7));
        var right = Support.relation("r", 1, List.of(), new Random(8));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, Inner$.MODULE$, Catalyst.<Expression>some(new GreaterThan(l, r)), JoinHint.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAJoinWhoseEqualityUsesALiteralAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(7));
        var right = Support.relation("r", 1, List.of(), new Random(8));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new And(new EqualTo(l, Literal$.MODULE$.apply(2)), new Not(new GreaterThan(l, r)));
        var plan = new Join(left, right, Inner$.MODULE$, Catalyst.<Expression>some(cond), JoinHint.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
