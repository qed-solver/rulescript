package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.types.BooleanType$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifySelectFiltersTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifySelectFilters.json"));

    private static Literal nullBoolean() {
        return Literal$.MODULE$.create(null, BooleanType$.MODULE$);
    }

    @Test
    void dropsNullDisjunctFromFilter() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        Attribute x = Catalyst.<Attribute>list(base.output()).get(0);
        var p = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Filter(new Or(p, nullBoolean()), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base && f.condition().equals(p));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsAndWithNullFilterAsIs() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        Attribute x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new And(new GreaterThan(x, Literal$.MODULE$.apply(2)), nullBoolean()), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsOrWithNonNullLiteralFilterAsIs() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        Attribute x = Catalyst.<Attribute>list(base.output()).get(0);
        var falseLiteral = Literal$.MODULE$.create(false, BooleanType$.MODULE$);
        var plan = new Filter(new Or(new GreaterThan(x, Literal$.MODULE$.apply(2)), falseLiteral), base);
        assertEquals(plan, rule.apply(plan));
    }
}
