package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — fixed single-row three-column Values pruned to the two referenced columns, modeled with concrete literal content.
public record PruneValuesCols() implements RRule {
    record LiteralValues(Object[] rowValues) implements RelRN {
        @Override
        public RelNode semantics() {
            String[] fields = new String[rowValues.length];
            for (int i = 0; i < rowValues.length; i++) fields[i] = "c" + i;
            return RuleBuilder.create().values(fields, rowValues).build();
        }
    }

    static final RelRN values3 = new LiteralValues(new Object[]{1, 2, 3});
    static final RelRN values2 = new LiteralValues(new Object[]{1, 2});
    static final SqlOperator top = RuleBuilder.create()
            .genericProjectionOp("Top", new RelType.VarType("Top_Type", true));

    @Override
    public RelRN before() {
        return values3.project(
                Seq.of(new RexRN.Proj(top, Seq.of(values3.field(0))), values3.field(1)));
    }

    @Override
    public RelRN after() {
        return values2.project(
                Seq.of(new RexRN.Proj(top, Seq.of(values2.field(0))), values2.field(1)));
    }
}
