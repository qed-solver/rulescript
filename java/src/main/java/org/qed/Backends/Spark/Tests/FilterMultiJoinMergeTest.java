package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterMultiJoinMergeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FilterMultiJoinMerge.json"));

    private static Join join(JoinType type, LogicalPlan left, LogicalPlan right, Expression cond) {
        return new Join(left, right, type, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
    }

    private static Join crossJoin(LogicalPlan left, LogicalPlan right) {
        return new Join(left, right, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
    }

    @Test
    void mergesFilterIntoPostJoinCondition() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var a0 = Catalyst.<Attribute>list(a.output()).get(0);
        var b0 = Catalyst.<Attribute>list(b.output()).get(0);
        var c0 = Catalyst.<Attribute>list(c.output()).get(0);
        var ab = crossJoin(a, b);
        var postJoin = new EqualTo(b0, c0);
        var abc = join(Inner$.MODULE$, ab, c, postJoin);
        var cond = new LessThan(a0, Literal$.MODULE$.apply(3));
        var plan = new Filter(cond, abc);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join j);
        var merged = (Join) rewritten;
        assertEquals(Inner$.MODULE$, merged.joinType());
        assertEquals(ab, merged.left());
        assertEquals(c, merged.right());
        assertEquals(new And(postJoin, cond), merged.condition().get());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesOuterJoinAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var a0 = Catalyst.<Attribute>list(a.output()).get(0);
        var b0 = Catalyst.<Attribute>list(b.output()).get(0);
        var c0 = Catalyst.<Attribute>list(c.output()).get(0);
        var ab = crossJoin(a, b);
        var abc = join(LeftOuter$.MODULE$, ab, c, new EqualTo(b0, c0));
        var plan = new Filter(new LessThan(a0, Literal$.MODULE$.apply(3)), abc);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesJoinWithoutFilterAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var b0 = Catalyst.<Attribute>list(b.output()).get(0);
        var c0 = Catalyst.<Attribute>list(c.output()).get(0);
        var ab = crossJoin(a, b);
        var plan = join(Inner$.MODULE$, ab, c, new EqualTo(b0, c0));
        assertEquals(plan, rule.apply(plan));
    }
}
