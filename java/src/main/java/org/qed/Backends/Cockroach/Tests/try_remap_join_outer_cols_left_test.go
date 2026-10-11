package rulescript_test

import (
	"testing"
)

func TestTryRemapJoinOuterColsLeft(t *testing.T) {
	testTryRemapJoinOuterCols(t, "TryRemapJoinOuterColsLeft", true)
}

func TestSQLTryRemapJoinOuterColsLeft(t *testing.T) {
	expectSQLFires(t, "TryRemapJoinOuterColsLeft", "SELECT * FROM (VALUES (1)) AS l(a) JOIN LATERAL (SELECT b FROM (VALUES (1)) AS r(b)) AS q ON l.a=q.b WHERE l.a>0")
}
