package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldComparisonWithAnyTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldComparisonWithAny.json"));

    @Test
    void foldsTrueEqualsAnyToTrue() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var trueLit = Literal$.MODULE$.TrueLiteral();
        var falseLit = Literal$.MODULE$.FalseLiteral();
        var plan = new Filter(new Or(new EqualTo(trueLit, falseLit), new EqualTo(trueLit, trueLit)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base
                && f.condition() instanceof Literal l && Boolean.TRUE.equals(l.value()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesDisjunctionWithSwappedOperandsAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(3));
        var trueLit = Literal$.MODULE$.TrueLiteral();
        var falseLit = Literal$.MODULE$.FalseLiteral();
        var plan = new Filter(new Or(new EqualTo(trueLit, falseLit), new EqualTo(falseLit, trueLit)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesSingleTrueEqualsTrueAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(4));
        var trueLit = Literal$.MODULE$.TrueLiteral();
        var plan = new Filter(new EqualTo(trueLit, trueLit), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDisjunctionWithOtherOperatorAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(5));
        var trueLit = Literal$.MODULE$.TrueLiteral();
        var falseLit = Literal$.MODULE$.FalseLiteral();
        var plan = new Filter(new Or(new EqualTo(trueLit, falseLit), new GreaterThan(trueLit, falseLit)), base);
        assertEquals(plan, rule.apply(plan));
    }
}
