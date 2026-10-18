package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ProjectInnerJoinValuesTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ProjectInnerJoinValues.json"));

    private static LocalRelation values(List<InternalRow> rows) {
        var attrs = List.of(Support.attribute("v0", false), Support.attribute("v1", false));
        return new LocalRelation(Catalyst.seq(attrs), Catalyst.seq(rows), false, Match.noStream());
    }

    private static LocalRelation oneRowValues() {
        return values(List.of(new GenericInternalRow(new Object[]{1, 2})));
    }

    private static List<Long> ids(scala.collection.Seq<Attribute> output) {
        return Catalyst.list(output).stream().map(a -> a.exprId().id()).toList();
    }

    @Test
    void rewritesInnerJoinWithOneRowValuesIntoFilterOverProject() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = oneRowValues();
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var v0 = Catalyst.<Attribute>list(right.output()).get(0);
        var v1 = Catalyst.<Attribute>list(right.output()).get(1);
        Expression on = new Or(new GreaterThan(x, v0), new LessThan(x, v1));
        var plan = new Join(left, right, Match.joinType("inner"), Catalyst.some(on), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var restore = assertInstanceOf(Project.class, rewritten);
        var filter = assertInstanceOf(Filter.class, restore.child());
        var project = assertInstanceOf(Project.class, filter.child());
        assertSame(left, project.child());
        assertEquals(3, Catalyst.list(project.projectList()).size());
        assertEquals(ids(plan.output()), ids(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinWithOneRowValuesAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(2));
        var right = oneRowValues();
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var v0 = Catalyst.<Attribute>list(right.output()).get(0);
        var v1 = Catalyst.<Attribute>list(right.output()).get(1);
        Expression on = new Or(new GreaterThan(x, v0), new LessThan(x, v1));
        var plan = new Join(left, right, Match.joinType("left"), Catalyst.some(on), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesInnerJoinWithTwoRowValuesAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(3));
        var right = values(List.of(new GenericInternalRow(new Object[]{1, 2}),
                new GenericInternalRow(new Object[]{3, 4})));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var v0 = Catalyst.<Attribute>list(right.output()).get(0);
        var v1 = Catalyst.<Attribute>list(right.output()).get(1);
        Expression on = new Or(new GreaterThan(x, v0), new LessThan(x, v1));
        var plan = new Join(left, right, Match.joinType("inner"), Catalyst.some(on), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
