package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — the window functions are modeled as partition-determined uninterpreted aggregates joined back to the input on the partition column (no frame/ordering semantics), with a single partition column, a single window function, a two-column input, one pushed conjunct that references only the partition column (the rule's ColsAreDeterminedBy precondition, encoded by construction), and one remaining conjunct over the window output row.
public record PushSelectIntoWindow() implements RRule {
    static final RelType.VarType kT = RexRN.varType("k", false);
    static final RelType.VarType vT = RexRN.varType("v", false);

    // Window's input: rows (k, v), where k is the partition column.
    static final RelRN input = RelRN.scanMany("Input", Seq.of(kT, vT));

    // The pushed conjunct: a predicate over the partition column k ONLY —
    // structurally it references no other column, satisfying the rule's
    // ColsAreDeterminedBy(OuterCols(P), partitionCols, input) precondition by
    // construction, so it is constant within each partition ("all-or-nothing").
    static final RexRN predP = input.field(0).pred("P");

    // The window function: value w is functionally determined by partition k —
    // an uninterpreted aggregate grouped by k over v.
    static final RelRN perPart = new RelRN.Aggregate(
            input,
            Seq.of(input.field(0)),
            Seq.of(input.field(1).aggCall("w")));

    // A Window operator's output shape (input cols + window cols) is an INNER
    // equi-join of the window input with the per-partition values on k: each
    // input row matches exactly one window row, preserving all input columns
    // and their multiplicity. Join layout: (k, v, k', w) = fields 0, 1, 2, 3.
    static final RexRN cond = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(input.joinField(0, perPart), input.joinField(2, perPart)));
    static final RelRN window = input.join(JoinRelType.INNER, cond, perPart);

    // The pushed conjunct re-applied over the window output's partition column
    // (index 0 in the join layout) — same symbol P, same logical column k.
    static final RexRN predPUp = window.field(0).pred("P");

    // The remaining conjunct: over the window output row (k, v, w) =
    // fields 0, 1, 3 of the join layout.
    static final SqlOperator qOp = RuleBuilder.create().genericPredicateOp("Q", true);
    static final RexRN predQ = new RexRN.Pred(qOp, Seq.of(
            window.field(0), window.field(1), window.field(3)));

    // After side: the window applied to the filtered input.
    static final RelRN filtered = input.filter(predP);
    static final RelRN perPartF = new RelRN.Aggregate(
            filtered,
            Seq.of(filtered.field(0)),
            Seq.of(filtered.field(1).aggCall("w")));
    static final RexRN condF = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(filtered.joinField(0, perPartF), filtered.joinField(2, perPartF)));
    static final RelRN windowF = filtered.join(JoinRelType.INNER, condF, perPartF);
    static final RexRN predQF = new RexRN.Pred(qOp, Seq.of(
            windowF.field(0), windowF.field(1), windowF.field(3)));

    @Override
    public RelRN before() {
        // Select(Window(input), P(k) AND Q(k, v, w))
        return window.filter(RexRN.and(predPUp, predQ));
    }

    @Override
    public RelRN after() {
        // Select(Window(Select(input, P)), Q(k, v, w))
        // P is pushed below the window: because P depends only on the
        // partition column it is constant per partition, so it eliminates
        // whole partitions below exactly as it eliminated whole partition
        // outputs above — surviving partitions keep all their rows, so the
        // window value w is unchanged, and Q stays above the window.
        return windowF.filter(predQF);
    }
}