package rulescript_test

import (
	"testing"
)

func TestEliminateSetRight(t *testing.T) {
	testEliminateSetEmptyRight(t, "EliminateSetRight")
}

func TestSQLEliminateSetRight(t *testing.T) {
	expectSQLFiresWith(t, "EliminateSetRight",
		"SELECT c, a, b FROM t UNION ALL SELECT c, a, b FROM t WHERE false",
		sqlHelpers{names: []string{"SimplifyZeroCardinalityGroup", "PruneScanCols"}})
}
