package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldComparisonTest {
    private final org.qed.spark.RuleScriptRule rule =
            org.qed.spark.RuleScriptRule.load(Support.generated().resolve("FoldComparison.json"));

    @Test
    void foldsTrueEqualsFalseToFalseFilter() {
        LocalRelation base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new EqualTo(Literal$.MODULE$.TrueLiteral(), Literal$.MODULE$.FalseLiteral()), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base
                && Literal$.MODULE$.FalseLiteral().equals(f.condition()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesTrueNotEqualsFalseAlone() {
        LocalRelation base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new Not(new EqualTo(Literal$.MODULE$.TrueLiteral(), Literal$.MODULE$.FalseLiteral())), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTrueEqualsTrueAlone() {
        LocalRelation base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new EqualTo(Literal$.MODULE$.TrueLiteral(), Literal$.MODULE$.TrueLiteral()), base);
        assertEquals(plan, rule.apply(plan));
    }
}
