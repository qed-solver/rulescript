package rulescript_test

import (
	"testing"
)

func TestRejectNullsUnderJoinLeft(t *testing.T) {
	testRejectNullsUnderJoin(t, "RejectNullsUnderJoinLeft", false)
}

func TestSQLRejectNullsUnderJoinLeft(t *testing.T) {
	expectSQLFires(t, "RejectNullsUnderJoinLeft", "SELECT * FROM (VALUES (1), (2)) AS u(x) JOIN (VALUES (1), (2)) AS t(a) ON u.x=t.a AND u.x IS NOT NULL")
}
