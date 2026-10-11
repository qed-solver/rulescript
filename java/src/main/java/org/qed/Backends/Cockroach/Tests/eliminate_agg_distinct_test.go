package rulescript_test

import (
	"testing"
)

func TestEliminateAggDistinct(t *testing.T) {
	testEliminateKeyedAggDistinct(t, "EliminateAggDistinct")
}

func TestSQLEliminateAggDistinct(t *testing.T) {
	expectSQLScanConstraint(t, "EliminateAggDistinct", "SELECT x, count(DISTINCT x) FROM u GROUP BY x", "agg-distinct", "agg-distinct")
}
