package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LateralSubquery;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.OuterReference;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LateralJoin;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewriteCorrelatedScalarSubqueryTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("RewriteCorrelatedScalarSubquery.json"));

    private static NamedExpression maxAlias(NamedExpression column) {
        var max = AggregateExpression$.MODULE$.apply(new Max((Expression) column), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        return Alias$.MODULE$.apply(max, "agg", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static LogicalPlan correlated(LogicalPlan left, LogicalPlan right, Expression correlation,
                                          org.apache.spark.sql.catalyst.expressions.Attribute outer,
                                          org.apache.spark.sql.catalyst.plans.JoinType type) {
        var subquery = new LateralSubquery(new Filter(correlation, right), Catalyst.<Expression>seq(List.of(outer)),
                NamedExpression$.MODULE$.newExprId(), Catalyst.<Expression>seq(List.of()),
                Option.<org.apache.spark.sql.catalyst.plans.logical.HintInfo>empty());
        return new LateralJoin(left, subquery, type, Option.<Expression>empty());
    }

    @Test
    void rewritesCorrelatedScalarMaxIntoJoinAndAggregate() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l0 = Catalyst.<org.apache.spark.sql.catalyst.expressions.Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<org.apache.spark.sql.catalyst.expressions.Attribute>list(right.output()).get(0);
        var correlation = new EqualTo(new OuterReference(l0), r0);
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(l0)),
                Catalyst.<NamedExpression>seq(List.of(l0, maxAlias(r0))),
                correlated(left, right, correlation, l0, LeftOuter$.MODULE$),
                Option.empty());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate);
        var aggregate = (Aggregate) rewritten;
        assertTrue(aggregate.child() instanceof Join);
        var join = (Join) aggregate.child();
        assertEquals(LeftOuter$.MODULE$, join.joinType());
        assertEquals(left, join.left());
        assertEquals(right, join.right());
        assertEquals(1, Catalyst.list(aggregate.groupingExpressions()).size());
        assertEquals(2, Catalyst.list(aggregate.aggregateExpressions()).size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesInnerLateralCorrelationAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l0 = Catalyst.<org.apache.spark.sql.catalyst.expressions.Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<org.apache.spark.sql.catalyst.expressions.Attribute>list(right.output()).get(0);
        var correlation = new EqualTo(new OuterReference(l0), r0);
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(l0)),
                Catalyst.<NamedExpression>seq(List.of(l0, maxAlias(r0))),
                correlated(left, right, correlation, l0, Inner$.MODULE$),
                Option.empty());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNonEqualityCorrelationAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l0 = Catalyst.<org.apache.spark.sql.catalyst.expressions.Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<org.apache.spark.sql.catalyst.expressions.Attribute>list(right.output()).get(0);
        var correlation = new GreaterThan(new OuterReference(l0), r0);
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(l0)),
                Catalyst.<NamedExpression>seq(List.of(l0, maxAlias(r0))),
                correlated(left, right, correlation, l0, LeftOuter$.MODULE$),
                Option.empty());
        assertEquals(plan, rule.apply(plan));
    }
}
