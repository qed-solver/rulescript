package rulescript_test

import (
	"testing"
)

func TestRejectNullsLeftJoin(t *testing.T) { testRejectNullsOuterJoin(t, "RejectNullsLeftJoin", false) }

func TestSQLRejectNullsLeftJoin(t *testing.T) {
	expectSQLFires(t, "RejectNullsLeftJoin", "SELECT * FROM (VALUES (1), (2)) AS u(x) LEFT JOIN (VALUES (1), (2)) AS t(a) ON u.x=t.a WHERE t.a IS NOT NULL")
}
