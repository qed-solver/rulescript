package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Exists;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.HintInfo;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoistJoinSubqueryTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("HoistJoinSubquery.json"));

    private static Exists uncorrelatedExists(LogicalPlan body) {
        return new Exists(body, Catalyst.<Expression>seq(List.of()), NamedExpression$.MODULE$.newExprId(),
                Catalyst.<Expression>seq(List.of()), Option.<HintInfo>empty());
    }

    @Test
    void hoistsUncorrelatedExistsFromJoinConditionIntoRightInput() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var sub = Support.relation("s", 1, List.of(), new Random(3));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var s = Catalyst.<Attribute>list(sub.output()).get(0);
        var onCond = new GreaterThan(l, r);
        var exists = uncorrelatedExists(new Filter(new GreaterThan(s, Literal$.MODULE$.apply(1)), sub));
        var plan = new Join(left, right, Inner$.MODULE$, Catalyst.some(new And(onCond, exists)),
                JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join, "result is not a join: " + rewritten);
        var join = (Join) rewritten;
        assertSame(Inner$.MODULE$, join.joinType());
        assertSame(left, join.left());
        assertEquals(Option.apply(onCond), join.condition());
        assertTrue(join.right() instanceof Filter, "right input is not a filter: " + join.right());
        var filter = (Filter) join.right();
        assertSame(right, filter.child());
        assertTrue(filter.condition() instanceof Exists, "filter condition is not EXISTS: " + filter.condition());
        assertEquals(exists.plan(), ((Exists) filter.condition()).plan());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinWithExistsAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(4));
        var right = Support.relation("r", 1, List.of(), new Random(5));
        var sub = Support.relation("s", 1, List.of(), new Random(6));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var s = Catalyst.<Attribute>list(sub.output()).get(0);
        var exists = uncorrelatedExists(new Filter(new GreaterThan(s, Literal$.MODULE$.apply(1)), sub));
        var plan = new Join(left, right, LeftOuter$.MODULE$,
                Catalyst.some(new And(new GreaterThan(l, r), exists)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesInnerJoinWithoutExistsAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(7));
        var right = Support.relation("r", 1, List.of(), new Random(8));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, Inner$.MODULE$, Catalyst.some(new GreaterThan(l, r)),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
