package rulescript_test

import (
	"testing"
)

func TestEliminateAggDistinctForKeys(t *testing.T) {
	testEliminateKeyedAggDistinct(t, "EliminateAggDistinctForKeys")
}

func TestSQLEliminateAggDistinctForKeys(t *testing.T) {
	expectSQLScanConstraint(t, "EliminateAggDistinctForKeys", "SELECT x, count(DISTINCT x) FROM u GROUP BY x", "agg-distinct", "agg-distinct")
}
