package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Sum;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
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

class JoinAggregateTransposeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("JoinAggregateTranspose.json"));

    private static LocalRelation table(String name, List<Integer> values) {
        var column = Support.attribute(name + "_0", false);
        var rows = new ArrayList<InternalRow>();
        for (int value : values) {
            rows.add(new GenericInternalRow(new Object[] {value}));
        }
        return new LocalRelation(Catalyst.<Attribute>seq(List.of(column)), Catalyst.<InternalRow>seq(rows),
                false, Match.noStream());
    }

    private static LogicalPlan unique(LogicalPlan relation) {
        var column = Catalyst.<Attribute>list(relation.output()).get(0);
        return new Aggregate(Catalyst.<Expression>seq(List.of(column)),
                Catalyst.<NamedExpression>seq(List.of(column)), relation, Option.<AggregateHint>empty());
    }

    private static Alias alias(Expression child, String name) {
        return Alias$.MODULE$.apply(child, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static LogicalPlan sumByGroup(LogicalPlan child, Attribute group, Attribute value) {
        var sum = AggregateExpression$.MODULE$.apply(new Sum(value), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        return new Aggregate(Catalyst.<Expression>seq(List.of(group)),
                Catalyst.<NamedExpression>seq(List.of(group, alias(sum, "s"))), child,
                Option.<AggregateHint>empty());
    }

    private static LogicalPlan join(LogicalPlan left, LogicalPlan right, JoinType type, Attribute leftKey,
            Attribute rightKey) {
        return new Join(left, right, type, Option.<Expression>apply(new EqualTo(leftKey, rightKey)),
                JoinHint$.MODULE$.NONE());
    }

    @Test
    void transposesSumBelowInnerJoinOnUniqueColumn() {
        var left = Support.relation("l", 2, List.of(0, 1), new Random(3));
        var leftColumns = Catalyst.<Attribute>list(left.output());
        var right = unique(table("r", List.of(0, 1, 2, 3)));
        var rightColumn = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = join(sumByGroup(left, leftColumns.get(0), leftColumns.get(1)), right, Inner$.MODULE$,
                leftColumns.get(0), rightColumn);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project project && project.child() instanceof Aggregate aggregate
                && aggregate.child() instanceof Join join && join.joinType() == Inner$.MODULE$);
        var names = Catalyst.<Attribute>list(rewritten.output()).stream().map(Attribute::name).toList();
        assertEquals(List.of("l_0", "s", "r_0"), names);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("l", 2, List.of(0, 1), new Random(3));
        var leftColumns = Catalyst.<Attribute>list(left.output());
        var right = unique(table("r", List.of(0, 1, 2, 3)));
        var rightColumn = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = join(sumByGroup(left, leftColumns.get(0), leftColumns.get(1)), right, LeftOuter$.MODULE$,
                leftColumns.get(0), rightColumn);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNonUniqueRightSideAlone() {
        var left = Support.relation("l", 2, List.of(0, 1), new Random(3));
        var leftColumns = Catalyst.<Attribute>list(left.output());
        var right = table("r", List.of(0, 0, 1));
        var rightColumn = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = join(sumByGroup(left, leftColumns.get(0), leftColumns.get(1)), right, Inner$.MODULE$,
                leftColumns.get(0), rightColumn);
        assertEquals(plan, rule.apply(plan));
    }
}
