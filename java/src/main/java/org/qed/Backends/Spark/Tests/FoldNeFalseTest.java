package org.qed.spark;

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
import org.apache.spark.sql.types.BooleanType$;
import org.apache.spark.sql.types.Metadata;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldNeFalseTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldNeFalse.json"));

    private static Attribute booleanColumn(String name) {
        return new AttributeReference(name, BooleanType$.MODULE$, true, Metadata.empty(),
                NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()));
    }

    private static LocalRelation booleanRelation(Attribute column) {
        var rows = new ArrayList<InternalRow>();
        for (Object value : new Object[] {true, false, null, true}) {
            rows.add(new GenericInternalRow(new Object[] {value}));
        }
        return new LocalRelation(Catalyst.seq(List.of(column)), Catalyst.seq(rows), false, Match.noStream());
    }

    @Test
    void notEqualToFalseBecomesPlainCondition() {
        var column = booleanColumn("b");
        var base = booleanRelation(column);
        var plan = new Filter(new Not(new EqualTo(column, Literal$.MODULE$.FalseLiteral())), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.condition().equals(column) && f.child() == base);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void notEqualToTrueIsLeftAlone() {
        var column = booleanColumn("b");
        var base = booleanRelation(column);
        var plan = new Filter(new Not(new EqualTo(column, Literal$.MODULE$.TrueLiteral())), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void equalToFalseIsLeftAlone() {
        var column = booleanColumn("b");
        var base = booleanRelation(column);
        var plan = new Filter(new EqualTo(column, Literal$.MODULE$.FalseLiteral()), base);
        assertEquals(plan, rule.apply(plan));
    }
}
