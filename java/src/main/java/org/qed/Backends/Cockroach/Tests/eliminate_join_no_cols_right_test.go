package rulescript_test

import (
	"testing"
)

func TestEliminateJoinNoColsRight(t *testing.T) {
	testEliminateJoinNoCols(t, "EliminateJoinNoColsRight", false)
}

func TestSQLEliminateJoinNoColsRight(t *testing.T) {
	expectSQLFires(t, "EliminateJoinNoColsRight", "SELECT l.x FROM (VALUES (1)) AS l(x) JOIN (SELECT FROM (VALUES (TRUE)) AS v(flag)) AS z ON l.x=1")
}
