package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Except;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateSetRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateSetRight.json"));

    private static LocalRelation emptyRelation(int arity) {
        var attrs = new ArrayList<Attribute>();
        for (int i = 0; i < arity; i++) {
            attrs.add(Support.attribute("e" + i, true));
        }
        return new LocalRelation(Catalyst.<Attribute>seq(attrs), Catalyst.<InternalRow>seq(List.of()), false,
                Match.noStream());
    }

    private static Project reordered(LocalRelation base) {
        var c = Catalyst.<Attribute>list(base.output());
        return new Project(Catalyst.<NamedExpression>seq(List.of(c.get(2), c.get(0), c.get(1))), base);
    }

    @Test
    void dropsEmptyRightOperandAndKeepsTheReorderedInput() {
        var base = Support.relation("r", 3, List.of(), new Random(1));
        var right = reordered(base);
        var plan = new Union(Catalyst.<LogicalPlan>seq(List.<LogicalPlan>of(right, emptyRelation(3))), false, false);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() == base);
        assertEquals(List.of("r_2", "r_0", "r_1"),
                Catalyst.<Attribute>list(rewritten.output()).stream().map(Attribute::name).toList());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesUnionOfTwoNonEmptyInputsAlone() {
        var base = Support.relation("r", 3, List.of(), new Random(1));
        var other = Support.relation("s", 3, List.of(), new Random(2));
        var plan = new Union(Catalyst.<LogicalPlan>seq(List.<LogicalPlan>of(reordered(base), other)), false, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesExceptWithEmptyRightOperandAlone() {
        var base = Support.relation("r", 3, List.of(), new Random(1));
        var plan = new Except(reordered(base), emptyRelation(3), true);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesEmptyLeftOperandAlone() {
        var base = Support.relation("r", 3, List.of(), new Random(1));
        var plan = new Union(Catalyst.<LogicalPlan>seq(List.<LogicalPlan>of(emptyRelation(3), reordered(base))),
                false, false);
        assertEquals(plan, rule.apply(plan));
    }
}
