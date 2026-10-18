package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.AttributeReference;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.types.BooleanType$;
import org.apache.spark.sql.types.Metadata;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimizeOneRowRelationSubqueryTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("OptimizeOneRowRelationSubquery.json"));

    private static LocalRelation oneRowTrue() {
        var column = new AttributeReference("d", BooleanType$.MODULE$, false, Metadata.empty(),
                NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()));
        var rows = List.<InternalRow>of(new GenericInternalRow(new Object[]{true}));
        return new LocalRelation(Catalyst.seq(List.<Attribute>of(column)), Catalyst.seq(rows), false, Match.noStream());
    }

    private static LocalRelation twoRowsTrue() {
        var column = new AttributeReference("d", BooleanType$.MODULE$, false, Metadata.empty(),
                NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()));
        var rows = List.<InternalRow>of(new GenericInternalRow(new Object[]{true}),
                new GenericInternalRow(new Object[]{true}));
        return new LocalRelation(Catalyst.seq(List.<Attribute>of(column)), Catalyst.seq(rows), false, Match.noStream());
    }

    private static LogicalPlan leaf(LogicalPlan plan) {
        var current = plan;
        while (current instanceof Project p) {
            current = p.child();
        }
        return current;
    }

    @Test
    void replacesTheOneRowJoinByAConstant() {
        var base = Support.relation("L", 1, List.of(), new Random(1));
        var join = new Join(base, oneRowTrue(), Inner$.MODULE$, Option.<Expression>apply(Literal$.MODULE$.TrueLiteral()),
                JoinHint$.MODULE$.NONE());
        var columns = Catalyst.<Attribute>list(join.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(columns.get(0), columns.get(1))), join);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        assertEquals(2, Catalyst.list(((Project) rewritten).projectList()).size());
        assertEquals(base, leaf(rewritten));
        assertEquals(Catalyst.list(plan.output()).stream().map(a -> a.exprId()).toList(),
                Catalyst.list(rewritten.output()).stream().map(a -> a.exprId()).toList());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsAJoinWithAnOuterJoinKind() {
        var base = Support.relation("L", 1, List.of(), new Random(1));
        var join = new Join(base, oneRowTrue(), LeftOuter$.MODULE$, Option.<Expression>apply(Literal$.MODULE$.TrueLiteral()),
                JoinHint$.MODULE$.NONE());
        var columns = Catalyst.<Attribute>list(join.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(columns.get(0), columns.get(1))), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsAJoinWithATwoRowRelation() {
        var base = Support.relation("L", 1, List.of(), new Random(1));
        var join = new Join(base, twoRowsTrue(), Inner$.MODULE$, Option.<Expression>apply(Literal$.MODULE$.TrueLiteral()),
                JoinHint$.MODULE$.NONE());
        var columns = Catalyst.<Attribute>list(join.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(columns.get(0), columns.get(1))), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsAJoinWithANonTrueCondition() {
        var base = Support.relation("L", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var join = new Join(base, oneRowTrue(), Inner$.MODULE$,
                Option.<Expression>apply(new GreaterThan(x, Literal$.MODULE$.apply(2))), JoinHint$.MODULE$.NONE());
        var columns = Catalyst.<Attribute>list(join.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(columns.get(0), columns.get(1))), join);
        assertEquals(plan, rule.apply(plan));
    }
}
