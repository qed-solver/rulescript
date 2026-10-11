package rulescript_test

import (
	"testing"
)

func TestEliminateDistinctSetLeft(t *testing.T) {
	testEliminateDistinctSet(t, "EliminateDistinctSetLeft")
}

func TestSQLEliminateDistinctSetLeft(t *testing.T) {
	expectSQLFiresWith(t, "EliminateDistinctSetLeft",
		"SELECT c, a, b FROM t UNION SELECT c, a, b FROM t WHERE false",
		sqlHelpers{names: []string{"SimplifyZeroCardinalityGroup", "PruneScanCols"}})
}
