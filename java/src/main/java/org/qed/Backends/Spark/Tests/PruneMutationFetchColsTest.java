package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PruneMutationFetchColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneMutationFetchCols.json"));

    private static Expression plus(Expression left, Expression right) {
        return Add$.MODULE$.apply(left, right, Add$.MODULE$.apply$default$3());
    }

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void movesTheNeededColumnIntoAnInnerProjection() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var columns = Catalyst.<Attribute>list(base.output());
        var first = columns.get(0);
        var plan = new Project(Catalyst.seq(List.of(alias(plus(first, Literal$.MODULE$.apply(5)), "m"))), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var outer = (Project) rewritten;
        assertTrue(outer.child() instanceof Project);
        var inner = (Project) outer.child();
        assertTrue(inner.child() == base);
        var innerItems = Catalyst.list(inner.projectList());
        assertEquals(1, innerItems.size());
        assertEquals(first.exprId(), innerItems.get(0).exprId());
        var outerItems = Catalyst.list(outer.projectList());
        assertEquals(1, outerItems.size());
        assertTrue(((Alias) outerItems.get(0)).child() instanceof Add);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAProjectionThatReadsTheSecondColumnAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(2));
        var columns = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.seq(List.of(alias(plus(columns.get(0), columns.get(1)), "m"))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAProjectionOverThreeColumnsAlone() {
        var base = Support.relation("t", 3, List.of(), new Random(3));
        var columns = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.seq(List.of(alias(plus(columns.get(0), Literal$.MODULE$.apply(5)), "m"))), base);
        assertEquals(plan, rule.apply(plan));
    }
}
