package rulescript_test

import (
	"testing"
)

func TestRejectNullsUnderJoinRight(t *testing.T) {
	testRejectNullsUnderJoin(t, "RejectNullsUnderJoinRight", true)
}

func TestSQLRejectNullsUnderJoinRight(t *testing.T) {
	expectSQLFires(t, "RejectNullsUnderJoinRight", "SELECT * FROM (VALUES (1), (2)) AS u(x) JOIN (VALUES (1), (2)) AS t(a) ON u.x=t.a AND t.a IS NOT NULL")
}
