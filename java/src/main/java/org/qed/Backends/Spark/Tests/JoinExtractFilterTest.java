package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinExtractFilterTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("JoinExtractFilter.json"));

    @Test
    void extractsInnerJoinConditionIntoFilter() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var lx = Catalyst.<Attribute>list(left.output()).get(0);
        var rx = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new EqualTo(lx, rx);
        var plan = new Join(left, right, Inner$.MODULE$, scala.Option.apply(cond), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter, "expected a Filter on top, got " + rewritten);
        var filter = (Filter) rewritten;
        assertEquals(cond, filter.condition());
        assertTrue(filter.child() instanceof Join, "expected an inner Join under the Filter, got " + filter.child());
        var join = (Join) filter.child();
        assertEquals(Inner$.MODULE$, join.joinType());
        assertTrue(join.condition().isEmpty(), "the join condition should be gone, got " + join.condition());
        assertEquals(left, join.left());
        assertEquals(right, join.right());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var lx = Catalyst.<Attribute>list(left.output()).get(0);
        var rx = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, LeftOuter$.MODULE$, scala.Option.apply(new EqualTo(lx, rx)),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
