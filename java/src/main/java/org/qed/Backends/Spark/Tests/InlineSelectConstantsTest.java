package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlineSelectConstantsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("InlineSelectConstants.json"));

    private static LogicalPlan constant(Attribute column, Integer... values) {
        var rows = new ArrayList<InternalRow>();
        for (var value : values) {
            rows.add(new GenericInternalRow(new Object[] {value}));
        }
        var relation = new LocalRelation(Catalyst.<Attribute>seq(List.of(column)), Catalyst.<InternalRow>seq(rows),
                false, Match.noStream());
        return new Aggregate(Catalyst.<Expression>seq(List.of(column)), Catalyst.<NamedExpression>seq(List.of(column)),
                relation, scala.Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    private static LogicalPlan plainConstant(Attribute column, Integer... values) {
        var rows = new ArrayList<InternalRow>();
        for (var value : values) {
            rows.add(new GenericInternalRow(new Object[] {value}));
        }
        return new LocalRelation(Catalyst.<Attribute>seq(List.of(column)), Catalyst.<InternalRow>seq(rows), false,
                Match.noStream());
    }

    @Test
    void inlinesConstantColumnIntoFilterOnVariable() {
        var source = Support.relation("src", 1, List.of(), new Random(7));
        var a = Catalyst.<Attribute>list(source.output()).get(0);
        var c = Support.attribute("c", false);
        var konst = constant(c, 2);
        var join = new Join(source, konst, Inner$.MODULE$, Catalyst.<Expression>some(new EqualTo(a, c)),
                JoinHint$.MODULE$.NONE());
        var plan = new Filter(new GreaterThan(a, Literal$.MODULE$.apply(1)), join);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child().equals(join)
                && f.condition().equals(new GreaterThan(c, Literal$.MODULE$.apply(1))));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var source = Support.relation("src", 1, List.of(), new Random(7));
        var a = Catalyst.<Attribute>list(source.output()).get(0);
        var c = Support.attribute("c", false);
        var join = new Join(source, constant(c, 2), LeftOuter$.MODULE$, Catalyst.<Expression>some(new EqualTo(a, c)),
                JoinHint$.MODULE$.NONE());
        var plan = new Filter(new GreaterThan(a, Literal$.MODULE$.apply(1)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNonEqualityJoinAlone() {
        var source = Support.relation("src", 1, List.of(), new Random(7));
        var a = Catalyst.<Attribute>list(source.output()).get(0);
        var c = Support.attribute("c", false);
        var join = new Join(source, constant(c, 2), Inner$.MODULE$, Catalyst.<Expression>some(new LessThan(a, c)),
                JoinHint$.MODULE$.NONE());
        var plan = new Filter(new GreaterThan(a, Literal$.MODULE$.apply(1)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNullableConstantAlone() {
        var source = Support.relation("src", 1, List.of(), new Random(7));
        var a = Catalyst.<Attribute>list(source.output()).get(0);
        var c = Support.attribute("c", true);
        var join = new Join(source, plainConstant(c, 2), Inner$.MODULE$, Catalyst.<Expression>some(new EqualTo(a, c)),
                JoinHint$.MODULE$.NONE());
        var plan = new Filter(new GreaterThan(a, Literal$.MODULE$.apply(1)), join);
        assertEquals(plan, rule.apply(plan));
    }
}
