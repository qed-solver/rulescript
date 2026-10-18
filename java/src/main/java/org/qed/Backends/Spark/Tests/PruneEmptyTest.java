package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PruneEmptyTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneEmpty.json"));

    private static LocalRelation emptyRelation(String name, int arity) {
        var attrs = new java.util.ArrayList<Attribute>();
        for (int i = 0; i < arity; i++) {
            attrs.add(Support.attribute(name + "_" + i, true));
        }
        return new LocalRelation(Catalyst.seq(attrs), Catalyst.<InternalRow>seq(List.of()), false, Match.noStream());
    }

    @Test
    void filterOverEmptyBecomesEmpty() {
        var base = emptyRelation("t", 1);
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof LocalRelation lr && lr.data().isEmpty());
        assertEquals(Catalyst.list(plan.output()).stream().map(a -> a.exprId()).toList(),
                Catalyst.list(rewritten.output()).stream().map(a -> a.exprId()).toList());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void filterOverNonEmptyRelationIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void projectOverEmptyIsLeftAlone() {
        var base = emptyRelation("t", 1);
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(x)), base);
        assertEquals(plan, rule.apply(plan));
    }
}
