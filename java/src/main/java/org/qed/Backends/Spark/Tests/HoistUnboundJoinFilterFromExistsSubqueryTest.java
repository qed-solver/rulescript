package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoistUnboundJoinFilterFromExistsSubqueryTest {
    private final RuleScriptRule rule = RuleScriptRule.load(
            Support.generated().resolve("HoistUnboundJoinFilterFromExistsSubquery.json"));

    private static Expression unbound() {
        return new GreaterThan(Literal$.MODULE$.apply(2), Literal$.MODULE$.apply(1));
    }

    private static Exists exists(LogicalPlan plan) {
        return new Exists(plan, Catalyst.<Expression>seq(List.of()), NamedExpression$.MODULE$.newExprId(),
                Catalyst.<Expression>seq(List.of()), Option.<HintInfo>empty());
    }

    private static Join join(LogicalPlan left, LogicalPlan right, org.apache.spark.sql.catalyst.plans.JoinType type,
            Expression condition) {
        return new Join(left, right, type, Catalyst.some(condition), JoinHint$.MODULE$.NONE());
    }

    @Test
    void hoistsUnboundConjunctOutOfExistsJoin() {
        var outer = Support.relation("outer", 1, List.of(), new Random(1));
        var left = Support.relation("left", 1, List.of(), new Random(2));
        var right = Support.relation("right", 1, List.of(), new Random(3));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var other = new EqualTo(l, r);
        var cond = unbound();
        var plan = new Filter(exists(join(left, right, Inner$.MODULE$, new And(cond, other))), outer);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == outer
                && f.condition() instanceof And a && a.left() instanceof Exists e
                && e.plan() instanceof Join j && j.joinType() == Inner$.MODULE$
                && j.condition().contains(other) && a.right().equals(cond));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterExistsJoinAlone() {
        var outer = Support.relation("outer", 1, List.of(), new Random(1));
        var left = Support.relation("left", 1, List.of(), new Random(2));
        var right = Support.relation("right", 1, List.of(), new Random(3));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Filter(exists(join(left, right, LeftOuter$.MODULE$, new And(unbound(), new EqualTo(l, r)))),
                outer);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesExistsJoinWithoutUnboundConjunctAlone() {
        var outer = Support.relation("outer", 1, List.of(), new Random(1));
        var left = Support.relation("left", 1, List.of(), new Random(2));
        var right = Support.relation("right", 1, List.of(), new Random(3));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Filter(exists(join(left, right, Inner$.MODULE$, new EqualTo(l, r))), outer);
        assertEquals(plan, rule.apply(plan));
    }
}
