package rulescript_test

import (
	"testing"
)

func TestEliminateSetLeft(t *testing.T) {
	testEliminateSetEmptyRight(t, "EliminateSetLeft")
}

func TestSQLEliminateSetLeft(t *testing.T) {
	expectSQLFiresWith(t, "EliminateSetLeft",
		"SELECT c, a, b FROM t UNION ALL SELECT c, a, b FROM t WHERE false",
		sqlHelpers{names: []string{"SimplifyZeroCardinalityGroup", "PruneScanCols"}})
}
