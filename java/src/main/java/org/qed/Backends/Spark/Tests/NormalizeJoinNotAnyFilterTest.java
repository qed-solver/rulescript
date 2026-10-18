package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Exists;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.InSubquery;
import org.apache.spark.sql.catalyst.expressions.ListQuery;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
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

class NormalizeJoinNotAnyFilterTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("NormalizeJoinNotAnyFilter.json"));

    private static Expression inSub(Attribute value, LogicalPlan sub) {
        var query = new ListQuery(sub, Catalyst.<Expression>seq(List.of()), NamedExpression$.MODULE$.newExprId(),
                sub.output().size(), Catalyst.<Expression>seq(List.of()), Option.<HintInfo>empty());
        return new InSubquery(Catalyst.<Expression>seq(List.of(value)), query);
    }

    @Test
    void rewritesNotInJoinConditionToNotExists() {
        var left = Support.relation("left", 1, List.of(), new Random(1));
        var right = Support.relation("right", 1, List.of(), new Random(2));
        var sub = Support.relation("sub", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var plan = new Join(left, right, Inner$.MODULE$, Catalyst.some(new Not(inSub(x, sub))), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join j && j.joinType() == Inner$.MODULE$ && j.left().equals(left)
                && j.right().equals(right));
        var cond = ((Join) rewritten).condition().get();
        assertTrue(cond instanceof Not n && n.child() instanceof Exists);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("left", 1, List.of(), new Random(1));
        var right = Support.relation("right", 1, List.of(), new Random(2));
        var sub = Support.relation("sub", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var plan = new Join(left, right, LeftOuter$.MODULE$, Catalyst.some(new Not(inSub(x, sub))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesPositiveInAlone() {
        var left = Support.relation("left", 1, List.of(), new Random(1));
        var right = Support.relation("right", 1, List.of(), new Random(2));
        var sub = Support.relation("sub", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var plan = new Join(left, right, Inner$.MODULE$, Catalyst.some(inSub(x, sub)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNotEqualityAlone() {
        var left = Support.relation("left", 1, List.of(), new Random(1));
        var right = Support.relation("right", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, Inner$.MODULE$, Catalyst.some(new Not(new EqualTo(x, y))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
