package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PruneScanColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneScanCols.json"));

    private static NamedExpression item(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void prunesTheUnreferencedThirdColumn() {
        var base = Support.relation("t", 3, List.of(), new Random(7));
        var cols = Catalyst.<Attribute>list(base.output());
        var sum = Add$.MODULE$.apply(cols.get(0), cols.get(1), Add$.MODULE$.apply$default$3());
        var plan = new Project(Catalyst.seq(List.of(item(sum, "top"))), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project outer && outer.child() instanceof Project inner
                && inner.child() == base && inner.output().size() == 2);
        assertEquals(Catalyst.list(plan.output()).size(), Catalyst.list(rewritten.output()).size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesATwoColumnScanAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(7));
        var cols = Catalyst.<Attribute>list(base.output());
        var sum = Add$.MODULE$.apply(cols.get(0), cols.get(1), Add$.MODULE$.apply$default$3());
        var plan = new Project(Catalyst.seq(List.of(item(sum, "top"))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAProjectionThatSkipsColumnOneAlone() {
        var base = Support.relation("t", 3, List.of(), new Random(7));
        var cols = Catalyst.<Attribute>list(base.output());
        var sum = Add$.MODULE$.apply(cols.get(0), cols.get(2), Add$.MODULE$.apply$default$3());
        var plan = new Project(Catalyst.seq(List.of(item(sum, "top"))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void firesWhenTheThreeColumnInputIsAFilter() {
        var base = Support.relation("t", 3, List.of(), new Random(7));
        var cols = Catalyst.<Attribute>list(base.output());
        var filtered = new Filter(new GreaterThan(cols.get(2), Literal$.MODULE$.apply(2)), base);
        var sum = Add$.MODULE$.apply(cols.get(0), cols.get(1), Add$.MODULE$.apply$default$3());
        var plan = new Project(Catalyst.seq(List.of(item(sum, "top"))), filtered);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAProjectionWithTwoOutputsAlone() {
        var base = Support.relation("t", 3, List.of(), new Random(7));
        var cols = Catalyst.<Attribute>list(base.output());
        var sum = Add$.MODULE$.apply(cols.get(0), cols.get(1), Add$.MODULE$.apply$default$3());
        var plan = new Project(Catalyst.seq(List.of(item(sum, "top"), item(cols.get(2), "extra"))), base);
        assertEquals(plan, rule.apply(plan));
    }
}
