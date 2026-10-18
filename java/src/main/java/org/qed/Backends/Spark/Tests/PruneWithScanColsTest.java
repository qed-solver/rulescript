package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PruneWithScanColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneWithScanCols.json"));

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Expression sum(Expression left, Expression right) {
        return Add$.MODULE$.apply(left, right, Add$.MODULE$.apply$default$3());
    }

    @Test
    void prunesUnusedThirdColumnUnderProjection() {
        var base = Support.relation("w", 3, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(sum(cols.get(0), cols.get(1)), "t"))), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project outer && outer.child() instanceof Project inner
                && inner.child() == base);
        var inner = (Project) ((Project) rewritten).child();
        assertEquals(List.of(cols.get(0).exprId(), cols.get(1).exprId()),
                Catalyst.list(inner.output()).stream().map(a -> a.exprId()).toList());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsPlanWhenProjectionReadsUnusedThirdColumn() {
        var base = Support.relation("w", 3, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(sum(cols.get(0), cols.get(2)), "t"))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsPlanWhenScanHasTwoColumns() {
        var base = Support.relation("w", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(sum(cols.get(0), cols.get(1)), "t"))), base);
        assertEquals(plan, rule.apply(plan));
    }
}
