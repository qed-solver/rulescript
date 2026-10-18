package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateZeroCardSelectTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateZeroCardSelect.json"));

    private static LocalRelation emptyRelation(String name, int arity) {
        var attrs = new ArrayList<Attribute>();
        for (int i = 0; i < arity; i++) {
            attrs.add(Support.attribute(name + "_" + i, false));
        }
        return new LocalRelation(Catalyst.seq(attrs), Catalyst.<InternalRow>seq(List.of()), false, Match.noStream());
    }

    @Test
    void collapsesStackedFiltersOverEmptyInputToEmpty() {
        var base = emptyRelation("t", 1);
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        var plan = new Filter(new LessThan(x, Literal$.MODULE$.apply(15)), inner);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof LocalRelation lr && lr.data().isEmpty());
        assertEquals(Catalyst.list(base.output()), Catalyst.list(((LocalRelation) rewritten).output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesStackedFiltersOverNonEmptyInputAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        var plan = new Filter(new LessThan(x, Literal$.MODULE$.apply(15)), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesASingleFilterOverEmptyInputAlone() {
        var base = emptyRelation("t", 1);
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesStackedFiltersOverEmptyInputOfOtherArityAlone() {
        var base = emptyRelation("t", 2);
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        var plan = new Filter(new LessThan(x, Literal$.MODULE$.apply(15)), inner);
        assertEquals(plan, rule.apply(plan));
    }
}
