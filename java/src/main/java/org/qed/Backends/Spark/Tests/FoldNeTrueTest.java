package org.qed.spark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.AttributeReference;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.types.BooleanType$;
import org.apache.spark.sql.types.Metadata;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class FoldNeTrueTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldNeTrue.json"));

    private static LocalRelation booleans(String name) {
        var attr = new AttributeReference(name, BooleanType$.MODULE$, true, Metadata.empty(),
                NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()));
        var rows = new ArrayList<InternalRow>();
        for (Object value : new Object[] {Boolean.TRUE, Boolean.FALSE, null}) {
            rows.add(new GenericInternalRow(new Object[] {value}));
        }
        return new LocalRelation(Catalyst.<Attribute>seq(List.of(attr)), Catalyst.<InternalRow>seq(rows),
                false, Match.noStream());
    }

    @Test
    void foldsNotEqualTrueIntoNot() {
        var base = booleans("b");
        var b = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Not(new EqualTo(b, Literal$.MODULE$.TrueLiteral())), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        assertEquals(new Not(b), ((Filter) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesEqualTrueAlone() {
        var base = booleans("b");
        var b = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualTo(b, Literal$.MODULE$.TrueLiteral()), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNotEqualFalseAlone() {
        var base = booleans("b");
        var b = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Not(new EqualTo(b, Literal$.MODULE$.FalseLiteral())), base);
        assertEquals(plan, rule.apply(plan));
    }
}
