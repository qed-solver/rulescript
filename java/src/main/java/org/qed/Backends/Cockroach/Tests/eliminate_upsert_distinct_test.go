package rulescript_test

import (
	"testing"
)

func TestEliminateUpsertDistinct(t *testing.T) {
	testEliminateKeyedGrouping(t, "EliminateUpsertDistinct")
}

func TestSQLEliminateUpsertDistinct(t *testing.T) {
	expectSQLScanConstraint(t, "EliminateUpsertDistinct", "SELECT x FROM u GROUP BY x", "group-by", "group-by")
}
