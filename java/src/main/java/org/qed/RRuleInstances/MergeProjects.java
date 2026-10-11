package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — one-column source and the outer projection references only the inner passthrough column, so the inner synthesized column is pruned.
public record MergeProjects() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN passthrough = source.field(0);
    static final RexRN synthesized = source.proj("Bottom", "Bottom_Type");
    static final RelRN inner =
            source.project(Seq.from(new RexRN[]{passthrough, synthesized}));
    static final SqlOperator top = RuleBuilder.create()
            .genericProjectionOp("Top", new RelType.VarType("Top_Type", true));

    @Override
    public RelRN before() {
        return inner.project(inner.field(0).proj(top));
    }

    @Override
    public RelRN after() {
        return source.project(passthrough.proj(top));
    }
}
