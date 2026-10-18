package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PruneOrdinalityColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneOrdinalityCols.json"));

    private static NamedExpression alias(Expression e, String name) {
        return Alias$.MODULE$.apply(e, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void prunesUnusedColumnBelowOrdinality() {
        var base = Support.relation("t", 3, List.of(), new Random(7));
        var cols = Catalyst.<Attribute>list(base.output());
        var c0 = cols.get(0);
        var c1 = cols.get(1);
        var c2 = cols.get(2);
        var middle = new Project(Catalyst.<NamedExpression>seq(List.of(c0, c1, c2,
                alias(new Add(c0, c1), "o"))), base);
        var o = Catalyst.<Attribute>list(middle.output()).get(3);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(
                alias(new Add(new Add(c0, c1), o), "g"))), middle);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var outer = (Project) rewritten;
        assertTrue(outer.child() instanceof Project);
        var ord = (Project) outer.child();
        assertTrue(ord.child() instanceof Project);
        var pruned = (Project) ord.child();
        assertTrue(pruned.child() == base);
        assertEquals(1, Catalyst.list(outer.projectList()).size());
        assertEquals(3, Catalyst.list(ord.projectList()).size());
        assertEquals(2, Catalyst.list(pruned.projectList()).size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesOrdinalityOverPrunedColumnAlone() {
        var base = Support.relation("t", 3, List.of(), new Random(7));
        var cols = Catalyst.<Attribute>list(base.output());
        var c0 = cols.get(0);
        var c1 = cols.get(1);
        var c2 = cols.get(2);
        var middle = new Project(Catalyst.<NamedExpression>seq(List.of(c0, c1, c2,
                alias(new Add(c0, c2), "o"))), base);
        var o = Catalyst.<Attribute>list(middle.output()).get(3);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(
                alias(new Add(new Add(c0, c1), o), "g"))), middle);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesOuterProjectOverPrunedColumnAlone() {
        var base = Support.relation("t", 3, List.of(), new Random(7));
        var cols = Catalyst.<Attribute>list(base.output());
        var c0 = cols.get(0);
        var c1 = cols.get(1);
        var c2 = cols.get(2);
        var middle = new Project(Catalyst.<NamedExpression>seq(List.of(c0, c1, c2,
                alias(new Add(c0, c1), "o"))), base);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(
                alias(new Add(new Add(c0, c1), c2), "g"))), middle);
        assertEquals(plan, rule.apply(plan));
    }
}
