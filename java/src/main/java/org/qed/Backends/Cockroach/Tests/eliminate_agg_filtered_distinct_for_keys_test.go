package rulescript_test

import (
	"testing"
)

func TestEliminateAggFilteredDistinctForKeys(t *testing.T) {
	testEliminateKeyedAggDistinct(t, "EliminateAggFilteredDistinctForKeys")
}

func TestSQLEliminateAggFilteredDistinctForKeys(t *testing.T) {
	expectSQLScanConstraint(t, "EliminateAggFilteredDistinctForKeys", "SELECT x, count(DISTINCT x) FROM u GROUP BY x", "agg-distinct", "agg-distinct")
}
