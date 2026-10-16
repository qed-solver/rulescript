package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — the partition-pruned file index is modeled as exactly the rows of the original scan matching the partition-key filters; Spark's real pruned index may additionally retain partitions it could not evaluate (a superset whose extra rows are eliminated by the kept full filter), a looser containment between scans the DSL cannot express.
public record PruneFileSourcePartitions() implements RRule {
    // File source table: two partition columns followed by a data column.
    static final RelRN source = RelRN.scanMany("Source", Seq.of(
            RexRN.varType("Part0_Type", true),
            RexRN.varType("Part1_Type", true),
            RexRN.varType("Data0_Type", true)));

    // The partition-key filters: an uninterpreted predicate over the partition
    // columns only (Spark sends a conjunct to the file index only when it
    // references partition columns exclusively).
    static final SqlOperator partOp = RuleBuilder.create().genericPredicateOp("P", true);
    static final RexRN partFilter = new RexRN.Pred(partOp, Seq.of(
            source.field(0), source.field(1)));

    // The remaining (data) filters: an uninterpreted predicate over the row.
    static final RexRN dataFilter = source.pred("D");

    // The full filter, kept above the pruned scan by the rule: the
    // conjunction of the partition-key filters and the remaining filters.
    static final RexRN fullFilter = RexRN.and(partFilter, dataFilter);

    // The partition-pruned relation: the same file source restricted to the
    // partitions matching the partition-key filters (bag-level model of
    // catalogFileIndex.filterPartitions(partitionKeyFilters)).
    static final RelRN pruned = source.filter(partFilter);

    // The PhysicalOperation's project list: an uninterpreted projection over
    // the row.
    static final SqlOperator piOp = RuleBuilder.create()
            .genericProjectionOp("Pi", new RelType.VarType("Pi_Type", true));

    static RelRN project(RelRN input) {
        return input.project(new RexRN.Proj(piOp, Seq.of(
                input.field(0), input.field(1), input.field(2))));
    }

    @Override
    public RelRN before() {
        // Project(projects, Filter(AND(all filters), relation))
        return project(source.filter(fullFilter));
    }

    @Override
    public RelRN after() {
        // Project(projects, Filter(AND(all filters), prunedRelation))
        return project(pruned.filter(fullFilter));
    }
}
