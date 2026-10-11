package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — one inner synthesized column and one outer computed expression, with a single outer passthrough column equal to that inner synthesized column (no additional inner passthrough or outer columns; the HasDuplicateRefs guard is not bag-semantic).
public record InlineProjectInProject() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");

    // Inner synthesized column: arbitrary uninterpreted function F over the source
    // column, e.g. `x+1 AS x2` in the rule's example.
    static final RexRN f = source.proj("F", "F_Type");
    static final RelRN inner = source.project(Seq.of(f));

    // Outer computed expression: arbitrary uninterpreted function T over the
    // (single) column of the inner project, e.g. `x2*2`.
    static final SqlOperator topOp =
            RuleBuilder.create().genericProjectionOp("T", new RelType.VarType("T_Type", true));

    // Both outer outputs reference the inner synthesized column: the computed
    // column T(x2) and the passthrough column x2 itself.
    static final RexRN innerCol = inner.field(0);
    static final RexRN tOfInnerCol = innerCol.proj(topOp);

    @Override
    public RelRN before() {
        return inner.project(Seq.of(tOfInnerCol, innerCol));
    }

    @Override
    public RelRN after() {
        // Inlined: both references to the inner synthesized column are replaced by
        // its defining expression F(c), and the inner project is removed.
        return source.project(Seq.of(f.proj(topOp), f));
    }
}
