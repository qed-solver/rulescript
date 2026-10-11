package rulescript_test

import (
	"testing"
)

func TestPruneJoinLeftCols(t *testing.T) {
	testPruneJoinLeftCols(t, "PruneJoinLeftCols")
}

func TestSQLPruneJoinLeftCols(t *testing.T) {
	expectSQLFiresOrNot(t, "PruneJoinLeftCols", "SELECT l.a+r.b FROM (VALUES (1,2),(3,4)) AS l(a,z) JOIN (VALUES (1),(2)) AS r(b) ON l.a=r.b", true)
}
