package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — only the Project-over-LocalRelation branch is modeled, on a fixed two-row three-column relation with a pure column-permutation projection; the Limit, LocalLimit, and Filter branches are not modeled.
public record ConvertToLocalRelation() implements RRule {
    record LiteralValues(Object[][] rows) implements RelRN {
        @Override
        public RelNode semantics() {
            int ncols = rows[0].length;
            String[] fields = new String[ncols];
            for (int i = 0; i < ncols; i++) fields[i] = "c" + i;
            Object[] flat = new Object[rows.length * ncols];
            for (int r = 0; r < rows.length; r++) {
                System.arraycopy(rows[r], 0, flat, r * ncols, ncols);
            }
            return RuleBuilder.create().values(fields, flat).build();
        }
    }

    static final RelRN localRelation = new LiteralValues(new Object[][]{{1, 2, 3}, {4, 5, 6}});

    @Override
    public RelRN before() {
        return localRelation.project(Seq.of(localRelation.field(2), localRelation.field(0)));
    }

    @Override
    public RelRN after() {
        return new LiteralValues(new Object[][]{{3, 1}, {6, 4}});
    }
}