package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.RightOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Distinct;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushDownJoinThroughUnionTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushDownJoinThroughUnion.json"));

    private static Join join(LogicalPlan left, LogicalPlan right, org.apache.spark.sql.catalyst.plans.JoinType type, Expression cond) {
        return new Join(left, right, type, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
    }

    private static Union union(LogicalPlan... inputs) {
        return new Union(Catalyst.<LogicalPlan>seq(List.of(inputs)), false, false);
    }

    @Test
    void pushesLeftOuterJoinIntoBothUnionBranches() {
        var x = Support.relation("x", 1, List.of(), new Random(1));
        var y = Support.relation("y", 1, List.of(), new Random(2));
        var o = Support.relation("o", 1, List.of(), new Random(3));
        var xa = Catalyst.<Attribute>list(x.output()).get(0);
        var ya = Catalyst.<Attribute>list(y.output()).get(0);
        var oa = Catalyst.<Attribute>list(o.output()).get(0);
        var u = union(x, y);
        var ua = Catalyst.<Attribute>list(u.output()).get(0);
        var plan = join(u, o, LeftOuter$.MODULE$, new GreaterThan(ua, oa));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Union);
        var branches = Catalyst.<LogicalPlan>list(((Union) rewritten).children());
        assertEquals(2, branches.size());
        var first = (Join) branches.get(0);
        var second = (Join) branches.get(1);
        assertEquals(LeftOuter$.MODULE$, first.joinType());
        assertEquals(LeftOuter$.MODULE$, second.joinType());
        assertEquals(x, first.left());
        assertEquals(y, second.left());
        assertEquals(o.data(), ((LocalRelation) first.right()).data());
        assertEquals(o.data(), ((LocalRelation) second.right()).data());
        assertEquals(Option.apply(new GreaterThan(xa, Catalyst.<Attribute>list(first.right().output()).get(0))), first.condition());
        assertEquals(Option.apply(new GreaterThan(ya, Catalyst.<Attribute>list(second.right().output()).get(0))), second.condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void pushesLeftOuterJoinIntoUnionOfTwoRelations() {
        var x = Support.relation("x", 1, List.of(), new Random(11));
        var y = Support.relation("y", 1, List.of(), new Random(12));
        var o = Support.relation("o", 1, List.of(), new Random(13));
        var u = union(x, y);
        var ua = Catalyst.<Attribute>list(u.output()).get(0);
        var oa = Catalyst.<Attribute>list(o.output()).get(0);
        var plan = join(u, o, LeftOuter$.MODULE$, new GreaterThan(ua, oa));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Union);
        assertEquals(2, Catalyst.list(((Union) rewritten).children()).size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesRightOuterJoinOverUnionAlone() {
        var x = Support.relation("x", 1, List.of(), new Random(1));
        var y = Support.relation("y", 1, List.of(), new Random(2));
        var o = Support.relation("o", 1, List.of(), new Random(3));
        var u = union(x, y);
        var ua = Catalyst.<Attribute>list(u.output()).get(0);
        var oa = Catalyst.<Attribute>list(o.output()).get(0);
        var plan = join(u, o, RightOuter$.MODULE$, new GreaterThan(ua, oa));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesInnerJoinOverUnionAlone() {
        var x = Support.relation("x", 1, List.of(), new Random(1));
        var y = Support.relation("y", 1, List.of(), new Random(2));
        var o = Support.relation("o", 1, List.of(), new Random(3));
        var u = union(x, y);
        var ua = Catalyst.<Attribute>list(u.output()).get(0);
        var oa = Catalyst.<Attribute>list(o.output()).get(0);
        var plan = join(u, o, Inner$.MODULE$, new GreaterThan(ua, oa));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesLeftOuterJoinOverDistinctUnionAlone() {
        var x = Support.relation("x", 1, List.of(), new Random(1));
        var y = Support.relation("y", 1, List.of(), new Random(2));
        var o = Support.relation("o", 1, List.of(), new Random(3));
        var d = new Distinct(union(x, y));
        var da = Catalyst.<Attribute>list(d.output()).get(0);
        var oa = Catalyst.<Attribute>list(o.output()).get(0);
        var plan = join(d, o, LeftOuter$.MODULE$, new GreaterThan(da, oa));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesLeftOuterJoinOverThreeWayUnionAlone() {
        var x = Support.relation("x", 1, List.of(), new Random(1));
        var y = Support.relation("y", 1, List.of(), new Random(2));
        var z = Support.relation("z", 1, List.of(), new Random(4));
        var o = Support.relation("o", 1, List.of(), new Random(3));
        var u = union(x, y, z);
        var ua = Catalyst.<Attribute>list(u.output()).get(0);
        var oa = Catalyst.<Attribute>list(o.output()).get(0);
        var plan = join(u, o, LeftOuter$.MODULE$, new GreaterThan(ua, oa));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesLeftOuterJoinOverSingleInputAlone() {
        var x = Support.relation("x", 1, List.of(), new Random(1));
        var o = Support.relation("o", 1, List.of(), new Random(3));
        var xa = Catalyst.<Attribute>list(x.output()).get(0);
        var oa = Catalyst.<Attribute>list(o.output()).get(0);
        var plan = join(x, o, LeftOuter$.MODULE$, new GreaterThan(xa, oa));
        assertEquals(plan, rule.apply(plan));
    }
}
