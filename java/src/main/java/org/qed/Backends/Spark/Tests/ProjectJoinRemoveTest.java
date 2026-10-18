package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectJoinRemoveTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ProjectJoinRemove.json"));

    private static LogicalPlan distinctOn(LocalRelation base) {
        var key = Catalyst.<Attribute>list(base.output()).get(0);
        return new Aggregate(Catalyst.<Expression>seq(List.of(key)), Catalyst.<NamedExpression>seq(List.of(key)), base,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    private static Join selfJoin(LocalRelation base, org.apache.spark.sql.catalyst.plans.JoinType type) {
        var left = distinctOn(base);
        var right = distinctOn((LocalRelation) base.newInstance());
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        return new Join(left, right, type, Catalyst.<Expression>some(new EqualTo(l, r)), JoinHint$.MODULE$.NONE());
    }

    @Test
    void removesLeftSelfJoinOnUniqueNonNullColumn() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var join = selfJoin(base, LeftOuter$.MODULE$);
        var keep = Catalyst.<Attribute>list(join.left().output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(keep)), join);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() instanceof Aggregate);
        assertEquals(new Project(Catalyst.<NamedExpression>seq(List.of(keep)), join.left()), rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesInnerSelfJoinAlone() {
        var base = Support.relation("t", 1, List.of(0), new Random(2));
        var join = selfJoin(base, Inner$.MODULE$);
        var keep = Catalyst.<Attribute>list(join.left().output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(keep)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectionOfRightSideAlone() {
        var base = Support.relation("t", 1, List.of(0), new Random(3));
        var join = selfJoin(base, LeftOuter$.MODULE$);
        var keep = Catalyst.<Attribute>list(join.right().output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(keep)), join);
        assertEquals(plan, rule.apply(plan));
    }
}
