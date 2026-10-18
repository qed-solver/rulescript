package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.LessThanOrEqual;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class JoinAssociateTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("JoinAssociate.json"));

    private static Expression and(Expression... parts) {
        return Catalyst.conjunction(List.of(parts));
    }

    private static Join join(LogicalPlan left, LogicalPlan right, JoinType type, Expression cond) {
        return new Join(left, right, type, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void rotatesInnerJoinsWhenPredicatesSplit() {
        var seeds = List.of(1, 2, 3, 4, 5);
        var pairs = new java.util.ArrayList<LogicalPlan[]>();
        for (int seed : seeds) {
            var a = Support.relation("a", 1, List.of(), new Random(seed));
            var b = Support.relation("b", 1, List.of(), new Random(seed + 10));
            var c = Support.relation("c", 1, List.of(), new Random(seed + 20));
            var x = Catalyst.<Attribute>list(a.output()).get(0);
            var y = Catalyst.<Attribute>list(b.output()).get(0);
            var z = Catalyst.<Attribute>list(c.output()).get(0);
            var pab = new EqualTo(x, y);
            var pb = new GreaterThan(y, Literal$.MODULE$.apply(0));
            var pabc = new LessThan(x, z);
            var pbc = new LessThanOrEqual(y, z);
            var pc = new GreaterThan(z, Literal$.MODULE$.apply(0));
            var plan = join(join(a, b, Inner$.MODULE$, and(pab, pb)), c, Inner$.MODULE$, and(pabc, pbc, pc));
            var rewritten = rule.apply(plan);
            var expected = join(a, join(b, c, Inner$.MODULE$, and(pb, pbc, pc)), Inner$.MODULE$, and(pab, pabc));
            assertNotEquals(plan, rewritten);
            assertEquals(expected, rewritten);
            pairs.add(new LogicalPlan[] {plan, rewritten});
        }
        Assumptions.assumeTrue(Support.canExecute());
        for (var pair : pairs) {
            assertEquals(Support.run(pair[0]), Support.run(pair[1]));
        }
    }

    @Test
    void leavesLeftOuterOuterJoinAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(a.output()).get(0);
        var y = Catalyst.<Attribute>list(b.output()).get(0);
        var z = Catalyst.<Attribute>list(c.output()).get(0);
        var inner = join(a, b, Inner$.MODULE$, and(new EqualTo(x, y), new GreaterThan(y, Literal$.MODULE$.apply(0))));
        var plan = join(inner, c, LeftOuter$.MODULE$,
                and(new LessThan(x, z), new LessThanOrEqual(y, z), new GreaterThan(z, Literal$.MODULE$.apply(0))));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesInnerPredicateReadingOutsideBAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(a.output()).get(0);
        var y = Catalyst.<Attribute>list(b.output()).get(0);
        var z = Catalyst.<Attribute>list(c.output()).get(0);
        var inner = join(a, b, Inner$.MODULE$, and(new EqualTo(x, y), new GreaterThan(x, Literal$.MODULE$.apply(0))));
        var plan = join(inner, c, Inner$.MODULE$,
                and(new LessThan(x, z), new LessThanOrEqual(y, z), new GreaterThan(z, Literal$.MODULE$.apply(0))));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFilterBetweenJoinsAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(a.output()).get(0);
        var y = Catalyst.<Attribute>list(b.output()).get(0);
        var z = Catalyst.<Attribute>list(c.output()).get(0);
        var inner = join(a, b, Inner$.MODULE$, and(new EqualTo(x, y), new GreaterThan(y, Literal$.MODULE$.apply(0))));
        var filtered = new Filter(new GreaterThan(y, Literal$.MODULE$.apply(0)), inner);
        var plan = join(filtered, c, Inner$.MODULE$,
                and(new LessThan(x, z), new LessThanOrEqual(y, z), new GreaterThan(z, Literal$.MODULE$.apply(0))));
        assertEquals(plan, rule.apply(plan));
    }
}
