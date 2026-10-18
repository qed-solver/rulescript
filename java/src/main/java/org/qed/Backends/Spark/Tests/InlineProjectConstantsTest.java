package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
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

class InlineProjectConstantsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("InlineProjectConstants.json"));

    private static LogicalPlan unique(LogicalPlan relation) {
        var column = Catalyst.<Attribute>list(relation.output()).get(0);
        return new Aggregate(Catalyst.seq(List.<Expression>of(column)), Catalyst.seq(List.<NamedExpression>of(column)),
                relation, Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    private static NamedExpression alias(Expression value) {
        return Alias$.MODULE$.apply(value, "p0", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void inlinesTheConstantIntoTheProjection() {
        var random = new Random(1);
        var source = Support.relation("Source", 1, List.of(), random);
        var constant = unique(Support.relation("Const", 1, List.of(0), random));
        var a = Catalyst.<Attribute>list(source.output()).get(0);
        var c = Catalyst.<Attribute>list(constant.output()).get(0);
        var join = new Join(source, constant, Inner$.MODULE$, Option.<Expression>apply(new EqualTo(a, c)),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.seq(List.of(alias(new Add(a, Literal$.MODULE$.apply(1))))), join);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var project = (Project) rewritten;
        assertEquals(join, project.child());
        var item = Catalyst.<NamedExpression>list(project.projectList()).get(0);
        assertTrue(item instanceof Alias);
        assertEquals(new Add(c, Literal$.MODULE$.apply(1)), ((Alias) item).child());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAnOuterJoinAlone() {
        var random = new Random(2);
        var source = Support.relation("Source", 1, List.of(), random);
        var constant = unique(Support.relation("Const", 1, List.of(0), random));
        var a = Catalyst.<Attribute>list(source.output()).get(0);
        var c = Catalyst.<Attribute>list(constant.output()).get(0);
        var join = new Join(source, constant, LeftOuter$.MODULE$, Option.<Expression>apply(new EqualTo(a, c)),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.seq(List.of(alias(new Add(a, Literal$.MODULE$.apply(1))))), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesANullableConstantAlone() {
        var random = new Random(3);
        var source = Support.relation("Source", 1, List.of(), random);
        var constant = unique(Support.relation("Const", 1, List.of(), random));
        var a = Catalyst.<Attribute>list(source.output()).get(0);
        var c = Catalyst.<Attribute>list(constant.output()).get(0);
        var join = new Join(source, constant, Inner$.MODULE$, Option.<Expression>apply(new EqualTo(a, c)),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.seq(List.of(alias(new Add(a, Literal$.MODULE$.apply(1))))), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesANonUniqueConstantAlone() {
        var random = new Random(4);
        var source = Support.relation("Source", 1, List.of(), random);
        var constant = new org.apache.spark.sql.catalyst.plans.logical.LocalRelation(
                Catalyst.<Attribute>seq(List.of(Support.attribute("Const_0", false))),
                Catalyst.<org.apache.spark.sql.catalyst.InternalRow>seq(List.of(
                        new org.apache.spark.sql.catalyst.expressions.GenericInternalRow(new Object[] {1}),
                        new org.apache.spark.sql.catalyst.expressions.GenericInternalRow(new Object[] {1}))),
                false, Match.noStream());
        var a = Catalyst.<Attribute>list(source.output()).get(0);
        var c = Catalyst.<Attribute>list(constant.output()).get(0);
        var join = new Join(source, constant, Inner$.MODULE$, Option.<Expression>apply(new EqualTo(a, c)),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.seq(List.of(alias(new Add(a, Literal$.MODULE$.apply(1))))), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesANonEqualityJoinAlone() {
        var random = new Random(5);
        var source = Support.relation("Source", 1, List.of(), random);
        var constant = unique(Support.relation("Const", 1, List.of(0), random));
        var a = Catalyst.<Attribute>list(source.output()).get(0);
        var c = Catalyst.<Attribute>list(constant.output()).get(0);
        var join = new Join(source, constant, Inner$.MODULE$, Option.<Expression>apply(new LessThan(a, c)),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.seq(List.of(alias(new Add(a, Literal$.MODULE$.apply(1))))), join);
        assertEquals(plan, rule.apply(plan));
    }
}
