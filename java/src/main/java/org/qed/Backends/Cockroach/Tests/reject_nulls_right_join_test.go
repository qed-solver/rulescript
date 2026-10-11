package rulescript_test

import (
	"testing"
)

func TestRejectNullsRightJoin(t *testing.T) {
	testRejectNullsOuterJoin(t, "RejectNullsRightJoin", true)
}

func TestSQLRejectNullsRightJoin(t *testing.T) {
	expectSQLFires(t, "RejectNullsRightJoin", "SELECT * FROM (VALUES (1), (2)) AS u(x) FULL JOIN (VALUES (1), (2)) AS t(a) ON u.x=t.a WHERE u.x IS NOT NULL")
}
