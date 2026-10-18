package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
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
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
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

class ReduceWindowPartitionColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ReduceWindowPartitionCols.json"));

    @Test
    void reducesWindowPartitionByDerivedColumnToItsKey() {
        var plan = partitioned(Inner$.MODULE$, true);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var top = (Project) rewritten;
        assertTrue(top.child() instanceof Join);
        var join = (Join) top.child();
        assertTrue(join.right() instanceof Project);
        var reduced = (Project) join.right();
        assertTrue(reduced.child() instanceof Aggregate);
        assertEquals(1, Catalyst.list(((Aggregate) reduced.child()).groupingExpressions()).size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesOuterJoinedWindowAlone() {
        var plan = partitioned(LeftOuter$.MODULE$, true);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesWindowJoinedOnKeyOnlyAlone() {
        var plan = partitioned(Inner$.MODULE$, false);
        assertEquals(plan, rule.apply(plan));
    }

    private static LogicalPlan partitioned(JoinType type, boolean bothColumns) {
        var base = Support.relation("B", 2, List.of(), new Random(1));
        var left = window(base);
        var leftAttrs = Catalyst.<Attribute>list(left.output());
        var rightWindow = Catalyst.fresh(window(base));
        var rightAttrs = Catalyst.<Attribute>list(rightWindow.output());
        var sum = AggregateExpression$.MODULE$.apply(new Max(rightAttrs.get(2)), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        var maxAlias = alias(sum, "w");
        var aggregate = new Aggregate(Catalyst.seq(List.<Expression>of(rightAttrs.get(0), rightAttrs.get(1))),
                Catalyst.seq(List.<NamedExpression>of(rightAttrs.get(0), rightAttrs.get(1), maxAlias)),
                rightWindow, Option.<AggregateHint>empty());
        var aggregateAttrs = Catalyst.<Attribute>list(aggregate.output());
        Expression cond = bothColumns
                ? new And(new EqualTo(leftAttrs.get(0), aggregateAttrs.get(0)),
                        new EqualTo(leftAttrs.get(1), aggregateAttrs.get(1)))
                : new EqualTo(leftAttrs.get(0), aggregateAttrs.get(0));
        var join = new Join(left, aggregate, type, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
        var joined = Catalyst.<Attribute>list(join.output());
        Expression top = Add$.MODULE$.apply(
                Add$.MODULE$.apply(joined.get(0), joined.get(1), Add$.MODULE$.apply$default$3()),
                Add$.MODULE$.apply(joined.get(2), joined.get(5), Add$.MODULE$.apply$default$3()),
                Add$.MODULE$.apply$default$3());
        return new Project(Catalyst.seq(List.<NamedExpression>of(alias(top, "t"))), join);
    }

    private static Project window(LogicalPlan relation) {
        var attrs = Catalyst.<Attribute>list(relation.output());
        var derived = Add$.MODULE$.apply(attrs.get(0), Literal$.MODULE$.apply(3), Add$.MODULE$.apply$default$3());
        return new Project(Catalyst.seq(List.<NamedExpression>of(attrs.get(0), alias(derived, "f"), attrs.get(1))),
                relation);
    }

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }
}
