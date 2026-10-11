package rulescript_test

import (
	"testing"
)

func TestPruneProjectSetCols(t *testing.T) {
	testPruneJoinLeftCols(t, "PruneProjectSetCols")
}

func TestSQLPruneProjectSetCols(t *testing.T) {
	expectSQLFiresOrNot(t, "PruneProjectSetCols", "SELECT l.a+r.b FROM (VALUES (1,2),(3,4)) AS l(a,z) JOIN (VALUES (1),(2)) AS r(b) ON l.a=r.b", true)
}
