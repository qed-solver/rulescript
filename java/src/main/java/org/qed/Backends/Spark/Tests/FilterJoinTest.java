package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FilterJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FilterJoin.json"));

    @Test
    void mergesFilterIntoInnerJoinCondition() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var joinCond = new EqualTo(x, y);
        var above = new GreaterThan(y, Literal$.MODULE$.apply(1));
        var join = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(joinCond), JoinHint$.MODULE$.NONE());
        var plan = new Filter(above, join);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertInstanceOf(Join.class, rewritten);
        var expected = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(new And(joinCond, above)),
                JoinHint$.MODULE$.NONE());
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFilterOverLeftOuterJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var joinCond = new EqualTo(x, y);
        var above = new GreaterThan(y, Literal$.MODULE$.apply(1));
        var join = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(joinCond), JoinHint$.MODULE$.NONE());
        var plan = new Filter(above, join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFilterOverJoinWithoutConditionAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var above = new GreaterThan(y, Literal$.MODULE$.apply(1));
        var join = new Join(left, right, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var plan = new Filter(above, join);
        assertEquals(plan, rule.apply(plan));
    }
}
