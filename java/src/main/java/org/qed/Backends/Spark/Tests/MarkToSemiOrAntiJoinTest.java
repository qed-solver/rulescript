package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkToSemiOrAntiJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("MarkToSemiOrAntiJoin.json"));

    private static NamedExpression named(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Join join(LogicalPlan left, LogicalPlan right, JoinType type, Expression cond) {
        return new Join(left, right, type, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
    }

    private static LogicalPlan markedUnion(JoinType firstType, LogicalPlan left, LogicalPlan right) {
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new EqualTo(l, r);
        var first = new Project(Catalyst.seq(List.<NamedExpression>of(l, named(Literal$.MODULE$.TrueLiteral(), "marker"))),
                join(left, right, firstType, cond));
        var second = new Project(Catalyst.seq(List.<NamedExpression>of(l, named(Literal$.MODULE$.FalseLiteral(), "marker"))),
                join(left, right, LeftAnti$.MODULE$, cond));
        return new Union(Catalyst.seq(List.of(first, second)), false, false);
    }

    @Test
    void rewritesMarkedUnionOfSemiAndAntiIntoSemiJoin() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var union = markedUnion(LeftSemi$.MODULE$, left, right);
        var value = Catalyst.<Attribute>list(union.output()).get(0);
        var marker = Catalyst.<Attribute>list(union.output()).get(1);
        var filter = new Filter(new And(marker, new GreaterThan(value, Literal$.MODULE$.apply(1))), union);
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(value)), filter);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() instanceof Filter f && f.child() instanceof Join j
                && j.joinType().equals(LeftSemi$.MODULE$));
        Assumptions.assumeTrue(Support.canExecute());
        var rows = Support.run(plan);
        assertFalse(rows.isEmpty());
        assertEquals(rows, Support.run(rewritten));
    }

    @Test
    void leavesFilterWithoutMarkerConjunctAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var union = markedUnion(LeftSemi$.MODULE$, left, right);
        var value = Catalyst.<Attribute>list(union.output()).get(0);
        var filter = new Filter(new GreaterThan(value, Literal$.MODULE$.apply(1)), union);
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(value)), filter);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesInnerJoinBranchAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var union = markedUnion(Inner$.MODULE$, left, right);
        var value = Catalyst.<Attribute>list(union.output()).get(0);
        var marker = Catalyst.<Attribute>list(union.output()).get(1);
        var filter = new Filter(new And(marker, new GreaterThan(value, Literal$.MODULE$.apply(1))), union);
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(value)), filter);
        assertEquals(plan, rule.apply(plan));
    }
}
