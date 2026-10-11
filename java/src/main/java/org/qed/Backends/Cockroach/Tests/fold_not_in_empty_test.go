package rulescript_test

import (
	"testing"
)

func TestFoldNotInEmpty(t *testing.T) {
	testAntiJoinEmpty(t, "FoldNotInEmpty")
}

func TestSQLFoldNotInEmpty(t *testing.T) {
	expectSQLFiresWith(t, "FoldNotInEmpty",
		"SELECT * FROM (SELECT a FROM t) q WHERE q.a NOT IN (SELECT x FROM u WHERE false)",
		sqlHelpers{limits: map[string]int{"SimplifyZeroCardinalityGroup": 1}, names: []string{"NormalizeSelectNotAnyFilter", "HoistSelectNotExists", "TryDecorrelateSelect", "TryDecorrelateProject", "TryDecorrelateProjectSelect", "DecorrelateJoin", "PruneValuesCols", "EliminateProject", "PruneScanCols"}})
}
