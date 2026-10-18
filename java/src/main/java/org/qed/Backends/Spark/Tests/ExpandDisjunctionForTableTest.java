package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.LessThanOrEqual;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ExpandDisjunctionForTableTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ExpandDisjunctionForTable.json"));

    @Test
    void expandsDisjunctionIntoPerTableDisjunctions() {
        var t1 = Support.relation("t1", 1, List.of(), new Random(1));
        var t2 = Support.relation("t2", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(t1.output()).get(0);
        var y = Catalyst.<Attribute>list(t2.output()).get(0);
        var pairsOnly = new Or(
                new And(new GreaterThan(x, Literal$.MODULE$.apply(1)), new LessThan(y, Literal$.MODULE$.apply(3))),
                new And(new EqualTo(x, Literal$.MODULE$.apply(0)), new EqualTo(y, Literal$.MODULE$.apply(2))));
        var cross = new LessThanOrEqual(x, y);
        var plan = new Join(t1, t2, Inner$.MODULE$, Catalyst.<Expression>some(new And(cross, pairsOnly)),
                JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var join = assertInstanceOf(Join.class, rewritten);
        assertEquals(t1, join.left());
        assertEquals(t2, join.right());
        var expected = new And(
                new And(
                        new And(cross, pairsOnly),
                        new Or(new GreaterThan(x, Literal$.MODULE$.apply(1)), new EqualTo(x, Literal$.MODULE$.apply(0)))),
                new Or(new LessThan(y, Literal$.MODULE$.apply(3)), new EqualTo(y, Literal$.MODULE$.apply(2))));
        assertEquals(Catalyst.<Expression>some(expected), join.condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var t1 = Support.relation("t1", 1, List.of(), new Random(5));
        var t2 = Support.relation("t2", 1, List.of(), new Random(6));
        var x = Catalyst.<Attribute>list(t1.output()).get(0);
        var y = Catalyst.<Attribute>list(t2.output()).get(0);
        var pairsOnly = new Or(
                new And(new GreaterThan(x, Literal$.MODULE$.apply(1)), new LessThan(y, Literal$.MODULE$.apply(3))),
                new And(new EqualTo(x, Literal$.MODULE$.apply(0)), new EqualTo(y, Literal$.MODULE$.apply(2))));
        var plan = new Join(t1, t2, LeftOuter$.MODULE$,
                Catalyst.<Expression>some(new And(new LessThanOrEqual(x, y), pairsOnly)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesJoinWithoutDisjunctionAlone() {
        var t1 = Support.relation("t1", 1, List.of(), new Random(8));
        var t2 = Support.relation("t2", 1, List.of(), new Random(9));
        var x = Catalyst.<Attribute>list(t1.output()).get(0);
        var y = Catalyst.<Attribute>list(t2.output()).get(0);
        var plan = new Join(t1, t2, Inner$.MODULE$,
                Catalyst.<Expression>some(new And(new LessThanOrEqual(x, y),
                        new And(new GreaterThan(x, Literal$.MODULE$.apply(1)), new LessThan(y, Literal$.MODULE$.apply(3))))),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
