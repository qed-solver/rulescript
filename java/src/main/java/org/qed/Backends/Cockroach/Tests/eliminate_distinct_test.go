package rulescript_test

import (
	"testing"
)

func TestEliminateDistinct(t *testing.T) {
	testEliminateKeyedGrouping(t, "EliminateDistinct")
}

func TestSQLEliminateDistinct(t *testing.T) {
	expectSQLScanConstraint(t, "EliminateDistinct", "SELECT x FROM u GROUP BY x", "group-by", "group-by")
}
