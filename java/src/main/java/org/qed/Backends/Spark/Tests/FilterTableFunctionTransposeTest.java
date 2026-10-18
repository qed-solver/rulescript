package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterTableFunctionTransposeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FilterTableFunctionTranspose.json"));

    @Test
    void pushesFilterBelowTableFunctionJoin() {
        var s = Support.relation("s", 2, List.of(), new Random(1));
        var t = Support.relation("t", 1, List.of(), new Random(2));
        var sCols = Catalyst.<Attribute>list(s.output());
        var tCols = Catalyst.<Attribute>list(t.output());
        var join = new Join(s, t, Inner$.MODULE$,
                Option.<Expression>apply(new EqualTo(sCols.get(0), tCols.get(0))), JoinHint$.MODULE$.NONE());
        var projected = new Project(Catalyst.<NamedExpression>seq(List.of(sCols.get(0), sCols.get(1))), join);
        var projCols = Catalyst.<Attribute>list(projected.output());
        var plan = new Filter(new GreaterThan(projCols.get(0), projCols.get(1)), projected);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() instanceof Join j
                && j.left() instanceof Filter f && f.child() == s);
        assertEquals(Catalyst.list(plan.output()), Catalyst.list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterTableFunctionAlone() {
        var s = Support.relation("s", 2, List.of(), new Random(1));
        var t = Support.relation("t", 1, List.of(), new Random(2));
        var sCols = Catalyst.<Attribute>list(s.output());
        var tCols = Catalyst.<Attribute>list(t.output());
        var join = new Join(s, t, LeftOuter$.MODULE$,
                Option.<Expression>apply(new EqualTo(sCols.get(0), tCols.get(0))), JoinHint$.MODULE$.NONE());
        var projected = new Project(Catalyst.<NamedExpression>seq(List.of(sCols.get(0), sCols.get(1))), join);
        var projCols = Catalyst.<Attribute>list(projected.output());
        var plan = new Filter(new GreaterThan(projCols.get(0), projCols.get(1)), projected);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesSwappedProjectionAlone() {
        var s = Support.relation("s", 2, List.of(), new Random(1));
        var t = Support.relation("t", 1, List.of(), new Random(2));
        var sCols = Catalyst.<Attribute>list(s.output());
        var tCols = Catalyst.<Attribute>list(t.output());
        var join = new Join(s, t, Inner$.MODULE$,
                Option.<Expression>apply(new EqualTo(sCols.get(0), tCols.get(0))), JoinHint$.MODULE$.NONE());
        var projected = new Project(Catalyst.<NamedExpression>seq(List.of(sCols.get(1), sCols.get(0))), join);
        var projCols = Catalyst.<Attribute>list(projected.output());
        var plan = new Filter(new GreaterThan(projCols.get(0), projCols.get(1)), projected);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesThreeColumnTableFunctionAlone() {
        var s = Support.relation("s", 3, List.of(), new Random(1));
        var t = Support.relation("t", 1, List.of(), new Random(2));
        var sCols = Catalyst.<Attribute>list(s.output());
        var tCols = Catalyst.<Attribute>list(t.output());
        var join = new Join(s, t, Inner$.MODULE$,
                Option.<Expression>apply(new EqualTo(sCols.get(0), tCols.get(0))), JoinHint$.MODULE$.NONE());
        var projected = new Project(Catalyst.<NamedExpression>seq(List.of(sCols.get(0), sCols.get(1))), join);
        var projCols = Catalyst.<Attribute>list(projected.output());
        var plan = new Filter(new GreaterThan(projCols.get(0), projCols.get(1)), projected);
        assertEquals(plan, rule.apply(plan));
    }
}
