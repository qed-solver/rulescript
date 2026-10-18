package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
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

class FilterAggregateTransposeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FilterAggregateTranspose.json"));

    private static Join join(JoinType type) {
        var left = Support.relation("A", 1, List.of(), new Random(1));
        var right = Support.relation("B", 1, List.of(), new Random(2));
        return new Join(left, right, type, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
    }

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Aggregate groupedMax(LogicalPlan input, Attribute key, Attribute value) {
        var max = AggregateExpression$.MODULE$.apply(new Max(value), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        return new Aggregate(Catalyst.<Expression>seq(List.of(key)),
                Catalyst.<NamedExpression>seq(List.of(key, alias(max, "m"))), input,
                Option.empty());
    }

    private static Filter overAggregate(JoinType type, boolean pushedOnKey) {
        var join = join(type);
        var columns = Catalyst.<Attribute>list(join.output());
        var key = columns.get(0);
        var agg = groupedMax(join, key, columns.get(1));
        var aggColumns = Catalyst.<Attribute>list(agg.output());
        var pushed = pushedOnKey
                ? new GreaterThan(key, Literal$.MODULE$.apply(1))
                : new GreaterThan(aggColumns.get(1), Literal$.MODULE$.apply(1));
        var qa = new LessThan(key, Literal$.MODULE$.apply(3));
        var qb = new GreaterThan(aggColumns.get(1), Literal$.MODULE$.apply(0));
        return new Filter(new And(pushed, new And(qa, qb)), agg);
    }

    @Test
    void pushesKeyConjunctBelowAggregate() {
        var plan = overAggregate(Inner$.MODULE$, true);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var top = rewritten instanceof Project p ? p.child() : rewritten;
        assertTrue(top instanceof Filter outer && outer.child() instanceof Aggregate agg
                && agg.child() instanceof Filter inner && inner.child() instanceof Join);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var plan = overAggregate(LeftOuter$.MODULE$, true);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesConjunctOverAggregateValueAlone() {
        var plan = overAggregate(Inner$.MODULE$, false);
        assertEquals(plan, rule.apply(plan));
    }
}
