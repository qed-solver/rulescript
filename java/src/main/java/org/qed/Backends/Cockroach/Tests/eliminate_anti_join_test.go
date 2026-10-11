package rulescript_test

import (
	"testing"
)

func TestEliminateAntiJoin(t *testing.T) {
	testAntiJoinEmpty(t, "EliminateAntiJoin")
}

func TestSQLEliminateAntiJoin(t *testing.T) {
	expectSQLFiresWith(t, "EliminateAntiJoin",
		"SELECT * FROM (SELECT a FROM t) q WHERE q.a NOT IN (SELECT x FROM u WHERE false)",
		sqlHelpers{limits: map[string]int{"SimplifyZeroCardinalityGroup": 1}, names: []string{"NormalizeSelectNotAnyFilter", "HoistSelectNotExists", "TryDecorrelateSelect", "TryDecorrelateProject", "TryDecorrelateProjectSelect", "DecorrelateJoin", "PruneValuesCols", "EliminateProject", "PruneScanCols"}})
}
