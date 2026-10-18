package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.AttributeReference;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.types.BooleanType$;
import org.apache.spark.sql.types.Metadata;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateJoinNoColsLeftTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateJoinNoColsLeft.json"));

    private static LocalRelation oneRow() {
        var flag = new AttributeReference("_r", BooleanType$.MODULE$, false, Metadata.empty(),
                NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()));
        var rows = new ArrayList<InternalRow>();
        rows.add(new GenericInternalRow(new Object[]{true}));
        return new LocalRelation(Catalyst.seq(List.of(flag)), Catalyst.seq(rows), false, Match.noStream());
    }

    private static LocalRelation twoRows() {
        var flag = new AttributeReference("_r", BooleanType$.MODULE$, false, Metadata.empty(),
                NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()));
        var rows = new ArrayList<InternalRow>();
        rows.add(new GenericInternalRow(new Object[]{true}));
        rows.add(new GenericInternalRow(new Object[]{true}));
        return new LocalRelation(Catalyst.seq(List.of(flag)), Catalyst.seq(rows), false, Match.noStream());
    }

    @Test
    void dropsOneRowLeftWithNoColumnsIntoFilter() {
        var base = Support.relation("r", 1, List.of(), new Random(7));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var cond = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var left = new Project(Catalyst.<NamedExpression>seq(List.of()), oneRow());
        var plan = new Join(left, base, Inner$.MODULE$, Catalyst.some(cond), org.apache.spark.sql.catalyst.plans.logical.JoinHint.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base && f.condition().equals(cond));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var base = Support.relation("r", 1, List.of(), new Random(7));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var cond = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var left = new Project(Catalyst.<NamedExpression>seq(List.of()), oneRow());
        var plan = new Join(left, base, LeftOuter$.MODULE$, Catalyst.some(cond), org.apache.spark.sql.catalyst.plans.logical.JoinHint.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesLeftWithAColumnAlone() {
        var base = Support.relation("r", 1, List.of(), new Random(7));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var cond = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var values = oneRow();
        var left = new Project(Catalyst.<NamedExpression>seq(List.of(Catalyst.<Attribute>list(values.output()).get(0))), values);
        var plan = new Join(left, base, Inner$.MODULE$, Catalyst.some(cond), org.apache.spark.sql.catalyst.plans.logical.JoinHint.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTwoRowLeftAlone() {
        var base = Support.relation("r", 1, List.of(), new Random(7));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var cond = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var left = new Project(Catalyst.<NamedExpression>seq(List.of()), twoRows());
        var plan = new Join(left, base, Inner$.MODULE$, Catalyst.some(cond), org.apache.spark.sql.catalyst.plans.logical.JoinHint.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
