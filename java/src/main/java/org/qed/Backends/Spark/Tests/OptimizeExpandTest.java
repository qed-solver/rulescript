package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Count;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class OptimizeExpandTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("OptimizeExpand.json"));

    private static Attribute column(LogicalPlan plan, int ordinal) {
        return Catalyst.<Attribute>list(plan.output()).get(ordinal);
    }

    private static NamedExpression named(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Expression shifted(Expression value, int by) {
        return Add$.MODULE$.apply(value, Literal$.MODULE$.apply(by), Add$.MODULE$.apply$default$3());
    }

    private static LogicalPlan branch(LogicalPlan child, int shift) {
        var items = List.<NamedExpression>of(
                column(child, 0),
                named(shifted(column(child, 1), shift), "c1"),
                named(shifted(column(child, 2), shift), "c2"),
                column(child, 3));
        return new Project(Catalyst.seq(items), child);
    }

    private static LogicalPlan expand(LogicalPlan child, int branches) {
        var inputs = new java.util.ArrayList<LogicalPlan>();
        for (int shift = 0; shift < branches; shift++) {
            inputs.add(branch(child, shift));
        }
        return new Union(Catalyst.seq(inputs), false, false);
    }

    private static Aggregate aggregate(LogicalPlan child, List<? extends Expression> groups,
                                       List<? extends NamedExpression> aggregates) {
        return new Aggregate(Catalyst.<Expression>seq(groups), Catalyst.<NamedExpression>seq(aggregates), child,
                Option.<AggregateHint>empty());
    }

    private static NamedExpression count(Expression value, boolean distinct) {
        var function = new Count(Catalyst.seq(List.of(value)));
        return named(AggregateExpression$.MODULE$.apply(function, Complete$.MODULE$, distinct,
                Option.empty(), NamedExpression$.MODULE$.newExprId()), "Cnt");
    }

    private static NamedExpression outerMax(Expression value) {
        return named(AggregateExpression$.MODULE$.apply(new Max(value), Complete$.MODULE$, false,
                Option.empty(), NamedExpression$.MODULE$.newExprId()), "Agg2");
    }

    private static LogicalPlan stack(LogicalPlan expanded, boolean distinctCount) {
        var pairs = aggregate(expanded,
                List.of(column(expanded, 0), column(expanded, 1), column(expanded, 2), column(expanded, 3)),
                List.of(column(expanded, 0), column(expanded, 1), column(expanded, 2), column(expanded, 3)));
        var counts = aggregate(pairs,
                List.of(column(pairs, 0), column(pairs, 1), column(pairs, 2)),
                List.of(column(pairs, 0), column(pairs, 1), column(pairs, 2),
                        count(column(pairs, 3), distinctCount)));
        return aggregate(counts, List.of(column(counts, 0)),
                List.of(column(counts, 0), outerMax(column(counts, 3))));
    }

    @Test
    void rewritesDedupBelowExpand() {
        var child = Support.relation("child", 4, List.of(), new Random(1));
        var plan = stack(expand(child, 3), false);
        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var outer = assertInstanceOf(Aggregate.class, rewritten);
        assertEquals(1, Catalyst.list(outer.groupingExpressions()).size());
        var counts = assertInstanceOf(Aggregate.class, outer.child());
        assertEquals(3, Catalyst.list(counts.groupingExpressions()).size());
        var pairs = assertInstanceOf(Aggregate.class, counts.child());
        assertEquals(4, Catalyst.list(pairs.groupingExpressions()).size());
        assertEquals(4, Catalyst.list(pairs.output()).size());
        var union = assertInstanceOf(Union.class, pairs.child());
        var branches = Catalyst.list(union.children());
        assertEquals(3, branches.size());
        for (var child_ : branches) {
            var projected = assertInstanceOf(Project.class, child_);
            var dedup = assertInstanceOf(Aggregate.class, projected.child());
            assertEquals(4, Catalyst.list(dedup.groupingExpressions()).size());
            assertEquals(4, Catalyst.list(dedup.output()).size());
            assertSame(child, dedup.child());
        }

        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesTwoBranchExpandAlone() {
        var child = Support.relation("child", 4, List.of(), new Random(1));
        var plan = stack(expand(child, 2), false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDistinctInnerCountAlone() {
        var child = Support.relation("child", 4, List.of(), new Random(1));
        var plan = stack(expand(child, 3), true);
        assertEquals(plan, rule.apply(plan));
    }
}
