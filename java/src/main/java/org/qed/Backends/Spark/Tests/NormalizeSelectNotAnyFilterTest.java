package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Exists;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.InSubquery;
import org.apache.spark.sql.catalyst.expressions.ListQuery;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.HintInfo;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NormalizeSelectNotAnyFilterTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("NormalizeSelectNotAnyFilter.json"));

    private static InSubquery inSubquery(List<Attribute> values, LogicalPlan sub) {
        var query = new ListQuery(sub, Catalyst.<Expression>seq(List.of()), NamedExpression$.MODULE$.newExprId(),
                sub.output().size(), Catalyst.<Expression>seq(List.of()), Option.<HintInfo>empty());
        return new InSubquery(Catalyst.<Expression>seq(values), query);
    }

    @Test
    void notInBecomesNotExists() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var sub = Support.relation("s", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Not(inSubquery(List.of(x), sub)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base && f.condition() instanceof Not n && n.child() instanceof Exists);
        assumeTrue(Support.canExecute());
        for (int seed = 1; seed <= 4; seed++) {
            var b = Support.relation("t", 1, List.of(), new Random(seed));
            var s = Support.relation("s", 1, List.of(), new Random(100 + seed));
            var bx = Catalyst.<Attribute>list(b.output()).get(0);
            var p = new Filter(new Not(inSubquery(List.of(bx), s)), b);
            assertEquals(Support.run(p), Support.run(rule.apply(p)));
        }
    }

    @Test
    void plainInIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var sub = Support.relation("s", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(inSubquery(List.of(x), sub), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void notInWithExtraConjunctIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var sub = Support.relation("s", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new And(new Not(inSubquery(List.of(x), sub)), new GreaterThan(x, Literal$.MODULE$.apply(2))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void notInOverTwoColumnsIsLeftAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var sub = Support.relation("s", 2, List.of(), new Random(2));
        var xs = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new Not(inSubquery(List.of(xs.get(0), xs.get(1)), sub)), base);
        assertEquals(plan, rule.apply(plan));
    }
}
