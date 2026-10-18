package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
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

class AggregateJoinTransposeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("AggregateJoinTranspose.json"));

    private static Aggregate groupBy(LogicalPlan child, Attribute... keys) {
        return new Aggregate(Catalyst.<Expression>seq(List.of(keys)), Catalyst.<NamedExpression>seq(List.of(keys)),
                child, Option.<AggregateHint>empty());
    }

    private static Join innerJoin(LogicalPlan left, LogicalPlan right, Expression cond) {
        return new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void pushesGroupingBelowAnInnerJoin() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("R", 2, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        var join = innerJoin(left, right, new EqualTo(l.get(0), r.get(0)));
        var plan = groupBy(join, l.get(1), r.get(1));

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate top && top.child() instanceof Join rejoin
                && rejoin.joinType() == Inner$.MODULE$ && rejoin.condition().isDefined()
                && rejoin.left() instanceof Aggregate leftAgg && rejoin.right() instanceof Aggregate rightAgg
                && leftAgg.child().equals(left) && rightAgg.child().equals(right)
                && leftAgg.groupingExpressions().size() == 2 && rightAgg.groupingExpressions().size() == 2);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterJoinAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("R", 2, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        var join = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(new EqualTo(l.get(0), r.get(0))),
                JoinHint$.MODULE$.NONE());
        var plan = groupBy(join, l.get(1), r.get(1));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesGroupingOnOneSideAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("R", 2, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        var join = innerJoin(left, right, new EqualTo(l.get(0), r.get(0)));
        var plan = groupBy(join, l.get(0), l.get(1));
        assertEquals(plan, rule.apply(plan));
    }
}
