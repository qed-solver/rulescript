package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateFunction;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.expressions.aggregate.Min;
import org.apache.spark.sql.catalyst.plans.Inner$;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollapseWindowTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("CollapseWindow.json"));

    private static NamedExpression named(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Aggregate perKey(LogicalPlan source, List<String> names, List<AggregateFunction> functions) {
        var key = Catalyst.<Attribute>list(source.output()).get(0);
        var items = new ArrayList<NamedExpression>();
        items.add(named(key, "k"));
        for (int i = 0; i < functions.size(); i++) {
            var aggregate = AggregateExpression$.MODULE$.apply(functions.get(i), Complete$.MODULE$, false,
                    Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
            items.add(named(aggregate, names.get(i)));
        }
        return new Aggregate(Catalyst.<Expression>seq(List.<Expression>of(key)), Catalyst.<NamedExpression>seq(items),
                source, Option.<AggregateHint>empty());
    }

    private static Join equiJoin(LogicalPlan left, LogicalPlan right, Attribute leftKey, Attribute rightKey,
            org.apache.spark.sql.catalyst.plans.JoinType type) {
        return new Join(left, right, type, Option.<Expression>apply(new EqualTo(leftKey, rightKey)),
                JoinHint$.MODULE$.NONE());
    }

    private static List<Attribute> columns(LogicalPlan plan) {
        return Catalyst.<Attribute>list(plan.output());
    }

    @Test
    void mergesTwoWindowsOverTheSamePartition() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(1));

        var innerCopy = Catalyst.fresh(base);
        var innerAggregate = perKey(innerCopy, List.of("f1"), List.of(new Max(columns(innerCopy).get(1))));
        var inner = equiJoin(base, innerAggregate, columns(base).get(0), columns(innerAggregate).get(0),
                Inner$.MODULE$);
        var innerColumns = columns(inner);
        var childWindow = new Project(Catalyst.<NamedExpression>seq(List.of(innerColumns.get(0),
                innerColumns.get(1), innerColumns.get(3))), inner);

        var outerCopy = Catalyst.fresh(base);
        var outerAggregate = perKey(outerCopy, List.of("f2"), List.of(new Min(columns(outerCopy).get(1))));
        var outer = equiJoin(childWindow, outerAggregate, columns(childWindow).get(0),
                columns(outerAggregate).get(0), Inner$.MODULE$);
        var outerColumns = columns(outer);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(outerColumns.get(0), outerColumns.get(1),
                outerColumns.get(2), outerColumns.get(4))), outer);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() instanceof Join j && j.right() instanceof Aggregate a
                && Catalyst.list(a.aggregateExpressions()).size() == 3
                && Catalyst.list(p.projectList()).size() == 4);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAnOuterLeftJoinAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(1));

        var innerCopy = Catalyst.fresh(base);
        var innerAggregate = perKey(innerCopy, List.of("f1"), List.of(new Max(columns(innerCopy).get(1))));
        var inner = equiJoin(base, innerAggregate, columns(base).get(0), columns(innerAggregate).get(0),
                Inner$.MODULE$);
        var innerColumns = columns(inner);
        var childWindow = new Project(Catalyst.<NamedExpression>seq(List.of(innerColumns.get(0),
                innerColumns.get(1), innerColumns.get(3))), inner);

        var outerCopy = Catalyst.fresh(base);
        var outerAggregate = perKey(outerCopy, List.of("f2"), List.of(new Min(columns(outerCopy).get(1))));
        var outer = equiJoin(childWindow, outerAggregate, columns(childWindow).get(0),
                columns(outerAggregate).get(0), LeftOuter$.MODULE$);
        var outerColumns = columns(outer);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(outerColumns.get(0), outerColumns.get(1),
                outerColumns.get(2), outerColumns.get(4))), outer);

        assertEquals(plan, rule.apply(plan));
    }
}
