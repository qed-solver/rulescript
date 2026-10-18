package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Multiply$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PruneInsertReturnColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneInsertReturnCols.json"));

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Expression plus(Expression a, Expression b) {
        return Add$.MODULE$.apply(a, b, Add$.MODULE$.apply$default$3());
    }

    private static Expression times(Expression a, Expression b) {
        return Multiply$.MODULE$.apply(a, b, Multiply$.MODULE$.apply$default$3());
    }

    @Test
    void prunesTheDroppedReturnColumn() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var ret = alias(plus(x, Literal$.MODULE$.apply(1)), "ret");
        var drop = alias(times(x, Literal$.MODULE$.apply(2)), "drop");
        var insert = new Project(Catalyst.seq(List.<NamedExpression>of(x, ret, drop)), base);
        var cols = Catalyst.<Attribute>list(insert.output());
        var top = alias(plus(cols.get(0), cols.get(1)), "top");
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(top)), insert);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project outer && outer.projectList().size() == 1);
        var inner = (Project) ((Project) rewritten).child();
        assertEquals(2, Catalyst.list(inner.projectList()).size());
        assertTrue(inner.child() == base);
        assertEquals(Catalyst.list(plan.output()).stream().map(a -> a.exprId()).toList(),
                Catalyst.list(rewritten.output()).stream().map(a -> a.exprId()).toList());
        var expected = new Project(Catalyst.seq(List.<NamedExpression>of(top)),
                new Project(Catalyst.seq(List.<NamedExpression>of(x, ret)), base));
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesTopReadingTheDroppedColumnAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var ret = alias(plus(x, Literal$.MODULE$.apply(1)), "ret");
        var drop = alias(times(x, Literal$.MODULE$.apply(2)), "drop");
        var insert = new Project(Catalyst.seq(List.<NamedExpression>of(x, ret, drop)), base);
        var cols = Catalyst.<Attribute>list(insert.output());
        var top = alias(plus(cols.get(0), cols.get(2)), "top");
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(top)), insert);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAProjectDirectlyOverAScanAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var top = alias(plus(x, Literal$.MODULE$.apply(1)), "top");
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(top)), base);
        assertEquals(plan, rule.apply(plan));
    }
}
