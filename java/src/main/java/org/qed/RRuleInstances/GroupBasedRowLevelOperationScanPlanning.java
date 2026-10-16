package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — the command-condition conjuncts fully evaluated by the data source are pushed
// into the target-table scan and dropped from the MERGE join's condition, which the rule also does
// for non-inner join kinds, but the simplification is only a bag-equivalent rewrite for INNER joins
public record GroupBasedRowLevelOperationScanPlanning() implements RRule {

    // Target table of the row-level operation (Spark's DataSourceV2Relation for the row
    // operation table). One uninterpreted column stands in for the table schema.
    static final RelRN target = RelRN.scan("Target", "Target_Type");

    // The other input of the rewrite plan's MERGE join (e.g. the new rows).
    static final RelRN other = RelRN.scan("Other", "Other_Type");

    // Command-condition conjuncts that reference only target-table columns and are fully
    // evaluated on the data source side (Spark's evaluatedFilters): pushing them into the
    // scan makes the scan itself guarantee them.
    static final RexRN eval1 = target.pred("Eval1");
    static final RexRN eval2 = target.pred("Eval2");

    // The remaining part of the join condition after dropping the evaluated filters
    // (optimizeMergeJoinCondition: remainingPredicates.reduceLeftOption(And)); it may
    // reference columns of both inputs of the join.
    static final RexRN rest = target.joinPred("Rest", other);

    @Override
    public RelRN before() {
        // Join(PhysicalOperation(_, _, r: DataSourceV2Relation), _, _, Some(cond), _):
        // plain relation (no filters pushed down yet) joined with the full merge
        // condition, which still contains the evaluated-filter conjuncts.
        return target.join(JoinRelType.INNER, RexRN.and(eval1, eval2, rest), other);
    }

    @Override
    public RelRN after() {
        // The relation is replaced by a DataSourceV2ScanRelation whose scan has pushed
        // and fully evaluated Eval1 and Eval2, so the join condition is simplified to
        // just the remaining conjuncts.
        return target.filter(RexRN.and(eval1, eval2)).join(JoinRelType.INNER, rest, other);
    }
}
