package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PruneSelectColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneSelectCols.json"));

    private static NamedExpression top(Expression value) {
        return Alias$.MODULE$.apply(value, "top", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void prunesTheUnusedThirdColumn() {
        var base = Support.relation("t", 3, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var cond = new GreaterThan(cols.get(0), cols.get(1));
        var plan = new Project(Catalyst.seq(List.of(top(new Add(cols.get(0), cols.get(1))))),
                new Filter(cond, base));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project outer && outer.child() instanceof Filter filter
                && filter.child() instanceof Project pruned && pruned.child() == base
                && Catalyst.list(pruned.projectList()).size() == 2);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsPlanWhenFilterReadsTheThirdColumn() {
        var base = Support.relation("t", 3, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var cond = new GreaterThan(cols.get(0), cols.get(2));
        var plan = new Project(Catalyst.seq(List.of(top(new Add(cols.get(0), cols.get(1))))),
                new Filter(cond, base));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsPlanWhenInputHasTwoColumns() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var cond = new GreaterThan(cols.get(0), cols.get(1));
        var plan = new Project(Catalyst.seq(List.of(top(new Add(cols.get(0), cols.get(1))))),
                new Filter(cond, base));
        assertEquals(plan, rule.apply(plan));
    }
}
