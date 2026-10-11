package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — the ordinality-generated column is modeled as one shared uninterpreted function O of the input columns (the ForDuplicateRemoval case, where O's values don't matter) and every filter conjunct references only input columns (no unbound conjunct over O, so the outer Select is vacuous).
public record PushSelectIntoOrdinality() implements RRule {
    // The Ordinality input: two uninterpreted columns.
    static final RelRN input = RelRN.scanMany("IN", Seq.of(
            new RelType.VarType("C0_Type", true),
            new RelType.VarType("C1_Type", true)));

    // O — the ordinality-generated column, ONE shared uninterpreted symbol on
    // both sides: the same logical quantity re-derived after the Select is
    // pushed beneath the Ordinality. ForDuplicateRemoval holds (EnsureKey
    // built the Ordinality just to give the rows a unique key), so O's actual
    // values don't matter and row-position/ordering semantics are not needed:
    // it is modeled as an uninterpreted function of the input row.
    static final SqlOperator oOp = RuleBuilder.create().genericProjectionOp("O", new RelType.VarType("O_Type", true));

    @Override
    public RelRN before() {
        // Ordinality($input $private): row = (c0, c1, O(c0, c1)).
        RelRN ord = input.project(Seq.of(
                input.field(0), input.field(1),
                new RexRN.Proj(oOp, Seq.of(input.field(0), input.field(1)))));
        // Select over the Ordinality whose conjunct P is bound by the input's
        // output columns (IsBoundBy $item (OutputCols $input)) — defined on
        // the input column c0, not on O.
        return ord.filter(ord.field(0).pred("P"));
    }

    @Override
    public RelRN after() {
        // (Select $input (ExtractBoundConditions $filters $inputCols)): the
        // bound conjunct P is pushed below the Ordinality, same predicate name
        // over the same underlying column c0.
        RelRN filtered = input.filter(input.field(0).pred("P"));
        // Ordinality re-derived over the filtered input, reusing the same O:
        // row = (c0, c1, O(c0, c1)) — the filter passthrough makes the
        // arguments the same symbols as on the before side.
        RelRN ord = filtered.project(Seq.of(
                filtered.field(0), filtered.field(1),
                new RexRN.Proj(oOp, Seq.of(filtered.field(0), filtered.field(1)))));
        // ExtractUnboundConditions is empty (every conjunct is bound), so the
        // outer Select is vacuous and omitted per EliminateSelect.
        return ord;
    }
}