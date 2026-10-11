package rulescript_test

import (
	"testing"
)

func TestEliminateDistinctSetRight(t *testing.T) {
	testEliminateDistinctSet(t, "EliminateDistinctSetRight")
}

func TestSQLEliminateDistinctSetRight(t *testing.T) {
	expectSQLFiresWith(t, "EliminateDistinctSetRight",
		"SELECT c, a, b FROM t UNION SELECT c, a, b FROM t WHERE false",
		sqlHelpers{names: []string{"SimplifyZeroCardinalityGroup", "PruneScanCols"}})
}
