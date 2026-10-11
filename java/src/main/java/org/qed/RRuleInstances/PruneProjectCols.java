package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — fixed three-column inner project whose outer references only the passthrough and one synthesized column, pruning exactly the unused third column.
public record PruneProjectCols() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN passthrough = source.field(0);
    static final RexRN usedSyn = source.proj("Used", "Used_Type");
    static final RexRN unusedSyn = source.proj("Unused", "Unused_Type");
    static final RelRN inner =
            source.project(Seq.from(new RexRN[]{passthrough, usedSyn, unusedSyn}));
    static final SqlOperator top = RuleBuilder.create()
            .genericProjectionOp("Top", new RelType.VarType("Top_Type", true));

    @Override
    public RelRN before() {
        return inner.project(
                new RexRN.Proj(top, Seq.from(new RexRN[]{inner.field(0), inner.field(1)})));
    }

    @Override
    public RelRN after() {
        return source.project(
                new RexRN.Proj(top, Seq.from(new RexRN[]{passthrough, usedSyn})));
    }
}
