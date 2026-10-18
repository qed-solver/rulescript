package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartitionPruningTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PartitionPruning.json"));

    private static Join join(LogicalPlan left, LogicalPlan right, JoinType type, Expression cond) {
        return new Join(left, right, type, scala.Option.apply(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void pushesSemiJoinBelowInnerJoin() {
        var build = Support.relation("b", 1, List.of(), new Random(1));
        var probe = Support.relation("p", 1, List.of(), new Random(2));
        var b = Catalyst.<Attribute>list(build.output()).get(0);
        var p = Catalyst.<Attribute>list(probe.output()).get(0);
        var plan = join(build, probe, Inner$.MODULE$, new EqualTo(b, p));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var outer = (Join) ((Project) rewritten).child();
        assertEquals(Inner$.MODULE$, outer.joinType());
        assertTrue(outer.left() instanceof Join);
        var semi = (Join) outer.left();
        assertEquals(LeftSemi$.MODULE$, semi.joinType());
        assertTrue(semi.left() == build);
        assertTrue(semi.right() == probe);
        assertTrue(outer.right() instanceof LocalRelation);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var build = Support.relation("b", 1, List.of(), new Random(1));
        var probe = Support.relation("p", 1, List.of(), new Random(2));
        var b = Catalyst.<Attribute>list(build.output()).get(0);
        var p = Catalyst.<Attribute>list(probe.output()).get(0);
        var plan = join(build, probe, LeftOuter$.MODULE$, new EqualTo(b, p));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesJoinWithoutConditionAlone() {
        var build = Support.relation("b", 1, List.of(), new Random(1));
        var probe = Support.relation("p", 1, List.of(), new Random(2));
        var plan = join(build, probe, Inner$.MODULE$, null);
        assertEquals(plan, rule.apply(plan));
    }
}
