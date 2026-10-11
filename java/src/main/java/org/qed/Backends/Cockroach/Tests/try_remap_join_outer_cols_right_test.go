package rulescript_test

import (
	"testing"
)

func TestTryRemapJoinOuterColsRight(t *testing.T) {
	testTryRemapJoinOuterCols(t, "TryRemapJoinOuterColsRight", false)
}

func TestSQLTryRemapJoinOuterColsRight(t *testing.T) {
	expectSQLFires(t, "TryRemapJoinOuterColsRight", "SELECT * FROM (VALUES (1)) AS l(a) JOIN LATERAL (SELECT b FROM (VALUES (1)) AS r(b)) AS q ON l.a=q.b WHERE q.b>0")
}
