package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Except;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Intersect;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushFilterIntoSetOpTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushFilterIntoSetOp.json"));

    @Test
    void pushesFilterIntoBothSidesOfDistinctIntersect() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var intersection = new Intersect(left, right, false);
        var x = Catalyst.<Attribute>list(intersection.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(1)), intersection);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Intersect i && !i.isAll()
                && i.left() instanceof Filter l && l.child() == left
                && i.right() instanceof Filter r && r.child() == right);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFilterOverIntersectAllAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var intersection = new Intersect(left, right, true);
        var x = Catalyst.<Attribute>list(intersection.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(1)), intersection);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFilterOverExceptAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var difference = new Except(left, right, false);
        var x = Catalyst.<Attribute>list(difference.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(1)), difference);
        assertEquals(plan, rule.apply(plan));
    }
}
