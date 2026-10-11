package rulescript_test

import (
	"testing"
)

func TestEliminateJoinNoColsLeft(t *testing.T) {
	testEliminateJoinNoCols(t, "EliminateJoinNoColsLeft", true)
}

func TestSQLEliminateJoinNoColsLeft(t *testing.T) {
	expectSQLFires(t, "EliminateJoinNoColsLeft", "SELECT r.x FROM (SELECT FROM (VALUES (TRUE)) AS v(flag)) AS z JOIN (VALUES (1)) AS r(x) ON r.x=1")
}
